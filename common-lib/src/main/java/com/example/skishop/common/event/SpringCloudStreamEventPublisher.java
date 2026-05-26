package com.example.skishop.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Spring Cloud Stream (Kafka) によるイベント発行実装。
 * JSON シリアライズは呼び出しスレッドで行い、
 * Kafka 送信は仮想スレッドで非同期に実行する（best-effort）。
 */
public class SpringCloudStreamEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringCloudStreamEventPublisher.class);

    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;
    private final String defaultBindingName;
    private final Executor asyncExecutor;

    public SpringCloudStreamEventPublisher(StreamBridge streamBridge,
                                            ObjectMapper objectMapper,
                                            String defaultBindingName) {
        this.streamBridge = streamBridge;
        this.objectMapper = objectMapper;
        this.defaultBindingName = defaultBindingName;
        this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
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

            asyncExecutor.execute(() -> {
                try {
                    boolean sent = streamBridge.send(defaultBindingName, message);
                    if (sent) {
                        log.info("Event sent to Kafka: type={}, eventId={}, correlationId={}",
                                event.eventType(), event.eventId(), event.correlationId());
                    } else {
                        log.warn("Event not delivered (no subscribers on binding '{}'): type={}, eventId={}",
                                defaultBindingName, event.eventType(), event.eventId());
                    }
                } catch (Exception e) {
                    log.warn("Async event publishing failed (best effort): type={}, eventId={}, reason={}",
                            event.eventType(), event.eventId(), e.getMessage());
                }
            });
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize event: eventId={}, reason={}", event.eventId(), e.getMessage());
        }
    }
}
