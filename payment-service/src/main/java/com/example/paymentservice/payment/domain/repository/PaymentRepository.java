package com.example.paymentservice.payment.domain.repository;

import com.example.paymentservice.payment.domain.model.Payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByIdForUpdate(Long id);

    List<Payment> findByUserId(UUID userId);
}
