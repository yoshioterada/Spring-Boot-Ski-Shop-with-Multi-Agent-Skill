package com.example.skishop.agent.intent.client;

import com.example.skishop.agent.common.dto.UserPurchaseHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * user-management-service / sales-management-service から購入履歴を取得するクライアント。
 * 失敗時はフォールバック値（新規顧客扱い）を返却する。
 */
public class UserProfileClient {

    private static final Logger log = LoggerFactory.getLogger(UserProfileClient.class);
    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public UserProfileClient(
            @Value("${services.user-management.base-url:http://localhost:8081}") String userManagementBaseUrl,
            @Value("${services.internal-api-key:}") String internalApiKey) {
        this(RestClient.builder()
                .baseUrl(userManagementBaseUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey == null ? "" : internalApiKey)
                .build());
    }

    // テスト用コンストラクタ
    UserProfileClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public UserPurchaseHistory getPurchaseHistory(String userId) {
        try {
            UserPurchaseHistory history = restClient.get()
                    .uri("/api/v1/internal/users/{userId}/purchase-history", userId)
                    .retrieve()
                    .body(UserPurchaseHistory.class);
            return history != null ? history : fallback(userId);
        } catch (RestClientException e) {
            log.warn("ユーザー履歴取得失敗 userId={}: {}", userId, e.getMessage());
            return fallback(userId);
        }
    }

    public static UserPurchaseHistory fallback(String userId) {
        return new UserPurchaseHistory(userId, List.of(), "BEGINNER", 0, "BRONZE");
    }
}
