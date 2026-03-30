package com.example.paymentservice.common.messaging;

import com.example.paymentservice.common.messaging.dto.PaymentConfirmedMessage;
import com.example.paymentservice.common.messaging.dto.PaymentRefundedMessage;
import com.example.paymentservice.payment.application.event.PaymentCompletedEvent;
import com.example.paymentservice.payment.application.event.PaymentFailedEvent;
import com.example.paymentservice.refund.application.event.RefundApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 결제 승인 완료 → payment.confirmed 토픽 발행
     * user-service가 수신하여 cookie_wallets.balance += cookieAmount 처리
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        try {
            PaymentConfirmedMessage message = PaymentConfirmedMessage.of(
                    event.paymentId(), event.userId(), event.amount(), event.cookieAmount());

            log.info("[Kafka] payment.confirmed: paymentId={}, userId={}, cookieAmount={}",
                    event.paymentId(), event.userId(), event.cookieAmount());
            kafkaTemplate.send(PaymentTopics.PAYMENT_CONFIRMED,
                    event.paymentId().toString(), message);
        } catch (Exception e) {
            log.warn("[Kafka] payment.confirmed 발행 실패 (paymentId={}): {}",
                    event.paymentId(), e.getMessage());
        }
    }

    /**
     * 결제 실패 → payment.failed 토픽 발행
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        try {
            log.info("[Kafka] payment.failed: paymentId={}, userId={}",
                    event.paymentId(), event.userId());
            kafkaTemplate.send(PaymentTopics.PAYMENT_FAILED,
                    event.paymentId().toString(), event);
        } catch (Exception e) {
            log.warn("[Kafka] payment.failed 발행 실패 (paymentId={}): {}",
                    event.paymentId(), e.getMessage());
        }
    }

    /**
     * 환불 승인 완료 → payment.refunded 토픽 발행
     * user-service가 수신하여 cookie_wallets.balance -= cookieAmount 처리
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRefundApproved(RefundApprovedEvent event) {
        try {
            PaymentRefundedMessage message = PaymentRefundedMessage.of(
                    event.refundId(), event.paymentId(), event.userId(),
                    event.amount(), event.cookieAmount());

            log.info("[Kafka] payment.refunded: refundId={}, userId={}, cookieAmount={}",
                    event.refundId(), event.userId(), event.cookieAmount());
            kafkaTemplate.send(PaymentTopics.PAYMENT_REFUNDED,
                    event.refundId().toString(), message);
        } catch (Exception e) {
            log.warn("[Kafka] payment.refunded 발행 실패 (refundId={}): {}",
                    event.refundId(), e.getMessage());
        }
    }
}
