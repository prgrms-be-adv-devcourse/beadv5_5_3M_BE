package com.example.streamingservice.infrastructure.scheduler;

import com.example.streamingservice.application.port.SchedulerPort;
import com.example.streamingservice.domain.Schedule;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

@Component
public class QuartzSchedulerAdapter implements SchedulerPort {

	static final String JOB_DATA_SCHEDULE_ID = "scheduleId";
	private static final String GROUP = "streaming";

	private static final String[] KINDS = {
		"lobbyOpen", "startingSoon", "started", "endingSoon", "ended", "forceExit"
	};

	private final Scheduler scheduler;
	private final Duration lobbyLead;
	private final Duration soonLead;
	private final Duration postGrace;

	public QuartzSchedulerAdapter(
			Scheduler scheduler,
			@Value("${streaming.lifecycle.lobby-lead:PT10M}") Duration lobbyLead,
			@Value("${streaming.lifecycle.soon-lead:PT1M}") Duration soonLead,
			@Value("${streaming.lifecycle.post-grace:PT3M}") Duration postGrace) {
		this.scheduler = scheduler;
		this.lobbyLead = lobbyLead;
		this.soonLead = soonLead;
		this.postGrace = postGrace;
	}

	@Override
	public void scheduleLifecycle(Schedule schedule) {
		long id = schedule.getScheduleId();
		Instant startTime = schedule.getStartTime();
		Instant endTime = schedule.getEndTime();

		register(id, "lobbyOpen", startTime.minus(lobbyLead), LobbyOpenJob.class);
		register(id, "startingSoon", startTime.minus(soonLead), StartingSoonJob.class);
		register(id, "started", startTime, StartedJob.class);
		register(id, "endingSoon", endTime.minus(soonLead), EndingSoonJob.class);
		register(id, "ended", endTime, EndedJob.class);
		register(id, "forceExit", endTime.plus(postGrace), ForceExitJob.class);
	}

	@Override
	public void unscheduleLifecycle(long scheduleId) {
		try {
			for (String kind : KINDS) {
				scheduler.deleteJob(JobKey.jobKey(jobName(scheduleId, kind), GROUP));
			}
		} catch (SchedulerException e) {
			throw new IllegalStateException(e);
		}
	}

	private void register(long scheduleId, String kind, Instant fireAt, Class<? extends Job> jobClass) {
		JobDataMap data = new JobDataMap();
		data.put(JOB_DATA_SCHEDULE_ID, scheduleId);

		JobDetail detail = JobBuilder.newJob(jobClass)
			.withIdentity(jobName(scheduleId, kind), GROUP)
			.usingJobData(data)
			.storeDurably()
			.build();

		Trigger trigger = TriggerBuilder.newTrigger()
			.withIdentity("trigger-" + jobName(scheduleId, kind), GROUP)
			.startAt(Date.from(fireAt))
			.withSchedule(SimpleScheduleBuilder.simpleSchedule()
				.withMisfireHandlingInstructionFireNow())
			.build();

		try {
			scheduler.scheduleJob(detail, Set.of(trigger), true);
		} catch (SchedulerException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String jobName(long scheduleId, String kind) {
		return "schedule-" + scheduleId + "-" + kind;
	}
}