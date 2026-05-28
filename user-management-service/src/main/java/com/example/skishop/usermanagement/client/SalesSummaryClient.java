package com.example.skishop.usermanagement.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SalesSummaryClient {

    private final RestClient restClient;

    @Autowired
    public SalesSummaryClient(@Value("${services.sales.base-url:http://localhost:8083}") String baseUrl,
                              @Value("${internal.api-key}") String internalApiKey) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build());
    }

    SalesSummaryClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<SalesSummary> getSummary(UUID userId) {
        return Optional.ofNullable(restClient.get()
                .uri("/api/v1/internal/sales/users/{userId}/summary", userId)
                .retrieve()
                .body(SalesSummary.class));
    }

    public record SalesSummary(
            UUID userId,
            long orderCount,
            BigDecimal totalPurchasedAmount,
            List<String> purchasedCategories
    ) {}
}
