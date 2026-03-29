package com.example.ticketservice.application.port.out;

import com.example.ticketservice.application.dto.request.DeductCookieRequest;
import com.example.ticketservice.application.dto.response.DeductCookieResponse;

public interface UserPort {
    DeductCookieResponse deductTicketFee(DeductCookieRequest request);
}
