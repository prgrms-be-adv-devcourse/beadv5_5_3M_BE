package com.example.streamingservice.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
		name = "entitlement",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_entitlement_user_schedule",
				columnNames = {"user_id", "schedule_id"}),
		indexes = @Index(name = "idx_entitlement_schedule_id", columnList = "schedule_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Entitlement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "schedule_id", nullable = false)
	private Long scheduleId;

	@Column(name = "ticket_id", nullable = false)
	private Long ticketId;

	@Column(name = "authorized_at", nullable = false)
	private Instant authorizedAt;

	private Entitlement(UUID userId, long scheduleId, long ticketId, Instant authorizedAt) {
		this.userId = userId;
		this.scheduleId = scheduleId;
		this.ticketId = ticketId;
		this.authorizedAt = authorizedAt;
	}

	public static Entitlement of(UUID userId, long scheduleId, long ticketId, Instant authorizedAt) {
		return new Entitlement(userId, scheduleId, ticketId, authorizedAt);
	}
}