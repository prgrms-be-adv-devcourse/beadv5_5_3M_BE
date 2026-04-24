package com.example.userservice.infrastructure.persistence.wallet;

import com.example.userservice.application.exception.UserNotFoundException;
import com.example.userservice.domain.model.Wallet;
import com.example.userservice.domain.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class WalletRepositoryAdapter implements WalletRepository {

    private final WalletJpaRepository walletJpaRepository;

    @Override
    public Wallet findByUserId(UUID userId) {
        return walletJpaRepository.findByIdWithLock(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    @Override
    public void save(Wallet wallet) {
        walletJpaRepository.save(wallet);
    }
}
