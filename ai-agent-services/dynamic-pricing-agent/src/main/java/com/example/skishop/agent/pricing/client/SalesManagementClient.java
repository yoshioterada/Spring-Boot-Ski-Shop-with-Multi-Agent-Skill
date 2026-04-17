package com.example.skishop.agent.pricing.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * sales-management-service から販売実績を取得する。
 */
public class SalesManagementClient {

    private static final Logger log = LoggerFactory.getLogger(SalesManagementClient.class);
    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public SalesManagementClient(
            @Value("${services.sales.base-url:http://localhost:8085}") String baseUrl,
            @Value("${services.internal-api-key:}") String apiKey) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", apiKey == null ? "" : apiKey)
                .defaultHeader("X-Caller-Service", "dynamic-pricing-agent")
                .build());
    }

    SalesManagementClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public int getSalesCount(String productId, int withinDays) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get()
                    .uri("/api/v1/internal/sales/{productId}/count?days={d}", productId, withinDays)
                    .retrieve()
                    .body(Map.class);
            if (body == null) return 0;
            Object count = body.get("count");
            if (count instanceof Number n) return n.intValue();
            return 0;
        } catch (RestClientException e) {
            log.warn("販売実績取得失敗 productId={}: {}", productId, e.getMessage());
            return 0;
        }
    }
}
