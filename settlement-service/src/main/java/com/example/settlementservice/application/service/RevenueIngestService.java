package com.example.settlementservice.application.service;

import com.example.settlementservice.application.exception.CreatorNotFoundException;
import com.example.settlementservice.application.port.in.IngestRevenueUseCase;
import com.example.settlementservice.application.port.out.CreatorPayoutQueryPort;
import com.example.settlementservice.application.port.out.SettlementLogRepository;
import com.example.settlementservice.application.port.out.WalletRepository;
import com.example.settlementservice.domain.common.FeePolicy;
import com.example.settlementservice.domain.common.Money;
import com.example.settlementservice.domain.settlement.SettlementLog;
import com.example.settlementservice.domain.wallet.Wallet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RevenueIngestService implements IngestRevenueUseCase {

    private final WalletRepository walletRepository;
    private final SettlementLogRepository settlementLogRepository;
    private final WalletCreationService walletCreationService;
    private final CreatorPayoutQueryPort creatorPayoutQueryPort;

    @Value("${settlement.cookie-to-krw-rate}")
    private BigDecimal cookieToKrwRate;

    @Value("${settlement.fee-rate}")
    private BigDecimal feeRate;

    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3,
               backoff = @Backoff(delay = 50, multiplier = 2))
    @Override
    public void ingestRevenue(UUID creatorId, Long ticketId, Long scheduleId, Integer cookieAmount) {
        String sourceEventId = String.valueOf(ticketId);

        if (settlementLogRepository.existsBySourceEventId(sourceEventId)) {
            log.info("Duplicate revenue event ignored: ticketId={}", ticketId);
            return;
        }

        if (!creatorPayoutQueryPort.existsCreator(creatorId)) {
            throw new CreatorNotFoundException(creatorId);
        }

        // findOrCreate(REQUIRES_NEW)로 DB에 존재 보장 후 outer TX에서 managed entity로 재조회
        walletCreationService.findOrCreate(creatorId);
        Wallet wallet = walletRepository.findByCreatorId(creatorId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found after creation: " + creatorId));

        Money gross = toGross(cookieAmount);
        FeePolicy feePolicy = new FeePolicy(feeRate);
        Money fee = feePolicy.calculateFee(gross);
        Money net = gross.subtract(fee);

        wallet.addBalance(net);
        walletRepository.save(wallet);

        settlementLogRepository.save(
                SettlementLog.creditRevenue(wallet, gross, fee, net, feeRate, sourceEventId));

        log.info("Revenue ingested: creatorId={}, ticketId={}, gross={}원, fee={}원, net={}원",
                creatorId, ticketId, gross.value(), fee.value(), net.value());
    }

    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3,
               backoff = @Backoff(delay = 50, multiplier = 2))
    @Override
    public void ingestRevenueBatch(List<RevenueIngestCommand> commands) {
        if (commands.isEmpty()) return;

        // 1. 이미 처리된 sourceEventId 한 번에 조회 (N번 쿼리 → 1번 IN 쿼리)
        List<String> sourceEventIds = commands.stream()
                .map(c -> String.valueOf(c.ticketId()))
                .toList();
        Set<String> existingIds = settlementLogRepository.findExistingSourceEventIds(sourceEventIds);

        List<RevenueIngestCommand> newCommands = commands.stream()
                .filter(c -> !existingIds.contains(String.valueOf(c.ticketId())))
                .toList();

        if (newCommands.isEmpty()) {
            log.info("All {} events were duplicates, skipping batch", commands.size());
            return;
        }

        // 2. 관련 Wallet 한 번에 조회 (N번 쿼리 → 1번 IN 쿼리)
        Set<UUID> creatorIds = newCommands.stream()
                .map(RevenueIngestCommand::creatorId)
                .collect(Collectors.toSet());

        Map<UUID, Wallet> walletMap = walletRepository.findAllByCreatorIdIn(creatorIds)
                .stream()
                .collect(Collectors.toMap(Wallet::getCreatorId, w -> w));

        // 없는 Wallet만 추려서 생성 후 batch 재조회
        // findOrCreate(REQUIRES_NEW)가 반환하는 엔티티는 outer TX에서 detached 상태.
        // merge()에 의존하지 않고, 신규 wallet들을 생성한 뒤 outer TX에서 IN 쿼리 한 번으로 batch 로딩.
        Set<UUID> missingIds = creatorIds.stream()
                .filter(id -> !walletMap.containsKey(id))
                .collect(Collectors.toSet());

        if (!missingIds.isEmpty()) {
            missingIds.forEach(id -> {
                if (!creatorPayoutQueryPort.existsCreator(id)) {
                    throw new CreatorNotFoundException(id);
                }
            });
            missingIds.forEach(walletCreationService::findOrCreate); // REQUIRES_NEW: DB에 존재 보장
            walletRepository.findAllByCreatorIdIn(missingIds)        // outer TX에서 managed entity batch 조회
                    .forEach(w -> walletMap.put(w.getCreatorId(), w));
        }

        // 3. 각 커맨드 처리 — 인메모리에서 잔액 누적 + 로그 생성
        FeePolicy feePolicy = new FeePolicy(feeRate);
        List<SettlementLog> logs = new ArrayList<>(newCommands.size());

        for (RevenueIngestCommand cmd : newCommands) {
            Wallet wallet = walletMap.get(cmd.creatorId());

            Money gross = toGross(cmd.cookieAmount());
            Money fee = feePolicy.calculateFee(gross);
            Money net = gross.subtract(fee);

            wallet.addBalance(net); // 인메모리 잔액 누적 (같은 크리에이터 여러 건도 정확히 누적됨)

            logs.add(SettlementLog.creditRevenue(wallet, gross, fee, net, feeRate,
                    String.valueOf(cmd.ticketId())));
        }

        // 4. Wallet 일괄 저장
        walletRepository.saveAll(new ArrayList<>(walletMap.values()));

        // 5. SettlementLog 배치 INSERT (SEQUENCE 전략 덕분에 실제 JDBC batch 실행)
        settlementLogRepository.saveAll(logs);

        log.info("Batch revenue ingested: total={}, new={}, skipped={}",
                commands.size(), newCommands.size(), existingIds.size());
    }

    private Money toGross(Integer cookieAmount) {
        long krw = BigDecimal.valueOf(cookieAmount)
                .multiply(cookieToKrwRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        return Money.of(krw);
    }
}