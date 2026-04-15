package com.example.ticketservice.infrastructure.caching.listener;

import com.example.ticketservice.domain.repository.CartItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleKeyExpirationListener implements MessageListener {

    private static final String SCHEDULE_KEY_PREFIX = "cart:schedule:";

    private final CartItemRepository cartItemRepository;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();

        if (!expiredKey.startsWith(SCHEDULE_KEY_PREFIX)) {
            return;
        }

        Long scheduleId = Long.parseLong(expiredKey.substring(SCHEDULE_KEY_PREFIX.length()));
        long count = cartItemRepository.countByScheduleId(scheduleId);

        log.info("[Redis Notification] 티케팅 하루 전 - scheduleId={}, 장바구니 담은 유저 수={}", scheduleId, count);
    }
}
