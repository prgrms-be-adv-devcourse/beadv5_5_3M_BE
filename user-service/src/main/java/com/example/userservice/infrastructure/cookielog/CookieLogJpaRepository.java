package com.example.userservice.infrastructure.cookielog;

import com.example.userservice.domain.model.CookieLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CookieLogJpaRepository extends JpaRepository<CookieLog, Long> {
}
