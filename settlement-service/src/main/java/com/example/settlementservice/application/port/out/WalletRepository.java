package com.example.settlementservice.application.port.out;

import com.example.settlementservice.domain.wallet.Wallet;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository {
    Optional<Wallet> findByCreatorId(UUID creatorId);
    List<Wallet> findAllByCreatorIdIn(Collection<UUID> creatorIds);
    Wallet save(Wallet wallet);
    List<Wallet> saveAll(List<Wallet> wallets);
}