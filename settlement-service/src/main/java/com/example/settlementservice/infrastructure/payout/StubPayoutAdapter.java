package com.example.settlementservice.infrastructure.payout;

import com.example.settlementservice.application.port.out.PayoutPort;
import com.example.settlementservice.domain.settlement.Settlement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StubPayoutAdapter implements PayoutPort {

    @Override
    public boolean payout(Settlement settlement) {
        log.info("[STUB] Payout executed - settlementId={}, requestAmount={}, account={}",
                settlement.getId(),
                settlement.getRequestAmount(),
                settlement.getPayoutAccountNumber()
        );
        return true;
    }
}