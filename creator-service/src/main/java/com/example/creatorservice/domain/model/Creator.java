package com.example.creatorservice.domain.model;

import com.example.creatorservice.presentation.dto.req.JoinRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

@Entity
@Table(
        name = "creators",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_creators_creator_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_creators_creator_nickname", columnNames = "nickname")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Creator {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "email", nullable = false, updatable = false)
    private String email;

    private String password;

    @Column(length = 20)
    private String saltKey;

    @Column(name = "phone_number")
    private String phoneNumber;

    private String nickname;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "account_holder")
    private String accountHolder;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public static Creator create(JoinRequest request) {
        Creator creator = new Creator();
        creator.accountHolder = request.accountHolder();
        creator.accountNumber = request.accountNumber();
        creator.bankName = request.bankName();
        creator.email = request.email();
        creator.nickname = request.nickname();
        creator.phoneNumber = request.phoneNumber();
        creator.saltKey = generateSalt();
        creator.password = new BCryptPasswordEncoder().encode(request.password() + creator.saltKey);
        return creator;
    }

    public void registerAccount(String bankName, String accountNumber, String accountHolder) {
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
    }

    public void registerPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public boolean hasAccount() {
        return this.bankName != null
                && this.accountNumber != null
                && this.accountHolder != null;
    }

    private static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = random.generateSeed(8);
        return Base64.getEncoder().encodeToString(salt);
    }
}