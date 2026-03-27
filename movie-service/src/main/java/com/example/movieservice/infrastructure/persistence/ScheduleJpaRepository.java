package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ScheduleJpaRepository extends JpaRepository<Schedule, Long> {
    @Query("SELECT COUNT(s) > 0 FROM Schedule s WHERE s.movie.creatorId = :creatorId AND s.isConfirmed = true AND s.startTime < :endTime AND s.endTime > :startTime")
    boolean existsOverlapping(@Param("creatorId") UUID creatorId, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Query("SELECT s FROM Schedule s WHERE s.movie.creatorId = :creatorId AND CAST(s.startTime AS date) = :date")
    List<Schedule> findAllByCreatorIdAndDate(@Param("creatorId") UUID creatorId, @Param("date") LocalDate date);
}
