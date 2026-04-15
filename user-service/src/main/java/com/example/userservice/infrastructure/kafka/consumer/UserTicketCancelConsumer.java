package com.example.userservice.infrastructure.kafka.consumer;

import com.example.userservice.infrastructure.kafka.consumer.dto.TicketCancelRequest;
import com.example.userservice.domain.model.CookieLog;
import com.example.userservice.domain.model.User;
import com.example.userservice.domain.repository.CookieLogRepository;
import com.example.userservice.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserTicketCancelConsumer {

    private final UserRepository userRepository;
    private final CookieLogRepository cookieLogRepository;

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(delay = 1000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(
            topics = "ticket.cancelled",
            groupId = "ticket-service"
    )
    @Transactional
    public void consume(String message) {
        TicketCancelRequest ticketCancelRequest = TicketCancelRequest.fromJson(message);
        User user = userRepository.findById(ticketCancelRequest.userId());
        user.addCookie(ticketCancelRequest.cookieAmount());

        CookieLog cookieLog = CookieLog.create(
                ticketCancelRequest.userId(),
                ticketCancelRequest.cookieAmount(),
                ticketCancelRequest.ticketId());
        cookieLogRepository.save(cookieLog);
    }

    @DltHandler
    public void handleDlt(String message) {
        log.error("[DLT] ticket.reserved 처리 최종 실패 - 수동 처리 필요. message: {}", message);
    }
}
