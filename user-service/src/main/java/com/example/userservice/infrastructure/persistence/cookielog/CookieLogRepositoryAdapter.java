package com.example.userservice.infrastructure.persistence.cookielog;

import com.example.userservice.domain.model.CookieLog;
import com.example.userservice.domain.repository.CookieLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CookieLogRepositoryAdapter implements CookieLogRepository {

    private final CookieLogJpaRepository cookieLogJpaRepository;

    @Override
    public void save(CookieLog cookieLog) {
        cookieLogJpaRepository.save(cookieLog);
    }

    @Override
    public boolean existsByPaymentId(Long paymentId) {
        return cookieLogJpaRepository.existsByPaymentId(paymentId);
    }

    @Override
    public boolean existsByRefundId(Long refundId) {
        return cookieLogJpaRepository.existsByRefundId(refundId);
    }

    @Override
    public boolean existsByTicketId(Long ticketId) {
        return cookieLogJpaRepository.existsByTicketId(ticketId);
    }

    @Override
    public List<CookieLog> findByUserId(UUID userId) {
        return cookieLogJpaRepository.findByUserIdOrderByCreateAtDesc(userId);
    }
}
