package com.example.settlementservice.application.port.out;

import java.util.UUID;

public interface CreatorPayoutQueryPort {
    CreatorPayoutSnapshot getPayoutSnapshot(UUID creatorId);
    boolean existsCreator(UUID creatorId);
}