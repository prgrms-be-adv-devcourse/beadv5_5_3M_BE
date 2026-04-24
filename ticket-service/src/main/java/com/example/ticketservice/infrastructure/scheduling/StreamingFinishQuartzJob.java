package com.example.ticketservice.infrastructure.scheduling;

import com.example.ticketservice.application.usecase.StreamingFinishUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class StreamingFinishQuartzJob extends QuartzJobBean {

    public static final String SCHEDULE_ID_KEY = "scheduleId";

    private final StreamingFinishUseCase streamingFinishUseCase;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        Long scheduleId = context.getJobDetail().getJobDataMap().getLong(SCHEDULE_ID_KEY);
        log.info("StreamingFinishQuartzJob 실행 - scheduleId={}", scheduleId);
        try {
            streamingFinishUseCase.execute(scheduleId);
        } catch (Exception e) {
            log.error("StreamingFinishQuartzJob 실패 - scheduleId={}", scheduleId, e);
            throw new JobExecutionException(e);
        }
    }
}