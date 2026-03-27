package com.example.ticketservice.application.port.out;

import java.util.List;

public interface TicketConfirmBatchPort {
    boolean run(List<Long> scheduleIds); // true=COMPLETED, false=FAILED/예외
}