package com.example.ticketservice.infrastructure.batch;

import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.infrastructure.persistence.TicketJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Arrays;
import java.util.List;

/**
 * 티켓 확정 배치 설정.
 *
 * [방식 A - 현재 채택] Bulk Update Tasklet
 *   단일 JPQL UPDATE 쿼리로 처리 → 성능 최적 (단순 상태 변경에 권장)
 *
 * [방식 B - 대안] JpaPagingItemReader + JpaItemWriter (비즈니스 로직이 복잡해질 경우 전환)
 *   - reader: JpaPagingItemReaderBuilder로 RESERVED 티켓 페이징 조회, saveState(false) 유지
 *   - processor: ticket.confirm() 등 도메인 메서드 호출
 *   - writer: JpaItemWriterBuilder로 변경사항 flush (merge 방식)
 *   단점: 대량 데이터 시 N번의 SELECT + N번의 UPDATE → Bulk Tasklet 대비 성능 열위
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TicketConfirmBatchConfig {

    private final TicketJpaRepository ticketJpaRepository;

    @Bean
    public Job ticketConfirmJob(JobRepository jobRepository, Step ticketConfirmStep) {
        return new JobBuilder("ticketConfirmJob", jobRepository)
                .start(ticketConfirmStep)
                .build();
    }

    @Bean
    public Step ticketConfirmStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("ticketConfirmStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String scheduleIdsParam = (String) chunkContext.getStepContext()
                            .getJobParameters()
                            .get("scheduleIds");

                    if (scheduleIdsParam == null || scheduleIdsParam.isBlank()) {
                        throw new IllegalArgumentException("scheduleIds JobParameter가 필요합니다");
                    }

                    List<Long> scheduleIds = Arrays.stream(scheduleIdsParam.split(","))
                            .map(s -> {
                                try {
                                    return Long.parseLong(s.trim());
                                } catch (NumberFormatException e) {
                                    throw new IllegalArgumentException("잘못된 scheduleId 형식: " + s);
                                }
                            })
                            .toList();

                    // 건별 UPDATE 대신 단일 Bulk Update 쿼리로 성능 최적화
                    int updatedCount = ticketJpaRepository.bulkUpdateStatus(
                            scheduleIds, TicketStatus.RESERVED, TicketStatus.CONFIRMED);

                    log.info("티켓 확정 완료 - scheduleIds: {}, 처리 건수: {}", scheduleIds, updatedCount);

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}