package com.example.userservice.infrastructure.cookielog;

import com.example.userservice.domain.model.CookieLog;
import com.example.userservice.domain.repository.CookieLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CookieLogRepositoryAdapter implements CookieLogRepository {

    private final CookieLogJpaRepository cookieLogJpaRepository;

    @Override
    public void save(CookieLog cookieLog) {
        cookieLogJpaRepository.save(cookieLog);
    }
}
