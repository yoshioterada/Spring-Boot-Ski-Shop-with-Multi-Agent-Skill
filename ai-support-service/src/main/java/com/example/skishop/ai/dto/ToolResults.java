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
            List<InventoryEntry> items,
            DataAvailability availability
    ) {
        public InventoryLevelsResult(String category, List<InventoryEntry> items) {
            this(category, items, DataAvailability.available());
        }
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
            int recommendedOrderQuantity,
            DataAvailability availability
    ) {
        public ReorderPointsResult(String sku, String name, int currentStock, int reorderPoint,
                                   int safetyStock, boolean needsReorder, int recommendedOrderQuantity) {
            this(sku, name, currentStock, reorderPoint, safetyStock, needsReorder,
                    recommendedOrderQuantity, DataAvailability.available());
        }
    }

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
            List<Map<String, Object>> topChanges,
            DataAvailability availability
    ) {
        public WeeklyComparisonResult(LocalDate thisWeekStart, LocalDate prevWeekStart,
                                      long thisRevenue, long prevRevenue, double revenueChange,
                                      long thisOrders, long prevOrders, double ordersChange,
                                      List<Map<String, Object>> topChanges) {
            this(thisWeekStart, prevWeekStart, thisRevenue, prevRevenue, revenueChange,
                    thisOrders, prevOrders, ordersChange, topChanges, DataAvailability.available());
        }
    }

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
            List<DeadStockEntry> items,
            DataAvailability availability
    ) {
        public DeadStockResult(String severity, String category, List<DeadStockEntry> items) {
            this(severity, category, items, DataAvailability.available());
        }
        public record DeadStockEntry(
                String sku, String name, int stock, long sales30, long sales90,
                LocalDate lastSoldAt, double avgPrice, String severityLevel,
                int daysOfSupply, double suggestedDiscountPct, String categoryId
        ) {
            public DeadStockEntry(String sku, String name, int stock, long sales30, long sales90,
                                  LocalDate lastSoldAt, double avgPrice, String severityLevel) {
                this(sku, name, stock, sales30, sales90, lastSoldAt, avgPrice, severityLevel, 0, 0, null);
            }
        }
    }

    // ── getInventoryVelocity (F4) ──
    public record InventoryVelocityResult(
            String sku,
            long sales30,
            long sales90,
            long units30,
            long units90,
            LocalDate lastSoldAt,
            double avgPrice,
            DataAvailability availability
    ) {
        public InventoryVelocityResult(String sku, long sales30, long sales90, long units30,
                                       long units90, LocalDate lastSoldAt, double avgPrice) {
            this(sku, sales30, sales90, units30, units90, lastSoldAt, avgPrice, DataAvailability.available());
        }
    }

    // ── getZeroHitOpportunities (F5) ──
    public record ZeroHitResult(
            int days,
            int limit,
            List<ZeroHitEntry> keywords,
            DataAvailability availability
    ) {
        public ZeroHitResult(int days, int limit, List<ZeroHitEntry> keywords) {
            this(days, limit, keywords, DataAvailability.available());
        }
        public record ZeroHitEntry(
                String keyword,
                long searchCount,
                double estimatedLoss,
                String category,
                String suggestedAction,
                String normalizedKeyword
        ) {
            public ZeroHitEntry(String keyword, long searchCount, double estimatedLoss) {
                this(keyword, searchCount, estimatedLoss, null, null, null);
            }
        }
    }

    // ── searchProductCatalog (F5) ──
    public record ProductSearchResult(
            String keyword,
            int totalHits,
            List<ProductHit> hits,
            DataAvailability availability
    ) {
        public ProductSearchResult(String keyword, int totalHits, List<ProductHit> hits) {
            this(keyword, totalHits, hits, DataAvailability.available());
        }
        public record ProductHit(String sku, String name, String category, double price) {}
    }
}
