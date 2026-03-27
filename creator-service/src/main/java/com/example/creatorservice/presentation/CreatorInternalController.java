package com.example.creatorservice.presentation;

import com.example.creatorservice.application.usecase.CreatorInternalUseCase;
import com.example.creatorservice.presentation.dto.PayoutAccountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/creators")
@RequiredArgsConstructor
public class CreatorInternalController {

    private final CreatorInternalUseCase creatorInternalUseCase;

    @GetMapping("/{creatorId}/payout-account")
    public PayoutAccountResponse getPayoutAccount(@PathVariable UUID creatorId) {
        return creatorInternalUseCase.getPayoutAccount(creatorId);
    }
}