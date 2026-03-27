package com.example.settlementservice.infrastructure.persistence;

import com.example.settlementservice.application.port.out.WalletRepository;
import com.example.settlementservice.domain.wallet.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class WalletJpaRepository implements WalletRepository {

    private final WalletJpaRepositoryDelegate delegate;

    @Override
    public Optional<Wallet> findByCreatorId(UUID creatorId) {
        return delegate.findByCreatorId(creatorId);
    }

    @Override
    public List<Wallet> findAllByCreatorIdIn(Collection<UUID> creatorIds) {
        return delegate.findAllByCreatorIdIn(creatorIds);
    }

    @Override
    public Wallet save(Wallet wallet) {
        return delegate.save(wallet);
    }

    @Override
    public List<Wallet> saveAll(List<Wallet> wallets) {
        return delegate.saveAll(wallets);
    }
}