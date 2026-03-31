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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {

    private static final int REFUND_AVAILABLE_DAYS = 7;

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final RefundGateway refundGateway;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public RefundInfo requestRefund(RefundCommand command) {
        Payment payment = paymentRepository.findByIdForUpdate(command.paymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        // 구매 후 N일 이내만 환불 가능
        if (payment.getCreatedAt().plusDays(REFUND_AVAILABLE_DAYS).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.REFUND_PERIOD_EXPIRED);
        }

        // 환불 쿠키 수 검증
        if (command.cookieAmount() > payment.getCookieAmount()) {
            throw new BusinessException(ErrorCode.REFUND_EXCEEDS_PAYMENT);
        }

        // 기존 환불 합산으로 중복 환불 방지
        int totalRefundedCookies = refundRepository.findByPaymentId(command.paymentId()).stream()
                .filter(r -> r.getStatus() != RefundStatus.FAILED)
                .mapToInt(Refund::getCookieAmount)
                .sum();
        if (totalRefundedCookies + command.cookieAmount() > payment.getCookieAmount()) {
            throw new BusinessException(ErrorCode.REFUND_EXCEEDS_PAYMENT);
        }

        // 쿠키 수 기준 비례 원금 계산 (BigDecimal로 반올림 처리)
        int wonAmount = BigDecimal.valueOf(command.cookieAmount())
                .multiply(BigDecimal.valueOf(payment.getAmount()))
                .divide(BigDecimal.valueOf(payment.getCookieAmount()), 0, RoundingMode.HALF_UP)
                .intValue();

        Refund refund = Refund.create(command.paymentId(), command.userId(), wonAmount, command.cookieAmount());
        return RefundInfo.from(refundRepository.save(refund));
    }

    @Transactional
    public RefundInfo approveRefund(Long refundId) {
        // 1. 비관적 락으로 조회 (동시 승인 방지)
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        // 2. PG 호출 전에 상태 검증 (이미 처리된 환불 재승인 방지)
        refund.validatePending();

        // 3. 결제 조회 후 paymentKey로 PG 취소 요청
        Payment payment = paymentRepository.findById(refund.getPaymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        try {
            refundGateway.cancelPayment(payment.getPaymentKey(), refund.getAmount(), "환불 확인");
        } catch (Exception e) {
            log.error("PG 환불 실패 - refundId: {}, paymentKey: {}, message: {}", refundId, payment.getPaymentKey(), e.getMessage());
            throw new BusinessException(ErrorCode.PG_REFUND_FAILED);
        }

        // 4. 환불 상태 변경
        refund.markSuccess();
        refundRepository.save(refund);

        // 3. Kafka 이벤트 발행 → user-service가 쿠키 지갑에서 차감 처리
        eventPublisher.publishEvent(
                RefundApprovedEvent.of(refund.getId(), refund.getPaymentId(),
                        refund.getUserId(), refund.getAmount(), refund.getCookieAmount())
        );

        return RefundInfo.from(refund);
    }

    @Transactional
    public RefundInfo rejectRefund(Long refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        refund.markFailed();
        return RefundInfo.from(refundRepository.save(refund));
    }

    public RefundInfo getRefund(Long refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
        return RefundInfo.from(refund);
    }

    public List<RefundInfo> getRefundsByUser(UUID userId) {
        return refundRepository.findByUserId(userId).stream()
                .map(RefundInfo::from)
                .toList();
    }
}
