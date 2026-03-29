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
public class SettlementConfirmJobConfig {

    private final SettlementRepository settlementRepository;
    private final SettlementBatchService settlementBatchService;

    @Bean
    public Job settlementConfirmJob(JobRepository jobRepository, Step confirmStep) {
        return new JobBuilder("settlementConfirmJob", jobRepository)
                .start(confirmStep)
                .build();
    }

    @Bean
    public Step confirmStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("confirmStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    List<Settlement> settlements = settlementRepository.findByStatus(SettlementStatus.REQUESTED);
                    log.info("Confirming {} settlements", settlements.size());
                    int failureCount = 0;
                    for (Settlement s : settlements) {
                        try {
                            settlementBatchService.confirmOne(s.getId());
                        } catch (Exception e) {
                            log.error("Failed to confirm settlement {}", s.getId(), e);
                            failureCount++;
                        }
                    }
                    if (failureCount > 0) {
                        contribution.setExitStatus(ExitStatus.FAILED);
                        throw new RuntimeException(
                                "Confirm job completed with " + failureCount + " failure(s) out of " + settlements.size());
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
