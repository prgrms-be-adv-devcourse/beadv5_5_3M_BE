package com.example.paymentservice.payment.client.toss;

import com.example.paymentservice.payment.client.PaymentGateway;
import com.example.paymentservice.payment.client.toss.dto.TossPaymentConfirmRequest;
import com.example.paymentservice.payment.client.toss.dto.TossPaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TossPaymentClient implements PaymentGateway {

    private final TossPaymentProperties properties;
    private final RestClient restClient = RestClient.create();

    @Override
    public PaymentGatewayResponse confirmPayment(String paymentKey, String orderId, int amount) {
        TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(paymentKey, orderId, amount);

        TossPaymentResponse response = restClient.post()
                .uri(properties.getBaseUrl() + "/v1/payments/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodeSecretKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TossPaymentResponse.class);

        return response.toGatewayResponse();
    }

    @Override
    public PaymentGatewayResponse cancelPayment(String paymentKey, String cancelReason) {
        TossPaymentResponse response = restClient.post()
                .uri(properties.getBaseUrl() + "/v1/payments/" + paymentKey + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodeSecretKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("cancelReason", cancelReason))
                .retrieve()
                .body(TossPaymentResponse.class);

        return response.toGatewayResponse();
    }

    private String encodeSecretKey() {
        return Base64.getEncoder().encodeToString((properties.getSecretKey() + ":").getBytes());
    }
}
