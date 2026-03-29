package com.example.settlementservice.infrastructure.batch;

import com.example.settlementservice.application.port.out.PayoutPort;
import com.example.settlementservice.application.port.out.SettlementLogRepository;
import com.example.settlementservice.application.port.out.SettlementRepository;
import com.example.settlementservice.application.port.out.WalletRepository;
import com.example.settlementservice.application.exception.SettlementNotFoundException;
import com.example.settlementservice.application.exception.WalletNotFoundException;
import com.example.settlementservice.domain.common.Money;
import com.example.settlementservice.domain.settlement.Settlement;
import com.example.settlementservice.domain.settlement.SettlementLog;
import com.example.settlementservice.domain.wallet.Wallet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementBatchService {

    private final SettlementRepository settlementRepository;
    private final WalletRepository walletRepository;
    private final SettlementLogRepository settlementLogRepository;
    private final PayoutPort payoutPort;

    // 월요일: REQUESTED → CONFIRMED
    @Transactional
    public void confirmOne(Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new SettlementNotFoundException(settlementId));
        settlement.confirm(OffsetDateTime.now(ZoneOffset.UTC));
        log.info("Settlement confirmed: {}", settlementId);
    }

    // 목요일: CONFIRMED → COMPLETED (지갑은 정산 신청 시 이미 차감됨)
    @Transactional
    public void completeOne(Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new SettlementNotFoundException(settlementId));

        boolean success;
        try {
            success = payoutPort.payout(settlement);
        } catch (Exception e) {
            log.error("Payout threw exception for settlement {}, treating as failure", settlementId, e);
            success = false;
        }

        if (success) {
            settlement.complete(OffsetDateTime.now(ZoneOffset.UTC));
            log.info("Settlement completed: {}", settlementId);
        } else {
            // 지급 실패 시 차감했던 금액을 환급
            Wallet wallet = walletRepository.findByCreatorId(settlement.getCreatorId())
                    .orElseThrow(() -> new WalletNotFoundException(settlement.getCreatorId()));
            Money requestAmount = new Money(settlement.getRequestAmount());
            wallet.addBalance(requestAmount);
            walletRepository.save(wallet);

            SettlementLog refundLog = SettlementLog.creditReversal(wallet, settlement, requestAmount);
            settlementLogRepository.save(refundLog);

            settlement.fail("Payout failed", OffsetDateTime.now(ZoneOffset.UTC));
            log.warn("Settlement failed and refunded: {}", settlementId);
        }
    }
}