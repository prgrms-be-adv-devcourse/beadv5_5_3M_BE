package com.example.userservice.presentation;

import com.example.userservice.application.usecase.UserUseCase;
import com.example.userservice.presentation.dto.req.DeductCookieRequest;
import com.example.userservice.presentation.dto.req.RefundCookieRequest;
import com.example.userservice.presentation.dto.res.DeductCookieResponse;
import com.example.userservice.presentation.dto.res.RefundCookieResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User Internal", description = "유저 내부 API (서비스 간 통신 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
public class UserInternalController {

    private final UserUseCase userUseCase;

    @Operation(summary = "쿠키 차감", description = "유저의 쿠키 잔액을 차감합니다. (내부 서비스 전용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "쿠키 차감 성공",
                    content = @Content(schema = @Schema(implementation = DeductCookieResponse.class))),
            @ApiResponse(responseCode = "400", description = "쿠키 잔액 부족", content = @Content),
            @ApiResponse(responseCode = "404", description = "유저를 찾을 수 없음", content = @Content)
    })
    @PostMapping("/deduct/cookie")
    public ResponseEntity<?> deductCookie(@RequestBody DeductCookieRequest request) {
        return ResponseEntity.ok().body(userUseCase.deductCookie(request));
    }

    @Operation(summary = "쿠키 환불", description = "유저의 쿠키 잔액을 환불합니다. (내부 서비스 전용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "쿠키 환불 성공",
                    content = @Content(schema = @Schema(implementation = RefundCookieResponse.class))),
            @ApiResponse(responseCode = "404", description = "유저를 찾을 수 없음", content = @Content)
    })
    @PostMapping("/refund/cookie")
    public ResponseEntity<RefundCookieResponse> refundCookie(@RequestBody RefundCookieRequest request) {
        return ResponseEntity.ok(userUseCase.refundCookie(request));
    }

}
