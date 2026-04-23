package com.example.ticketservice.infrastructure.scheduling;

import com.example.ticketservice.application.usecase.CartCloseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Slf4j
@DisallowConcurrentExecution
public class CartCloseQuartzJob extends QuartzJobBean {

    public static final String SCHEDULE_ID_KEY = "scheduleId";

    private CartCloseUseCase cartCloseUseCase;

    public void setCartCloseUseCase(CartCloseUseCase cartCloseUseCase) {
        this.cartCloseUseCase = cartCloseUseCase;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        Long scheduleId = context.getJobDetail().getJobDataMap().getLong(SCHEDULE_ID_KEY);
        log.info("CartCloseQuartzJob 실행 - scheduleId={}", scheduleId);
        try {
            cartCloseUseCase.execute(scheduleId);
        } catch (Exception e) {
            log.error("CartCloseQuartzJob 실패 - scheduleId={}", scheduleId, e);
            throw new JobExecutionException(e);
        }
    }
}