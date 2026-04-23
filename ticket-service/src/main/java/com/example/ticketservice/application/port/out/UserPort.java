package com.example.ticketservice.application.port.out;

import com.example.ticketservice.application.dto.request.DeductCookieRequest;
import com.example.ticketservice.application.dto.request.RefundCookieRequest;
import com.example.ticketservice.application.dto.response.DeductCookieResponse;
import com.example.ticketservice.application.dto.response.RefundCookieResponse;

public interface UserPort {
    DeductCookieResponse deductTicketFee(DeductCookieRequest request);
    RefundCookieResponse refundCookie(RefundCookieRequest request);
}
