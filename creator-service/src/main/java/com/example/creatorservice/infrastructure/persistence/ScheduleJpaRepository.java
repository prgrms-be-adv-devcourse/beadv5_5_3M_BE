package com.example.creatorservice.infrastructure.persistence;

import com.example.creatorservice.domain.model.Schedule;
import com.example.creatorservice.domain.model.Schedule.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ScheduleJpaRepository extends JpaRepository<Schedule, Long> {

    @Query("SELECT COUNT(s) > 0 FROM Schedule s WHERE s.movie.creatorId = :creatorId AND s.isConfirmed = true AND s.startTime < :endTime AND s.endTime > :startTime")
    boolean existsOverlapping(@Param("creatorId") UUID creatorId,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime);

    @Query("SELECT s FROM Schedule s JOIN FETCH s.movie WHERE s.movie.creatorId = :creatorId AND CAST(s.startTime AS date) = :date")
    List<Schedule> findAllByCreatorIdAndDate(@Param("creatorId") UUID creatorId,
                                             @Param("date") LocalDate date);

    @Query("SELECT s FROM Schedule s WHERE s.status = 'SCHEDULED' AND s.startTime > :now AND s.startTime <= :tenMinutesLater")
    List<Schedule> findScheduledToWaiting(@Param("now") LocalDateTime now,
                                          @Param("tenMinutesLater") LocalDateTime tenMinutesLater);

    @Query("SELECT s FROM Schedule s WHERE s.status IN ('SCHEDULED', 'WAITING') AND s.startTime <= :now")
    List<Schedule> findToOnAir(@Param("now") LocalDateTime now);

    @Query("SELECT s FROM Schedule s WHERE s.status = 'ON_AIR' AND s.endTime <= :tenMinutesAgo")
    List<Schedule> findOnAirToCompleted(@Param("tenMinutesAgo") LocalDateTime tenMinutesAgo);
}