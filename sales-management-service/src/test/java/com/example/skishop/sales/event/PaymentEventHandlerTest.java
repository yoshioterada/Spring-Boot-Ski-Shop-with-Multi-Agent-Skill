package com.example.skishop.sales.event;

import com.example.skishop.sales.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentEventHandlerTest {

    @Mock
    private OrderService orderService;
    @Mock
    private ProcessedEventService processedEventService;

    private PaymentEventHandler paymentEventHandler;

    @BeforeEach
    void setUp() {
        paymentEventHandler = new PaymentEventHandler(new ObjectMapper(), orderService, processedEventService);
    }

    @Test
    @DisplayName("PaymentCapturedイベントを初回処理すると注文を支払い済みにする")
    void should_markPaymentCaptured_when_eventIsFirstTime() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String eventId = "evt-captured-1";
        when(processedEventService.tryStart(eventId, "PaymentCaptured", "sales-payment-events")).thenReturn(true);
        Message<String> message = message(payload(eventId, "PaymentCaptured", orderId, paymentId));

        // Act
        paymentEventHandler.handle(message);

        // Assert
        verify(orderService).markPaymentCaptured(orderId, paymentId);
        verify(processedEventService).markProcessed(eventId);
    }

    @Test
    @DisplayName("PaymentFailedイベントを初回処理すると注文を支払い失敗にする")
    void should_markPaymentFailed_when_eventIsFirstTime() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String eventId = "evt-failed-1";
        when(processedEventService.tryStart(eventId, "PaymentFailed", "sales-payment-events")).thenReturn(true);
        Message<String> message = message(payload(eventId, "PaymentFailed", orderId, paymentId));

        // Act
        paymentEventHandler.handle(message);

        // Assert
        verify(orderService).markPaymentFailed(orderId, paymentId);
        verify(processedEventService).markProcessed(eventId);
    }

    @Test
    @DisplayName("重複イベントは注文を更新しない")
    void should_skipOrderUpdate_when_eventAlreadyProcessed() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String eventId = "evt-duplicate";
        when(processedEventService.tryStart(eventId, "PaymentCaptured", "sales-payment-events")).thenReturn(false);
        Message<String> message = message(payload(eventId, "PaymentCaptured", orderId, paymentId));

        // Act
        paymentEventHandler.handle(message);

        // Assert
        verify(orderService, never()).markPaymentCaptured(orderId, paymentId);
        verify(processedEventService, never()).markProcessed(eventId);
    }

    @Test
    @DisplayName("eventIdがない支払いイベントは拒否する")
    void should_throwException_when_eventIdMissing() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String payload = """
                {"eventType":"PaymentCaptured","payload":{"orderId":"%s","paymentId":"%s"}}
                """.formatted(orderId, paymentId);
        Message<String> message = message(payload);

        // Act & Assert
        assertThatThrownBy(() -> paymentEventHandler.handle(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event id is required");

        verify(orderService, never()).markPaymentCaptured(orderId, paymentId);
    }

    @Test
    @DisplayName("対象外イベントは処理しない")
    void should_ignoreUnsupportedEventType() {
        // Arrange
        String payload = "{\"eventId\":\"evt-other\",\"eventType\":\"OrderCreated\",\"payload\":{}}";
        Message<String> message = message(payload);

        // Act
        paymentEventHandler.handle(message);

        // Assert
        verify(processedEventService, never()).tryStart("evt-other", "OrderCreated", "sales-payment-events");
    }

    private String payload(String eventId, String eventType, UUID orderId, UUID paymentId) {
        return """
                {"eventId":"%s","eventType":"%s","payload":{"orderId":"%s","paymentId":"%s"}}
                """.formatted(eventId, eventType, orderId, paymentId);
    }

    private Message<String> message(String payload) {
        return MessageBuilder.withPayload(Objects.requireNonNull(payload)).build();
    }
}