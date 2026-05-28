package com.example.skishop.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.messaging.Message;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventRelay - retry と FAILED 監視")
class OutboxEventRelayTest {

    private static final String BINDING_NAME = "domainEvents-out-0";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private StreamBridge streamBridge;

    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private OutboxEventRelay relay;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        meterRegistry = new SimpleMeterRegistry();
        relay = new OutboxEventRelay(
                jdbcTemplate,
                transactionTemplate,
                streamBridge,
                objectMapper,
                meterRegistry,
                BINDING_NAME,
                OutboxRelayProperties.ofMillis(10, 3, 1_000, 8_000));
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(org.mockito.Mockito.mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(org.mockito.ArgumentMatchers.<Consumer<TransactionStatus>>any());
    }

    @Test
    @DisplayName("send 成功で PUBLISHED に更新され success metric が増える")
    void should_markPublishedAndIncrementSuccessMetric_when_sendSucceeds() throws Exception {
        UUID outboxId = UUID.randomUUID();
        stubPendingRows(row(outboxId, "evt-success", 0));
        when(streamBridge.send(eq(BINDING_NAME), any(Message.class))).thenReturn(true);

        relay.relayPendingEvents();

        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("status = 'PUBLISHED'"), eq(outboxId));
        assertThat(meterRegistry.get("skishop_outbox_relay_success_total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("skishop_outbox_publish_duration_seconds").timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("send false で retry_count と next_retry_at と last_error を更新する")
    void should_scheduleRetry_when_sendReturnsFalse() throws Exception {
        UUID outboxId = UUID.randomUUID();
        stubPendingRows(row(outboxId, "evt-retry", 0));
        when(streamBridge.send(eq(BINDING_NAME), any(Message.class))).thenReturn(false);

        relay.relayPendingEvents();

        ArgumentCaptor<Timestamp> nextRetryAtCaptor = ArgumentCaptor.forClass(Timestamp.class);
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("retry_count = retry_count + 1"),
                nextRetryAtCaptor.capture(),
                eq("StreamBridge returned false"),
                eq(outboxId));
        assertThat(nextRetryAtCaptor.getValue().toInstant()).isAfter(Instant.now().minusSeconds(1));
        assertThat(meterRegistry.get("skishop_outbox_relay_retry_total").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("例外発生時に last_error は単一行に sanitize されて保存される")
    void should_storeSanitizedError_when_sendThrowsException() throws Exception {
        UUID outboxId = UUID.randomUUID();
        stubPendingRows(row(outboxId, "evt-exception", 1));
        when(streamBridge.send(eq(BINDING_NAME), any(Message.class)))
                .thenThrow(new IllegalStateException("broker\nconnection\tfailed"));

        relay.relayPendingEvents();

        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("retry_count = retry_count + 1"),
                any(Timestamp.class),
                eq("broker connection failed"),
                eq(outboxId));
        assertThat(meterRegistry.get("skishop_outbox_relay_retry_total").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("retry 上限到達時に FAILED と failed_at を記録する")
    void should_markFailed_when_maxRetryReached() throws Exception {
        UUID outboxId = UUID.randomUUID();
        stubPendingRows(row(outboxId, "evt-failed", 2));
        when(streamBridge.send(eq(BINDING_NAME), any(Message.class))).thenReturn(false);

        relay.relayPendingEvents();

        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("status = 'FAILED'"),
                eq("StreamBridge returned false"),
                eq(outboxId));
        assertThat(meterRegistry.get("skishop_outbox_relay_failed_total").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("未来の next_retry_at は SELECT 条件で除外される")
    void should_selectOnlyDuePendingRows() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<OutboxEventRelay.OutboxRow>>any(), eq(10)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    assertThat(sql).contains("next_retry_at IS NULL OR next_retry_at <= NOW()");
                    return List.of();
                });

        relay.relayPendingEvents();

        verify(streamBridge, never()).send(anyString(), any(Message.class));
    }

    @Test
    @DisplayName("pending / failed / oldest pending metrics を取得できる")
    void should_exposeOutboxGaugeMetrics() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("PENDING"))).thenReturn(4);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("FAILED"))).thenReturn(2);
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("MIN(created_at)"), eq(Double.class)))
                .thenReturn(901.0);

        assertThat(meterRegistry.get("skishop_outbox_pending_count").gauge().value()).isEqualTo(4.0);
        assertThat(meterRegistry.get("skishop_outbox_failed_count").gauge().value()).isEqualTo(2.0);
        assertThat(meterRegistry.get("skishop_outbox_oldest_pending_age_seconds").gauge().value()).isEqualTo(901.0);
    }

    private void stubPendingRows(OutboxRowFixture fixture) throws Exception {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<OutboxEventRelay.OutboxRow>>any(), eq(10)))
                .thenAnswer(invocation -> {
                    RowMapper<OutboxEventRelay.OutboxRow> mapper = invocation.getArgument(1);
                    ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
                    when(resultSet.getObject("id")).thenReturn(fixture.id());
                    when(resultSet.getString("event_id")).thenReturn(fixture.eventId());
                    when(resultSet.getString("event_type")).thenReturn("OrderCreated");
                    when(resultSet.getString("producer")).thenReturn("sales-service");
                    when(resultSet.getString("payload")).thenReturn("{\"orderId\":\"ORD-001\"}");
                    when(resultSet.getString("correlation_id")).thenReturn("corr-001");
                    when(resultSet.getInt("version")).thenReturn(1);
                    when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-05-28T00:00:00Z")));
                    when(resultSet.getInt("retry_count")).thenReturn(fixture.retryCount());
                    return List.of(mapper.mapRow(resultSet, 0));
                });
    }

    private OutboxRowFixture row(UUID id, String eventId, int retryCount) {
        return new OutboxRowFixture(id, eventId, retryCount);
    }

    private record OutboxRowFixture(UUID id, String eventId, int retryCount) {}
}