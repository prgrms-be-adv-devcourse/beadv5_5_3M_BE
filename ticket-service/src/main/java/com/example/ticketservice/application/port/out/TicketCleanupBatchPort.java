package com.example.ticketservice.application.port.out;

public interface TicketCleanupBatchPort {

    // 미결제 RESERVED 티켓 일괄 DELETE
    void run(Long scheduleId);
}