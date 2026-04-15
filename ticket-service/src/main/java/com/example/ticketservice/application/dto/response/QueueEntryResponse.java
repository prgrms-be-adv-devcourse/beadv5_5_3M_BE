package com.example.ticketservice.application.dto.response;

public record QueueEntryResponse(
        String type,       // "PURCHASED" or "QUEUED"
        TicketResponse ticket,  // PURCHASED 시 티켓 정보
        Long scheduleId,   // QUEUED 시 스케줄 ID
        Long position      // QUEUED 시 대기 순번
) {
    public static QueueEntryResponse purchased(TicketResponse ticket) {
        return new QueueEntryResponse("PURCHASED", ticket, null, null);
    }

    public static QueueEntryResponse queued(Long scheduleId, long position) {
        return new QueueEntryResponse("QUEUED", null, scheduleId, position);
    }
}