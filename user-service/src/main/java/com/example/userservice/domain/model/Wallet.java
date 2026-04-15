package com.example.userservice.domain.model;

import com.example.userservice.application.exception.InsufficientCookieException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@Table(name = "wallets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    @Id
    private UUID walletId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "wallet_id")
    private User user;

    @Version
    private Long version;

    private Integer balance;

    public static Wallet create(User user) {
        Wallet wallet = new Wallet();
        wallet.user = user;
        wallet.balance = 0;
        return wallet;
    }

    public void deduct(Integer amount) {
        if (this.balance - amount < 0) {
            throw new InsufficientCookieException();
        }
        this.balance -= amount;
    }

    public void add(Integer amount) {
        this.balance += amount;
    }
}
