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
        if (cookieLogRepository.existsByPaymentId(paymentConfirmRequest.paymentId())) {
            log.warn("[payment.confirmed] 중복 처리 방지 - paymentId: {}", paymentConfirmRequest.paymentId());
            return;
        }
        Wallet wallet = walletRepository.findByUserId(paymentConfirmRequest.userId());
        wallet.add(paymentConfirmRequest.cookieAmount());
        CookieLog cookieLog = CookieLog.createForPayment(
                paymentConfirmRequest.userId(),
                paymentConfirmRequest.cookieAmount(),
                paymentConfirmRequest.paymentId());
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
        if (cookieLogRepository.existsByRefundId(paymentRefundRequest.refundId())) {
            log.warn("[payment.refunded] 중복 처리 방지 - refundId: {}", paymentRefundRequest.refundId());
            return;
        }
        Wallet wallet = walletRepository.findByUserId(paymentRefundRequest.userId());
        wallet.deduct(paymentRefundRequest.cookieAmount());
        CookieLog cookieLog = CookieLog.createForRefund(
                paymentRefundRequest.userId(),
                -paymentRefundRequest.cookieAmount(),
                paymentRefundRequest.refundId());
        cookieLogRepository.save(cookieLog);
    }

    @DltHandler
    public void handleDlt(String message) {
        log.error("[DLT] ticket.reserved 처리 최종 실패 - 수동 처리 필요. message: {}", message);
    }
}
