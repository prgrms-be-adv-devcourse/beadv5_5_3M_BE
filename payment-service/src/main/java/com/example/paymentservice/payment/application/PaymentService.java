package com.example.paymentservice.payment.application;

import com.example.paymentservice.common.exception.BusinessException;
import com.example.paymentservice.common.exception.ErrorCode;
import com.example.paymentservice.payment.application.dto.PaymentCommand;
import com.example.paymentservice.payment.application.dto.PaymentConfirmCommand;
import com.example.paymentservice.payment.application.dto.PaymentInfo;
import com.example.paymentservice.payment.application.event.PaymentCompletedEvent;
import com.example.paymentservice.payment.application.event.PaymentFailedEvent;
import com.example.paymentservice.payment.client.PaymentGateway;
import com.example.paymentservice.payment.client.PaymentGateway.PaymentGatewayResponse;
import com.example.paymentservice.payment.domain.model.Payment;
import com.example.paymentservice.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PaymentInfo createPayment(PaymentCommand command) {
        Payment payment = Payment.create(command.userId(), command.amount(), command.cookieAmount());
        return PaymentInfo.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentInfo confirmPayment(PaymentConfirmCommand command) {
        Payment payment = paymentRepository.findByIdForUpdate(command.paymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        payment.markInProgress();
        paymentRepository.save(payment);

        try {
            PaymentGatewayResponse pgResponse = paymentGateway.confirmPayment(
                    command.paymentKey(), command.orderId(), payment.getAmount());
            payment.markSuccess(pgResponse.paymentKey(), command.orderId());
        } catch (Exception e) {
            log.error("[Payment] PG 결제 확인 실패: paymentId={}, reason={}", payment.getId(), e.getMessage(), e);
            payment.markFailed();
            paymentRepository.save(payment);
            eventPublisher.publishEvent(
                    PaymentFailedEvent.of(payment.getId(), payment.getUserId())
            );
            throw new BusinessException(ErrorCode.PG_CONFIRM_FAILED);
        }
        paymentRepository.save(payment);

        eventPublisher.publishEvent(
                PaymentCompletedEvent.of(payment.getId(), payment.getUserId(),
                        payment.getAmount(), payment.getCookieAmount())
        );

        return PaymentInfo.from(payment);
    }

    @Transactional
    public PaymentInfo failPayment(Long paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        payment.markFailed();
        Payment saved = paymentRepository.save(payment);

        eventPublisher.publishEvent(
                PaymentFailedEvent.of(saved.getId(), saved.getUserId())
        );

        return PaymentInfo.from(saved);
    }

    public PaymentInfo getPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        return PaymentInfo.from(payment);
    }

    public List<PaymentInfo> getPaymentsByUser(UUID userId) {
        return paymentRepository.findByUserId(userId).stream()
                .map(PaymentInfo::from)
                .toList();
    }
}
