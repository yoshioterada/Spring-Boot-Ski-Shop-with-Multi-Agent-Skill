package com.example.skishop.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
public class SalesRecommendationClient {

    private static final Logger log = LoggerFactory.getLogger(SalesRecommendationClient.class);
    private static final int DEFAULT_TREND_DAYS = 14;

    private final WebClient salesWebClient;

    public SalesRecommendationClient(@Qualifier("salesWebClient") WebClient salesWebClient) {
        this.salesWebClient = salesWebClient;
    }

    public List<String> fetchTopProductIds(int limit) {
        int safeLimit = Math.max(1, limit);
        try {
            SalesAnalyticsSummary summary = salesWebClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/admin/orders/analytics/summary")
                            .queryParam("days", DEFAULT_TREND_DAYS)
                            .queryParam("topProductLimit", safeLimit)
                            .build())
                    .retrieve()
                    .bodyToMono(SalesAnalyticsSummary.class)
                    .block();
            if (summary == null || summary.topProducts() == null) {
                return List.of();
            }
            return summary.topProducts().stream()
                    .map(TopProduct::productId)
                    .filter(productId -> productId != null && !productId.isBlank())
                    .distinct()
                    .limit(safeLimit)
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Failed to fetch sales top products: {}", ex.getMessage());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SalesAnalyticsSummary(List<TopProduct> topProducts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopProduct(String productId, String productName) {}
}