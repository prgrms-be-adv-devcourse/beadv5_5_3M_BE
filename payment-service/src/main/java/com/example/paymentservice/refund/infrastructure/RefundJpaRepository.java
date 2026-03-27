package com.example.paymentservice.refund.infrastructure;

import com.example.paymentservice.refund.domain.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefundJpaRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByUserId(UUID userId);

    List<Refund> findByPaymentId(Long paymentId);
}
