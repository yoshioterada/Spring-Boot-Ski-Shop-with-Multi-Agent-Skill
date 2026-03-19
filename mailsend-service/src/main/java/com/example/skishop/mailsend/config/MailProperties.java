package com.example.skishop.mailsend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mail")
public record MailProperties(
        Retry retry,
        String baseUrl
) {
    public record Retry(
            int maxAttempts,
            long initialIntervalMs,
            double multiplier
    ) {
    }
}
