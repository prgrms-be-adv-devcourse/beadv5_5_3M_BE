package com.example.ticketservice.infrastructure.messaging.consumer;

import com.example.ticketservice.application.service.QueueAutoProcessService;
import com.example.ticketservice.infrastructure.messaging.KafkaTopics;
import com.example.ticketservice.infrastructure.messaging.dto.event.QueueDrainMessage;
import com.example.ticketservice.infrastructure.util.KafkaMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * queue.drain 토픽 컨슈머.
 *
 * 결제 완료(ticket.paid) 또는 환불로 인한 재고 복구 시 발행된 신호를 소비하여
 * 대기열 드레인을 실행한다.
 *
 * - key=scheduleId 파티셔닝으로 동일 스케줄의 신호는 동일 파티션에 적재 →
 *   단일 컨슈머 스레드가 순차 처리하므로 스케줄 단위 동시 드레인 없음.
 * - concurrency=4: 파티션 수만큼 병렬 소비 (서로 다른 스케줄은 병렬 처리).
 * - 컨슈머 스레드가 checkAndProcess 완료까지 블록 → 소비 속도가 드레인 처리 속도에 맞춰 자동 조절.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueDrainConsumer {

    private final QueueAutoProcessService queueAutoProcessService;
    private final KafkaMessageUtil kafkaMessageUtil;

    @KafkaListener(
            topics = KafkaTopics.QUEUE_DRAIN,
            groupId = "queue-drain-group",
            concurrency = "4"
    )
    public void consume(String message) {
        try {
            QueueDrainMessage msg = kafkaMessageUtil.deserialize(message, QueueDrainMessage.class);
            log.debug("queue.drain 수신 - scheduleId={}", msg.scheduleId());
            queueAutoProcessService.checkAndProcess(msg.scheduleId());
        } catch (Exception e) {
            log.error("queue.drain 처리 실패 - message={}", message, e);
            // 정상 소비 처리하여 Kafka 재시도 무한 루프 방지
            // 다음 queue.drain 메시지에서 재트리거됨
        }
    }
}