package com.example.ticketservice.infrastructure.scheduling;

import com.example.ticketservice.application.usecase.TicketingStartUseCase;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Slf4j
@DisallowConcurrentExecution
public class TicketingStartQuartzJob extends QuartzJobBean {

    public static final String SCHEDULE_ID_KEY = "scheduleId";

    private TicketingStartUseCase ticketingStartUseCase;

    public void setTicketingStartUseCase(TicketingStartUseCase ticketingStartUseCase) {
        this.ticketingStartUseCase = ticketingStartUseCase;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        Long scheduleId = context.getJobDetail().getJobDataMap().getLong(SCHEDULE_ID_KEY);
        log.info("TicketingStartQuartzJob 실행 - scheduleId={}", scheduleId);
        try {
            ticketingStartUseCase.execute(scheduleId);
        } catch (Exception e) {
            log.error("TicketingStartQuartzJob 실패 - scheduleId={}", scheduleId, e);
            throw new JobExecutionException(e);
        }
    }
}