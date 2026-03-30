package com.example.settlementservice.application.service;

import com.example.settlementservice.application.exception.*;
import com.example.settlementservice.application.port.in.CancelSettlementUseCase;
import com.example.settlementservice.application.port.in.GetSettlementUseCase;
import com.example.settlementservice.application.port.in.RequestSettlementUseCase;
import com.example.settlementservice.application.port.out.*;
import com.example.settlementservice.domain.common.Money;
import com.example.settlementservice.domain.settlement.Settlement;
import com.example.settlementservice.domain.settlement.SettlementLog;
import com.example.settlementservice.domain.wallet.Wallet;
import com.example.settlementservice.presentation.web.dto.PostSettlementRequest;
import com.example.settlementservice.presentation.web.dto.SettlementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class SettlementService implements
        RequestSettlementUseCase,
        CancelSettlementUseCase,
        GetSettlementUseCase {

    private final WalletRepository walletRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementLogRepository settlementLogRepository;
    private final CreatorPayoutQueryPort creatorPayoutQueryPort;

    @Override
    public SettlementResponse requestSettlement(UUID creatorId, String idempotencyKey, PostSettlementRequest request) {
        if (settlementRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new DuplicateIdempotencyKeyException(idempotencyKey);
        }

        Wallet wallet = walletRepository.findByCreatorId(creatorId)
                .orElseThrow(() -> new WalletNotFoundException(creatorId));

        // Creator 서비스에서 계좌 정보 스냅샷 조회 (REST)
        CreatorPayoutSnapshot snapshot = creatorPayoutQueryPort.getPayoutSnapshot(creatorId);

        Money requestAmount = Money.of(request.requestAmount());
        OffsetDateTime deadline = nextMondayNineAmKst();

        // 1. 정산 엔티티 생성 (Wallet은 이미 net 잔액 — 수수료는 적립 시 차감됨)
        Settlement settlement = Settlement.request(
                wallet,
                requestAmount,
                snapshot.bankName(),
                snapshot.accountNumber(),
                snapshot.accountHolder(),
                idempotencyKey,
                deadline
        );
        settlementRepository.save(settlement);

        // 2. 지갑에서 사전 차감 (정산 신청 시 바로 잠금)
        wallet.subtractBalance(requestAmount);
        walletRepository.save(wallet);

        // 3. 차감 내역 로그 기록
        SettlementLog debitLog = SettlementLog.debitSettlementRequest(wallet, settlement, requestAmount);
        settlementLogRepository.save(debitLog);

        return SettlementResponse.from(settlement);
    }

    @Override
    public SettlementResponse cancelSettlement(Long settlementId, UUID creatorId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new SettlementNotFoundException(settlementId));

        if (!settlement.getCreatorId().equals(creatorId)) {
            throw new SettlementNotFoundException(settlementId);
        }

        // 도메인 검증 (상태 REQUESTED + 마감 전 여부)
        settlement.cancel(OffsetDateTime.now(ZoneOffset.UTC));

        // 차감했던 금액을 지갑에 환급
        Wallet wallet = walletRepository.findByCreatorId(creatorId)
                .orElseThrow(() -> new WalletNotFoundException(creatorId));
        Money requestAmount = new Money(settlement.getRequestAmount());
        wallet.addBalance(requestAmount);
        walletRepository.save(wallet);

        // 환급 로그 기록
        SettlementLog refundLog = SettlementLog.creditReversal(wallet, settlement, requestAmount);
        settlementLogRepository.save(refundLog);

        return SettlementResponse.from(settlement);
    }

    @Override
    @Transactional(readOnly = true)
    public SettlementResponse getSettlement(Long id, UUID creatorId) {
        Settlement settlement = settlementRepository.findById(id)
                .orElseThrow(() -> new SettlementNotFoundException(id));

        if (!settlement.getCreatorId().equals(creatorId)) {
            throw new SettlementNotFoundException(id);
        }

        return SettlementResponse.from(settlement);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettlementResponse> getSettlementsByCreatorId(UUID creatorId) {
        return settlementRepository.findByCreatorId(creatorId).stream()
                .map(SettlementResponse::from)
                .toList();
    }

    private OffsetDateTime nextMondayNineAmKst() {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        return OffsetDateTime.now(kst)
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .withHour(9).withMinute(0).withSecond(0).withNano(0)
                .withOffsetSameInstant(ZoneOffset.UTC);
    }
}