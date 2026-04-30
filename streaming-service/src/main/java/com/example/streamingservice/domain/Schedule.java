package com.example.streamingservice.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "schedule", indexes = {
		@Index(name = "idx_schedule_start_time", columnList = "start_time"),
		@Index(name = "idx_schedule_end_time", columnList = "end_time")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule {

	private static final Duration LOBBY_LEAD = Duration.ofMinutes(10);
	private static final Duration POST_GRACE = Duration.ofMinutes(3);

	@Id
	@Column(name = "schedule_id", nullable = false)
	private Long scheduleId;

	@Column(name = "movie_id", nullable = false)
	private Long movieId;

	@Column(name = "creator_id", nullable = false)
	private UUID creatorId;

	@Column(name = "title", nullable = false, length = 255)
	private String title;

	@Setter
	@Column(name = "start_time", nullable = false)
	private Instant startTime;

	@Setter
	@Column(name = "end_time", nullable = false)
	private Instant endTime;

	@Column(name = "video_path", length = 1024)
	private String videoPath;

	@Column(name = "running_time")
	private Integer runningTime;

	@Column(name = "seats", nullable = false)
	private Integer seats;

	@Column(name = "image_url", length = 1024)
	private String imageUrl;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Schedule(Long scheduleId, Long movieId, UUID creatorId, String title,
	                Instant startTime, Instant endTime, Integer seats, String imageUrl) {
		this.scheduleId = scheduleId;
		this.movieId = movieId;
		this.creatorId = creatorId;
		this.title = title;
		this.startTime = startTime;
		this.endTime = endTime;
		this.seats = seats;
		this.imageUrl = imageUrl;
	}

	public ScheduleStatus currentStatus(Instant now) {
		Instant lobbyStart = startTime.minus(LOBBY_LEAD);
		Instant postEnd = endTime.plus(POST_GRACE);
		if (now.isBefore(lobbyStart)) {
			return ScheduleStatus.CLOSED;
		}
		if (now.isBefore(startTime)) {
			return ScheduleStatus.LOBBY;
		}
		if (now.isBefore(endTime)) {
			return ScheduleStatus.ON_AIR;
		}
		if (now.isBefore(postEnd)) {
			return ScheduleStatus.POST;
		}
		return ScheduleStatus.FINISHED;
	}

	public boolean canEnterSession(Instant now) {
		ScheduleStatus status = currentStatus(now);
		return status == ScheduleStatus.LOBBY
				|| status == ScheduleStatus.ON_AIR
				|| status == ScheduleStatus.POST;
	}

	public boolean canServeHls(Instant now) {
		return currentStatus(now) == ScheduleStatus.ON_AIR;
	}

	public boolean isForceExitTime(Instant now) {
		return !now.isBefore(endTime.plus(POST_GRACE));
	}

	public void attachVideoLocation(String videoPath, Integer runningTime) {
		if (this.videoPath != null) {
			throw new IllegalStateException(
					"videoPath already set for schedule " + scheduleId);
		}
		this.videoPath = videoPath;
		this.runningTime = runningTime;
	}
}