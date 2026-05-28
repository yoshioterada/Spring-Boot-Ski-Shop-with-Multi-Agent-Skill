package com.example.skishop.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class OutboxEventRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventRelay.class);
        private static final int MAX_ERROR_LENGTH = 1_000;
    private static final String FIELD_PRODUCER = "producer";

    private static final String SELECT_PENDING = """
            SELECT id, event_id, event_type, producer, payload::text AS payload, correlation_id, version, created_at, retry_count
            FROM event_outbox
            WHERE status = 'PENDING'
              AND (next_retry_at IS NULL OR next_retry_at <= NOW())
            ORDER BY created_at
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """;

    private static final String MARK_PUBLISHED = """
            UPDATE event_outbox
            SET status = 'PUBLISHED', published_at = NOW(), last_error = NULL
            WHERE id = ?
            """;

        private static final String MARK_RETRY = """
            UPDATE event_outbox
            SET retry_count = retry_count + 1,
            next_retry_at = ?,
            last_error = ?
            WHERE id = ?
            """;

        private static final String MARK_FAILED = """
            UPDATE event_outbox
            SET status = 'FAILED',
            retry_count = retry_count + 1,
            next_retry_at = NULL,
            last_error = ?,
            failed_at = NOW()
            WHERE id = ?
            """;

        private static final String COUNT_BY_STATUS = "SELECT COUNT(*) FROM event_outbox WHERE status = ?";
        private static final String OLDEST_PENDING_AGE_SECONDS = """
            SELECT COALESCE(EXTRACT(EPOCH FROM (NOW() - MIN(created_at))), 0)
            FROM event_outbox
            WHERE status = 'PENDING'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;
    private final String bindingName;
        private final OutboxRelayProperties properties;
        private final Counter successCounter;
        private final Counter retryCounter;
        private final Counter failedCounter;
        private final Timer publishTimer;

    public OutboxEventRelay(JdbcTemplate jdbcTemplate,
                            TransactionTemplate transactionTemplate,
                            StreamBridge streamBridge,
                            ObjectMapper objectMapper,
                            String bindingName,
                            int batchSize) {
        this(jdbcTemplate,
            transactionTemplate,
            streamBridge,
            objectMapper,
            null,
            bindingName,
            OutboxRelayProperties.ofMillis(batchSize, 10, 5_000, 300_000));
        }

        public OutboxEventRelay(JdbcTemplate jdbcTemplate,
                    TransactionTemplate transactionTemplate,
                    StreamBridge streamBridge,
                    ObjectMapper objectMapper,
                    MeterRegistry meterRegistry,
                    String bindingName,
                    OutboxRelayProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.streamBridge = streamBridge;
        this.objectMapper = objectMapper;
        this.bindingName = bindingName;
        this.properties = properties;
        this.successCounter = counter(meterRegistry, "skishop_outbox_relay_success_total");
        this.retryCounter = counter(meterRegistry, "skishop_outbox_relay_retry_total");
        this.failedCounter = counter(meterRegistry, "skishop_outbox_relay_failed_total");
        this.publishTimer = meterRegistry == null ? null : Timer.builder("skishop_outbox_publish_duration_seconds")
            .description("Outbox event publish duration")
            .register(meterRegistry);
        registerGauges(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${skishop.event.outbox.relay.fixed-delay-ms:5000}")
    public void relayPendingEvents() {
        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxRow> rows = jdbcTemplate.query(SELECT_PENDING, (rs, rowNum) -> new OutboxRow(
                    (UUID) rs.getObject("id"),
                    rs.getString("event_id"),
                    rs.getString("event_type"),
                    rs.getString(FIELD_PRODUCER),
                    rs.getString("payload"),
                    rs.getString("correlation_id"),
                    rs.getInt("version"),
                    rs.getTimestamp("created_at"),
                    rs.getInt("retry_count")), properties.batchSize());

            for (OutboxRow row : rows) {
                try {
                    if (publish(row)) {
                        jdbcTemplate.update(MARK_PUBLISHED, row.id());
                        increment(successCounter);
                        log.info("Outbox event relayed: type={}, eventId={}", row.eventType(), row.eventId());
                    } else {
                        handleRelayFailure(row, "StreamBridge returned false");
                    }
                } catch (Exception exception) {
                    handleRelayFailure(row, exception.getMessage());
                }
            }
        });
    }

    private boolean publish(OutboxRow row) throws JsonProcessingException {
        long startedAt = System.nanoTime();
        try {
                String eventJson = java.util.Objects.requireNonNull(toEventJson(row));
                Message<String> message = MessageBuilder.withPayload(eventJson)
                    .setHeader("eventType", row.eventType())
                    .setHeader("eventId", row.eventId())
                    .setHeader("correlationId", row.correlationId())
                    .setHeader(FIELD_PRODUCER, row.producer())
                    .setHeader("eventTimestamp", row.createdAt().toInstant().toString())
                    .build();
            return streamBridge.send(bindingName, message);
        } finally {
            if (publishTimer != null) {
                publishTimer.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
            }
        }
    }

    private void handleRelayFailure(OutboxRow row, String errorMessage) {
        String sanitizedError = sanitizeError(errorMessage);
        int nextRetryCount = row.retryCount() + 1;
        if (nextRetryCount >= properties.maxRetries()) {
            jdbcTemplate.update(MARK_FAILED, sanitizedError, row.id());
            increment(failedCounter);
            if (log.isErrorEnabled()) {
                log.error("Outbox event relay reached max retries: type={}, eventId={}, retryCount={}, reason={}",
                        row.eventType(), row.eventId(), nextRetryCount, sanitizedError);
            }
            return;
        }

        Instant nextRetryAt = Instant.now().plus(backoffDelay(row.retryCount()));
        jdbcTemplate.update(MARK_RETRY, Timestamp.from(nextRetryAt), sanitizedError, row.id());
        increment(retryCounter);
        if (log.isWarnEnabled()) {
            log.warn("Outbox event relay failed and will be retried: type={}, eventId={}, retryCount={}, nextRetryAt={}, reason={}",
                    row.eventType(), row.eventId(), nextRetryCount, nextRetryAt, sanitizedError);
        }
    }

    private Duration backoffDelay(int retryCount) {
        long multiplier = 1L << Math.min(retryCount, 30);
        long delayMs = saturatedMultiply(properties.baseDelay().toMillis(), multiplier);
        return Duration.ofMillis(Math.min(delayMs, properties.maxDelay().toMillis()));
    }

    private long saturatedMultiply(long left, long right) {
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private String sanitizeError(String errorMessage) {
        String value = errorMessage == null || errorMessage.isBlank() ? "unknown relay error" : errorMessage;
        String singleLine = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return singleLine.length() <= MAX_ERROR_LENGTH ? singleLine : singleLine.substring(0, MAX_ERROR_LENGTH);
    }

    private Counter counter(MeterRegistry meterRegistry, String name) {
        return meterRegistry == null ? null : Counter.builder(name).register(meterRegistry);
    }

    private void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    private void registerGauges(MeterRegistry meterRegistry) {
        if (meterRegistry == null) {
            return;
        }
        Gauge.builder("skishop_outbox_pending_count", this, relay -> relay.countByStatus("PENDING"))
                .description("Current pending outbox event count")
                .register(meterRegistry);
        Gauge.builder("skishop_outbox_failed_count", this, relay -> relay.countByStatus("FAILED"))
                .description("Current failed outbox event count")
                .register(meterRegistry);
        Gauge.builder("skishop_outbox_oldest_pending_age_seconds", this, OutboxEventRelay::oldestPendingAgeSeconds)
                .description("Age in seconds of the oldest pending outbox event")
                .register(meterRegistry);
    }

    private double countByStatus(String status) {
        try {
            Integer count = jdbcTemplate.queryForObject(COUNT_BY_STATUS, Integer.class, status);
            return count == null ? 0 : count;
        } catch (DataAccessException exception) {
            log.debug("Failed to read outbox {} count: {}", status, exception.getMessage());
            return 0;
        }
    }

    private double oldestPendingAgeSeconds() {
        try {
            Double seconds = jdbcTemplate.queryForObject(OLDEST_PENDING_AGE_SECONDS, Double.class);
            return seconds == null ? 0 : seconds;
        } catch (DataAccessException exception) {
            log.debug("Failed to read oldest pending outbox age: {}", exception.getMessage());
            return 0;
        }
    }

    private String toEventJson(OutboxRow row) throws JsonProcessingException {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("eventId", row.eventId());
        event.put("eventType", row.eventType());
        event.put("timestamp", row.createdAt().toInstant().toString());
        event.put(FIELD_PRODUCER, row.producer());
        event.set("payload", objectMapper.readTree(row.payload()));
        event.put("correlationId", row.correlationId());
        event.put("version", row.version());
        return objectMapper.writeValueAsString(event);
    }

    record OutboxRow(
            UUID id,
            String eventId,
            String eventType,
            String producer,
            String payload,
            String correlationId,
            int version,
            Timestamp createdAt,
            int retryCount
    ) {}
}
