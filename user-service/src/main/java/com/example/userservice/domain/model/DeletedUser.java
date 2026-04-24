package com.example.userservice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "deleted_users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeletedUser {

    @Id
    private UUID userId;

    private LocalDateTime deletedAt;

    public static DeletedUser create(UUID userId) {
        DeletedUser deletedUser = new DeletedUser();
        deletedUser.userId = userId;
        deletedUser.deletedAt = LocalDateTime.now();
        return deletedUser;
    }
}
