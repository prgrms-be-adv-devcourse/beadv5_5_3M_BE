package com.example.ticketservice.infrastructure.scheduling;

import com.example.ticketservice.application.port.out.SchedulerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuartzSchedulerAdapter implements SchedulerPort {

    private static final String SCHEDULE_ID_KEY = "scheduleId";

    private final Scheduler scheduler;

    @Override
    public void scheduleCartCloseJob(Long scheduleId, LocalDateTime triggerTime) {
        scheduleJobForSchedule(CartCloseQuartzJob.class, "CartClose", scheduleId, triggerTime);
    }

    @Override
    public void scheduleTicketingStartJob(Long scheduleId, LocalDateTime triggerTime) {
        scheduleJobForSchedule(TicketingStartQuartzJob.class, "TicketingStart", scheduleId, triggerTime);
    }

    @Override
    public void scheduleReviewAuthJob(Long scheduleId, LocalDateTime triggerTime) {
        scheduleJobForSchedule(ReviewAuthQuartzJob.class, "ReviewAuth", scheduleId, triggerTime);
    }

    @Override
    public void scheduleStreamingStartJob(Long scheduleId, LocalDateTime triggerTime) {
        scheduleJobForSchedule(StreamingStartQuartzJob.class, "StreamingStart", scheduleId, triggerTime);
    }

    @Override
    public void scheduleStreamingFinishJob(Long scheduleId, LocalDateTime triggerTime) {
        scheduleJobForSchedule(StreamingFinishQuartzJob.class, "StreamingFinish", scheduleId, triggerTime);
    }

    @Override
    public void cancelScheduledJobs(Long scheduleId) {
        deleteJob("CartCloseJob_" + scheduleId, "CART_CLOSE", scheduleId);
        deleteJob("TicketingStartJob_" + scheduleId, "TICKETING_START", scheduleId);
        deleteJob("ReviewAuthJob_" + scheduleId, "REVIEW_AUTH", scheduleId);
        deleteJob("StreamingStartJob_" + scheduleId, "STREAMING_START", scheduleId);
        deleteJob("StreamingFinishJob_" + scheduleId, "STREAMING_FINISH", scheduleId);
    }

    private void scheduleJobForSchedule(Class<? extends QuartzJobBean> jobClass, String jobType,
                                        Long scheduleId, LocalDateTime triggerTime) {
        String group = jobType.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
        String jobName = jobType + "Job_" + scheduleId;
        String triggerName = jobType + "Trigger_" + scheduleId;

        JobDetail job = JobBuilder.newJob(jobClass)
                .withIdentity(jobName, group)
                .usingJobData(SCHEDULE_ID_KEY, scheduleId)
                .storeDurably()
                .build();

        Date startAt = Date.from(triggerTime.atZone(ZoneId.systemDefault()).toInstant());
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerName, group)
                .startAt(startAt)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                .build();

        try {
            scheduler.scheduleJob(job, trigger);
            log.info("{} Job 등록 - scheduleId={}, triggerTime={}", jobType, scheduleId, startAt);
        } catch (SchedulerException e) {
            log.error("{} Job 등록 실패 - scheduleId={}", jobType, scheduleId, e);
            throw new RuntimeException("Quartz job 등록 실패: " + jobType + ", scheduleId=" + scheduleId, e);
        }
    }

    private void deleteJob(String jobName, String group, Long scheduleId) {
        try {
            scheduler.deleteJob(new JobKey(jobName, group));
        } catch (SchedulerException e) {
            log.warn("Job 삭제 실패 - jobName={}, scheduleId={}: {}", jobName, scheduleId, e.getMessage());
        }
    }
}
