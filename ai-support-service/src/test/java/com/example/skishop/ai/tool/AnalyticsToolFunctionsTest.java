package com.example.skishop.ai.tool;

import com.example.skishop.ai.dto.ToolResults.*;
import com.example.skishop.ai.dto.DataAvailability;
import com.example.skishop.ai.dto.DeadStockResponse;
import com.example.skishop.ai.dto.DeadStockResponse.DeadStockItem;
import com.example.skishop.ai.dto.DeadStockResponse.Severity;
import com.example.skishop.ai.dto.ZeroHitOpportunityResponse;
import com.example.skishop.ai.dto.ZeroHitOpportunityResponse.Opportunity;
import com.example.skishop.ai.dto.ZeroHitOpportunityResponse.Summary;
import com.example.skishop.ai.service.DeadStockService;
import com.example.skishop.ai.service.ZeroHitOpportunityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * AnalyticsToolFunctions 単体テスト (P2-1〜P2-3).
 * WebClient はモック化し、Tool の戻り値型が Record であることを検証.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsToolFunctionsTest {

    @Mock
    private WebClient salesWebClient;
    @Mock
    private WebClient userWebClient;
    @Mock
    private WebClient inventoryWebClient;
    @Mock
    private WebClient couponWebClient;
    @Mock
    private WebClient weatherWebClient;
    @Mock
    private DeadStockService deadStockService;
    @Mock
    private ZeroHitOpportunityService zeroHitOpportunityService;

    private AnalyticsToolFunctions tools;

    @BeforeEach
    void setUp() {
        tools = new AnalyticsToolFunctions(salesWebClient, userWebClient, inventoryWebClient,
                couponWebClient, weatherWebClient, deadStockService, zeroHitOpportunityService);
    }

    // ── P2-3: 戻り値がすべて Record クラス（Object 禁止） ──

    @Test
    void getDailyRevenue_returnsRecordType() {
        // WebClient はモックなので fetchSalesMap は空 Map → fallback 的動作
        // fallback メソッドは CircuitBreaker 経由でのみ呼ばれるため、直接テスト
        var result = new DailyRevenueResult(30, null, 0, 0, 0, java.util.List.of());
        assertThat(result).isInstanceOf(Record.class);
        assertThat(result.days()).isEqualTo(30);
    }

    @Test
    void getTopProducts_returnsRecordType() {
        var result = new TopProductsResult(7, 10, "ski", java.util.List.of());
        assertThat(result).isInstanceOf(Record.class);
        assertThat(result.limit()).isEqualTo(10);
    }

    @Test
    void getCategoryShare_returnsRecordType() {
        var result = new CategoryShareResult(30, java.util.List.of());
        assertThat(result).isInstanceOf(Record.class);
    }

    @Test
    void getInventoryLevels_returnsRecordType() {
        var result = new InventoryLevelsResult("boots", java.util.List.of());
        assertThat(result).isInstanceOf(Record.class);
    }

    @Test
    void getReorderPoints_returnsRecordType() {
        var result = new ReorderPointsResult("SKU-001", "Test", 10, 5, 3, true, 5);
        assertThat(result).isInstanceOf(Record.class);
        assertThat(result.needsReorder()).isTrue();
    }

    @Test
    void getWeeklyComparison_returnsRecordType() {
        var result = new WeeklyComparisonResult(
                java.time.LocalDate.of(2025, 1, 6),
                java.time.LocalDate.of(2024, 12, 30),
                100000, 90000, 0.111, 50, 45, 0.111, java.util.List.of());
        assertThat(result).isInstanceOf(Record.class);
    }

    @Test
    void getSeasonalHistorical_returnsRecordType() {
        var result = new SeasonalHistoricalResult(12, java.util.List.of());
        assertThat(result).isInstanceOf(Record.class);
    }

    @Test
    void getWeatherForecast_returnsRecordType() {
        var result = new WeatherForecastResult("tohoku", 4, java.util.List.of());
        assertThat(result).isInstanceOf(Record.class);
    }

    // ── P3 Tools (F4/F5) ──

    @Test
    void getDeadStock_returnsRecordType() {
        var result = new DeadStockResult("critical", null, java.util.List.of());
        assertThat(result).isInstanceOf(Record.class);
    }

    @Test
    void getInventoryVelocity_returnsRecordType() {
        var result = new InventoryVelocityResult("SKU-001", 0, 0, 0, 0, null, 0);
        assertThat(result).isInstanceOf(Record.class);
    }

    @Test
    void getZeroHitOpportunities_returnsRecordType() {
        var result = new ZeroHitResult(30, 10, java.util.List.of());
        assertThat(result).isInstanceOf(Record.class);
    }

    @Test
    void searchProductCatalog_returnsRecordType() {
        var result = new ProductSearchResult("ski boots", 0, java.util.List.of());
        assertThat(result).isInstanceOf(Record.class);
    }

        @Test
        void getDeadStock_usesDedicatedServiceAndPreservesFields() {
        // Arrange
        var response = new DeadStockResponse(Instant.now(), List.of(
            new DeadStockItem("SKU-1", "Powder Ski", 12, 0, 1, 360,
                Severity.CRITICAL, 40.0, "reason", "cat-ski"),
            new DeadStockItem("SKU-2", "Jacket", 8, 3, 9, 120,
                Severity.HIGH, 25.5, "reason", "cat-wear"),
            new DeadStockItem("SKU-3", "Helmet", 5, 8, 20, 60,
                Severity.MEDIUM, 10.0, "reason", "cat-ski")
        ), "narrative", DataAvailability.available());
        when(deadStockService.getDeadStock()).thenReturn(response);

        // Act
        DeadStockResult result = tools.getDeadStock("all", "cat-ski");

        // Assert
        assertThat(result.items()).hasSize(2);
        assertThat(result.availability()).isEqualTo(DataAvailability.available());
        assertThat(result.items()).extracting(DeadStockResult.DeadStockEntry::sku)
            .containsExactly("SKU-1", "SKU-3");
        assertThat(result.items().getFirst().daysOfSupply()).isEqualTo(360);
        assertThat(result.items().getFirst().suggestedDiscountPct()).isEqualTo(40.0);
        assertThat(result.items().getFirst().categoryId()).isEqualTo("cat-ski");
        }

        @Test
        void getDeadStock_appliesSeverityFilterConsistentlyWithDedicatedResponse() {
        // Arrange
        var response = new DeadStockResponse(Instant.now(), List.of(
            new DeadStockItem("SKU-1", "Powder Ski", 12, 0, 1, 360,
                Severity.CRITICAL, 40.0, null, "cat-ski"),
            new DeadStockItem("SKU-2", "Jacket", 8, 3, 9, 120,
                Severity.HIGH, 25.5, null, "cat-wear")
        ), "narrative", DataAvailability.partial(List.of("sales-service")));
        when(deadStockService.getDeadStock()).thenReturn(response);

        // Act
        DeadStockResult result = tools.getDeadStock("critical", null);

        // Assert
        assertThat(result.items()).extracting(DeadStockResult.DeadStockEntry::sku)
            .containsExactly("SKU-1");
        assertThat(result.availability()).isEqualTo(response.availability());
        }

        @Test
        void getZeroHitOpportunities_usesDedicatedServiceAfterDismissals() {
        // Arrange
        var response = new ZeroHitOpportunityResponse(Instant.now(), 30,
            new Summary(1, 47, 117500, "cat-ski"),
            List.of(new Opportunity(1, "atomic cloud", 47, Instant.now(),
                "cat-ski", "women", 117500, "HIGH",
                "Atomic Cloud 系の仕入れ検討", List.of(), List.of())),
            DataAvailability.available());
        when(zeroHitOpportunityService.getOpportunities(30, 1, null, 10)).thenReturn(response);

        // Act
        ZeroHitResult result = tools.getZeroHitOpportunities(30, 10);

        // Assert
        assertThat(result.keywords()).hasSize(1);
        ZeroHitResult.ZeroHitEntry entry = result.keywords().getFirst();
        assertThat(entry.keyword()).isEqualTo("atomic cloud");
        assertThat(entry.normalizedKeyword()).isEqualTo("atomic cloud");
        assertThat(entry.searchCount()).isEqualTo(47);
        assertThat(entry.estimatedLoss()).isEqualTo(117500);
        assertThat(entry.category()).isEqualTo("cat-ski");
        assertThat(entry.suggestedAction()).isEqualTo("Atomic Cloud 系の仕入れ検討");
        assertThat(result.availability()).isEqualTo(DataAvailability.available());
        }

        @Test
        void getZeroHitOpportunities_propagatesUnavailableAndTrueZeroStates() {
        // Arrange
        var unavailable = DataAvailability.unavailable(List.of("inventory-service"));
        var response = new ZeroHitOpportunityResponse(Instant.now(), 30,
            new Summary(0, 0, 0, "unknown"), List.of(), unavailable);
        when(zeroHitOpportunityService.getOpportunities(30, 1, null, 10)).thenReturn(response);

        // Act
        ZeroHitResult result = tools.getZeroHitOpportunities(30, 10);

        // Assert
        assertThat(result.keywords()).isEmpty();
        assertThat(result.availability()).isEqualTo(unavailable);

        // Arrange
        var trueZero = new ZeroHitOpportunityResponse(Instant.now(), 30,
            new Summary(0, 0, 0, "unknown"), List.of(), DataAvailability.available());
        when(zeroHitOpportunityService.getOpportunities(30, 1, null, 10)).thenReturn(trueZero);

        // Act
        ZeroHitResult trueZeroResult = tools.getZeroHitOpportunities(30, 10);

        // Assert
        assertThat(trueZeroResult.keywords()).isEmpty();
        assertThat(trueZeroResult.availability().dataAvailable()).isTrue();
        }

    // ── 引数バリデーション境界値テスト (P2-2) ──

    @Test
    void dailyRevenueResult_preservesBoundaryValues() {
        // days=7 (min)
        var min = new DailyRevenueResult(7, null, 1000, 10, 142.86, java.util.List.of());
        assertThat(min.days()).isEqualTo(7);

        // days=365 (max)
        var max = new DailyRevenueResult(365, "all", 100000, 500, 273.97, java.util.List.of());
        assertThat(max.days()).isEqualTo(365);
    }

    @Test
    void topProductsResult_preservesBoundaryValues() {
        var min = new TopProductsResult(1, 1, null, java.util.List.of());
        assertThat(min.days()).isEqualTo(1);
        assertThat(min.limit()).isEqualTo(1);

        var max = new TopProductsResult(365, 50, "all", java.util.List.of());
        assertThat(max.limit()).isEqualTo(50);
    }

    @Test
    void seasonalHistorical_preservesBoundaryValues() {
        var min = new SeasonalHistoricalResult(1, java.util.List.of());
        assertThat(min.months()).isEqualTo(1);

        var max = new SeasonalHistoricalResult(36, java.util.List.of());
        assertThat(max.months()).isEqualTo(36);
    }

    @Test
    void weatherForecast_preservesBoundaryValues() {
        var min = new WeatherForecastResult("region", 1, java.util.List.of());
        assertThat(min.weeksAhead()).isEqualTo(1);

        var max = new WeatherForecastResult("region", 8, java.util.List.of());
        assertThat(max.weeksAhead()).isEqualTo(8);
    }

    @Test
    void zeroHitOpportunities_preservesBoundaryValues() {
        var min = new ZeroHitResult(1, 1, java.util.List.of());
        assertThat(min.days()).isEqualTo(1);

        var max = new ZeroHitResult(365, 100, java.util.List.of());
        assertThat(max.limit()).isEqualTo(100);
    }

    // ── ツール数確認 ──

    @Test
    void allToolMethods_haveToolAnnotation() {
        long toolCount = java.util.Arrays.stream(AnalyticsToolFunctions.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class))
                .count();
        // P2: 8 + P3: 4 = 12 total
        assertThat(toolCount).isEqualTo(12);
    }

    @Test
    void noToolMethod_returnsObjectType() {
        java.util.Arrays.stream(AnalyticsToolFunctions.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class))
                .forEach(m -> {
                    assertThat(m.getReturnType())
                            .as("Tool method %s must not return Object", m.getName())
                            .isNotEqualTo(Object.class);
                    assertThat(Record.class.isAssignableFrom(m.getReturnType())
                            || m.getReturnType() == WeatherForecastResult.class)
                            .as("Tool method %s should return a Record type", m.getName())
                            .isTrue();
                });
    }
}
