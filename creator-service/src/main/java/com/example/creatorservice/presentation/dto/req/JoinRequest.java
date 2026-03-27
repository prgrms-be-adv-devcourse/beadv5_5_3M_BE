package com.example.creatorservice.presentation.dto.req;

public record JoinRequest(
        String email,
        String password,
        String phoneNumber,
        String bankName,
        String accountNumber,
        String accountHolder,
        String nickname
) {


}
