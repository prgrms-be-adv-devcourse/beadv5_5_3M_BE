package com.example.ticketservice.infrastructure.client.user;

import com.example.ticketservice.application.dto.request.DeductCookieRequest;
import com.example.ticketservice.application.dto.response.DeductCookieResponse;
import com.example.ticketservice.application.port.out.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class UserClient implements UserPort {
    private final RestClient userRestClient;

    // POST baseUrl + internal/users/deduct/cookie
    @Override
    public DeductCookieResponse deductTicketFee(DeductCookieRequest request) {

        try {
            return userRestClient.post()
                    .uri("/internal/users/deduct/cookie")
                    .body(request)
                    .retrieve()
                    .body(DeductCookieResponse.class);
        } catch (RestClientException e) {
            throw new RuntimeException(e);
        }
    }
}
