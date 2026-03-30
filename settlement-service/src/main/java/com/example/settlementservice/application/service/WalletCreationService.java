package com.example.settlementservice.application.service;

import com.example.settlementservice.application.port.out.WalletRepository;
import com.example.settlementservice.domain.wallet.Wallet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletCreationService {

    private final WalletRepository walletRepository;

    /**
     * Wallet을 조회하거나 없으면 새로 생성한다.
     * REQUIRES_NEW: 호출자의 트랜잭션과 독립 실행 — DataIntegrityViolationException이
     * 발생해도 외부 트랜잭션을 오염시키지 않음.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Wallet findOrCreate(UUID creatorId) {
        return walletRepository.findByCreatorId(creatorId)
                .orElseGet(() -> {
                    try {
                        return walletRepository.save(Wallet.create(creatorId));
                    } catch (DataIntegrityViolationException e) {
                        // 동시 생성 시 UNIQUE 제약 위반 → 이미 생성된 Wallet 재조회
                        log.warn("Concurrent wallet creation detected for creator {}, re-fetching", creatorId);
                        return walletRepository.findByCreatorId(creatorId)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Wallet not found after constraint violation for creator: " + creatorId));
                    }
                });
    }
}