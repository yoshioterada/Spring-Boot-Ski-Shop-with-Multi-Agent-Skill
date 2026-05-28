package com.example.skishop.ai.service;

import com.example.skishop.ai.dto.*;
import com.example.skishop.ai.model.BehaviorMetrics;
import com.example.skishop.ai.model.DemandForecast;
import com.example.skishop.ai.model.UserProfile;
import com.example.skishop.ai.repository.BehaviorMetricsRepository;
import com.example.skishop.ai.repository.ChatSessionRepository;
import com.example.skishop.ai.repository.DemandForecastRepository;
import com.example.skishop.ai.repository.RecommendationRepository;
import com.example.skishop.ai.repository.UserProfileRepository;
import com.example.skishop.ai.model.ChatSession;
import com.example.skishop.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final UserProfileRepository userProfileRepository;
    private final BehaviorMetricsRepository behaviorMetricsRepository;
    private final DemandForecastRepository demandForecastRepository;
    private final WebClient salesWebClient;
    private final WebClient inventoryWebClient;
    private final WebClient userWebClient;
    private final RecommendationRepository recommendationRepository;
    private final ChatSessionRepository chatSessionRepository;

    public AnalyticsService(UserProfileRepository userProfileRepository,
                            BehaviorMetricsRepository behaviorMetricsRepository,
                            DemandForecastRepository demandForecastRepository) {
        this(userProfileRepository, behaviorMetricsRepository, demandForecastRepository,
                null, null, null, null, null);
    }

    @Autowired
    public AnalyticsService(UserProfileRepository userProfileRepository,
                            BehaviorMetricsRepository behaviorMetricsRepository,
                            DemandForecastRepository demandForecastRepository,
                            @Qualifier("salesWebClient") WebClient salesWebClient,
                            @Qualifier("inventoryWebClient") WebClient inventoryWebClient,
                            @Qualifier("userWebClient") WebClient userWebClient,
                            RecommendationRepository recommendationRepository,
                            ChatSessionRepository chatSessionRepository) {
        this.userProfileRepository = userProfileRepository;
        this.behaviorMetricsRepository = behaviorMetricsRepository;
        this.demandForecastRepository = demandForecastRepository;
        this.salesWebClient = salesWebClient;
        this.inventoryWebClient = inventoryWebClient;
        this.userWebClient = userWebClient;
        this.recommendationRepository = recommendationRepository;
        this.chatSessionRepository = chatSessionRepository;
    }

    public UserBehaviorResponse getUserBehavior(String userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("UserProfile", userId));

        List<BehaviorMetrics> metrics = behaviorMetricsRepository.findByUserId(userId);
        Map<String, Object> metricsMap = new HashMap<>();
        for (BehaviorMetrics m : metrics) {
            metricsMap.put(m.getMetricType(), m.getValue());
        }

        log.info("Retrieved behavior analytics for user {}", userId);
        return new UserBehaviorResponse(
                userId, metricsMap, profile.getPreferences(),
                profile.getLoyaltyTier(), profile.getTotalSpent(), Instant.now());
    }

    public SalesForecastResponse getSalesForecast(String productId, String period, int horizon) {
        List<DemandForecast> forecasts = demandForecastRepository.findByProductIdAndPeriod(productId, period);

        List<SalesForecastResponse.ForecastEntry> entries = forecasts.stream()
                .limit(horizon)
                .map(f -> new SalesForecastResponse.ForecastEntry(
                        f.getPeriodStart(), f.getPeriodEnd(),
                        f.getPredictedDemand(), f.getConfidenceInterval(),
                        f.getSeasonalFactor(), f.getTrendFactor()))
                .toList();

        double avgAccuracy = forecasts.stream()
                .filter(f -> f.getAccuracy() != null)
                .mapToDouble(DemandForecast::getAccuracy)
                .average().orElse(0.0);

        log.info("Retrieved sales forecast for product {}, period={}, horizon={}", productId, period, horizon);
        return new SalesForecastResponse(
                productId, period, horizon, entries,
                "ARIMA_with_seasonality", "v3.2", avgAccuracy, Instant.now());
    }

    public ProductPerformanceResponse getProductPerformance(String productId, String category) {
        Map<String, Object> metrics = new HashMap<>();
        List<String> missing = new ArrayList<>();

        Map<String, Object> salesSummary = fetchMap(salesWebClient, "sales-service",
                "/api/v1/admin/orders/analytics/summary", Map.of("days", 30, "topProductLimit", 50), missing);
        Map<String, Object> searchSummary = fetchMap(inventoryWebClient, "inventory-service",
                "/api/v1/products/analytics/search-summary", Map.of("days", 30, "limit", 20), missing);
        List<Map<String, Object>> inventory = fetchList(inventoryWebClient, "inventory-service",
                "/api/v1/inventory/all", Map.of(), missing);

        long purchases = extractPurchases(salesSummary, productId);
        long searches = toLong(searchSummary, "totalSearches");
        double conversionRate = searches > 0 ? (double) purchases / searches : 0.0;
        Map<String, Object> product = findProduct(inventory, productId, category);

        metrics.put("views", searches);
        metrics.put("purchases", purchases);
        metrics.put("conversionRate", conversionRate);
        metrics.put("averageRating", null);
        metrics.put("stockQuantity", toInt(product, "stockQuantity", 0));
        metrics.put("availableQuantity", toInt(product, "availableQuantity", 0));
        metrics.put("sales30d", purchases);
        metrics.put("dataSources", List.of("sales-service", "inventory-service"));

        log.info("Retrieved product performance for productId={}, category={}", productId, category);
        return new ProductPerformanceResponse(productId, category, metrics,
                availability(missing, "sales-service", "inventory-service"), Instant.now());
    }

    public TrendAnalysisResponse getTrends(String category, String timeframe) {
        int days = daysFromTimeframe(timeframe);
        List<String> missing = new ArrayList<>();
        Map<String, Object> salesTrends = fetchMap(salesWebClient, "sales-service",
                "/api/v1/admin/orders/analytics/trends", Map.of("days", days), missing);
        Map<String, Object> searchSummary = fetchMap(inventoryWebClient, "inventory-service",
                "/api/v1/products/analytics/search-summary", Map.of("days", days, "limit", 10), missing);

        List<Map<String, Object>> trends = new ArrayList<>();
        Object rawTrends = salesTrends.get("trends");
        if (rawTrends instanceof List<?> list) {
            list.stream().filter(Map.class::isInstance).forEach(e -> trends.add(new HashMap<>((Map<String, Object>) e)));
        }
        Map<String, Object> searchTrend = new HashMap<>();
        searchTrend.put("type", "search");
        searchTrend.put("totalSearches", toLong(searchSummary, "totalSearches"));
        searchTrend.put("uniqueKeywords", toLong(searchSummary, "uniqueKeywords"));
        searchTrend.put("zeroHitRatio", toDouble(searchSummary, "zeroHitRatio"));
        trends.add(searchTrend);

        log.info("Retrieved trends for category={}, timeframe={}", category, timeframe);
        return new TrendAnalysisResponse(category, timeframe, trends,
                availability(missing, "sales-service", "inventory-service"), Instant.now());
    }

    public CustomerSegmentResponse getCustomerSegments(String segmentType) {
        List<String> missing = new ArrayList<>();
        Map<String, Object> users = fetchMap(userWebClient, "user-service",
                "/api/v1/admin/users/analytics/summary", Map.of("days", 30), missing);
        List<CustomerSegmentResponse.SegmentInfo> segments = extractUserSegments(users);
        log.info("Retrieved customer segments for type={}", segmentType);
        return new CustomerSegmentResponse(segmentType, segments,
                availability(missing, "user-service"), Instant.now());
    }

    public CustomReportResponse generateCustomReport(CustomReportRequest request) {
        List<String> missing = new ArrayList<>();
        Map<String, Object> data = new HashMap<>();
        switch (request.reportType()) {
            case "sales_summary" -> data.put("sales", fetchMap(salesWebClient, "sales-service",
                    "/api/v1/admin/orders/analytics/summary", reportParams(request, 30), missing));
            case "search_summary" -> data.put("search", fetchMap(inventoryWebClient, "inventory-service",
                    "/api/v1/products/analytics/search-summary", reportParams(request, 30), missing));
            case "dashboard", "overview" -> {
                data.put("sales", fetchMap(salesWebClient, "sales-service",
                        "/api/v1/admin/orders/analytics/summary", reportParams(request, 30), missing));
                data.put("users", fetchMap(userWebClient, "user-service",
                        "/api/v1/admin/users/analytics/summary", reportParams(request, 30), missing));
                data.put("search", fetchMap(inventoryWebClient, "inventory-service",
                        "/api/v1/products/analytics/search-summary", reportParams(request, 30), missing));
            }
            default -> {
                data.put("sales", fetchMap(salesWebClient, "sales-service",
                        "/api/v1/admin/orders/analytics/summary", reportParams(request, 30), missing));
                data.put("search", fetchMap(inventoryWebClient, "inventory-service",
                        "/api/v1/products/analytics/search-summary", reportParams(request, 30), missing));
            }
        }
        log.info("Generated custom report of type={}", request.reportType());
        return new CustomReportResponse(
                UUID.randomUUID().toString(),
                request.reportType(),
                data,
                availability(missing, "sales-service", "user-service", "inventory-service"),
                Instant.now());
    }

    public DashboardResponse getDashboard(String dashboardType) {
        Map<String, Object> data = new HashMap<>();
        List<String> missing = new ArrayList<>();
        Map<String, Object> sales = fetchMap(salesWebClient, "sales-service",
                "/api/v1/admin/orders/analytics/summary", Map.of("days", 30, "topProductLimit", 10), missing);
        Map<String, Object> users = fetchMap(userWebClient, "user-service",
                "/api/v1/admin/users/analytics/summary", Map.of("days", 30), missing);
        Map<String, Object> search = fetchMap(inventoryWebClient, "inventory-service",
                "/api/v1/products/analytics/search-summary", Map.of("days", 30, "limit", 10), missing);
        List<Map<String, Object>> zeroHits = fetchList(inventoryWebClient, "inventory-service",
                "/api/v1/products/analytics/zero-hit-queries", Map.of("days", 30, "minCount", 1, "limit", 10), missing);

        data.put("totalRevenue", toLong(sales, "totalRevenue"));
        data.put("totalOrders", toLong(sales, "totalOrders"));
        data.put("totalUsers", toLong(users, "totalUsers"));
        data.put("activeUsers", toLong(users, "activeUsers"));
        data.put("totalSearches", toLong(search, "totalSearches"));
        data.put("zeroHitRatio", toDouble(search, "zeroHitRatio"));
        data.put("zeroHitOpportunities", zeroHits);
        data.put("topProducts", sales.getOrDefault("topProducts", List.of()));
        data.put("popularKeywords", search.getOrDefault("popularKeywords", List.of()));
        data.put("totalRecommendations", countRecommendations(missing));
        data.put("activeChatSessions", countActiveChatSessions(missing));

        log.info("Generated dashboard data for type={}", dashboardType);
        return new DashboardResponse(dashboardType, data,
                availability(missing, "sales-service", "user-service", "inventory-service"), Instant.now());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchMap(WebClient client, String source, String path,
                                         Map<String, Object> params, List<String> missing) {
        if (client == null) {
            missing.add(source);
            return Map.of();
        }
        try {
            Map<String, Object> result = client.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path(path);
                        params.forEach((k, v) -> {
                            if (v != null && !v.toString().isBlank()) b.queryParam(k, v);
                        });
                        return b.build();
                    })
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(8));
            if (result == null) {
                missing.add(source);
                return Map.of();
            }
            return result;
        } catch (Exception ex) {
            log.warn("Failed to fetch analytics source {} {}: {}", source, path, ex.getMessage());
            missing.add(source);
            return Map.of();
        }
    }

    private List<Map<String, Object>> fetchList(WebClient client, String source, String path,
                                                Map<String, Object> params, List<String> missing) {
        if (client == null) {
            missing.add(source);
            return List.of();
        }
        try {
            List<Map<String, Object>> result = client.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path(path);
                        params.forEach((k, v) -> {
                            if (v != null && !v.toString().isBlank()) b.queryParam(k, v);
                        });
                        return b.build();
                    })
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block(Duration.ofSeconds(8));
            if (result == null) {
                missing.add(source);
                return List.of();
            }
            return result;
        } catch (Exception ex) {
            log.warn("Failed to fetch analytics source {} {}: {}", source, path, ex.getMessage());
            missing.add(source);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private long extractPurchases(Map<String, Object> salesSummary, String productId) {
        Object raw = salesSummary.get("topProducts");
        if (!(raw instanceof List<?> list)) return 0L;
        return list.stream()
                .filter(Map.class::isInstance)
                .map(e -> (Map<String, Object>) e)
                .filter(p -> productId == null || productId.isBlank()
                        || productId.equals(Objects.toString(p.get("productId"), "")))
                .mapToLong(p -> toLong(p, "quantity"))
                .sum();
    }

    private Map<String, Object> findProduct(List<Map<String, Object>> inventory, String productId, String category) {
        return inventory.stream()
                .filter(p -> productId == null || productId.isBlank()
                        || productId.equals(Objects.toString(p.get("id"), "")))
                .filter(p -> category == null || category.isBlank()
                        || category.equals(Objects.toString(p.get("categoryId"), "")))
                .findFirst()
                .orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    private List<CustomerSegmentResponse.SegmentInfo> extractUserSegments(Map<String, Object> users) {
        Object raw = users.get("segments");
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream()
                .filter(Map.class::isInstance)
                .map(e -> {
                    Map<String, Object> row = (Map<String, Object>) e;
                    String id = Objects.toString(row.getOrDefault("segmentId", row.get("label")), "");
                    String name = Objects.toString(row.getOrDefault("label", id), "");
                    int count = toInt(row, "count", 0);
                    return new CustomerSegmentResponse.SegmentInfo(id, name, count, row);
                })
                .toList();
    }

    private Map<String, Object> reportParams(CustomReportRequest request, int defaultDays) {
        Map<String, Object> params = new HashMap<>();
        params.put("days", defaultDays);
        if (request.parameters() != null) {
            Object days = request.parameters().get("days");
            if (days != null) params.put("days", days);
            Object limit = request.parameters().get("limit");
            if (limit != null) params.put("limit", limit);
        }
        return params;
    }

    private int daysFromTimeframe(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) return 30;
        return switch (timeframe.toLowerCase()) {
            case "weekly", "week" -> 7;
            case "quarterly", "quarter" -> 90;
            case "yearly", "year" -> 365;
            default -> 30;
        };
    }

    private DataAvailability availability(List<String> missing, String... sources) {
        List<String> distinct = missing.stream().distinct().toList();
        if (distinct.isEmpty()) return DataAvailability.available();
        return distinct.size() >= sources.length
                ? DataAvailability.unavailable(distinct)
                : DataAvailability.partial(distinct);
    }

    private long countRecommendations(List<String> missing) {
        if (recommendationRepository == null) {
            missing.add("ai-recommendation-store");
            return 0L;
        }
        try {
            return recommendationRepository.count();
        } catch (RuntimeException ex) {
            log.warn("Failed to count recommendations: {}", ex.getMessage());
            missing.add("ai-recommendation-store");
            return 0L;
        }
    }

    private long countActiveChatSessions(List<String> missing) {
        if (chatSessionRepository == null) {
            missing.add("ai-chat-session-store");
            return 0L;
        }
        try {
            return chatSessionRepository.countByStatus(ChatSession.SessionStatus.ACTIVE);
        } catch (RuntimeException ex) {
            log.warn("Failed to count active chat sessions: {}", ex.getMessage());
            missing.add("ai-chat-session-store");
            return 0L;
        }
    }

    private static long toLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) { return 0L; }
        }
        return 0L;
    }

    private static int toInt(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { return defaultValue; }
        }
        return defaultValue;
    }

    private static double toDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) { return 0.0; }
        }
        return 0.0;
    }
}
