package com.example.ticketservice.infrastructure.caching.config;

import com.example.ticketservice.infrastructure.caching.listener.ScheduleKeyExpirationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        // keyspace notification 활성화 (E: keyevent, x: expired)
        connectionFactory.getConnection().serverCommands()
                .setConfig("notify-keyspace-events", "Ex");
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ScheduleKeyExpirationListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // 모든 DB의 expired 이벤트 구독
        container.addMessageListener(listener, new PatternTopic("__keyevent@*__:expired"));
        return container;
    }
}
