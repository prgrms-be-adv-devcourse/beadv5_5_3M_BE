package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.model.Schedule;
import com.example.movieservice.domain.model.Schedule.ScheduleStatus;
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

    @Query("SELECT s FROM Schedule s WHERE s.movie.movieId = :movieId AND s.isConfirmed = true AND s.endTime > :now")
    List<Schedule> findUpcomingByMovieId(@Param("movieId") Long movieId, @Param("now") LocalDateTime now);

    boolean existsByMovieMovieIdAndIsConfirmedTrue(Long movieId);

    @Query("SELECT s FROM Schedule s WHERE s.status = :status AND s.startTime > :now AND s.startTime <= :tenMinutesLater")
    List<Schedule> findScheduledToWaiting(@Param("status") ScheduleStatus status,
                                          @Param("now") LocalDateTime now,
                                          @Param("tenMinutesLater") LocalDateTime tenMinutesLater);

    @Query("SELECT s FROM Schedule s WHERE s.status IN :statuses AND s.startTime <= :now")
    List<Schedule> findToOnAir(@Param("statuses") List<ScheduleStatus> statuses,
                               @Param("now") LocalDateTime now);

    @Query("SELECT s FROM Schedule s WHERE s.status = :status AND s.endTime <= :tenMinutesAgo")
    List<Schedule> findOnAirToCompleted(@Param("status") ScheduleStatus status,
                                        @Param("tenMinutesAgo") LocalDateTime tenMinutesAgo);

    @Query("SELECT DISTINCT s.movie FROM Schedule s LEFT JOIN FETCH s.movie.categories WHERE s.status = 'ON_AIR'")
    List<Movie> findOnAirMovies();

    @Query("SELECT s FROM Schedule s JOIN FETCH s.movie m LEFT JOIN FETCH m.categories " +
            "WHERE s.status = 'SCHEDULED' " +
            "AND s.startTime = (SELECT MIN(s2.startTime) FROM Schedule s2 WHERE s2.movie = s.movie AND s2.status = 'SCHEDULED')")
    List<Schedule> findScheduleMovies();
}
