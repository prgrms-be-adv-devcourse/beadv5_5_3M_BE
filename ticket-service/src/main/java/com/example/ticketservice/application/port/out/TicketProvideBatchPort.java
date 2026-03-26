package com.example.ticketservice.application.port.out;

public interface TicketProvideBatchPort {
    boolean run(); // true=COMPLETED, false=FAILED/예외
}