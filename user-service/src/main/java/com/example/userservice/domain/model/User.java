package com.example.userservice.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Schema(description = "유저 엔티티")
@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Schema(description = "유저 고유 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    @Id
    private UUID userId;

    @Schema(description = "이메일 주소 (고유)", example = "user@example.com")
    @NotEmpty
    @Column(unique = true)
    private String email;

    @Schema(description = "암호화된 비밀번호", accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotEmpty
    private String password;

    @Schema(description = "닉네임 (고유)", example = "멋진유저")
    @NotEmpty
    @Column(unique = true, length = 50)
    private String nickname;

    @Schema(description = "프로필 이미지 URL", example = "https://storage.example.com/profiles/uuid.jpg")
    private String profileUrl;

    @Schema(description = "전화번호", example = "010-1234-5678")
    @Column(length = 20)
    private String phone;

    @Schema(description = "비밀번호 솔트 키", accessMode = Schema.AccessMode.WRITE_ONLY)
    @Column(length = 20)
    private String saltKey;

    @Schema(description = "유저 역할", example = "USER")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Schema(description = "연령대", example = "20")
    private Integer ageGroup;

    @Schema(description = "성별", example = "MALE")
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Wallet wallet;

    @Schema(description = "생성일시")
    private LocalDateTime createAt;

    @Schema(description = "수정일시")
    private LocalDateTime updateAt;

    public static User create(String email, String rawPassword, String nickname, Integer ageGroup, Gender gender) {
        User user = new User();
        user.userId = UUID.randomUUID();
        user.email = email;
        user.nickname = nickname;
        user.role = Role.USER;
        user.ageGroup = ageGroup;
        user.gender = gender;
        user.saltKey = generateSalt();
        user.password = new BCryptPasswordEncoder().encode(rawPassword + user.saltKey);
        return user;
    }

    private static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = random.generateSeed(8);
        return Base64.getEncoder().encodeToString(salt);
    }

    public void updateProfile(String nickname, String phone, String profileUrl) {
        if (nickname != null && !nickname.isEmpty()) this.nickname = nickname;
        if (phone != null) this.phone = phone;
        if (profileUrl != null) this.profileUrl = profileUrl;
    }

    public Integer getBalance() {
        return wallet != null ? wallet.getBalance() : 0;
    }

    @PrePersist
    public void onCreate() {
        if (createAt == null) {
            createAt = LocalDateTime.now();
        }

        if (updateAt == null) {
            updateAt = createAt;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updateAt = LocalDateTime.now();
    }

}
