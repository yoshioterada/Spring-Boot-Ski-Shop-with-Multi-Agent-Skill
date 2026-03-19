package com.example.skishop.ai.service;

import com.example.skishop.ai.dto.*;
import com.example.skishop.ai.model.BehaviorMetrics;
import com.example.skishop.ai.model.DemandForecast;
import com.example.skishop.ai.model.UserProfile;
import com.example.skishop.ai.repository.BehaviorMetricsRepository;
import com.example.skishop.ai.repository.DemandForecastRepository;
import com.example.skishop.ai.repository.UserProfileRepository;
import com.example.skishop.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private BehaviorMetricsRepository behaviorMetricsRepository;
    @Mock
    private DemandForecastRepository demandForecastRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(
                userProfileRepository, behaviorMetricsRepository, demandForecastRepository);
    }

    @Test
    @DisplayName("ユーザー行動分析が正常に取得される")
    void should_returnUserBehavior_when_validUserId() {
        // Arrange
        String userId = "user-001";
        UserProfile profile = new UserProfile(userId);
        profile.setLoyaltyTier("GOLD");
        profile.setTotalSpent(50000.0);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(behaviorMetricsRepository.findByUserId(userId))
                .thenReturn(List.of(new BehaviorMetrics(userId, "page_views", 150.0, "general")));

        // Act
        UserBehaviorResponse response = analyticsService.getUserBehavior(userId);

        // Assert
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.loyaltyTier()).isEqualTo("GOLD");
        assertThat(response.totalSpent()).isEqualTo(50000.0);
        assertThat(response.metrics()).containsKey("page_views");
    }

    @Test
    @DisplayName("存在しないユーザーの行動分析で例外がスローされる")
    void should_throwException_when_userProfileNotFound() {
        // Arrange
        when(userProfileRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> analyticsService.getUserBehavior("nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("売上予測が正常に取得される")
    void should_returnSalesForecast_when_validProductId() {
        // Arrange
        DemandForecast forecast = new DemandForecast(
                "prod-001", "ski_boots", "WEEKLY", 45.0,
                "ARIMA", 0.85, Instant.now(), Instant.now().plusSeconds(604800));
        forecast.setAccuracy(0.87);
        when(demandForecastRepository.findByProductIdAndPeriod("prod-001", "WEEKLY"))
                .thenReturn(List.of(forecast));

        // Act
        SalesForecastResponse response = analyticsService.getSalesForecast("prod-001", "WEEKLY", 4);

        // Assert
        assertThat(response.productId()).isEqualTo("prod-001");
        assertThat(response.forecastPeriod()).isEqualTo("WEEKLY");
        assertThat(response.forecasts()).hasSize(1);
    }

    @Test
    @DisplayName("商品パフォーマンスが取得される")
    void should_returnProductPerformance_when_requested() {
        // Act
        ProductPerformanceResponse response = analyticsService.getProductPerformance("prod-001", "ski");

        // Assert
        assertThat(response.productId()).isEqualTo("prod-001");
        assertThat(response.metrics()).isNotEmpty();
    }

    @Test
    @DisplayName("トレンド分析が取得される")
    void should_returnTrends_when_requested() {
        // Act
        TrendAnalysisResponse response = analyticsService.getTrends("ski", "monthly");

        // Assert
        assertThat(response.category()).isEqualTo("ski");
        assertThat(response.timeframe()).isEqualTo("monthly");
    }

    @Test
    @DisplayName("顧客セグメントが取得される")
    void should_returnCustomerSegments_when_requested() {
        // Act
        CustomerSegmentResponse response = analyticsService.getCustomerSegments("loyalty");

        // Assert
        assertThat(response.segmentType()).isEqualTo("loyalty");
    }

    @Test
    @DisplayName("カスタムレポートが生成される")
    void should_generateCustomReport_when_validRequest() {
        // Arrange
        CustomReportRequest request = new CustomReportRequest("sales_summary", Map.of(), "last_30_days");

        // Act
        CustomReportResponse response = analyticsService.generateCustomReport(request);

        // Assert
        assertThat(response.reportType()).isEqualTo("sales_summary");
        assertThat(response.reportId()).isNotBlank();
    }

    @Test
    @DisplayName("ダッシュボードデータが取得される")
    void should_returnDashboard_when_requested() {
        // Act
        DashboardResponse response = analyticsService.getDashboard("overview");

        // Assert
        assertThat(response.dashboardType()).isEqualTo("overview");
        assertThat(response.data()).isNotEmpty();
    }
}
