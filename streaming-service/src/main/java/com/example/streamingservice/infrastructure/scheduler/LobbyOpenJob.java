package com.example.streamingservice.infrastructure.scheduler;

import com.example.streamingservice.application.usecase.LifecycleUseCase;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.PersistJobDataAfterExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class LobbyOpenJob extends QuartzJobBean {

	@Autowired
	private LifecycleUseCase lifecycle;

	@Override
	protected void executeInternal(JobExecutionContext context) {
		long scheduleId = context.getMergedJobDataMap().getLong(QuartzSchedulerAdapter.JOB_DATA_SCHEDULE_ID);
		lifecycle.onLobbyOpen(scheduleId);
	}
}