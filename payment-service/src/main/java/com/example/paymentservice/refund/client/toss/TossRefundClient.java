package com.example.paymentservice.refund.client.toss;

import com.example.paymentservice.payment.client.toss.TossPaymentProperties;
import com.example.paymentservice.refund.client.RefundGateway;
import com.example.paymentservice.refund.client.toss.dto.TossRefundRequest;
import com.example.paymentservice.refund.client.toss.dto.TossRefundResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;

@Component
@RequiredArgsConstructor
public class TossRefundClient implements RefundGateway {

    private final TossPaymentProperties properties;
    private final RestClient restClient = RestClient.create();

    @Override
    public RefundGatewayResponse cancelPayment(String paymentKey, int cancelAmount, String cancelReason) {
        TossRefundRequest request = new TossRefundRequest(cancelReason, cancelAmount);

        TossRefundResponse response = restClient.post()
                .uri(properties.getBaseUrl() + "/v1/payments/" + paymentKey + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodeSecretKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TossRefundResponse.class);

        return response.toGatewayResponse(cancelAmount);
    }

    private String encodeSecretKey() {
        return Base64.getEncoder().encodeToString((properties.getSecretKey() + ":").getBytes());
    }
}
