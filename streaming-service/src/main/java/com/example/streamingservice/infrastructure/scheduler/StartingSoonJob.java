package com.example.streamingservice.infrastructure.scheduler;

import com.example.streamingservice.application.usecase.LifecycleUseCase;
import lombok.RequiredArgsConstructor;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.PersistJobDataAfterExecution;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class StartingSoonJob extends QuartzJobBean {

	private final LifecycleUseCase lifecycle;

	@Override
	protected void executeInternal(JobExecutionContext context) {
		long scheduleId = context.getMergedJobDataMap().getLong(QuartzSchedulerAdapter.JOB_DATA_SCHEDULE_ID);
		lifecycle.onStartingSoon(scheduleId);
	}
}