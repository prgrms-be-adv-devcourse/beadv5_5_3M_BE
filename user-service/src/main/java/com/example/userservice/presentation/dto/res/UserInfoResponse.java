package com.example.userservice.presentation.dto.res;

public record UserInfoResponse(
        String nickname,
        Integer cookieBalance,
        String email,
        String profileUrl,
        String phone
) {
}
