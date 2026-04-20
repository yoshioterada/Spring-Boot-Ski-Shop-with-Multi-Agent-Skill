package com.example.skishop.ai.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Function Tool 戻り値 Record クラス群 (spec § 5.2 / D-COM-01: Object 禁止).
 */
public final class ToolResults {

    private ToolResults() {}

    // ── getDailyRevenue ──
    public record DailyRevenueResult(
            int days,
            String category,
            long totalRevenue,
            long totalOrders,
            double avgDailyRevenue,
            List<DailyEntry> dailyBreakdown
    ) {
        public record DailyEntry(LocalDate date, long revenue, long orders) {}
    }

    // ── getTopProducts ──
    public record TopProductsResult(
            int days,
            int limit,
            String category,
            List<ProductEntry> products
    ) {
        public record ProductEntry(String sku, String name, long sales, long revenue, double share) {}
    }

    // ── getCategoryShare ──
    public record CategoryShareResult(
            int days,
            List<CategoryEntry> categories
    ) {
        public record CategoryEntry(String categoryId, String categoryName, long revenue, double share) {}
    }

    // ── getInventoryLevels ──
    public record InventoryLevelsResult(
            String category,
            List<InventoryEntry> items
    ) {
        public record InventoryEntry(String sku, String name, int stockQuantity, int reorderPoint, String status) {}
    }

    // ── getReorderPoints ──
    public record ReorderPointsResult(
            String sku,
            String name,
            int currentStock,
            int reorderPoint,
            int safetyStock,
            boolean needsReorder,
            int recommendedOrderQuantity
    ) {}

    // ── getWeeklyComparison ──
    public record WeeklyComparisonResult(
            LocalDate thisWeekStart,
            LocalDate prevWeekStart,
            long thisRevenue,
            long prevRevenue,
            double revenueChange,
            long thisOrders,
            long prevOrders,
            double ordersChange,
            List<Map<String, Object>> topChanges
    ) {}

    // ── getSeasonalHistorical ──
    public record SeasonalHistoricalResult(
            int months,
            List<MonthlyEntry> monthly
    ) {
        public record MonthlyEntry(String yearMonth, long revenue, long orders, double yoyChange) {}
    }

    // ── getWeatherForecast ──
    public record WeatherForecastResult(
            String region,
            int weeksAhead,
            List<WeekForecast> weeks
    ) {
        public record WeekForecast(String weekStart, double avgTemp, double snowfall, String condition) {}
    }

    // ── getDeadStock (F4) ──
    public record DeadStockResult(
            String severity,
            String category,
            List<DeadStockEntry> items
    ) {
        public record DeadStockEntry(
                String sku, String name, int stock, long sales30, long sales90,
                LocalDate lastSoldAt, double avgPrice, String severityLevel
        ) {}
    }

    // ── getInventoryVelocity (F4) ──
    public record InventoryVelocityResult(
            String sku,
            long sales30,
            long sales90,
            long units30,
            long units90,
            LocalDate lastSoldAt,
            double avgPrice
    ) {}

    // ── getZeroHitOpportunities (F5) ──
    public record ZeroHitResult(
            int days,
            int limit,
            List<ZeroHitEntry> keywords
    ) {
        public record ZeroHitEntry(String keyword, long searchCount, double estimatedLoss) {}
    }

    // ── searchProductCatalog (F5) ──
    public record ProductSearchResult(
            String keyword,
            int totalHits,
            List<ProductHit> hits
    ) {
        public record ProductHit(String sku, String name, String category, double price) {}
    }
}
