package com.example.settlementservice.domain.wallet;

import com.example.settlementservice.domain.common.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "wallets",
        uniqueConstraints = @UniqueConstraint(name = "uk_wallets_creator_id", columnNames = "creator_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID creatorId;

    @Column(nullable = false)
    private Long balance = 0L;

    @Version
    private Long version;

    public static Wallet create(UUID creatorId) {
        Wallet wallet = new Wallet();
        wallet.creatorId = creatorId;
        wallet.balance = 0L;
        return wallet;
    }

    public void addBalance(Money amount) {
        this.balance += amount.value();
    }

    public void subtractBalance(Money amount) {
        if (this.balance < amount.value()) {
            throw new IllegalStateException(
                    "Insufficient balance for creator " + creatorId + ": " + this.balance + " < " + amount.value()
            );
        }
        this.balance -= amount.value();
    }

    public Money getBalanceAsMoney() {
        return new Money(this.balance);
    }
}