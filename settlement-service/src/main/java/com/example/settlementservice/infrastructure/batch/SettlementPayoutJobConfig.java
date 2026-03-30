package com.example.settlementservice.infrastructure.batch;

import com.example.settlementservice.application.exception.SettlementNotFoundException;
import com.example.settlementservice.application.exception.WalletNotFoundException;
import com.example.settlementservice.application.port.out.SettlementRepository;
import com.example.settlementservice.domain.settlement.InvalidSettlementStateException;
import com.example.settlementservice.domain.settlement.Settlement;
import com.example.settlementservice.domain.settlement.SettlementStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Iterator;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SettlementPayoutJobConfig {

    private final SettlementRepository settlementRepository;
    private final SettlementBatchService settlementBatchService;

    @Bean
    public Job settlementPayoutJob(JobRepository jobRepository, Step payoutStep) {
        return new JobBuilder("settlementPayoutJob", jobRepository)
                .start(payoutStep)
                .build();
    }

    @Bean
    public Step payoutStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("payoutStep", jobRepository)
                .<Long, Long>chunk(10)
                .reader(payoutItemReader())
                .processor(payoutItemProcessor())
                .writer(chunk -> log.info("Processed payout for {} settlements: {}", chunk.size(), chunk.getItems()))
                .transactionManager(transactionManager)
                .faultTolerant()
                .skip(InvalidSettlementStateException.class)  // 상태 불일치 건 (이미 완료/실패)
                .skip(SettlementNotFoundException.class)       // 삭제된 건
                .skip(WalletNotFoundException.class)           // 환급 대상 지갑 없는 건
                .skipLimit(50)                                 // 전체 skip이 50건 초과 시 Job FAILED
                .skipListener(payoutSkipListener())
                .build();
    }

    // TODO: 정산 건수가 1만 건 이상이 되는 시점에 JpaPagingItemReader로 전환 필요
    //       현재 구조는 첫 read() 시 전체 ID를 한 번에 조회함
    private ItemReader<Long> payoutItemReader() {
        return new ItemReader<>() {
            private Iterator<Long> iterator;

            @Override
            public Long read() {
                if (iterator == null) {
                    iterator = settlementRepository.findByStatus(SettlementStatus.CONFIRMED)
                            .stream().map(Settlement::getId).iterator();
                    log.info("payoutItemReader: loaded CONFIRMED settlement IDs");
                }
                return iterator.hasNext() ? iterator.next() : null;
            }
        };
    }

    private ItemProcessor<Long, Long> payoutItemProcessor() {
        return id -> {
            settlementBatchService.completeOne(id);
            return id;
        };
    }

    private SkipListener<Long, Long> payoutSkipListener() {
        return new SkipListener<>() {
            @Override
            public void onSkipInRead(Throwable t) {
                log.error("payoutStep: skip during read - {}", t.getMessage());
            }

            @Override
            public void onSkipInProcess(Long id, Throwable t) {
                log.error("payoutStep: skipped settlement id={} - {}: {}", id, t.getClass().getSimpleName(), t.getMessage());
            }
        };
    }
}