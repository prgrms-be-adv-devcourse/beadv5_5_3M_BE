package com.example.userservice.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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

    private String profileUrl;

    @Column(length = 20)
    private String saltKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private LocalDateTime createAt;
    private LocalDateTime updateAt;

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
