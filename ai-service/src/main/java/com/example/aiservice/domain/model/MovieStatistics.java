package com.example.aiservice.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "movie_statistics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MovieStatistics {

    @EmbeddedId
    private MovieStatisticsId id;

    @Column(name = "watch_count", nullable = false)
    private int watchCount;
}
