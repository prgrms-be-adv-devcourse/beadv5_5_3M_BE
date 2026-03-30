package com.example.movieservice.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    @Column(name = "nickname", length = 100)
    private String nickname;

    public void update(String nickname){
        this.nickname = nickname;
    }
}
