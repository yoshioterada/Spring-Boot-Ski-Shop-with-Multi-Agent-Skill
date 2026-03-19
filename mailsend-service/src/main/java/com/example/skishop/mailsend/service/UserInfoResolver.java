package com.example.skishop.mailsend.service;

import com.example.skishop.common.exception.ExternalServiceException;
import com.example.skishop.mailsend.config.ServicesProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
public class UserInfoResolver {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;

    public UserInfoResolver(ServicesProperties servicesProperties) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl(servicesProperties.userManagement().url())
                .requestFactory(requestFactory)
                .build();
    }

    public UserInfo resolve(UUID customerId) {
        try {
            return restClient.get()
                    .uri("/api/v1/users/{id}", customerId)
                    .retrieve()
                    .body(UserInfo.class);
        } catch (Exception e) {
            throw new ExternalServiceException("user-management-service",
                    "Failed to resolve user info: customerId=" + customerId, e);
        }
    }

    public record UserInfo(UUID id, String email, String firstName, String lastName) {
    }
}
