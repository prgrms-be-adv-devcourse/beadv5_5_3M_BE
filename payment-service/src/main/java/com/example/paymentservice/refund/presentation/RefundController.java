package com.example.paymentservice.refund.presentation;

import com.example.paymentservice.common.response.ApiResponse;
import com.example.paymentservice.refund.application.RefundService;
import com.example.paymentservice.refund.application.dto.RefundInfo;
import com.example.paymentservice.refund.presentation.dto.RefundRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Refund", description = "환불 API")
@RestController
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @Operation(summary = "환불 요청", description = "결제 건에 대한 환불을 요청한다. (결제 후 7일 이내만 가능)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RefundInfo> requestRefund(@RequestBody @Valid RefundRequest request) {
        return ApiResponse.ok(refundService.requestRefund(request.toCommand()));
    }

    @Operation(summary = "환불 승인", description = "관리자가 환불 요청을 승인한다.")
    @PostMapping("/{refundId}/approve")
    public ApiResponse<RefundInfo> approveRefund(@PathVariable Long refundId) {
        return ApiResponse.ok(refundService.approveRefund(refundId));
    }

    @Operation(summary = "환불 거절", description = "관리자가 환불 요청을 거절한다.")
    @PostMapping("/{refundId}/reject")
    public ApiResponse<RefundInfo> rejectRefund(@PathVariable Long refundId) {
        return ApiResponse.ok(refundService.rejectRefund(refundId));
    }

    @Operation(summary = "환불 단건 조회", description = "환불 ID로 환불 정보를 조회한다.")
    @GetMapping("/{refundId}")
    public ApiResponse<RefundInfo> getRefund(@PathVariable Long refundId) {
        return ApiResponse.ok(refundService.getRefund(refundId));
    }

    @Operation(summary = "사용자별 환불 목록 조회", description = "사용자 ID로 환불 내역을 조회한다.")
    @GetMapping("/users/{userId}")
    public ApiResponse<List<RefundInfo>> getRefundsByUser(@PathVariable UUID userId) {
        return ApiResponse.ok(refundService.getRefundsByUser(userId));
    }
}
