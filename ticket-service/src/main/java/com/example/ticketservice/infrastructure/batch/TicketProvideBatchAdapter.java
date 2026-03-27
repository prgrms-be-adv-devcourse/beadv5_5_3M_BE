package com.example.ticketservice.infrastructure.batch;

import com.example.ticketservice.application.port.out.TicketProvideBatchPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class TicketProvideBatchAdapter implements TicketProvideBatchPort {

    private final JobOperator jobOperator;
    private final Job ticketProvideJob;

    public TicketProvideBatchAdapter(
            JobOperator jobOperator,
            @Qualifier("ticketProvideJob") Job ticketProvideJob) {
        this.jobOperator = jobOperator;
        this.ticketProvideJob = ticketProvideJob;
    }

    @Override
    public boolean run() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLocalDateTime("runAt", LocalDateTime.now())
                    .toJobParameters();

            JobExecution execution = jobOperator.start(ticketProvideJob, params);
            log.info("일일 대금 지급 배치 완료 - status: {}", execution.getStatus());
            return execution.getStatus() == BatchStatus.COMPLETED;
        } catch (Exception e) {
            log.error("일일 대금 지급 배치 실행 중 예외 발생", e);
            return false;
        }
    }
}