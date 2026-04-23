package com.example.userservice.infrastructure.persistence.cookielog;

import com.example.userservice.domain.model.CookieLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CookieLogJpaRepository extends JpaRepository<CookieLog, Long> {

    boolean existsByPaymentId(Long paymentId);

    boolean existsByRefundId(Long refundId);

    boolean existsByTicketId(Long ticketId);
}
