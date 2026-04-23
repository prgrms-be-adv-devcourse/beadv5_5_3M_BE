package com.example.userservice.domain.repository;

import com.example.userservice.domain.model.CookieLog;

public interface CookieLogRepository {

    void save(CookieLog cookieLog);

    boolean existsByPaymentId(Long paymentId);

    boolean existsByRefundId(Long refundId);

    boolean existsByTicketId(Long ticketId);
}
