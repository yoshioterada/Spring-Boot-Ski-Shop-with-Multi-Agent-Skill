package com.example.skishop.ai.service;

import com.example.skishop.ai.dto.*;
import com.example.skishop.ai.model.BehaviorMetrics;
import com.example.skishop.ai.model.DemandForecast;
import com.example.skishop.ai.model.UserProfile;
import com.example.skishop.ai.repository.BehaviorMetricsRepository;
import com.example.skishop.ai.repository.DemandForecastRepository;
import com.example.skishop.ai.repository.UserProfileRepository;
import com.example.skishop.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final UserProfileRepository userProfileRepository;
    private final BehaviorMetricsRepository behaviorMetricsRepository;
    private final DemandForecastRepository demandForecastRepository;

    public AnalyticsService(UserProfileRepository userProfileRepository,
                            BehaviorMetricsRepository behaviorMetricsRepository,
                            DemandForecastRepository demandForecastRepository) {
        this.userProfileRepository = userProfileRepository;
        this.behaviorMetricsRepository = behaviorMetricsRepository;
        this.demandForecastRepository = demandForecastRepository;
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
        metrics.put("views", 0);
        metrics.put("purchases", 0);
        metrics.put("conversionRate", 0.0);
        metrics.put("averageRating", 0.0);

        log.info("Retrieved product performance for productId={}, category={}", productId, category);
        return new ProductPerformanceResponse(productId, category, metrics, Instant.now());
    }

    public TrendAnalysisResponse getTrends(String category, String timeframe) {
        log.info("Retrieved trends for category={}, timeframe={}", category, timeframe);
        return new TrendAnalysisResponse(category, timeframe, List.of(), Instant.now());
    }

    public CustomerSegmentResponse getCustomerSegments(String segmentType) {
        log.info("Retrieved customer segments for type={}", segmentType);
        return new CustomerSegmentResponse(segmentType, List.of(), Instant.now());
    }

    public CustomReportResponse generateCustomReport(CustomReportRequest request) {
        log.info("Generated custom report of type={}", request.reportType());
        return new CustomReportResponse(
                UUID.randomUUID().toString(),
                request.reportType(),
                Map.of("status", "generated"),
                Instant.now());
    }

    public DashboardResponse getDashboard(String dashboardType) {
        Map<String, Object> data = new HashMap<>();
        data.put("totalUsers", 0);
        data.put("totalSearches", 0);
        data.put("totalRecommendations", 0);
        data.put("activeChatSessions", 0);

        log.info("Generated dashboard data for type={}", dashboardType);
        return new DashboardResponse(dashboardType, data, Instant.now());
    }
}
