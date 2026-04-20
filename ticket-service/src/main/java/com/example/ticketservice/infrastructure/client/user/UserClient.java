package com.example.ticketservice.infrastructure.client.user;

import com.example.ticketservice.application.dto.request.DeductCookieRequest;
import com.example.ticketservice.application.dto.request.RefundCookieRequest;
import com.example.ticketservice.application.dto.response.DeductCookieResponse;
import com.example.ticketservice.application.dto.response.RefundCookieResponse;
import com.example.ticketservice.application.port.out.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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
        } catch (HttpClientErrorException e) {
            // 4xx: 잔액 부족(402), 유저 없음(404) 등 — 차감 실패로 처리
            return new DeductCookieResponse(null, null, null, false);
        } catch (RestClientException e) {
            throw new RuntimeException(e);
        }
    }

    // POST baseUrl + internal/users/refund/cookie
    @Override
    public RefundCookieResponse refundCookie(RefundCookieRequest request) {
        try {
            return userRestClient.post()
                    .uri("/internal/users/refund/cookie")
                    .body(request)
                    .retrieve()
                    .body(RefundCookieResponse.class);
        } catch (HttpClientErrorException e) {
            // 4xx: 환불 대상 없음 등 클라이언트 오류 — flag=false 응답으로 변환하여 호출부에서 처리
            return new RefundCookieResponse(request.userId(), request.ticketId(), 0, false);
        } catch (RestClientException e) {
            throw new RuntimeException(e);
        }
    }
}
