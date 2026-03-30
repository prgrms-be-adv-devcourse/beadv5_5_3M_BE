package com.example.settlementservice.application.service;

import com.example.settlementservice.application.exception.WalletNotFoundException;
import com.example.settlementservice.application.port.out.*;
import com.example.settlementservice.domain.common.Money;
import com.example.settlementservice.domain.settlement.Settlement;
import com.example.settlementservice.domain.settlement.SettlementLog;
import com.example.settlementservice.domain.wallet.Wallet;
import com.example.settlementservice.presentation.web.dto.PostSettlementRequest;
import com.example.settlementservice.presentation.web.dto.SettlementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import java.util.UUID;


@Component
@Transactional
@RequiredArgsConstructor
public class SettlementRequestExecutor {

    private final WalletRepository walletRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementLogRepository settlementLogRepository;
    private final CreatorPayoutQueryPort creatorPayoutQueryPort;

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class,
               maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
    public SettlementResponse execute(UUID creatorId, String idempotencyKey, PostSettlementRequest request) {
        // 1. 멱등성: 이미 성공한 요청이면 기존 응답 반환 (일반 재시도 경로)
        Optional<Settlement> existing = settlementRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return SettlementResponse.from(existing.get());
        }

        // 2. 외부 의존성 먼저 검증 — DB 저장 전 실패 시 아무것도 저장되지 않아 재시도 안전
        CreatorPayoutSnapshot snapshot = creatorPayoutQueryPort.getPayoutSnapshot(creatorId);

        Wallet wallet = walletRepository.findByCreatorId(creatorId)
                .orElseThrow(() -> new WalletNotFoundException(creatorId));

        Long beforeBalance = wallet.getBalance();

        Money requestAmount = Money.of(request.requestAmount());

        // 3. 비즈니스 로직 실행 — wallet 차감 (in-memory)
        wallet.subtractBalance(requestAmount);

        // 4. Settlement 저장 — idempotency key 커밋 (비즈니스 로직 완료 이후)
        //    DataIntegrityViolationException 발생 시 wallet 차감을 포함한 전체 TX 롤백
        Settlement settlement = Settlement.request(
                wallet,
                requestAmount,
                snapshot.bankName(),
                snapshot.accountNumber(),
                snapshot.accountHolder(),
                idempotencyKey,
                nextMondayNineAmKst(),
                beforeBalance
        );
        settlementRepository.save(settlement);

        // 5. wallet 변경 영속화
        walletRepository.save(wallet);

        // 6. 차감 로그 기록
        SettlementLog debitLog = SettlementLog.debitSettlementRequest(wallet, settlement, requestAmount);
        settlementLogRepository.save(debitLog);

        return SettlementResponse.from(settlement);
    }

    private OffsetDateTime nextMondayNineAmKst() {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        return OffsetDateTime.now(kst)
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .withHour(9).withMinute(0).withSecond(0).withNano(0)
                .withOffsetSameInstant(ZoneOffset.UTC);
    }
}