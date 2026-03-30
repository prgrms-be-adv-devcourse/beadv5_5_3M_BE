package com.example.paymentservice.refund.domain.repository;

import com.example.paymentservice.refund.domain.model.Refund;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository {

    Refund save(Refund refund);

    Optional<Refund> findById(Long id);

    Optional<Refund> findByIdForUpdate(Long id);

    List<Refund> findByUserId(UUID userId);

    List<Refund> findByPaymentId(Long paymentId);
}
