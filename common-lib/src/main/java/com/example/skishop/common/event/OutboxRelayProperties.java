package com.example.skishop.common.event;

import java.time.Duration;

public record OutboxRelayProperties(
        int batchSize,
        int maxRetries,
        Duration baseDelay,
        Duration maxDelay
) {

    public OutboxRelayProperties {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (maxRetries <= 0) {
            throw new IllegalArgumentException("maxRetries must be positive");
        }
        if (baseDelay.isNegative() || baseDelay.isZero()) {
            throw new IllegalArgumentException("baseDelay must be positive");
        }
        if (maxDelay.compareTo(baseDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be greater than or equal to baseDelay");
        }
    }

    public static OutboxRelayProperties ofMillis(int batchSize, int maxRetries, long baseDelayMs, long maxDelayMs) {
        return new OutboxRelayProperties(
                batchSize,
                maxRetries,
                Duration.ofMillis(baseDelayMs),
                Duration.ofMillis(maxDelayMs));
    }
}