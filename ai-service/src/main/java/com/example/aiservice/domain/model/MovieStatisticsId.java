package com.example.aiservice.domain.model;

import com.example.aiservice.domain.model.enums.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MovieStatisticsId implements Serializable {

    @Column(name = "movie_id")
    private Long movieId;

    @Column(name = "age_group")
    private int ageGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;
}
