package com.example.userservice.infrastructure.kafka.consumer;

import com.example.userservice.infrastructure.kafka.KafkaUtil;
import com.example.userservice.infrastructure.kafka.consumer.dto.TicketCancelRequest;
import com.example.userservice.domain.model.CookieLog;
import com.example.userservice.domain.model.Wallet;
import com.example.userservice.domain.repository.CookieLogRepository;
import com.example.userservice.domain.repository.WalletRepository;
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

    private final WalletRepository walletRepository;
    private final CookieLogRepository cookieLogRepository;
    private final KafkaUtil kafkaUtil;

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
        TicketCancelRequest ticketCancelRequest = kafkaUtil.deserialize(message, TicketCancelRequest.class);
        Wallet wallet = walletRepository.findByUserId(ticketCancelRequest.userId());
        wallet.add(ticketCancelRequest.cookieAmount());

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
