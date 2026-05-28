package com.example.skishop.usermanagement.client;

import com.example.skishop.usermanagement.dto.CouponSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CouponSummaryClient {

    private final RestClient restClient;

    @Autowired
    public CouponSummaryClient(@Value("${services.coupon.base-url:http://localhost:8086}") String baseUrl,
                               @Value("${internal.api-key}") String internalApiKey) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build());
    }

    CouponSummaryClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<List<CouponSummary>> getAvailableCoupons(UUID userId) {
        return Optional.ofNullable(restClient.get()
                .uri("/api/v1/internal/coupons/users/{userId}/summary", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<CouponSummary>>() {}));
    }
}
