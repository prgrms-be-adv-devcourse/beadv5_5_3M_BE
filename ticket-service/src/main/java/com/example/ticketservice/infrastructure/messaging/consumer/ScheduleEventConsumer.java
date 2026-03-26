package com.example.ticketservice.infrastructure.messaging.consumer;

import com.example.ticketservice.common.exception.ScheduleErrorCode;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.model.Ticket;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import com.example.ticketservice.domain.repository.TicketRepository;
import com.example.ticketservice.infrastructure.messaging.dto.request.ScheduleConfirmedMessage;
import com.example.ticketservice.infrastructure.util.KafkaMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleEventConsumer {

    private final ScheduleRepository scheduleRepository;
    private final TicketRepository ticketRepository;
    private final KafkaMessageUtil kafkaMessageUtil;

    @Transactional
    @KafkaListener(topics = "movie.schedule.confirmed", groupId = "ticket-group")
    public void consume(String message) {
        ScheduleConfirmedMessage request = kafkaMessageUtil.deserialize(message, ScheduleConfirmedMessage.class);

        if (scheduleRepository.existsById(request.scheduleId())) {
            throw ScheduleErrorCode.ALREADY_CONFIRMED.of(request.scheduleId());
        }

        Schedule schedule = Schedule.create(
                request.scheduleId(),
                request.startTime(),
                request.endTime(),
                request.title(),
                request.cookie(),
                request.creatorId(),
                request.movieId(),
                request.imageUrl(),
                request.seats()
        );
        scheduleRepository.save(schedule);

        // 좌석 수만큼 AVAILABLE 티켓 미리 생성 (ticketNum: 1 ~ seats)
        List<Ticket> tickets = IntStream.rangeClosed(1, request.seats())
                .mapToObj(num -> Ticket.create(schedule, num))
                .toList();
        ticketRepository.saveAll(tickets);

        log.debug("Schedule confirmed: scheduleId={}, tickets created: {}", request.scheduleId(), request.seats());
    }
}