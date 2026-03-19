package com.example.skishop.common.event;

import java.time.Instant;
import java.util.UUID;

public record DomainEvent<T>(
        String eventId,
        String eventType,
        Instant timestamp,
        String producer,
        T payload,
        String correlationId,
        int version
) {
    public static <T> DomainEvent<T> create(String eventType, String producer, T payload) {
        return new DomainEvent<>(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                producer,
                payload,
                UUID.randomUUID().toString(),
                1
        );
    }

    public static <T> DomainEvent<T> create(String eventType, String producer, T payload, String correlationId) {
        return new DomainEvent<>(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                producer,
                payload,
                correlationId,
                1
        );
    }
}
