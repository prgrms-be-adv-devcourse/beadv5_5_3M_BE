package com.example.userservice.domain.repository;

import com.example.userservice.domain.model.CookieLog;

import java.util.List;
import java.util.UUID;

public interface CookieLogRepository {

    void save(CookieLog cookieLog);

    boolean existsByPaymentId(Long paymentId);

    boolean existsByRefundId(Long refundId);

    boolean existsByTicketId(Long ticketId);

    boolean existsByTicketIdAndAmountGreaterThan(Long ticketId, Integer amount);

    List<CookieLog> findByUserId(UUID userId);
}
