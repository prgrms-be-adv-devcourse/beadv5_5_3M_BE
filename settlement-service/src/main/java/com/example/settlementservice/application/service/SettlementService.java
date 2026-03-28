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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementService implements
        RequestSettlementUseCase,
        CancelSettlementUseCase,
        GetSettlementUseCase {

    private final WalletRepository walletRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementLogRepository settlementLogRepository;
    private final SettlementRequestExecutor settlementRequestExecutor;

    @Override
    public SettlementResponse requestSettlement(UUID creatorId, String idempotencyKey, PostSettlementRequest request) {
        try {
            return settlementRequestExecutor.execute(creatorId, idempotencyKey, request);
        } catch (DataIntegrityViolationException e) {
            // TX 외부에서 catch → rollback-only 오염 없음
            // race condition: 동시 INSERT로 UNIQUE 충돌 시 이미 저장된 Settlement 반환
            return settlementRepository.findByIdempotencyKey(idempotencyKey)
                    .map(SettlementResponse::from)
                    .orElseThrow(() -> e);
        }
    }

    @Override
    @Transactional
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
}