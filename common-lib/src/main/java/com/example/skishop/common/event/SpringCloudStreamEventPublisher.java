package com.example.skishop.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Spring Cloud Stream (Kafka) によるイベント発行実装。
 * Phase 2 で各サービスに導入する。
 */
public class SpringCloudStreamEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringCloudStreamEventPublisher.class);

    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;
    private final String defaultBindingName;

    public SpringCloudStreamEventPublisher(StreamBridge streamBridge,
                                            ObjectMapper objectMapper,
                                            String defaultBindingName) {
        this.streamBridge = streamBridge;
        this.objectMapper = objectMapper;
        this.defaultBindingName = defaultBindingName;
    }

    @Override
    public <T> void publish(DomainEvent<T> event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            Message<String> message = MessageBuilder.withPayload(json)
                    .setHeader("eventType", event.eventType())
                    .setHeader("eventId", event.eventId())
                    .setHeader("correlationId", event.correlationId())
                    .setHeader("producer", event.producer())
                    .setHeader("eventTimestamp", event.timestamp().toString())
                    .build();

            boolean sent = streamBridge.send(defaultBindingName, message);
            if (sent) {
                log.info("Event sent to Kafka: type={}, eventId={}, correlationId={}",
                        event.eventType(), event.eventId(), event.correlationId());
            } else {
                throw new EventPublishException(
                        "Failed to send event: eventId=" + event.eventId());
            }
        } catch (JsonProcessingException e) {
            throw new EventPublishException(
                    "Failed to serialize event: eventId=" + event.eventId(), e);
        }
    }
}
