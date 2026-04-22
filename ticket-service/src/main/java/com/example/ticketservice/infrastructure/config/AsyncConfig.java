package com.example.ticketservice.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    /**
     * 대기열 드레인 내 병렬 구매 처리 전용 스레드풀 (processWindowParallel).
     * Kafka queue.drain 컨슈머(concurrency=4)가 checkAndProcess를 직접 호출하고,
     * 그 안의 processWindowParallel에서 window 크기만큼 병렬 tryPurchase를 실행.
     * - corePoolSize=4: 컨슈머 스레드 수와 동일, 각 드레인이 1개 이상 스레드 사용 가능
     * - maxPoolSize=8: 버스트 시 최대 확장
     * - queueCapacity=50: 드레인당 window 크기가 stock/queue에 의해 제한되므로 여유 있게 설정
     */
    @Bean("queueExecutor")
    public Executor queueExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("queue-drain-worker-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
