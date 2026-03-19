package com.example.skishop.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Value("${app.services.auth-url:http://localhost:8080}")
    private String authServiceUrl;

    @Value("${app.services.user-url:http://localhost:8081}")
    private String userServiceUrl;

    @Value("${app.services.inventory-url:http://localhost:8082}")
    private String inventoryServiceUrl;

    @Value("${app.services.sales-url:http://localhost:8083}")
    private String salesServiceUrl;

    @Value("${app.services.payment-url:http://localhost:8084}")
    private String paymentServiceUrl;

    @Value("${app.services.point-url:http://localhost:8085}")
    private String pointServiceUrl;

    @Value("${app.services.coupon-url:http://localhost:8088}")
    private String couponServiceUrl;

    @Value("${app.services.ai-url:http://localhost:8087}")
    private String aiServiceUrl;

    @Value("${app.services.mail-url:http://localhost:8089}")
    private String mailServiceUrl;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r
                        .path("/api/v1/auth/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb.setName("authCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/auth"))
                                .retry(retryConfig -> retryConfig.setRetries(3)))
                        .uri(authServiceUrl))
                .route("user-management-service", r -> r
                        .path("/api/v1/users/**", "/api/v1/admin/users/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb.setName("userCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/user"))
                                .retry(retryConfig -> retryConfig.setRetries(3)))
                        .uri(userServiceUrl))
                .route("inventory-management-service", r -> r
                        .path("/api/v1/products/**", "/api/v1/inventory/**", "/api/v1/prices/**",
                                "/api/v1/categories/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb.setName("inventoryCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/inventory"))
                                .retry(retryConfig -> retryConfig.setRetries(3)))
                        .uri(inventoryServiceUrl))
                .route("sales-management-service", r -> r
                        .path("/api/v1/orders/**", "/api/v1/admin/orders/**", "/api/v1/shipments/**", "/api/v1/returns/**",
                                "/api/v1/reports/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb.setName("salesCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/sales"))
                                .retry(retryConfig -> retryConfig.setRetries(3)))
                        .uri(salesServiceUrl))
                .route("payment-cart-service", r -> r
                        .path("/api/v1/cart/**", "/api/v1/payments/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb.setName("paymentCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/payment"))
                                .retry(retryConfig -> retryConfig.setRetries(3)))
                        .uri(paymentServiceUrl))
                .route("point-service", r -> r
                        .path("/api/v1/points/**", "/api/v1/tiers/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb.setName("pointCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/point"))
                                .retry(retryConfig -> retryConfig.setRetries(3)))
                        .uri(pointServiceUrl))
                .route("coupon-service", r -> r
                        .path("/api/v1/coupons/**", "/api/v1/campaigns/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb.setName("couponCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/coupon"))
                                .retry(retryConfig -> retryConfig.setRetries(3)))
                        .uri(couponServiceUrl))
                .route("ai-support-chat", r -> r
                        .path("/api/v1/chat/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb.setName("aiCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/ai")))
                        .uri(aiServiceUrl))
                .route("ai-support-recommendations", r -> r
                        .path("/api/v1/recommendations/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb.setName("aiCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/ai")))
                        .uri(aiServiceUrl))
                .route("ai-support-search", r -> r
                        .path("/api/v1/search/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb.setName("aiCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/ai")))
                        .uri(aiServiceUrl))
                .route("ai-support-analytics", r -> r
                        .path("/api/v1/analytics/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb.setName("aiCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/ai")))
                        .uri(aiServiceUrl))
                .route("ai-support-models", r -> r
                        .path("/api/v1/models/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb.setName("aiCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/ai")))
                        .uri(aiServiceUrl))
                .route("mailsend-service", r -> r
                        .path("/api/v1/mail/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb.setName("mailCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/mail"))
                                .retry(retryConfig -> retryConfig.setRetries(3)))
                        .uri(mailServiceUrl))
                .build();
    }
}
