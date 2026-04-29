package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.model.Schedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleJpaRepository extends JpaRepository<Schedule, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Schedule s WHERE s.scheduleId = :id")
    Optional<Schedule> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT s FROM Schedule s WHERE s.movie.movieId = :movieId AND s.isConfirmed = true AND s.endTime > :now")
    List<Schedule> findUpcomingByMovieId(@Param("movieId") Long movieId, @Param("now") LocalDateTime now);

    boolean existsByMovieMovieIdAndIsConfirmedTrue(Long movieId);

    @Query("SELECT DISTINCT s.movie FROM Schedule s LEFT JOIN FETCH s.movie.categories WHERE s.status = 'ON_AIR'")
    List<Movie> findOnAirMovies();

    @Query("SELECT s FROM Schedule s JOIN FETCH s.movie m LEFT JOIN FETCH m.categories " +
            "WHERE s.status = 'SCHEDULED' " +
            "AND s.scheduleId = (SELECT MIN(s2.scheduleId) FROM Schedule s2 WHERE s2.movie = s.movie AND s2.status = 'SCHEDULED' AND s2.startTime = (SELECT MIN(s3.startTime) FROM Schedule s3 WHERE s3.movie = s.movie AND s3.status = 'SCHEDULED'))")
    List<Schedule> findScheduleMovies();

    @Query("SELECT s FROM Schedule s JOIN FETCH s.movie WHERE CAST(s.startTime AS date) = :date AND s.isConfirmed = true")
    List<Schedule> findConfirmedByDate(@Param("date") LocalDate date);

    @Query("SELECT s FROM Schedule s JOIN FETCH s.movie WHERE CAST(s.startTime AS date) = :date AND s.isConfirmed = true AND s.movie.creatorId = :creatorId")
    List<Schedule> findConfirmedByDateAndCreator(@Param("date") LocalDate date, @Param("creatorId") UUID creatorId);
}