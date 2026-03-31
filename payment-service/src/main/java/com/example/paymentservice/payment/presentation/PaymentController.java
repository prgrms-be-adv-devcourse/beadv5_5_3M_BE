package com.example.paymentservice.payment.presentation;

import com.example.paymentservice.payment.application.PaymentService;
import com.example.paymentservice.payment.application.dto.PaymentInfo;
import com.example.paymentservice.payment.presentation.dto.PaymentConfirmRequest;
import com.example.paymentservice.payment.presentation.dto.PaymentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Payment", description = "결제 API")
@RestController
@RequestMapping("/api/payments/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "결제 생성", description = "Toss 결제를 위한 결제 건을 생성한다.")
    @PostMapping
    public ResponseEntity<PaymentInfo> createPayment(@RequestBody @Valid PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createPayment(request.toCommand()));
    }

    @Operation(summary = "결제 승인", description = "Toss 결제 완료 후 paymentKey/orderId를 전달받아 결제를 승인한다.")
    @PostMapping("/confirm")
    public ResponseEntity<PaymentInfo> confirmPayment(@RequestBody @Valid PaymentConfirmRequest request) {
        return ResponseEntity.ok(paymentService.confirmPayment(request.toCommand()));
    }

    @Operation(summary = "결제 실패 처리", description = "결제 건을 실패 상태로 변경한다.")
    @PostMapping("/{paymentId}/fail")
    public ResponseEntity<PaymentInfo> failPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.failPayment(paymentId));
    }

    @Operation(summary = "결제 단건 조회", description = "결제 ID로 결제 정보를 조회한다.")
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentInfo> getPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.getPayment(paymentId));
    }

    @Operation(summary = "사용자별 결제 목록 조회", description = "사용자 ID로 결제 내역을 조회한다.")
    @GetMapping("/users/{userId}")
    public ResponseEntity<List<PaymentInfo>> getPaymentsByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(paymentService.getPaymentsByUser(userId));
    }
}
