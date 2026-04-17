package com.example.aiservice.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "creator")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Creator {

    @Id
    @Column(name = "creator_id")
    private UUID creatorId;

    @Column(name = "nickname", nullable = false, length = 100)
    private String nickname;

    public void update(String nickname) {
        this.nickname = nickname;
    }
}
