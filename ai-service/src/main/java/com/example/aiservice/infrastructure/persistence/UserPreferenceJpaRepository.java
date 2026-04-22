package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserPreferenceJpaRepository extends JpaRepository<UserPreference, UUID> {

    @Modifying
    @Query("UPDATE UserPreference p SET p.watchCount = p.watchCount + 1 WHERE p.userId = :userId")
    void incrementWatchCount(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE UserPreference p SET p.epsilon = :epsilon, p.explorationClickRate = :explorationClickRate, p.updatedAt = CURRENT_TIMESTAMP WHERE p.userId = :userId")
    void updateEpsilonAndCtr(@Param("userId") UUID userId, @Param("epsilon") double epsilon, @Param("explorationClickRate") double explorationClickRate);
}
