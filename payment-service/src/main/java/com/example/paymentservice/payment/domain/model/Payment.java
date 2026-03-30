package com.example.paymentservice.payment.domain.model;

import com.example.paymentservice.common.exception.BusinessException;
import com.example.paymentservice.common.exception.ErrorCode;
import com.example.paymentservice.payment.domain.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int amount;

    @Column(name = "cookie_amount", nullable = false)
    private int cookieAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "payment_key")
    private String paymentKey;

    @Column(name = "order_id", unique = true)
    private String orderId;

    public static Payment create(UUID userId, int amount, int cookieAmount) {
        if (amount <= 0 || cookieAmount <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_INVALID);
        }
        Payment payment = new Payment();
        payment.userId = userId;
        payment.amount = amount;
        payment.cookieAmount = cookieAmount;
        payment.status = PaymentStatus.READY;
        payment.createdAt = LocalDateTime.now();
        return payment;
    }

    public void markInProgress() {
        validateNotProcessed();
        this.status = PaymentStatus.IN_PROGRESS;
    }

    public void markSuccess(String paymentKey, String orderId) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.status = PaymentStatus.SUCCESS;
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public void cancel() {
        if (this.status != PaymentStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
        this.status = PaymentStatus.CANCELLED;
    }

    private void validateNotProcessed() {
        if (this.status == PaymentStatus.SUCCESS || this.status == PaymentStatus.FAILED) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
    }
}
