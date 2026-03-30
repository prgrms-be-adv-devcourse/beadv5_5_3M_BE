package com.example.settlementservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PermanentFailureJpaRepository extends JpaRepository<PermanentFailureEvent, Long> {
}