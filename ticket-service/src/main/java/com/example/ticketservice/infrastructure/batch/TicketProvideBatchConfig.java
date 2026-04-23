package com.example.ticketservice.infrastructure.batch;

import com.example.ticketservice.application.port.out.EventPublisherPort;
import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Ticket;
import com.example.ticketservice.infrastructure.messaging.KafkaTopics;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * 일일 티켓 대금 지급 배치 설정.
 *
 * [방식] Paged Tasklet (pageSize=500)
 *   Slice 기반 페이징으로 메모리 사용량 제한.
 *   각 페이지마다: Kafka 메시지 수집 → bulkMarkProvided() → Kafka 발행.
 */
@Slf4j
@Configuration
public class TicketProvideBatchConfig {

    private static final int PAGE_SIZE = 500;

    private final TicketJpaRepository ticketJpaRepository;
    private final EventPublisherPort eventPublisherPort;

    public TicketProvideBatchConfig(
            TicketJpaRepository ticketJpaRepository,
            @Qualifier("kafkaBulkEventPublisher") EventPublisherPort eventPublisherPort) {
        this.ticketJpaRepository = ticketJpaRepository;
        this.eventPublisherPort = eventPublisherPort;
    }

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
                    int totalUpdated = 0;
                    int totalPublished = 0;
                    int page = 0;

                    Slice<Ticket> slice;
                    do {
                        // provideFlag=false만 조회하므로, 업데이트 후에도 page=0 유지
                        slice = ticketJpaRepository.findByStatusAndProvideFlag(
                                TicketStatus.CONFIRMED, false, PageRequest.of(0, PAGE_SIZE));

                        List<Ticket> tickets = slice.getContent();
                        if (tickets.isEmpty()) {
                            break;
                        }

                        // Kafka 메시지 사전 수집 (Tasklet 트랜잭션 내에서 schedule lazy load)
                        List<DailyTicketFeeProvideMessage> messages = tickets.stream()
                                .map(t -> new DailyTicketFeeProvideMessage(
                                        t.getSchedule().getCreatorId(),
                                        t.getId(),
                                        t.getSchedule().getId(),
                                        t.getSchedule().getCookie()))
                                .toList();

                        // DB 먼저: provideFlag = true 벌크 업데이트
                        List<Long> ticketIds = tickets.stream().map(Ticket::getId).toList();
                        int updated = ticketJpaRepository.bulkMarkProvided(ticketIds);

                        // Kafka 발행 (bulkKafkaTemplate — linger.ms + snappy 적용)
                        messages.forEach(msg ->
                                eventPublisherPort.publish(KafkaTopics.TICKET_PROVIDE, msg.ticketId().toString(), msg));

                        totalUpdated += updated;
                        totalPublished += messages.size();
                        page++;
                    } while (slice.hasNext());

                    if (totalUpdated > 0) {
                        log.info("일일 대금 지급 처리 완료 - 페이지: {}, DB 업데이트: {}, Kafka 발행: {}",
                                page, totalUpdated, totalPublished);
                    } else {
                        log.debug("대금 지급 처리할 티켓 없음");
                    }

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}