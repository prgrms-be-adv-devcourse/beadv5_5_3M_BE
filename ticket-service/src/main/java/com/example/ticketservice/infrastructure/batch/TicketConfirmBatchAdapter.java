package com.example.ticketservice.infrastructure.batch;

import com.example.ticketservice.application.port.out.TicketConfirmBatchPort;
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
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TicketConfirmBatchAdapter implements TicketConfirmBatchPort {

    private final JobOperator jobOperator;
    private final Job ticketConfirmJob;

    public TicketConfirmBatchAdapter(
            JobOperator jobOperator,
            @Qualifier("ticketConfirmJob") Job ticketConfirmJob) {
        this.jobOperator = jobOperator;
        this.ticketConfirmJob = ticketConfirmJob;
    }

    @Override
    public boolean run(List<Long> scheduleIds) {
        try {
            String scheduleIdsParam = scheduleIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            JobParameters params = new JobParametersBuilder()
                    .addString("scheduleIds", scheduleIdsParam)
                    .addLocalDateTime("runAt", LocalDateTime.now())
                    .toJobParameters();

            JobExecution execution = jobOperator.start(ticketConfirmJob, params);
            log.info("티켓 확정 배치 완료 - status: {}, scheduleIds: {}", execution.getStatus(), scheduleIdsParam);
            return execution.getStatus() == BatchStatus.COMPLETED;
        } catch (Exception e) {
            log.error("티켓 확정 배치 실행 중 예외 발생", e);
            return false;
        }
    }
}