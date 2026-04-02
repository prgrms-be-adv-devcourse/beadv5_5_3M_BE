package com.example.settlementservice.infrastructure.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BatchScheduler {

    private final JobOperator jobOperator;
    private final Job settlementConfirmJob;
    private final Job settlementPayoutJob;

    public BatchScheduler(
            JobOperator jobOperator,
            @Qualifier("settlementConfirmJob") Job settlementConfirmJob,
            @Qualifier("settlementPayoutJob") Job settlementPayoutJob
    ) {
        this.jobOperator = jobOperator;
        this.settlementConfirmJob = settlementConfirmJob;
        this.settlementPayoutJob = settlementPayoutJob;
    }

    @Scheduled(cron = "0 1 * * * *", zone = "Asia/Seoul")
    public void runConfirmJob() {
        runJob(settlementConfirmJob, "settlementConfirmJob");
    }

    @Scheduled(cron = "0 2 * * * *", zone = "Asia/Seoul")
    public void runPayoutJob() {
        runJob(settlementPayoutJob, "settlementPayoutJob");
    }

    private void runJob(Job job, String jobName) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters();
            jobOperator.start(job, params);
            log.info("Batch job started: {}", jobName);
        } catch (Exception e) {
            log.error("Failed to launch batch job: {}", jobName, e);
        }
    }
}