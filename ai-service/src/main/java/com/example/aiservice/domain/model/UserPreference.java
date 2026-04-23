package com.example.aiservice.domain.model;

import com.example.aiservice.domain.model.enums.Gender;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_preference")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserPreference {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "age_group", nullable = false)
    private int ageGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 10)
    private Gender gender;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cluster", columnDefinition = "jsonb")
    private List<ClusterCenter> cluster;

    @Column(name = "watch_count", nullable = false)
    private int watchCount;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "exploration_click_rate", nullable = false)
    private double explorationClickRate;

    @Column(name = "epsilon", nullable = false)
    private double epsilon;

    public UserPreference withCluster(List<ClusterCenter> cluster) {
        return UserPreference.builder()
                .userId(this.userId)
                .ageGroup(this.ageGroup)
                .gender(this.gender)
                .cluster(cluster)
                .watchCount(this.watchCount)
                .updatedAt(LocalDateTime.now())
                .explorationClickRate(this.explorationClickRate)
                .epsilon(this.epsilon)
                .build();
    }
}
