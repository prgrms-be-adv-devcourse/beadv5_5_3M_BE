package com.example.ticketservice.application.service;

import com.example.ticketservice.application.port.out.TicketProvideBatchPort;
import com.example.ticketservice.application.usecase.ProvideTicketFeeUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProvideTicketFeeService implements ProvideTicketFeeUseCase {

    private final TicketProvideBatchPort ticketProvideBatchPort;

    @Override
    public void provide() {
        boolean success = ticketProvideBatchPort.run();
        if (!success) {
            log.error("일일 대금 지급 배치 실패");
        }
    }
}