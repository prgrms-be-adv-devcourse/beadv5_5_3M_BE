package com.example.settlementservice.infrastructure.kafka;


public record DlqMessage(
        String originalPayload,   // 원본 Kafka 메시지 값
        String errorMessage,      // 실패 원인 메시지
        String topic,             // 원본 토픽
        int partition,            // 원본 파티션
        long offset,              // 원본 오프셋
        String occurredAt,        // UTC 기준 최초 실패 시각 (ISO-8601)
        int retryCount            // DLQ 재처리 시도 횟수 (초기값 0)
) {}