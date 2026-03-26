package com.example.settlementservice.domain.settlement;

import com.example.settlementservice.domain.common.Money;
import com.example.settlementservice.domain.wallet.Wallet;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "settlement_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "settlement_log_seq")
    @SequenceGenerator(name = "settlement_log_seq", sequenceName = "settlement_logs_id_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id")
    private Settlement settlement;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private SettlementLedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementLedgerDirection direction;

    /** 실제 Wallet에 반영된 금액 (net) */
    @Column(nullable = false)
    private Long amount;

    /** 수익 적립 시 수수료 차감 전 원화 금액 (REVENUE_INGEST 전용, 나머지 null) */
    @Column(name = "gross_amount")
    private Long grossAmount;

    /** 수익 적립 시 차감된 플랫폼 수수료 금액 (REVENUE_INGEST 전용, 나머지 null) */
    @Column(name = "fee_amount")
    private Long feeAmount;

    /** 수익 적립 시 적용된 수수료율 (REVENUE_INGEST 전용, 나머지 null) */
    @Column(name = "fee_rate", precision = 5, scale = 4)
    private BigDecimal feeRate;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @Column(name = "source_event_id", unique = true)
    private String sourceEventId;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime occurredAt;

    @PrePersist
    protected void onPersist() {
        this.occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public static SettlementLog creditRevenue(
            Wallet wallet, Money gross, Money fee, Money net, BigDecimal feeRate, String sourceEventId) {
        SettlementLog log = new SettlementLog();
        log.wallet = wallet;
        log.settlement = null;
        log.entryType = SettlementLedgerEntryType.REVENUE_INGEST;
        log.direction = SettlementLedgerDirection.CREDIT;
        log.grossAmount = gross.value();
        log.feeAmount = fee.value();
        log.feeRate = feeRate;
        log.amount = net.value();
        log.balanceAfter = wallet.getBalance();
        log.sourceEventId = sourceEventId;
        return log;
    }

    public static SettlementLog debitSettlementRequest(Wallet wallet, Settlement settlement, Money amount) {
        SettlementLog log = new SettlementLog();
        log.wallet = wallet;
        log.settlement = settlement;
        log.entryType = SettlementLedgerEntryType.SETTLEMENT_REQUEST_DEBIT;
        log.direction = SettlementLedgerDirection.DEBIT;
        log.amount = amount.value();
        log.balanceAfter = wallet.getBalance();
        log.sourceEventId = null;
        return log;
    }

    public static SettlementLog debitPayout(Wallet wallet, Settlement settlement, Money amount) {
        SettlementLog log = new SettlementLog();
        log.wallet = wallet;
        log.settlement = settlement;
        log.entryType = SettlementLedgerEntryType.SETTLEMENT_PAYOUT;
        log.direction = SettlementLedgerDirection.DEBIT;
        log.amount = amount.value();
        log.balanceAfter = wallet.getBalance();
        log.sourceEventId = null;
        return log;
    }

    public static SettlementLog creditReversal(Wallet wallet, Settlement settlement, Money amount) {
        SettlementLog log = new SettlementLog();
        log.wallet = wallet;
        log.settlement = settlement;
        log.entryType = SettlementLedgerEntryType.SETTLEMENT_CANCEL_REFUND;
        log.direction = SettlementLedgerDirection.CREDIT;
        log.amount = amount.value();
        log.balanceAfter = wallet.getBalance();
        log.sourceEventId = null;
        return log;
    }
}