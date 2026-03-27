package com.example.settlementservice.infrastructure.persistence;

import com.example.settlementservice.domain.wallet.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletJpaRepositoryDelegate extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByCreatorId(UUID creatorId);
    List<Wallet> findAllByCreatorIdIn(Collection<UUID> creatorIds);
}