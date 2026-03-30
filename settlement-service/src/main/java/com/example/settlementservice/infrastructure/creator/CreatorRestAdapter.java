package com.example.settlementservice.infrastructure.creator;

import com.example.settlementservice.application.port.out.CreatorPayoutQueryPort;
import com.example.settlementservice.application.port.out.CreatorPayoutSnapshot;
import com.example.settlementservice.application.exception.CreatorPayoutAccountNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CreatorRestAdapter implements CreatorPayoutQueryPort {

    private final RestClient creatorRestClient;

    @Override
    public CreatorPayoutSnapshot getPayoutSnapshot(UUID creatorId) {
        try {
            CreatorAccountResponse response = creatorRestClient.get()
                    .uri("/internal/creators/{creatorId}/payout-account", creatorId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            (req, resp) -> { throw new CreatorPayoutAccountNotFoundException(creatorId); })
                    .body(CreatorAccountResponse.class);

            return new CreatorPayoutSnapshot(
                    response.creatorId(),
                    response.bankName(),
                    response.accountNumber(),
                    response.accountHolder()
            );
        } catch (HttpClientErrorException.NotFound e) {
            throw new CreatorPayoutAccountNotFoundException(creatorId);
        }
    }

    @Override
    public boolean existsCreator(UUID creatorId) {
        try {
            creatorRestClient.get()
                    .uri("/internal/creators/{creatorId}/exists", creatorId)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }
}