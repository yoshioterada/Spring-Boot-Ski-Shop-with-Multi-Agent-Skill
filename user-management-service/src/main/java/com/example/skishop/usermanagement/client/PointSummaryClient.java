package com.example.skishop.usermanagement.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

@Component
public class PointSummaryClient {

    private final RestClient restClient;

    @Autowired
    public PointSummaryClient(@Value("${services.point.base-url:http://localhost:8085}") String baseUrl,
                              @Value("${internal.api-key}") String internalApiKey) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build());
    }

    PointSummaryClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<PointSummary> getSummary(UUID userId) {
        return Optional.ofNullable(restClient.get()
                .uri("/api/v1/internal/points/{userId}/summary", userId)
                .retrieve()
                .body(PointSummary.class));
    }

    public record PointSummary(
            UUID userId,
            String tierLevel,
            String tierName,
            long pointBalance,
            long totalEarned,
            long totalRedeemed
    ) {}
}
