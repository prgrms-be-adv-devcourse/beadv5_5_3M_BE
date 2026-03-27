package com.example.paymentservice.refund.domain.model;

import com.example.paymentservice.common.exception.BusinessException;
import com.example.paymentservice.common.exception.ErrorCode;
import com.example.paymentservice.refund.domain.RefundStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(nullable = false)
    private int amount;

    @Column(name = "cookie_amount", nullable = false)
    private int cookieAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RefundStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    public static Refund create(Long paymentId, UUID userId, int amount, int cookieAmount) {
        if (amount <= 0 || cookieAmount <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_INVALID);
        }
        Refund refund = new Refund();
        refund.paymentId = paymentId;
        refund.userId = userId;
        refund.amount = amount;
        refund.cookieAmount = cookieAmount;
        refund.status = RefundStatus.PENDING;
        refund.createdAt = LocalDateTime.now();
        return refund;
    }

    public void markSuccess() {
        validatePending();
        this.status = RefundStatus.SUCCESS;
    }

    public void markFailed() {
        validatePending();
        this.status = RefundStatus.FAILED;
    }

    public void validatePending() {
        if (this.status != RefundStatus.PENDING) {
            throw new BusinessException(ErrorCode.REFUND_ALREADY_PROCESSED);
        }
    }
}
