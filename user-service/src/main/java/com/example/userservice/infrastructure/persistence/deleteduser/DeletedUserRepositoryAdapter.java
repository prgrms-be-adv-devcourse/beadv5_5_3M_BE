package com.example.userservice.infrastructure.persistence.deleteduser;

import com.example.userservice.domain.model.DeletedUser;
import com.example.userservice.domain.repository.DeletedUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DeletedUserRepositoryAdapter implements DeletedUserRepository {

    private final DeletedUserJpaRepository deletedUserJpaRepository;

    @Override
    public void save(DeletedUser deletedUser) {
        deletedUserJpaRepository.save(deletedUser);
    }

    @Override
    public boolean existsByUserId(UUID userId) {
        return deletedUserJpaRepository.existsById(userId);
    }
}
