package com.example.paymentservice.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxConfig {

    /**
     * OutboxEnqueuer / OutboxRelay가 주입받는 ObjectMapper.
     * 다른 AutoConfiguration(Jackson 등)에서 이미 등록한 bean이 있으면 그쪽을 우선 사용.
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper outboxObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
