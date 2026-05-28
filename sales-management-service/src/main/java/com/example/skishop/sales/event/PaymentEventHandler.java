package com.example.skishop.sales.event;

import com.example.skishop.sales.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventHandler.class);
    private static final String CONSUMER_NAME = "sales-payment-events";
    private static final String PAYMENT_CAPTURED = "PaymentCaptured";
    private static final String PAYMENT_FAILED = "PaymentFailed";

    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    private final ProcessedEventService processedEventService;

    public PaymentEventHandler(ObjectMapper objectMapper,
                               OrderService orderService,
                               ProcessedEventService processedEventService) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
        this.processedEventService = processedEventService;
    }

    @Transactional
    public void handle(Message<String> message) {
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String eventType = resolveEventType(message, root);
            if (!PAYMENT_CAPTURED.equals(eventType) && !PAYMENT_FAILED.equals(eventType)) {
                return;
            }

            String eventId = resolveEventId(message, root);
            if (eventId == null || eventId.isBlank()) {
                throw new IllegalArgumentException("Payment event id is required for idempotent consumption");
            }
            if (!processedEventService.tryStart(eventId, eventType, CONSUMER_NAME)) {
                if (log.isInfoEnabled()) {
                    log.info("Duplicate payment event skipped: eventType={}, eventId={}", eventType, eventId);
                }
                return;
            }

            JsonNode payload = root.path("payload");
            UUID orderId = uuid(payload, "orderId");
            UUID paymentId = uuid(payload, "paymentId");
            if (orderId == null || paymentId == null) {
                throw new IllegalArgumentException("Payment event orderId/paymentId is required: eventType=" + eventType);
            }

            if (PAYMENT_CAPTURED.equals(eventType)) {
                orderService.markPaymentCaptured(orderId, paymentId);
            } else {
                orderService.markPaymentFailed(orderId, paymentId);
            }
            processedEventService.markProcessed(eventId);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to consume payment event", e);
        }
    }

    private String resolveEventType(Message<String> message, JsonNode root) {
        String eventType = header(message, "eventType");
        if (eventType == null || eventType.isBlank()) {
            eventType = root.path("eventType").asText();
        }
        return eventType;
    }

    private String resolveEventId(Message<String> message, JsonNode root) {
        String eventId = header(message, "eventId");
        if (eventId == null || eventId.isBlank()) {
            eventId = root.path("eventId").asText();
        }
        return eventId;
    }

    private String header(Message<String> message, String name) {
        Object value = message.getHeaders().get(name);
        return value == null ? null : value.toString();
    }

    private UUID uuid(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return UUID.fromString(value.asText());
    }
}