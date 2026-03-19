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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("正常系: StreamBridge 経由でメッセージが送信される")
    @SuppressWarnings("unchecked")
    void should_sendMessage_when_publishCalled() {
        // Arrange
        DomainEvent<String> event = DomainEvent.create("user.created", "auth-service", "payload-data");
        when(streamBridge.send(eq(BINDING_NAME), any(Message.class))).thenReturn(true);

        // Act
        publisher.publish(event);

        // Assert
        ArgumentCaptor<Message<String>> captor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge).send(eq(BINDING_NAME), captor.capture());

        Message<String> sent = captor.getValue();
        assertThat(sent.getHeaders().get("eventType")).isEqualTo("user.created");
        assertThat(sent.getHeaders().get("eventId")).isEqualTo(event.eventId());
        assertThat(sent.getHeaders().get("correlationId")).isEqualTo(event.correlationId());
        assertThat(sent.getHeaders().get("producer")).isEqualTo("auth-service");
        assertThat(sent.getHeaders().get("eventTimestamp", String.class)).isNotBlank();
        assertThat(sent.getPayload()).contains("user.created");
    }

    @Test
    @DisplayName("異常系: StreamBridge が false を返す場合に EventPublishException がスローされる")
    @SuppressWarnings("unchecked")
    void should_throwEventPublishException_when_sendReturnsFalse() {
        // Arrange
        DomainEvent<String> event = DomainEvent.create("order.placed", "sales-service", "order-123");
        when(streamBridge.send(eq(BINDING_NAME), any(Message.class))).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("Failed to send event")
                .hasMessageContaining(event.eventId());
    }

    @Test
    @DisplayName("異常系: JSON シリアライズ失敗時に EventPublishException がスローされる")
    void should_throwEventPublishException_when_serializationFails() throws Exception {
        // Arrange
        ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialize error") {});
        var failPublisher = new SpringCloudStreamEventPublisher(streamBridge, failingMapper, BINDING_NAME);
        DomainEvent<String> event = DomainEvent.create("test.event", "test-service", "data");

        // Act & Assert
        assertThatThrownBy(() -> failPublisher.publish(event))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("Failed to serialize event")
                .hasMessageContaining(event.eventId());
    }

    @Test
    @DisplayName("複雑なペイロード (Record) でも正常にシリアライズして送信される")
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
        verify(streamBridge).send(eq(BINDING_NAME), captor.capture());
        assertThat(captor.getValue().getPayload()).contains("ORD-001").contains("5000");
    }
}
