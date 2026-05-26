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
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpringCloudStreamEventPublisher - Kafka イベント発行")
class SpringCloudStreamEventPublisherTest {

    @Mock
    private StreamBridge streamBridge;

    private ObjectMapper objectMapper;
    private SpringCloudStreamEventPublisher publisher;

    private static final String BINDING_NAME = "domainEvents-out-0";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        publisher = new SpringCloudStreamEventPublisher(streamBridge, objectMapper, BINDING_NAME);
    }

    @Test
    @DisplayName("正常系: StreamBridge 経由でメッセージが非同期送信される")
    @SuppressWarnings("unchecked")
    void should_sendMessage_when_publishCalled() {
        // Arrange
        DomainEvent<String> event = DomainEvent.create("user.created", "auth-service", "payload-data");
        when(streamBridge.send(eq(BINDING_NAME), any(Message.class))).thenReturn(true);

        // Act
        publisher.publish(event);

        // Assert (非同期のため timeout を使用)
        ArgumentCaptor<Message<String>> captor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, timeout(3000)).send(eq(BINDING_NAME), captor.capture());

        Message<String> sent = captor.getValue();
        assertThat(sent.getHeaders().get("eventType")).isEqualTo("user.created");
        assertThat(sent.getHeaders().get("eventId")).isEqualTo(event.eventId());
        assertThat(sent.getHeaders().get("correlationId")).isEqualTo(event.correlationId());
        assertThat(sent.getHeaders().get("producer")).isEqualTo("auth-service");
        assertThat(sent.getHeaders().get("eventTimestamp", String.class)).isNotBlank();
        assertThat(sent.getPayload()).contains("user.created");
    }

    @Test
    @DisplayName("異常系: StreamBridge が false を返してもcaller には例外が伝播しない（best-effort）")
    @SuppressWarnings("unchecked")
    void should_notThrow_when_sendReturnsFalse() {
        // Arrange
        DomainEvent<String> event = DomainEvent.create("order.placed", "sales-service", "order-123");
        when(streamBridge.send(eq(BINDING_NAME), any(Message.class))).thenReturn(false);

        // Act & Assert - 非同期のため例外は伝播しない
        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
        verify(streamBridge, timeout(3000)).send(eq(BINDING_NAME), any(Message.class));
    }

    @Test
    @DisplayName("異常系: JSON シリアライズ失敗時も caller には例外が伝播しない（best-effort）")
    void should_notThrow_when_serializationFails() throws Exception {
        // Arrange
        ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialize error") {});
        var failPublisher = new SpringCloudStreamEventPublisher(streamBridge, failingMapper, BINDING_NAME);
        DomainEvent<String> event = DomainEvent.create("test.event", "test-service", "data");

        // Act & Assert - シリアライズ失敗は同期でキャッチされるが例外は投げない
        assertThatCode(() -> failPublisher.publish(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("複雑なペイロード (Record) でも正常にシリアライズして非同期送信される")
    @SuppressWarnings("unchecked")
    void should_serializeComplexPayload_when_recordPayloadProvided() {
        // Arrange
        record OrderPayload(String orderId, int amount) {}
        DomainEvent<OrderPayload> event = DomainEvent.create(
                "order.created", "sales-service", new OrderPayload("ORD-001", 5000));
        when(streamBridge.send(eq(BINDING_NAME), any(Message.class))).thenReturn(true);

        // Act
        publisher.publish(event);

        // Assert
        ArgumentCaptor<Message<String>> captor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, timeout(3000)).send(eq(BINDING_NAME), captor.capture());
        assertThat(captor.getValue().getPayload()).contains("ORD-001").contains("5000");
    }
}
