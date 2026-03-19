package com.example.skishop.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Transactional Outbox パターンによるイベント発行実装。
 * <p>
 * 既存のビジネストランザクション内で {@code event_outbox} テーブルに INSERT し、
 * 別プロセス（Debezium CDC またはポーリング）が Kafka に中継する。
 * これによりデータ整合性と少なくとも 1 回配信を保証する。
 * </p>
 * <p>Phase 3 で各サービスに導入する。</p>
 */
public class OutboxEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private static final String INSERT_SQL =
            "INSERT INTO event_outbox (event_id, event_type, producer, payload, correlation_id, version) " +
            "VALUES (?, ?, ?, ?::jsonb, ?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> void publish(DomainEvent<T> event) {
        try {
            String payloadJson = objectMapper.writeValueAsString(event.payload());
            jdbcTemplate.update(INSERT_SQL,
                    event.eventId(),
                    event.eventType(),
                    event.producer(),
                    payloadJson,
                    event.correlationId(),
                    event.version());
            log.info("Event written to outbox: type={}, eventId={}, correlationId={}",
                    event.eventType(), event.eventId(), event.correlationId());
        } catch (JsonProcessingException e) {
            throw new EventPublishException(
                    "Failed to serialize event payload: eventId=" + event.eventId(), e);
        }
    }
}
