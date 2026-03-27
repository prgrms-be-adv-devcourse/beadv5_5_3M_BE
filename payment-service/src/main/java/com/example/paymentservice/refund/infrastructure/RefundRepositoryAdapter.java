package com.example.paymentservice.refund.infrastructure;

import com.example.paymentservice.refund.domain.model.Refund;
import com.example.paymentservice.refund.domain.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RefundRepositoryAdapter implements RefundRepository {

    private final RefundJpaRepository jpaRepository;

    @Override
    public Refund save(Refund refund) {
        return jpaRepository.save(refund);
    }

    @Override
    public Optional<Refund> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Refund> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public List<Refund> findByPaymentId(Long paymentId) {
        return jpaRepository.findByPaymentId(paymentId);
    }
}
