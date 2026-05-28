package com.example.skishop.ai.tool;

import com.example.skishop.ai.dto.ToolResults.*;
import com.example.skishop.ai.dto.DataAvailability;
import com.example.skishop.ai.dto.DeadStockResponse;
import com.example.skishop.ai.dto.ZeroHitOpportunityResponse;
import com.example.skishop.ai.service.DeadStockService;
import com.example.skishop.ai.service.ZeroHitOpportunityService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * AI Analyzer Function Tool 群 (spec § 5.2 + § 19.5 + § 20.6).
 * <p>
 * 全 12 メソッド × 型付き Record 戻り値（Object 禁止: D-COM-01）.
 * 引数に {@code @Min}/{@code @Max} バリデーション（D-COM-05 アンチパターン対策）.
 */
@Component
public class AnalyticsToolFunctions {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsToolFunctions.class);

    private final WebClient salesWebClient;
    private final WebClient inventoryWebClient;
    private final WebClient weatherWebClient;
    private final DeadStockService deadStockService;
    private final ZeroHitOpportunityService zeroHitOpportunityService;

    public AnalyticsToolFunctions(
            @Qualifier("salesWebClient") WebClient salesWebClient,
            @Qualifier("userWebClient") WebClient userWebClient,
            @Qualifier("inventoryWebClient") WebClient inventoryWebClient,
            @Qualifier("couponWebClient") WebClient couponWebClient,
            @Qualifier("weatherWebClient") WebClient weatherWebClient,
            DeadStockService deadStockService,
            ZeroHitOpportunityService zeroHitOpportunityService) {
        this.salesWebClient = salesWebClient;
        this.inventoryWebClient = inventoryWebClient;
        this.weatherWebClient = weatherWebClient;
        this.deadStockService = deadStockService;
        this.zeroHitOpportunityService = zeroHitOpportunityService;
    }

    // ── P2 Tools (8 種) ──────────────────────────────────────

    @Tool(description = "指定日数の日次売上を取得する。カテゴリ指定は任意。")
    @CircuitBreaker(name = "salesService", fallbackMethod = "fallbackDailyRevenue")
    @Retry(name = "serviceCall")
    public DailyRevenueResult getDailyRevenue(
            @ToolParam(description = "集計日数 (7-365)") @Min(7) @Max(365) int days,
            @ToolParam(description = "カテゴリ ID（任意）") @Nullable String category) {
        logToolCall("getDailyRevenue", days, category);
        Map<String, Object> data = fetchSalesMap("/api/v1/admin/orders/analytics/summary",
                Map.of("days", days, "category", Objects.toString(category, "")));
        long totalRevenue = toLong(data, "totalRevenue");
        long totalOrders = toLong(data, "totalOrders");
        return new DailyRevenueResult(days, category, totalRevenue, totalOrders,
                days > 0 ? (double) totalRevenue / days : 0, List.of());
    }

    @SuppressWarnings("unused")
    private DailyRevenueResult fallbackDailyRevenue(int days, String category, Throwable t) {
        log.warn("Fallback getDailyRevenue: {}", t.getMessage());
        return new DailyRevenueResult(days, category, 0, 0, 0, List.of());
    }

    @Tool(description = "指定日数の売上上位商品を取得する。")
    @CircuitBreaker(name = "salesService", fallbackMethod = "fallbackTopProducts")
    @Retry(name = "serviceCall")
    public TopProductsResult getTopProducts(
            @ToolParam(description = "集計日数") @Min(1) @Max(365) int days,
            @ToolParam(description = "取得件数") @Min(1) @Max(50) int limit,
            @ToolParam(description = "カテゴリ ID（任意）") @Nullable String category) {
        logToolCall("getTopProducts", days, limit, category);
        Map<String, Object> data = fetchSalesMap("/api/v1/admin/orders/analytics/summary",
                Map.of("days", days, "topProductLimit", limit, "category", Objects.toString(category, "")));
        List<TopProductsResult.ProductEntry> products = extractTopProducts(data);
        return new TopProductsResult(days, limit, category, products);
    }

    @SuppressWarnings("unused")
    private TopProductsResult fallbackTopProducts(int days, int limit, String category, Throwable t) {
        return new TopProductsResult(days, limit, category, List.of());
    }

    @Tool(description = "指定日数のカテゴリ別売上シェアを取得する。")
    @CircuitBreaker(name = "salesService", fallbackMethod = "fallbackCategoryShare")
    @Retry(name = "serviceCall")
    public CategoryShareResult getCategoryShare(
            @ToolParam(description = "集計日数") @Min(7) @Max(365) int days) {
        logToolCall("getCategoryShare", days);
        Map<String, Object> data = fetchSalesMap("/api/v1/admin/orders/analytics/summary", Map.of("days", days));
        List<CategoryShareResult.CategoryEntry> categories = extractCategoryShare(data);
        return new CategoryShareResult(days, categories);
    }

    @SuppressWarnings("unused")
    private CategoryShareResult fallbackCategoryShare(int days, Throwable t) {
        return new CategoryShareResult(days, List.of());
    }

    @Tool(description = "在庫水準一覧を取得する。カテゴリ指定は任意。")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackInventoryLevels")
    @Retry(name = "serviceCall")
    public InventoryLevelsResult getInventoryLevels(
            @ToolParam(description = "カテゴリ ID（任意）") @Nullable String category) {
        logToolCall("getInventoryLevels", category);
        List<Map<String, Object>> rows = fetchInventoryList("/api/v1/inventory/all");
        List<InventoryLevelsResult.InventoryEntry> items = rows.stream()
                .filter(row -> category == null || category.isBlank()
                        || Objects.equals(category, Objects.toString(row.get("categoryId"), "")))
                .map(row -> {
                    int stock = toInt(row, "availableQuantity", toInt(row, "stockQuantity", 0));
                    int reorderPoint = 10;
                    String status = stock <= 0 ? "OUT_OF_STOCK" : (stock <= reorderPoint ? "LOW_STOCK" : "AVAILABLE");
                    return new InventoryLevelsResult.InventoryEntry(
                            Objects.toString(row.get("sku"), ""),
                            Objects.toString(row.get("name"), ""),
                            stock,
                            reorderPoint,
                            status);
                })
                .toList();
        return new InventoryLevelsResult(category, items, availabilityFrom("inventory-service", rows));
    }

    @SuppressWarnings("unused")
    private InventoryLevelsResult fallbackInventoryLevels(String category, Throwable t) {
        return new InventoryLevelsResult(category, List.of(), DataAvailability.unavailable(List.of("inventory-service")));
    }

    @Tool(description = "指定 SKU の発注点と現在庫を取得する。")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackReorderPoints")
    @Retry(name = "serviceCall")
    public ReorderPointsResult getReorderPoints(
            @ToolParam(description = "商品 SKU") String sku) {
        logToolCall("getReorderPoints", sku);
        List<Map<String, Object>> rows = fetchInventoryList("/api/v1/inventory/all");
        return rows.stream()
                .filter(row -> sku.equals(Objects.toString(row.get("sku"), "")))
                .findFirst()
                .map(row -> {
                    int stock = toInt(row, "availableQuantity", toInt(row, "stockQuantity", 0));
                    int reorderPoint = 10;
                    int safetyStock = 5;
                    boolean needsReorder = stock <= reorderPoint;
                    int recommendedQty = needsReorder ? Math.max(0, reorderPoint + safetyStock - stock) : 0;
                    return new ReorderPointsResult(
                            sku,
                            Objects.toString(row.get("name"), ""),
                            stock,
                            reorderPoint,
                            safetyStock,
                            needsReorder,
                            recommendedQty,
                            availabilityFrom("inventory-service", rows));
                })
                .orElseGet(() -> new ReorderPointsResult(
                        sku, "", 0, 10, 5, false, 0,
                        rows.isEmpty() ? DataAvailability.unavailable(List.of("inventory-service")) : DataAvailability.available()));
    }

    @SuppressWarnings("unused")
    private ReorderPointsResult fallbackReorderPoints(String sku, Throwable t) {
        return new ReorderPointsResult(sku, "", 0, 0, 0, false, 0,
                DataAvailability.unavailable(List.of("inventory-service")));
    }

    @Tool(description = "今週と先週の売上を比較する。")
    @CircuitBreaker(name = "salesService", fallbackMethod = "fallbackWeeklyComparison")
    @Retry(name = "serviceCall")
    public WeeklyComparisonResult getWeeklyComparison(
            @ToolParam(description = "今週の開始日 (yyyy-MM-dd)") String thisWeekStart) {
        logToolCall("getWeeklyComparison", thisWeekStart);
        LocalDate ws = LocalDate.parse(thisWeekStart);
        LocalDate prevWs = ws.minusWeeks(1);
        Map<String, Object> thisWeek = fetchSalesMap("/api/v1/admin/orders/analytics/summary", Map.of("days", 7));
        Map<String, Object> prevWeek = fetchSalesMap("/api/v1/admin/orders/analytics/summary", Map.of("days", 14));
        long thisRevenue = toLong(thisWeek, "totalRevenue");
        long thisOrders = toLong(thisWeek, "totalOrders");
        long fourteenDayRevenue = toLong(prevWeek, "totalRevenue");
        long fourteenDayOrders = toLong(prevWeek, "totalOrders");
        long prevRevenue = Math.max(0, fourteenDayRevenue - thisRevenue);
        long prevOrders = Math.max(0, fourteenDayOrders - thisOrders);
        DataAvailability availability = thisWeek.isEmpty()
                ? DataAvailability.unavailable(List.of("sales-service"))
                : DataAvailability.available();
        return new WeeklyComparisonResult(ws, prevWs, thisRevenue, prevRevenue,
                ratio(thisRevenue, prevRevenue), thisOrders, prevOrders, ratio(thisOrders, prevOrders),
                extractTopChanges(thisWeek), availability);
    }

    @SuppressWarnings("unused")
    private WeeklyComparisonResult fallbackWeeklyComparison(String thisWeekStart, Throwable t) {
        LocalDate ws = LocalDate.parse(thisWeekStart);
        return new WeeklyComparisonResult(ws, ws.minusWeeks(1), 0, 0, 0, 0, 0, 0, List.of(),
                DataAvailability.unavailable(List.of("sales-service")));
    }

    @Tool(description = "過去 N か月の月次売上推移を取得する。")
    @CircuitBreaker(name = "salesService", fallbackMethod = "fallbackSeasonalHistorical")
    @Retry(name = "serviceCall")
    public SeasonalHistoricalResult getSeasonalHistorical(
            @ToolParam(description = "月数 (1-36)") @Min(1) @Max(36) int months) {
        logToolCall("getSeasonalHistorical", months);
        Map<String, Object> data = fetchSalesMap("/api/v1/admin/orders/analytics/monthly-revenue",
                Map.of("months", months));
        List<SeasonalHistoricalResult.MonthlyEntry> monthly = extractMonthlyRevenue(data);
        return new SeasonalHistoricalResult(months, monthly);
    }

    @SuppressWarnings("unused")
    private SeasonalHistoricalResult fallbackSeasonalHistorical(int months, Throwable t) {
        return new SeasonalHistoricalResult(months, List.of());
    }

    private static final Map<String, String> REGION_LOCATION_MAP = Map.of(
            "kanto", "Tokyo, Japan",
            "hokkaido", "Niseko, Hokkaido, Japan",
            "tohoku", "Zao, Yamagata, Japan",
            "chubu", "Hakuba, Nagano, Japan",
            "niigata", "Naeba, Niigata, Japan",
            "kansai", "Osaka, Japan"
    );

    @Tool(description = "指定地域の気象予報を取得する。失敗時は null を返す。")
    @CircuitBreaker(name = "weatherService", fallbackMethod = "fallbackWeatherForecast")
    @Retry(name = "serviceCall")
    public WeatherForecastResult getWeatherForecast(
            @ToolParam(description = "地域コード") String region,
            @ToolParam(description = "先週数 (1-8)") @Min(1) @Max(8) int weeksAhead) {
        logToolCall("getWeatherForecast", region, weeksAhead);
        String location = REGION_LOCATION_MAP.getOrDefault(
                region != null ? region.toLowerCase() : "", "Tokyo, Japan");
        int days = Math.min(weeksAhead * 7, 16);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = weatherWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/agents/weather/forecast")
                        .queryParam("location", location)
                        .queryParam("days", days)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(10));

        List<WeatherForecastResult.WeekForecast> weeks = aggregateToWeekly(response, weeksAhead);
        return new WeatherForecastResult(region, weeksAhead, weeks);
    }

    @SuppressWarnings("unused")
    private WeatherForecastResult fallbackWeatherForecast(String region, int weeksAhead, Throwable t) {
        log.warn("Fallback getWeatherForecast: {}", t.getMessage());
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<WeatherForecastResult.WeekForecast> aggregateToWeekly(Map<String, Object> response, int weeksAhead) {
        if (response == null) return List.of();
        Object raw = response.get("dailyForecasts");
        if (!(raw instanceof List<?> list) || list.isEmpty()) return List.of();

        List<WeatherForecastResult.WeekForecast> weeks = new ArrayList<>();
        int dayIndex = 0;
        for (int w = 0; w < weeksAhead && dayIndex < list.size(); w++) {
            double totalTemp = 0;
            double totalSnow = 0;
            int count = 0;
            String weekStart = null;
            String condition = "晴れ";
            double maxSnow = 0;

            for (int d = 0; d < 7 && dayIndex < list.size(); d++, dayIndex++) {
                Map<String, Object> day = (Map<String, Object>) list.get(dayIndex);
                if (weekStart == null) {
                    weekStart = Objects.toString(day.get("date"), "");
                }
                double maxTemp = toDouble(day, "maxTempCelsius");
                double minTemp = toDouble(day, "minTempCelsius");
                totalTemp += (maxTemp + minTemp) / 2.0;
                double snowfall = toDouble(day, "snowfallCm");
                totalSnow += snowfall;
                if (snowfall > maxSnow) maxSnow = snowfall;
                count++;
            }
            double avgTemp = count > 0 ? totalTemp / count : 0;
            if (totalSnow > 5) condition = "雪";
            else if (totalSnow > 0) condition = "一部降雪";
            else if (avgTemp > 10) condition = "晴れ";
            else condition = "曇り";

            weeks.add(new WeatherForecastResult.WeekForecast(
                    weekStart,
                    Math.round(avgTemp * 10.0) / 10.0,
                    Math.round(totalSnow * 10.0) / 10.0,
                    condition
            ));
        }
        return weeks;
    }

    private static double toDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return 0;
    }

    // ── P3 Tools (F4/F5 用 4 種) ─────────────────────────────

    @Tool(description = "滞留在庫（デッドストック）一覧を取得する。mv_sku_velocity ビューから判定。")
    @CircuitBreaker(name = "salesService", fallbackMethod = "fallbackDeadStock")
    @Retry(name = "serviceCall")
    public DeadStockResult getDeadStock(
            @ToolParam(description = "深刻度フィルタ: critical/warning/all") String severity,
            @ToolParam(description = "カテゴリ ID（任意）") @Nullable String category) {
        logToolCall("getDeadStock", severity, category);
        DeadStockResponse response = deadStockService.getDeadStock();
        List<DeadStockResult.DeadStockEntry> items = response.items().stream()
            .filter(item -> category == null || category.isBlank()
                || Objects.equals(category, Objects.toString(item.categoryId(), "")))
            .filter(item -> severityMatches(severity, item.severity()))
            .map(this::toToolDeadStockEntry)
                .toList();
        return new DeadStockResult(severity, category, items, response.availability());
    }

    @SuppressWarnings("unused")
    private DeadStockResult fallbackDeadStock(String severity, String category, Throwable t) {
        return new DeadStockResult(severity, category, List.of(),
                DataAvailability.unavailable(List.of("sales-service", "inventory-service")));
    }

    @Tool(description = "指定 SKU の販売速度（30 日/90 日）を取得する。")
    @CircuitBreaker(name = "salesService", fallbackMethod = "fallbackInventoryVelocity")
    @Retry(name = "serviceCall")
    public InventoryVelocityResult getInventoryVelocity(
            @ToolParam(description = "商品 SKU") String sku) {
        logToolCall("getInventoryVelocity", sku);
        List<Map<String, Object>> velocity = fetchSalesList("/api/v1/admin/orders/analytics/sku-velocity");
        return velocity.stream()
                .filter(row -> sku.equals(Objects.toString(row.get("sku"), "")))
                .findFirst()
                .map(row -> new InventoryVelocityResult(
                        sku,
                        toLong(row, "sales30"),
                        toLong(row, "sales90"),
                        toLong(row, "units30"),
                        toLong(row, "units90"),
                        toLocalDate(row.get("lastSoldAt")),
                        toDouble(row, "avgPrice"),
                        DataAvailability.available()))
                .orElseGet(() -> new InventoryVelocityResult(
                        sku, 0, 0, 0, 0, null, 0,
                        velocity.isEmpty() ? DataAvailability.unavailable(List.of("sales-service")) : DataAvailability.available()));
    }

    @SuppressWarnings("unused")
    private InventoryVelocityResult fallbackInventoryVelocity(String sku, Throwable t) {
        return new InventoryVelocityResult(sku, 0, 0, 0, 0, null, 0,
                DataAvailability.unavailable(List.of("sales-service")));
    }

    @Tool(description = "ゼロヒット（検索されるが商品がない）キーワード一覧を取得する。")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackZeroHit")
    @Retry(name = "serviceCall")
    public ZeroHitResult getZeroHitOpportunities(
            @ToolParam(description = "集計日数") @Min(1) @Max(365) int days,
            @ToolParam(description = "取得件数上限") @Min(1) @Max(100) int limit) {
        logToolCall("getZeroHitOpportunities", days, limit);
        ZeroHitOpportunityResponse response = zeroHitOpportunityService.getOpportunities(days, 1, null, limit);
        List<ZeroHitResult.ZeroHitEntry> entries = response.opportunities().stream()
            .map(this::toToolZeroHitEntry)
                .toList();
        return new ZeroHitResult(response.periodDays(), limit, entries, response.availability());
    }

    @SuppressWarnings("unused")
    private ZeroHitResult fallbackZeroHit(int days, int limit, Throwable t) {
        return new ZeroHitResult(days, limit, List.of(), DataAvailability.unavailable(List.of("inventory-service")));
    }

    @Tool(description = "商品カタログをキーワードで検索する。")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackProductSearch")
    @Retry(name = "serviceCall")
    public ProductSearchResult searchProductCatalog(
            @ToolParam(description = "検索キーワード") String keyword) {
        logToolCall("searchProductCatalog", keyword);
        Map<String, Object> data = fetchInventoryMap("/api/v1/products/search",
                Map.of("q", keyword, "page", 0, "size", 20));
        List<ProductSearchResult.ProductHit> hits = extractProductHits(data);
        int totalHits = toIntNested(data, "page", "totalElements", hits.size());
        return new ProductSearchResult(keyword, totalHits, hits,
                data.isEmpty() ? DataAvailability.unavailable(List.of("inventory-service")) : DataAvailability.available());
    }

    @SuppressWarnings("unused")
    private ProductSearchResult fallbackProductSearch(String keyword, Throwable t) {
        return new ProductSearchResult(keyword, 0, List.of(), DataAvailability.unavailable(List.of("inventory-service")));
    }

    // ── Internal Helpers ─────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<TopProductsResult.ProductEntry> extractTopProducts(Map<String, Object> data) {
        Object raw = data.get("topProducts");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        long totalRevenue = list.stream()
                .filter(Map.class::isInstance)
                .mapToLong(e -> toLong((Map<String, Object>) e, "revenue"))
                .sum();
        return list.stream()
                .filter(Map.class::isInstance)
                .map(e -> {
                    Map<String, Object> m = (Map<String, Object>) e;
                    String sku = Objects.toString(m.get("productId"), "");
                    String name = Objects.toString(m.get("productName"), "");
                    long sales = toLong(m, "quantity");
                    long revenue = toLong(m, "revenue");
                    double share = totalRevenue > 0 ? (double) revenue / totalRevenue * 100 : 0;
                    return new TopProductsResult.ProductEntry(sku, name, sales, revenue, share);
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<CategoryShareResult.CategoryEntry> extractCategoryShare(Map<String, Object> data) {
        Object raw = data.get("categoryRevenue");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        long totalRevenue = list.stream()
                .filter(Map.class::isInstance)
                .mapToLong(e -> toLong((Map<String, Object>) e, "revenue"))
                .sum();
        return list.stream()
                .filter(Map.class::isInstance)
                .map(e -> {
                    Map<String, Object> m = (Map<String, Object>) e;
                    String categoryId = Objects.toString(m.get("categoryId"), "");
                    String categoryName = Objects.toString(m.get("categoryName"), "");
                    long revenue = toLong(m, "revenue");
                    double share = totalRevenue > 0 ? (double) revenue / totalRevenue * 100 : 0;
                    return new CategoryShareResult.CategoryEntry(categoryId, categoryName, revenue, share);
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<SeasonalHistoricalResult.MonthlyEntry> extractMonthlyRevenue(Map<String, Object> data) {
        Object raw = data.get("monthly");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(e -> {
                    Map<String, Object> m = (Map<String, Object>) e;
                    String yearMonth = Objects.toString(m.get("yearMonth"), "");
                    long revenue = toLong(m, "revenue");
                    long orders = toLong(m, "orders");
                    return new SeasonalHistoricalResult.MonthlyEntry(yearMonth, revenue, orders, 0);
                })
                .toList();
    }

    private List<Map<String, Object>> fetchSalesList(String path) {
        try {
            return salesWebClient.get()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block(Duration.ofSeconds(8));
        } catch (Exception e) {
            log.warn("fetchSalesList failed for {}: {}", path, e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> fetchInventoryList(String path) {
        return fetchInventoryList(path, Map.of());
    }

    private List<Map<String, Object>> fetchInventoryList(String path, Map<String, Object> params) {
        try {
            return inventoryWebClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path(path);
                        params.forEach((k, v) -> {
                            if (v != null && !v.toString().isEmpty()) {
                                b.queryParam(k, v);
                            }
                        });
                        return b.build();
                    })
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block(Duration.ofSeconds(8));
        } catch (Exception e) {
            log.warn("fetchInventoryList failed for {}: {}", path, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<ProductSearchResult.ProductHit> extractProductHits(Map<String, Object> data) {
        Object raw = data.get("content");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(e -> {
                    Map<String, Object> row = (Map<String, Object>) e;
                    return new ProductSearchResult.ProductHit(
                            Objects.toString(row.get("sku"), ""),
                            Objects.toString(row.get("name"), ""),
                            Objects.toString(row.get("categoryId"), ""),
                            toDouble(row, "salePrice") > 0 ? toDouble(row, "salePrice") : toDouble(row, "regularPrice"));
                })
                .toList();
    }

    private DeadStockResult.DeadStockEntry toToolDeadStockEntry(DeadStockResponse.DeadStockItem item) {
        return new DeadStockResult.DeadStockEntry(
                item.sku(),
                item.name(),
                item.stock(),
                item.sales30(),
                item.sales90(),
                null,
                0,
                item.severity().name(),
                item.daysOfSupply(),
                item.suggestedDiscountPct(),
                item.categoryId());
    }

    private ZeroHitResult.ZeroHitEntry toToolZeroHitEntry(ZeroHitOpportunityResponse.Opportunity opportunity) {
        return new ZeroHitResult.ZeroHitEntry(
                opportunity.normalizedKeyword(),
                opportunity.searchCount(),
                opportunity.estimatedLossJpy(),
                opportunity.estimatedCategory(),
                suggestedAction(opportunity),
                opportunity.normalizedKeyword());
    }

    private static boolean severityMatches(String requestedSeverity, DeadStockResponse.Severity itemSeverity) {
        String normalized = Objects.toString(requestedSeverity, "all").trim();
        if (normalized.isBlank() || "all".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("warning".equalsIgnoreCase(normalized)) {
            return itemSeverity == DeadStockResponse.Severity.HIGH;
        }
        if ("healthy".equalsIgnoreCase(normalized)) {
            return itemSeverity == DeadStockResponse.Severity.MEDIUM;
        }
        return itemSeverity.name().equalsIgnoreCase(normalized);
    }

    private static String suggestedAction(ZeroHitOpportunityResponse.Opportunity opportunity) {
        if (opportunity.narrative() != null && !opportunity.narrative().isBlank()) {
            return opportunity.narrative();
        }
        if (opportunity.priority() != null && !opportunity.priority().isBlank()) {
            return "Review assortment priority: " + opportunity.priority();
        }
        return "Review assortment for " + opportunity.normalizedKeyword();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractTopChanges(Map<String, Object> data) {
        Object raw = data.get("topProducts");
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream()
                .filter(Map.class::isInstance)
                .limit(5)
                .map(e -> (Map<String, Object>) e)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchSalesMap(String path, Map<String, Object> params) {
        try {
            return salesWebClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path(path);
                        params.forEach((k, v) -> {
                            if (v != null && !v.toString().isEmpty()) {
                                b.queryParam(k, v);
                            }
                        });
                        return b.build();
                    })
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(8));
        } catch (Exception e) {
            log.warn("fetchSalesMap failed for {}: {}", path, e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchInventoryMap(String path, Map<String, Object> params) {
        try {
            return inventoryWebClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path(path);
                        params.forEach((k, v) -> {
                            if (v != null && !v.toString().isEmpty()) {
                                b.queryParam(k, v);
                            }
                        });
                        return b.build();
                    })
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(8));
        } catch (Exception e) {
            log.warn("fetchInventoryMap failed for {}: {}", path, e.getMessage());
            return Map.of();
        }
    }

    private static long toLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.longValue();
        return 0;
    }

    private static int toInt(Map<String, Object> map, String key, int defaultValue) {
        Object val = map == null ? null : map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { return defaultValue; }
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static int toIntNested(Map<String, Object> map, String objectKey, String key, int defaultValue) {
        Object nested = map.get(objectKey);
        if (nested instanceof Map<?, ?> nestedMap) {
            return toInt((Map<String, Object>) nestedMap, key, defaultValue);
        }
        return defaultValue;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        if (value instanceof java.time.LocalDateTime dateTime) return dateTime.toLocalDate();
        if (value instanceof Instant instant) return instant.atZone(java.time.ZoneId.of("Asia/Tokyo")).toLocalDate();
        try {
            return LocalDate.parse(value.toString().substring(0, 10));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static double ratio(long current, long previous) {
        if (previous == 0) return current > 0 ? 1.0 : 0.0;
        return (double) (current - previous) / previous;
    }

    private static DataAvailability availabilityFrom(String source, Collection<?> rows) {
        return rows == null || rows.isEmpty()
                ? DataAvailability.unavailable(List.of(source))
                : DataAvailability.available();
    }

    private void logToolCall(String toolName, Object... params) {
        String paramsHash = Integer.toHexString(Arrays.hashCode(params));
        log.info("[Tool] name={} paramsHash={} traceId={}", toolName, paramsHash,
                org.slf4j.MDC.get("traceId"));
    }
}
