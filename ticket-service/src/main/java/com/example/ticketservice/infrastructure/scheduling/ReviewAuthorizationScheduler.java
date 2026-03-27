package com.example.ticketservice.infrastructure.scheduling;

import com.example.ticketservice.application.usecase.ConfirmScheduledTicketsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewAuthorizationScheduler {

    private final ConfirmScheduledTicketsUseCase confirmScheduledTicketsUseCase;

    @Scheduled(cron = "0 50 * * * *")
    public void run() {
        confirmScheduledTicketsUseCase.confirm();
    }
}