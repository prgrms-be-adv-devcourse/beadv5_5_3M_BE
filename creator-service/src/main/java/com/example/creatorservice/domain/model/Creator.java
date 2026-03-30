package com.example.creatorservice.domain.model;

import com.example.creatorservice.presentation.dto.req.JoinRequest;
import io.swagger.v3.oas.annotations.media.Schema;
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

@Schema(description = "크리에이터 엔티티")
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

    @Schema(description = "크리에이터 고유 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Schema(description = "이메일 주소 (고유)", example = "creator@example.com")
    @Column(name = "email", nullable = false, updatable = false)
    private String email;

    @Schema(description = "암호화된 비밀번호", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @Schema(description = "비밀번호 솔트 키", accessMode = Schema.AccessMode.WRITE_ONLY)
    @Column(length = 20)
    private String saltKey;

    @Schema(description = "전화번호", example = "010-1234-5678")
    @Column(name = "phone_number")
    private String phoneNumber;

    @Schema(description = "닉네임 (고유)", example = "멋진크리에이터")
    private String nickname;

    @Schema(description = "은행명", example = "국민은행")
    @Column(name = "bank_name")
    private String bankName;

    @Schema(description = "계좌번호", example = "123-456-789012")
    @Column(name = "account_number")
    private String accountNumber;

    @Schema(description = "예금주명", example = "홍길동")
    @Column(name = "account_holder")
    private String accountHolder;

    @Schema(description = "생성일시")
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;

    @Schema(description = "수정일시")
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