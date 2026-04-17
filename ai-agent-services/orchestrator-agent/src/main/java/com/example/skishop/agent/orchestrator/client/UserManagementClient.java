package com.example.skishop.agent.orchestrator.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

public class UserManagementClient {

    private static final Logger log = LoggerFactory.getLogger(UserManagementClient.class);
    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public UserManagementClient(
            @Value("${services.user-management.base-url:http://localhost:8081}") String baseUrl) {
        this(RestClient.builder().baseUrl(baseUrl).build());
    }

    UserManagementClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public UserProfile getUserProfile(String userId, String jwtToken) {
        log.debug("Fetching user profile: userId={}", userId);
        try {
            UserProfile profile = restClient.get()
                    .uri("/api/v1/users/{id}/profile", userId)
                    .header("Authorization", "Bearer " + (jwtToken == null ? "" : jwtToken))
                    .retrieve()
                    .body(UserProfile.class);
            return profile == null ? fallback(userId) : profile;
        } catch (RestClientException e) {
            log.warn("ユーザープロフィール取得失敗 userId={}: {}", userId, e.getMessage());
            return fallback(userId);
        }
    }

    public static UserProfile fallback(String userId) {
        return new UserProfile(userId, userId, "BRONZE", List.of(), "BEGINNER", 0);
    }

    public record UserProfile(
            String userId,
            String displayName,
            String customerTier,
            List<String> purchasedCategories,
            String preferredSkillLevel,
            Integer pointBalance
    ) {}
}
