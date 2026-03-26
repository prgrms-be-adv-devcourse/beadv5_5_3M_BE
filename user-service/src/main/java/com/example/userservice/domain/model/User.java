package com.example.userservice.domain.model;

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

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    private UUID userId;

    @NotEmpty
    @Column(unique = true)
    private String email;

    @NotEmpty
    private String password;

    @NotEmpty
    @Column(unique = true, length = 50)
    private String nickname;

    private String profileUrl;

    @Column(length = 20)
    private String phone;

    @Column(length = 20)
    private String saltKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private Integer balance;

    private LocalDateTime createAt;
    private LocalDateTime updateAt;

    public static User create(String email, String rawPassword, String nickname) {
        User user = new User();
        user.userId = UUID.randomUUID();
        user.email = email;
        user.nickname = nickname;
        user.role = Role.USER;
        user.saltKey = generateSalt();
        user.password = new BCryptPasswordEncoder().encode(rawPassword + user.saltKey);
        user.balance = 0;
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
