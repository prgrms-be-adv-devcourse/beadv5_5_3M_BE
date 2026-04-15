package com.example.userservice.domain.repository;

import com.example.userservice.domain.model.Wallet;

import java.util.UUID;

public interface WalletRepository {
    Wallet findByUserId(UUID userId);
    void save(Wallet wallet);
}
