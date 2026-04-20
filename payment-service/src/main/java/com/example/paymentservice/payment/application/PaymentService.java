package com.example.paymentservice.payment.application;

import com.example.paymentservice.common.exception.BusinessException;
import com.example.paymentservice.common.exception.ErrorCode;
import com.example.paymentservice.payment.application.dto.PaymentConfirmCommand;
import com.example.paymentservice.payment.application.dto.PaymentInfo;
import com.example.paymentservice.payment.application.event.PaymentCompletedEvent;
import com.example.paymentservice.payment.application.event.PaymentFailedEvent;
import com.example.paymentservice.payment.client.PaymentGateway;
import com.example.paymentservice.payment.client.PaymentGateway.PaymentGatewayResponse;
import com.example.paymentservice.payment.domain.PaymentStatus;
import com.example.paymentservice.payment.domain.model.Payment;
import com.example.paymentservice.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate txTemplate;

    private static final int MAX_RETRIES = 3;

    /**
     * 결제 승인 — TransactionTemplate으로 트랜잭션 분리
     *
     * [TX1] 멱등성 체크 + Payment 생성(IN_PROGRESS)
     * [TX 밖] 토스 PG confirm API 호출 (DB 커넥션 점유 X)
     * [TX2] 결과에 따라 SUCCESS 또는 FAILED 처리
     */
    public PaymentInfo confirmPayment(PaymentConfirmCommand command) {

        // ━━━ TX1: 멱등성 체크 + Payment 생성(IN_PROGRESS) ━━━
        Payment result = txTemplate.execute(status -> {
            Optional<Payment> existing = paymentRepository.findByOrderId(command.orderId());
            if (existing.isPresent() && existing.get().getStatus() == PaymentStatus.SUCCESS) {
                return existing.get();
            }

            Payment payment = Payment.create(command.userId(), command.amount(), command.cookieAmount());
            return paymentRepository.save(payment);
        });

        // 멱등성: 이미 성공한 결제면 바로 반환 (새 Payment는 IN_PROGRESS이므로 구분 가능)
        if (result.getStatus() == PaymentStatus.SUCCESS) {
            log.info("[Payment] 멱등성 - 이미 완료된 결제: orderId={}", command.orderId());
            return PaymentInfo.from(result);
        }

        Payment inProgress = result;

        Long paymentId = inProgress.getId();
        log.info("[Payment] 결제 처리 시작 - paymentId: {}, amount: {}", paymentId, inProgress.getAmount());

        // ━━━ TX 밖: PG API 호출 (DB 커넥션 점유 X) ━━━
        PaymentGatewayResponse pgResponse = null;
        Exception pgException = null;
        try {
            pgResponse = paymentGateway.confirmPayment(
                    command.paymentKey(), command.orderId(), inProgress.getAmount());
        } catch (Exception e) {
            log.error("[Payment] PG 결제 확인 실패: paymentId={}, reason={}", paymentId, e.getMessage(), e);
            pgException = e;
        }

        // ━━━ TX2: 결과에 따라 최종 DB 반영 ━━━
        if (pgException != null) {
            txTemplate.executeWithoutResult(s -> {
                Payment p = paymentRepository.findById(paymentId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
                p.markFailed();
                eventPublisher.publishEvent(PaymentFailedEvent.of(p.getId(), p.getUserId()));
            });
            throw new BusinessException(ErrorCode.PG_CONFIRM_FAILED);
        }

        String pgPaymentKey = pgResponse.paymentKey();
        return retryCommit(paymentId, pgPaymentKey, command.orderId());
    }

    /**
     * TX2 성공 처리 — PG에서 돈이 빠진 후이므로 DB 반영 실패 시 재시도
     */
    private PaymentInfo retryCommit(Long paymentId, String pgPaymentKey, String orderId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                PaymentInfo result = txTemplate.execute(s -> {
                    Payment p = paymentRepository.findById(paymentId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
                    p.markSuccess(pgPaymentKey, orderId);
                    eventPublisher.publishEvent(
                            PaymentCompletedEvent.of(p.getId(), p.getUserId(),
                                    p.getAmount(), p.getCookieAmount()));
                    return PaymentInfo.from(p);
                });
                log.info("[Payment] 결제 완료 - paymentId: {}", paymentId);
                return result;
            } catch (Exception e) {
                log.warn("[Payment] DB 저장 실패 (시도 {}/{}) - paymentId: {}",
                        attempt, MAX_RETRIES, paymentId);
                if (attempt == MAX_RETRIES) {
                    log.error("[Payment] 결제 DB 반영 최종 실패! paymentId={}", paymentId);
                    throw e;
                }
                try { Thread.sleep(500L * attempt); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    @Transactional
    public PaymentInfo failPayment(Long paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        payment.markFailed();
        eventPublisher.publishEvent(PaymentFailedEvent.of(payment.getId(), payment.getUserId()));
        return PaymentInfo.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        return PaymentInfo.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentInfo> getPaymentsByUser(UUID userId) {
        return paymentRepository.findByUserId(userId).stream()
                .map(PaymentInfo::from)
                .toList();
    }
}
