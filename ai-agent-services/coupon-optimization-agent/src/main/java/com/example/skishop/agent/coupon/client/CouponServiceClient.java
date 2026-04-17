package com.example.skishop.agent.coupon.client;

import com.example.skishop.agent.common.dto.CouponCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CouponServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CouponServiceClient.class);
    private static final ParameterizedTypeReference<List<CouponCandidate>> COUPON_LIST =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public CouponServiceClient(
            @Value("${services.coupon.base-url:http://localhost:8086}") String baseUrl,
            @Value("${services.internal-api-key:}") String apiKey) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", apiKey == null ? "" : apiKey)
                .defaultHeader("X-Caller-Service", "coupon-optimization-agent")
                .build());
    }

    CouponServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<CouponCandidate> getUserCoupons(String userId) {
        try {
            List<CouponCandidate> body = restClient.get()
                    .uri("/api/v1/internal/coupons/users/{userId}", userId)
                    .retrieve()
                    .body(COUPON_LIST);
            return body == null ? new ArrayList<>() : new ArrayList<>(body);
        } catch (RestClientException e) {
            log.warn("ユーザークーポン取得失敗 userId={}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    public Optional<CouponCandidate> findByCouponCode(String couponCode) {
        try {
            CouponCandidate body = restClient.get()
                    .uri("/api/v1/internal/coupons/by-code/{code}", couponCode)
                    .retrieve()
                    .body(CouponCandidate.class);
            return Optional.ofNullable(body);
        } catch (RestClientException e) {
            log.warn("クーポンコード検索失敗 code={}: {}", couponCode, e.getMessage());
            return Optional.empty();
        }
    }
}
