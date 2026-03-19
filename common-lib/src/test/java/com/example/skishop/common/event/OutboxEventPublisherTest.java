package com.example.skishop.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventPublisher - Outbox テーブル書込みイベント発行")
class OutboxEventPublisherTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ObjectMapper objectMapper;
    private OutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        publisher = new OutboxEventPublisher(jdbcTemplate, objectMapper);
    }

    @Test
    @DisplayName("正常系: event_outbox テーブルに INSERT される")
    void should_insertIntoOutbox_when_publishCalled() {
        // Arrange
        record TestPayload(String orderId, int amount) {}
        DomainEvent<TestPayload> event = DomainEvent.create(
                "order.created", "sales-service", new TestPayload("ORD-001", 5000));

        // Act
        publisher.publish(event);

        // Assert
        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> producerCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> correlationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> versionCaptor = ArgumentCaptor.forClass(Integer.class);

        verify(jdbcTemplate).update(anyString(),
                eventIdCaptor.capture(),
                eventTypeCaptor.capture(),
                producerCaptor.capture(),
                payloadCaptor.capture(),
                correlationCaptor.capture(),
                versionCaptor.capture());

        assertThat(eventIdCaptor.getValue()).isEqualTo(event.eventId());
        assertThat(eventTypeCaptor.getValue()).isEqualTo("order.created");
        assertThat(producerCaptor.getValue()).isEqualTo("sales-service");
        assertThat(payloadCaptor.getValue()).contains("ORD-001").contains("5000");
        assertThat(correlationCaptor.getValue()).isEqualTo(event.correlationId());
        assertThat(versionCaptor.getValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("異常系: JSON シリアライズ失敗時に EventPublishException がスローされる")
    void should_throwEventPublishException_when_serializationFails() throws Exception {
        // Arrange
        ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialize error") {});
        var failPublisher = new OutboxEventPublisher(jdbcTemplate, failingMapper);
        DomainEvent<String> event = DomainEvent.create("test.event", "test-service", "data");

        // Act & Assert
        assertThatThrownBy(() -> failPublisher.publish(event))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("Failed to serialize event payload")
                .hasMessageContaining(event.eventId());
    }

    @Test
    @DisplayName("correlationId が正しく outbox に保存される")
    void should_persistCorrelationId_when_eventHasCorrelationId() {
        // Arrange
        DomainEvent<String> event = DomainEvent.create(
                "user.updated", "user-service", "payload");

        // Act
        publisher.publish(event);

        // Assert
        ArgumentCaptor<String> correlationCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(anyString(),
                any(), any(), any(), any(),
                correlationCaptor.capture(),
                eq(1));
        assertThat(correlationCaptor.getValue()).isNotBlank();
    }
}
