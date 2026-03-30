package com.example.settlementservice.application.port.in;

import java.util.List;
import java.util.UUID;

public interface IngestRevenueUseCase {

    void ingestRevenue(UUID creatorId, Long ticketId, Long scheduleId, Integer cookieAmount);

    void ingestRevenueBatch(List<RevenueIngestCommand> commands);

    record RevenueIngestCommand(UUID creatorId, Long ticketId, Long scheduleId, Integer cookieAmount) {}
}