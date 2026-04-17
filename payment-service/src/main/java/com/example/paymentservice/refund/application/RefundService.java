package com.example.paymentservice.refund.application;

import com.example.paymentservice.common.exception.BusinessException;
import com.example.paymentservice.common.exception.ErrorCode;
import com.example.paymentservice.payment.domain.PaymentStatus;
import com.example.paymentservice.payment.domain.model.Payment;
import com.example.paymentservice.payment.domain.repository.PaymentRepository;
import com.example.paymentservice.refund.application.dto.RefundCommand;
import com.example.paymentservice.refund.application.dto.RefundInfo;
import com.example.paymentservice.refund.application.event.RefundApprovedEvent;
import com.example.paymentservice.refund.client.RefundGateway;
import com.example.paymentservice.refund.domain.RefundStatus;
import com.example.paymentservice.refund.domain.model.Refund;
import com.example.paymentservice.refund.domain.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final int REFUND_AVAILABLE_DAYS = 7;
    private static final int MAX_RETRIES = 3;

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final RefundGateway refundGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate txTemplate;

    @Transactional
    public RefundInfo requestRefund(RefundCommand command) {
        Payment payment = paymentRepository.findByIdForUpdate(command.paymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        if (payment.getCreatedAt().plusDays(REFUND_AVAILABLE_DAYS).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.REFUND_PERIOD_EXPIRED);
        }

        if (command.cookieAmount() > payment.getCookieAmount()) {
            throw new BusinessException(ErrorCode.REFUND_EXCEEDS_PAYMENT);
        }

        int totalRefundedCookies = refundRepository.findByPaymentId(command.paymentId()).stream()
                .filter(r -> r.getStatus() != RefundStatus.FAILED)
                .mapToInt(Refund::getCookieAmount)
                .sum();
        if (totalRefundedCookies + command.cookieAmount() > payment.getCookieAmount()) {
            throw new BusinessException(ErrorCode.REFUND_EXCEEDS_PAYMENT);
        }

        int wonAmount = BigDecimal.valueOf(command.cookieAmount())
                .multiply(BigDecimal.valueOf(payment.getAmount()))
                .divide(BigDecimal.valueOf(payment.getCookieAmount()), 0, RoundingMode.HALF_UP)
                .intValue();

        Refund refund = Refund.create(command.paymentId(), command.userId(), wonAmount, command.cookieAmount());
        return RefundInfo.from(refundRepository.save(refund));
    }

    /**
     * 환불 승인 — TransactionTemplate으로 트랜잭션 분리
     *
     * [TX1] 검증 + PROCESSING 상태 변경 (비관적 락으로 이중 승인 방지)
     * [TX 밖] 토스 PG 취소 API 호출 (DB 커넥션 점유 X)
     * [TX2] 결과에 따라 SUCCESS 또는 FAILED 처리
     */
    public RefundInfo approveRefund(Long refundId) {

        // ━━━ TX1: 검증 + PROCESSING ━━━
        Refund processingRefund = txTemplate.execute(status -> {
            Refund refund = refundRepository.findByIdForUpdate(refundId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

            refund.validatePending();
            refund.markProcessing();
            return refund;
        });

        Payment payment = paymentRepository.findById(processingRefund.getPaymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        String paymentKey = payment.getPaymentKey();
        int refundAmount = processingRefund.getAmount();

        log.info("[Refund] 환불 처리 시작 - refundId: {}, amount: {}", refundId, refundAmount);

        // ━━━ TX 밖: PG 취소 API 호출 (DB 커넥션 점유 X) ━━━
        Exception pgException = null;
        try {
            refundGateway.cancelPayment(paymentKey, refundAmount, "환불 확인");
        } catch (Exception e) {
            log.error("[Refund] PG 환불 실패 - refundId: {}, paymentKey: {}, message: {}",
                    refundId, paymentKey, e.getMessage());
            pgException = e;
        }

        // ━━━ TX2: 결과에 따라 최종 DB 반영 ━━━
        if (pgException != null) {
            txTemplate.executeWithoutResult(s -> {
                Refund r = refundRepository.findById(refundId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
                r.markProcessingFailed();
            });
            throw new BusinessException(ErrorCode.PG_REFUND_FAILED);
        }

        return retryCommit(refundId);
    }

    /**
     * TX2 성공 처리 — PG에서 환불된 후이므로 DB 반영 실패 시 재시도
     */
    private RefundInfo retryCommit(Long refundId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                RefundInfo result = txTemplate.execute(s -> {
                    Refund r = refundRepository.findById(refundId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
                    r.markSuccess();
                    eventPublisher.publishEvent(
                            RefundApprovedEvent.of(r.getId(), r.getPaymentId(),
                                    r.getUserId(), r.getAmount(), r.getCookieAmount()));
                    return RefundInfo.from(r);
                });
                log.info("[Refund] 환불 완료 - refundId: {}", refundId);
                return result;
            } catch (Exception e) {
                log.warn("[Refund] DB 저장 실패 (시도 {}/{}) - refundId: {}",
                        attempt, MAX_RETRIES, refundId);
                if (attempt == MAX_RETRIES) {
                    log.error("[Refund] 환불 DB 반영 최종 실패! refundId={}", refundId);
                    throw e;
                }
                try { Thread.sleep(500L * attempt); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    @Transactional
    public RefundInfo rejectRefund(Long refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        refund.markFailed();
        return RefundInfo.from(refund);
    }

    @Transactional(readOnly = true)
    public RefundInfo getRefund(Long refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
        return RefundInfo.from(refund);
    }

    @Transactional(readOnly = true)
    public List<RefundInfo> getRefundsByUser(UUID userId) {
        return refundRepository.findByUserId(userId).stream()
                .map(RefundInfo::from)
                .toList();
    }
}
