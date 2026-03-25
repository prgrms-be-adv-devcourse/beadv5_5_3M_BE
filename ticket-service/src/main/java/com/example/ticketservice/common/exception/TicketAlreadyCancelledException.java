package com.example.ticketservice.common.exception;

import java.util.UUID;

public class TicketAlreadyCancelledException extends RuntimeException {
    public TicketAlreadyCancelledException(Long ticketId) {
        super("Ticket already cancelled: " + ticketId);
    }
}
