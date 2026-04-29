package com.example.ticketservice.infrastructure.scheduling;

import com.example.ticketservice.application.usecase.ProvideTicketFeeUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyTicketFeeProvideScheduler {

    private final ProvideTicketFeeUseCase provideTicketFeeUseCase;

    @Scheduled(cron = "0 54 16 * * *") // 매일 오후 4시 54분
    public void run() {
        provideTicketFeeUseCase.provide();
    }
}