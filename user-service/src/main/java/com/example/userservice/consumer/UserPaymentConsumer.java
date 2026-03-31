package com.example.userservice.consumer;

import com.example.userservice.consumer.dto.PaymentConfirmRequest;
import com.example.userservice.consumer.dto.PaymentRefundRequest;
import com.example.userservice.domain.model.CookieLog;
import com.example.userservice.domain.model.User;
import com.example.userservice.domain.repository.CookieLogRepository;
import com.example.userservice.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserPaymentConsumer {

    private final UserRepository userRepository;
    private final CookieLogRepository cookieLogRepository;

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(delay = 1000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(
            topics = "payment.confirmed",
            groupId = "payment-service"
    )
    @Transactional
    public void paymentConfirmConsumer(String message) {
        PaymentConfirmRequest paymentConfirmRequest = PaymentConfirmRequest.fromJson(message);
        User user = userRepository.findById(paymentConfirmRequest.userId());
        user.addCookie(paymentConfirmRequest.cookieAmount());
        CookieLog cookieLog = CookieLog.create(
                user.getUserId(),
                paymentConfirmRequest.cookieAmount(),
                null);

        cookieLogRepository.save(cookieLog);
    }

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(delay = 1000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(
            topics = "payment.refunded",
            groupId = "payment-service"
    )
    @Transactional
    public void paymentRefundConsumer(String message) {
        PaymentRefundRequest paymentConfirmRequest = PaymentRefundRequest.fromJson(message);
        User user = userRepository.findById(paymentConfirmRequest.userId());
        user.deductCookie(paymentConfirmRequest.cookieAmount());
        CookieLog cookieLog = CookieLog.create(
                user.getUserId(),
                -paymentConfirmRequest.cookieAmount(),
                null);

        cookieLogRepository.save(cookieLog);
    }

    @DltHandler
    public void handleDlt(String message) {
        log.error("[DLT] ticket.reserved 처리 최종 실패 - 수동 처리 필요. message: {}", message);
    }
}
