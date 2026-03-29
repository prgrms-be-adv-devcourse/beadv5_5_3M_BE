package com.example.settlementservice.infrastructure.batch;

import com.example.settlementservice.application.port.out.SettlementRepository;
import com.example.settlementservice.domain.settlement.Settlement;
import com.example.settlementservice.domain.settlement.SettlementStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

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
                .tasklet((contribution, chunkContext) -> {
                    List<Settlement> settlements = settlementRepository.findByStatus(SettlementStatus.CONFIRMED);
                    log.info("Processing payout for {} settlements", settlements.size());
                    int failureCount = 0;
                    for (Settlement s : settlements) {
                        try {
                            settlementBatchService.completeOne(s.getId());
                        } catch (Exception e) {
                            log.error("Failed to process payout for settlement {}", s.getId(), e);
                            failureCount++;
                        }
                    }
                    if (failureCount > 0) {
                        contribution.setExitStatus(ExitStatus.FAILED);
                        throw new RuntimeException(
                                "Payout job completed with " + failureCount + " failure(s) out of " + settlements.size());
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}