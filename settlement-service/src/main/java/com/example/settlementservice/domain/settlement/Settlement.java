package com.example.settlementservice.domain.settlement;

import com.example.settlementservice.domain.common.Money;
import com.example.settlementservice.domain.wallet.Wallet;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "settlements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "creator_id", nullable = false, columnDefinition = "uuid")
    private UUID creatorId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "request_amount", nullable = false)
    private Long requestAmount;

    /** 정산 신청 시점의 Wallet 잔액 스냅샷 (감사 추적용) */
    @Column(name = "balance_snapshot", nullable = false)
    private Long balanceSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status;

    @Column(name = "requested_at", nullable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime requestedAt;

    @Column(name = "settlement_deadline", nullable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime settlementDeadline;

    @Column(name = "confirmed_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime confirmedAt;

    @Column(name = "completed_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime completedAt;

    @Column(name = "cancelled_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime cancelledAt;

    @Column(name = "failed_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime failedAt;

    @Column(name = "payout_bank_name")
    private String payoutBankName;

    @Column(name = "payout_account_number")
    private String payoutAccountNumber;

    @Column(name = "payout_account_holder")
    private String payoutAccountHolder;

    @Column(name = "fail_reason")
    private String failReason;

    public static Settlement request(
            Wallet wallet,
            Money requestAmount,
            String payoutBankName,
            String payoutAccountNumber,
            String payoutAccountHolder,
            String idempotencyKey,
            OffsetDateTime deadline
    ) {
        Settlement s = new Settlement();
        s.wallet = wallet;
        s.creatorId = wallet.getCreatorId();
        s.idempotencyKey = idempotencyKey;
        s.requestAmount = requestAmount.value();
        s.balanceSnapshot = wallet.getBalance();  // 신청 시점 잔액 스냅샷
        s.status = SettlementStatus.REQUESTED;
        s.requestedAt = OffsetDateTime.now(ZoneOffset.UTC);
        s.settlementDeadline = deadline;
        s.payoutBankName = payoutBankName;
        s.payoutAccountNumber = payoutAccountNumber;
        s.payoutAccountHolder = payoutAccountHolder;
        return s;
    }

    public void confirm(OffsetDateTime now) {
        if (status != SettlementStatus.REQUESTED) {
            throw new InvalidSettlementStateException(id, status, SettlementStatus.REQUESTED);
        }
        this.status = SettlementStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    public void complete(OffsetDateTime now) {
        if (status != SettlementStatus.CONFIRMED) {
            throw new InvalidSettlementStateException(id, status, SettlementStatus.CONFIRMED);
        }
        this.status = SettlementStatus.COMPLETED;
        this.completedAt = now;
    }

    public void fail(String reason, OffsetDateTime now) {
        if (status != SettlementStatus.CONFIRMED) {
            throw new InvalidSettlementStateException(id, status, SettlementStatus.CONFIRMED);
        }
        this.status = SettlementStatus.FAILED;
        this.failReason = reason;
        this.failedAt = now;
    }

    public void cancel(OffsetDateTime now) {
        if (status != SettlementStatus.REQUESTED) {
            throw new InvalidSettlementStateException(id, status, SettlementStatus.REQUESTED);
        }
        if (now.isAfter(settlementDeadline)) {
            throw new IllegalStateException("Settlement deadline has passed for settlement: " + id);
        }
        this.status = SettlementStatus.CANCELLED;
        this.cancelledAt = now;
    }
}