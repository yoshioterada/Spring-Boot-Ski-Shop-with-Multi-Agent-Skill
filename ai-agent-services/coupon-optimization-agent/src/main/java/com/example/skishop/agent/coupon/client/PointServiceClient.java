package com.example.skishop.agent.coupon.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

public class PointServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PointServiceClient.class);
    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public PointServiceClient(
            @Value("${services.point.base-url:http://localhost:8087}") String baseUrl,
            @Value("${services.internal-api-key:}") String apiKey) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", apiKey == null ? "" : apiKey)
                .defaultHeader("X-Caller-Service", "coupon-optimization-agent")
                .build());
    }

    PointServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public int getPointBalance(String userId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get()
                    .uri("/api/v1/internal/points/{userId}/balance", userId)
                    .retrieve()
                    .body(Map.class);
            if (body == null) return 0;
            Object balance = body.get("balance");
            if (balance instanceof Number n) return n.intValue();
            return 0;
        } catch (RestClientException e) {
            log.warn("ポイント残高取得失敗 userId={}: {}", userId, e.getMessage());
            return 0;
        }
    }
}
