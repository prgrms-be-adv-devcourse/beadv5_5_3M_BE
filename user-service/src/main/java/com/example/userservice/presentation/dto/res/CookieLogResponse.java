package com.example.userservice.presentation.dto.res;

import com.example.userservice.domain.model.CookieLog;

import java.time.LocalDateTime;

public record CookieLogResponse(
        Long id,
        Integer amount,
        Long paymentId,
        Long refundId,
        Long ticketId,
        LocalDateTime createAt
) {
    public static CookieLogResponse from(CookieLog log) {
        return new CookieLogResponse(
                log.getId(),
                log.getAmount(),
                log.getPaymentId(),
                log.getRefundId(),
                log.getTicketId(),
                log.getCreateAt()
        );
    }
}
