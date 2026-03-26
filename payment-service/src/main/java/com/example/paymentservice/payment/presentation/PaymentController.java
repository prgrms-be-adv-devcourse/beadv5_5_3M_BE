package com.example.paymentservice.payment.presentation;

import com.example.paymentservice.common.response.ApiResponse;
import com.example.paymentservice.payment.application.PaymentService;
import com.example.paymentservice.payment.application.dto.PaymentInfo;
import com.example.paymentservice.payment.presentation.dto.PaymentConfirmRequest;
import com.example.paymentservice.payment.presentation.dto.PaymentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Payment", description = "결제 API")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "결제 생성", description = "Toss 결제를 위한 결제 건을 생성한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentInfo> createPayment(@RequestBody @Valid PaymentRequest request) {
        return ApiResponse.ok(paymentService.createPayment(request.toCommand()));
    }

    @Operation(summary = "결제 승인", description = "Toss 결제 완료 후 paymentKey/orderId를 전달받아 결제를 승인한다.")
    @PostMapping("/confirm")
    public ApiResponse<PaymentInfo> confirmPayment(@RequestBody @Valid PaymentConfirmRequest request) {
        return ApiResponse.ok(paymentService.confirmPayment(request.toCommand()));
    }

    @Operation(summary = "결제 실패 처리", description = "결제 건을 실패 상태로 변경한다.")
    @PostMapping("/{paymentId}/fail")
    public ApiResponse<PaymentInfo> failPayment(@PathVariable Long paymentId) {
        return ApiResponse.ok(paymentService.failPayment(paymentId));
    }

    @Operation(summary = "결제 단건 조회", description = "결제 ID로 결제 정보를 조회한다.")
    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentInfo> getPayment(@PathVariable Long paymentId) {
        return ApiResponse.ok(paymentService.getPayment(paymentId));
    }

    @Operation(summary = "사용자별 결제 목록 조회", description = "사용자 ID로 결제 내역을 조회한다.")
    @GetMapping("/users/{userId}")
    public ApiResponse<List<PaymentInfo>> getPaymentsByUser(@PathVariable UUID userId) {
        return ApiResponse.ok(paymentService.getPaymentsByUser(userId));
    }
}
