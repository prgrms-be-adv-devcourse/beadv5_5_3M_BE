package com.example.ticketservice.infrastructure.scheduling;

import com.example.ticketservice.application.port.out.SchedulerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuartzSchedulerAdapter implements SchedulerPort {

    private static final String CART_CLOSE_GROUP = "CART_CLOSE";
    private static final String TICKETING_START_GROUP = "TICKETING_START";
    private static final String REVIEW_AUTH_GROUP = "REVIEW_AUTH";

    private final Scheduler scheduler;

    @Override
    public void scheduleCartCloseJob(Long scheduleId, LocalDateTime triggerTime) {
        JobDetail job = JobBuilder.newJob(CartCloseQuartzJob.class)
                .withIdentity("CartCloseJob_" + scheduleId, CART_CLOSE_GROUP)
                .usingJobData(CartCloseQuartzJob.SCHEDULE_ID_KEY, scheduleId)
                .storeDurably()
                .build();
        Trigger trigger = buildTrigger("CartCloseTrigger_" + scheduleId, CART_CLOSE_GROUP, triggerTime);
        scheduleJob(job, trigger, scheduleId, "CartClose");
    }

    @Override
    public void scheduleTicketingStartJob(Long scheduleId, LocalDateTime triggerTime) {
        JobDetail job = JobBuilder.newJob(TicketingStartQuartzJob.class)
                .withIdentity("TicketingStartJob_" + scheduleId, TICKETING_START_GROUP)
                .usingJobData(TicketingStartQuartzJob.SCHEDULE_ID_KEY, scheduleId)
                .storeDurably()
                .build();
        Trigger trigger = buildTrigger("TicketingStartTrigger_" + scheduleId, TICKETING_START_GROUP, triggerTime);
        scheduleJob(job, trigger, scheduleId, "TicketingStart");
    }

    @Override
    public void scheduleReviewAuthJob(Long scheduleId, LocalDateTime triggerTime) {
        JobDetail job = JobBuilder.newJob(ReviewAuthQuartzJob.class)
                .withIdentity("ReviewAuthJob_" + scheduleId, REVIEW_AUTH_GROUP)
                .usingJobData(ReviewAuthQuartzJob.SCHEDULE_ID_KEY, scheduleId)
                .storeDurably()
                .build();
        Trigger trigger = buildTrigger("ReviewAuthTrigger_" + scheduleId, REVIEW_AUTH_GROUP, triggerTime);
        scheduleJob(job, trigger, scheduleId, "ReviewAuth");
    }

    @Override
    public void cancelScheduledJobs(Long scheduleId) {
        deleteJob("CartCloseJob_" + scheduleId, CART_CLOSE_GROUP, scheduleId);
        deleteJob("TicketingStartJob_" + scheduleId, TICKETING_START_GROUP, scheduleId);
        deleteJob("ReviewAuthJob_" + scheduleId, REVIEW_AUTH_GROUP, scheduleId);
    }

    private Trigger buildTrigger(String name, String group, LocalDateTime triggerTime) {
        Date startAt = Date.from(triggerTime.atZone(ZoneId.systemDefault()).toInstant());
        return TriggerBuilder.newTrigger()
                .withIdentity(name, group)
                .startAt(startAt)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                .build();
    }

    private void scheduleJob(JobDetail job, Trigger trigger, Long scheduleId, String jobType) {
        try {
            scheduler.scheduleJob(job, trigger);
            log.info("{} Job 등록 - scheduleId={}, triggerTime={}", jobType, scheduleId,
                    trigger.getStartTime());
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
