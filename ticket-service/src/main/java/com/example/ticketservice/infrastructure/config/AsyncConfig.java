package com.example.ticketservice.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 대기열 드레인 전용 스레드풀.
     * t3.large(2 vCPU) 기준으로 코어 2, 최대 4로 설정.
     * queueCapacity로 backpressure를 걸어 스레드 무제한 생성 방지.
     */
    @Bean("queueExecutor")
    public Executor queueExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("queue-async-");
        executor.initialize();
        return executor;
    }
}
