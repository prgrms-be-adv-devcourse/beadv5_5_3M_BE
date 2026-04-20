package com.example.userservice.infrastructure.kafka.consumer;

import com.example.userservice.infrastructure.kafka.KafkaUtil;
import com.example.userservice.infrastructure.kafka.consumer.dto.PaymentConfirmRequest;
import com.example.userservice.infrastructure.kafka.consumer.dto.PaymentRefundRequest;
import com.example.userservice.domain.model.CookieLog;
import com.example.userservice.domain.model.Wallet;
import com.example.userservice.domain.repository.CookieLogRepository;
import com.example.userservice.domain.repository.WalletRepository;
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

    private final WalletRepository walletRepository;
    private final CookieLogRepository cookieLogRepository;
    private final KafkaUtil kafkaUtil;

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
        PaymentConfirmRequest paymentConfirmRequest = kafkaUtil.deserialize(message, PaymentConfirmRequest.class);
        Wallet wallet = walletRepository.findByUserId(paymentConfirmRequest.userId());
        wallet.add(paymentConfirmRequest.cookieAmount());
        CookieLog cookieLog = CookieLog.create(
                paymentConfirmRequest.userId(),
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
        PaymentRefundRequest paymentRefundRequest = kafkaUtil.deserialize(message, PaymentRefundRequest.class);
        Wallet wallet = walletRepository.findByUserId(paymentRefundRequest.userId());
        wallet.deduct(paymentRefundRequest.cookieAmount());
        CookieLog cookieLog = CookieLog.create(
                paymentRefundRequest.userId(),
                -paymentRefundRequest.cookieAmount(),
                null);
        cookieLogRepository.save(cookieLog);
    }

    @DltHandler
    public void handleDlt(String message) {
        log.error("[DLT] ticket.reserved 처리 최종 실패 - 수동 처리 필요. message: {}", message);
    }
}
