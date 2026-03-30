package com.example.ticketservice.infrastructure.batch;

import com.example.ticketservice.application.port.out.EventPublisherPort;
import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Ticket;
import com.example.ticketservice.infrastructure.messaging.dto.event.DailyTicketFeeProvideMessage;
import com.example.ticketservice.infrastructure.persistence.TicketJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * 일일 티켓 대금 지급 배치 설정.
 *
 * [방식] Bulk Tasklet
 *   1. CONFIRMED + provideFlag=false 티켓 전체 조회
 *   2. DailyTicketFeeProvideMessage 사전 수집 (Tasklet 트랜잭션 내 lazy load)
 *   3. bulkMarkProvided() — 단일 JPQL UPDATE로 provideFlag=true (DB 먼저)
 *   4. ticket.provide 토픽 Kafka 발행 (bulkKafkaTemplate 사용)
 */
@Slf4j
@Configuration
public class TicketProvideBatchConfig {

    private final TicketJpaRepository ticketJpaRepository;
    private final EventPublisherPort eventPublisherPort;

    public TicketProvideBatchConfig(
            TicketJpaRepository ticketJpaRepository,
            @Qualifier("kafkaBulkEventPublisher") EventPublisherPort eventPublisherPort) {
        this.ticketJpaRepository = ticketJpaRepository;
        this.eventPublisherPort = eventPublisherPort;
    }

    private static final String TOPIC = "ticket.provide";

    @Bean
    public Job ticketProvideJob(JobRepository jobRepository, Step ticketProvideStep) {
        return new JobBuilder("ticketProvideJob", jobRepository)
                .start(ticketProvideStep)
                .build();
    }

    @Bean
    public Step ticketProvideStep(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager) {
        return new StepBuilder("ticketProvideStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {

                    // 1. 처리 대상 조회 (CONFIRMED + 미지급)
                    List<Ticket> tickets = ticketJpaRepository
                            .findAllByStatusAndProvideFlag(TicketStatus.CONFIRMED, false);

                    if (tickets.isEmpty()) {
                        log.debug("대금 지급 처리할 티켓 없음");
                        return RepeatStatus.FINISHED;
                    }

                    // 2. Kafka 메시지 사전 수집 (Tasklet 트랜잭션 내에서 schedule lazy load)
                    List<DailyTicketFeeProvideMessage> messages = tickets.stream()
                            .map(t -> new DailyTicketFeeProvideMessage(
                                    t.getSchedule().getCreatorId(),
                                    t.getId(),
                                    t.getSchedule().getId(),
                                    t.getSchedule().getCookie()))
                            .toList();

                    // 3. DB 먼저: provideFlag = true 벌크 업데이트
                    List<Long> ticketIds = tickets.stream().map(Ticket::getId).toList();
                    int updated = ticketJpaRepository.bulkMarkProvided(ticketIds);

                    // 4. Kafka 발행 (bulkKafkaTemplate — linger.ms + snappy 적용)
                    messages.forEach(msg ->
                            eventPublisherPort.publish(TOPIC, msg.ticketId().toString(), msg));

                    log.info("일일 대금 지급 처리 완료 - DB 업데이트: {}, Kafka 발행: {}",
                            updated, messages.size());

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}