package com.example.userservice.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Schema(description = "쿠키 거래 로그 엔티티")
@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uq_cookie_log_payment", columnNames = {"userId", "paymentId"}),
        @UniqueConstraint(name = "uq_cookie_log_refund", columnNames = {"userId", "refundId"}),
        @UniqueConstraint(name = "uq_cookie_log_ticket", columnNames = {"userId", "ticketId"})
})
@NoArgsConstructor(access = PROTECTED)
public class CookieLog {

    @Schema(description = "로그 ID")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "유저 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID userId;

    @Schema(description = "쿠키 변동량 (양수: 충전, 음수: 차감)", example = "-5")
    private Integer amount;

    @Schema(description = "관련 결제 ID", example = "1")
    private Long paymentId;

    @Schema(description = "관련 환불 ID", example = "1")
    private Long refundId;

    @Schema(description = "관련 티켓 ID", example = "1")
    private Long ticketId;

    @Schema(description = "생성일시")
    private LocalDateTime createAt;

    private CookieLog(UUID userId, Integer amount, Long paymentId, Long refundId, Long ticketId) {
        this.userId = userId;
        this.amount = amount;
        this.paymentId = paymentId;
        this.refundId = refundId;
        this.ticketId = ticketId;
    }

    public static CookieLog createForPayment(UUID userId, Integer amount, Long paymentId) {
        return new CookieLog(userId, amount, paymentId, null, null);
    }

    public static CookieLog createForRefund(UUID userId, Integer amount, Long refundId) {
        return new CookieLog(userId, amount, null, refundId, null);
    }

    public static CookieLog create(UUID userId, Integer amount, Long ticketId) {
        return new CookieLog(userId, amount, null, null, ticketId);
    }

    @PrePersist
    public void onCreate() {
        if (createAt == null) {
            createAt = LocalDateTime.now();
        }
    }
}
