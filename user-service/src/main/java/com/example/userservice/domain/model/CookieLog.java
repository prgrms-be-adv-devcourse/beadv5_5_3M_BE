package com.example.userservice.domain.model;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Entity
@NoArgsConstructor(access = PROTECTED)
public class CookieLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID userId;

    private Integer amount;

    private Long ticketId;

    private LocalDateTime createAt;

    public CookieLog(UUID userId, Integer amount, Long ticketId) {
        this.userId = userId;
        this.amount = amount;
        this.ticketId = ticketId;
    }

    public static CookieLog create(UUID userId, Integer amount, Long ticketId) {
        return new CookieLog(userId, amount, ticketId);
    }

    @PrePersist
    public void onCreate() {
        if (createAt == null) {
            createAt = LocalDateTime.now();
        }
    }
}
