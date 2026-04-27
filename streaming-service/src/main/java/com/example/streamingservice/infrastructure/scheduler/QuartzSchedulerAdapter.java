package com.example.streamingservice.infrastructure.scheduler;

import com.example.streamingservice.application.port.SchedulerPort;
import com.example.streamingservice.domain.Schedule;
import lombok.RequiredArgsConstructor;
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
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class QuartzSchedulerAdapter implements SchedulerPort {

	static final String JOB_DATA_SCHEDULE_ID = "scheduleId";
	private static final String GROUP = "streaming";

	// 관측용: 3분 단위 체인 구성. 운영 복원 시 10m / 1m / 10m로 되돌릴 것.
	private static final Duration LOBBY_LEAD = Duration.ofMinutes(6);   // prod: ofMinutes(10)
	private static final Duration SOON_LEAD = Duration.ofMinutes(3);    // prod: ofMinutes(1)
	private static final Duration POST_GRACE = Duration.ofMinutes(3);   // prod: ofMinutes(10)

	private static final String[] KINDS = {
		"lobbyOpen", "startingSoon", "started", "endingSoon", "ended", "forceExit"
	};

	private final Scheduler scheduler;

	@Override
	public void scheduleLifecycle(Schedule schedule) {
		long id = schedule.getScheduleId();
		Instant startTime = schedule.getStartTime();
		Instant endTime = schedule.getEndTime();

		register(id, "lobbyOpen", startTime.minus(LOBBY_LEAD), LobbyOpenJob.class);
		register(id, "startingSoon", startTime.minus(SOON_LEAD), StartingSoonJob.class);
		register(id, "started", startTime, StartedJob.class);
		register(id, "endingSoon", endTime.minus(SOON_LEAD), EndingSoonJob.class);
		register(id, "ended", endTime, EndedJob.class);
		register(id, "forceExit", endTime.plus(POST_GRACE), ForceExitJob.class);
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