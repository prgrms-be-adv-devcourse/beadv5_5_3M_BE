package com.example.settlementservice.infrastructure.kafka;

import com.example.settlementservice.application.port.in.IngestRevenueUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
public class RevenueIngestListener {

    private final IngestRevenueUseCase ingestRevenueUseCase;
    private final ObjectMapper objectMapper;
    private final DlqProducer dlqProducer;

    @KafkaListener(topics = "ticket.provide", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        List<IngestRevenueUseCase.RevenueIngestCommand> commands = new ArrayList<>(records.size());
        List<ConsumerRecord<String, String>> parsedRecords = new ArrayList<>(records.size());

        // 1단계: 역직렬화 — 실패한 레코드는 즉시 DLQ로, 성공한 레코드만 수집
        for (ConsumerRecord<String, String> record : records) {
            try {
                RevenueIngestEventPayload payload = objectMapper.readValue(
                        record.value(), RevenueIngestEventPayload.class);
                commands.add(new IngestRevenueUseCase.RevenueIngestCommand(
                        payload.creatorId(),
                        payload.ticketId(),
                        payload.scheduleId(),
                        payload.cookieAmount()
                ));
                parsedRecords.add(record);
            } catch (Exception e) {
                log.error("Deserialization failed, sending to DLQ: partition={}, offset={}",
                        record.partition(), record.offset(), e);
                dlqProducer.send(record, e);
            }
        }

        // 2단계: 배치 처리 — 실패 시 개별 처리로 폴백 (정상 레코드가 DLQ로 가지 않도록)
        if (!commands.isEmpty()) {
            try {
                ingestRevenueUseCase.ingestRevenueBatch(commands);
            } catch (Exception batchEx) {
                log.warn("Batch processing failed ({}), falling back to individual processing for {} records",
                        batchEx.getMessage(), commands.size());
                processIndividually(commands, parsedRecords);
            }
        }

        // 3단계: 항상 offset commit — 실패는 DLQ에 보존됨
        ack.acknowledge();
        log.debug("Batch committed: total={}, parsed={}, dlq={}",
                records.size(), commands.size(), records.size() - commands.size());
    }

    /**
     * 배치 실패 시 폴백: 레코드 하나씩 독립 처리.
     * 각 레코드가 독립적으로 성공/실패하므로 1건 실패가 나머지에 영향을 주지 않는다.
     */
    private void processIndividually(
            List<IngestRevenueUseCase.RevenueIngestCommand> commands,
            List<ConsumerRecord<String, String>> parsedRecords) {

        int successCount = 0;
        int dlqCount = 0;

        for (int i = 0; i < commands.size(); i++) {
            IngestRevenueUseCase.RevenueIngestCommand cmd = commands.get(i);
            try {
                ingestRevenueUseCase.ingestRevenue(
                        cmd.creatorId(), cmd.ticketId(), cmd.scheduleId(), cmd.cookieAmount());
                successCount++;
            } catch (DataIntegrityViolationException e) {
                // source_event_id UNIQUE 제약 위반 → 이미 처리된 중복 이벤트, 정상 처리로 간주
                log.info("Duplicate event detected via UNIQUE constraint, skipping: ticketId={}", cmd.ticketId());
                successCount++;
            } catch (Exception e) {
                log.error("Individual processing failed, sending to DLQ: ticketId={}", cmd.ticketId(), e);
                dlqProducer.send(parsedRecords.get(i), e);
                dlqCount++;
            }
        }

        log.info("Individual fallback result: total={}, success={}, dlq={}",
                commands.size(), successCount, dlqCount);
    }
}