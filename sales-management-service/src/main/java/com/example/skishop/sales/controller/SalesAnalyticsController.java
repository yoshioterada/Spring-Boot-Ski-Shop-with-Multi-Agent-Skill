package com.example.skishop.sales.controller;

import com.example.skishop.sales.dto.SalesAnalyticsDto.SalesAnalyticsSummary;
import com.example.skishop.sales.dto.SalesAnalyticsDto.SalesTrendsResponse;
import com.example.skishop.sales.service.SalesAnalyticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 売上分析エンドポイント。管理画面ダッシュボードからのみ利用される。
 * <p>
 * パスは {@code /api/v1/admin/orders/analytics/**} 配下に配置することで、
 * api-gateway-service の {@code sales-management-service} ルート
 * ({@code /api/v1/admin/orders/**}) を変更せずに公開できる。
 */
@RestController
@RequestMapping("/api/v1/admin/orders/analytics")
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
public class SalesAnalyticsController {

    private final SalesAnalyticsService analyticsService;
    private final JdbcTemplate jdbcTemplate;

    public SalesAnalyticsController(SalesAnalyticsService analyticsService, JdbcTemplate jdbcTemplate) {
        this.analyticsService = analyticsService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/summary")
    public ResponseEntity<SalesAnalyticsSummary> summary(
            @RequestParam(name = "days", defaultValue = "14") @Min(1) @Max(365) int days,
            @RequestParam(name = "topProductLimit", defaultValue = "10") @Min(1) @Max(50) int topProductLimit) {
        return ResponseEntity.ok(analyticsService.getSummary(days, topProductLimit));
    }

    @GetMapping("/trends")
    public ResponseEntity<SalesTrendsResponse> trends(
            @RequestParam(name = "days", defaultValue = "30") @Min(1) @Max(365) int days) {
        return ResponseEntity.ok(analyticsService.getTrends(days));
    }

    /**
     * F4 滞留在庫レーダー用: mv_sku_velocity の全行を返す (ADR D-F4-06)。
     * ai-support-service の DeadStockService から内部 API キーで呼び出される。
     */
    @GetMapping("/sku-velocity")
    public ResponseEntity<List<Map<String, Object>>> skuVelocity() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT sku, sales_30 AS \"sales30\", sales_90 AS \"sales90\", "
                + "units_30 AS \"units30\", units_90 AS \"units90\", "
                + "last_sold_at AS \"lastSoldAt\", avg_price AS \"avgPrice\" "
                + "FROM mv_sku_velocity ORDER BY sales_30 ASC, sales_90 ASC");
        return ResponseEntity.ok(rows);
    }

    /**
     * 月次売上推移 (AI Chat ツール用)。指定月数分の月別 revenue/orders を返す。
     */
    @GetMapping("/monthly-revenue")
    public ResponseEntity<Map<String, Object>> monthlyRevenue(
            @RequestParam(name = "months", defaultValue = "6") @Min(1) @Max(36) int months) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT to_char(date_trunc('month', o.created_at), 'YYYY-MM') AS \"yearMonth\", "
                + "COALESCE(SUM(o.total_amount), 0) AS \"revenue\", "
                + "COUNT(o.id) AS \"orders\" "
                + "FROM orders o "
                + "WHERE o.created_at >= date_trunc('month', CURRENT_TIMESTAMP) - (CAST(? AS integer) * INTERVAL '1 month') "
                + "AND o.status != 'CANCELLED' "
                + "GROUP BY date_trunc('month', o.created_at) "
                + "ORDER BY date_trunc('month', o.created_at)",
                months);
        return ResponseEntity.ok(Map.of("months", months, "monthly", rows));
    }

    /**
     * F2 季節予測 Step 1 用: seasonal_weights マスタの全行を返す (D-F2-02)。
     */
    @GetMapping("/seasonal-weights")
    public ResponseEntity<List<Map<String, Object>>> seasonalWeights() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT category_id AS \"categoryId\", month, weight "
                + "FROM seasonal_weights ORDER BY category_id, month");
        return ResponseEntity.ok(rows);
    }

    /**
     * F2 季節予測 Step 1 用: カテゴリ別月次販売数量 (過去 N 年分)。
     * SKU プレフィックスからカテゴリを判定する。
     */
    @GetMapping("/monthly-sales")
    public ResponseEntity<Map<String, Map<String, Long>>> monthlySales(
            @RequestParam(name = "years", defaultValue = "2") @Min(1) @Max(5) int years,
            @RequestParam(name = "categories", defaultValue = "") String categories) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT "
                + "  CASE "
                + "    WHEN oi.product_sku LIKE 'SKI%' THEN 'cat-ski' "
                + "    WHEN oi.product_sku LIKE 'BTS%' THEN 'cat-boots' "
                + "    WHEN oi.product_sku LIKE 'WER%' THEN 'cat-wear' "
                + "    WHEN oi.product_sku LIKE 'GLV%' THEN 'cat-gloves' "
                + "    WHEN oi.product_sku LIKE 'GGL%' THEN 'cat-goggles' "
                + "    WHEN oi.product_sku LIKE 'HLM%' THEN 'cat-helmets' "
                + "    WHEN oi.product_sku LIKE 'POL%' THEN 'cat-poles' "
                + "  END AS category_id, "
                + "  EXTRACT(MONTH FROM o.created_at)::int AS month, "
                + "  SUM(oi.quantity) AS units "
                + "FROM order_items oi JOIN orders o ON oi.order_id = o.id "
                + "WHERE o.status != 'CANCELLED' "
                + "  AND o.created_at >= CURRENT_TIMESTAMP - (CAST(? AS integer) * INTERVAL '1 year') "
                + "GROUP BY category_id, month "
                + "ORDER BY category_id, month",
                years);

        Map<String, Map<String, Long>> result = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String catId = (String) row.get("category_id");
            if (catId == null) continue;
            String month = String.valueOf(((Number) row.get("month")).intValue());
            long units = ((Number) row.get("units")).longValue();
            result.computeIfAbsent(catId, k -> new java.util.LinkedHashMap<>()).put(month, units);
        }

        // カテゴリフィルタ適用
        if (categories != null && !categories.isBlank()) {
            var allowed = java.util.Set.of(categories.split(","));
            result.keySet().retainAll(allowed);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * F2 季節予測 Step 1 用: 直近 N 日の YoY 成長率。
     * growthRate = 1.0 で成長なし、1.1 で +10% 成長。
     */
    @GetMapping("/yoy-growth")
    public ResponseEntity<Map<String, Object>> yoyGrowth(
            @RequestParam(name = "days", defaultValue = "90") @Min(1) @Max(365) int days) {
        // 直近 N 日の売上合計
        var currentRow = jdbcTemplate.queryForMap(
                "SELECT COALESCE(SUM(total_amount), 0) AS revenue "
                + "FROM orders WHERE status != 'CANCELLED' "
                + "AND created_at >= CURRENT_TIMESTAMP - (CAST(? AS integer) * INTERVAL '1 day')",
                days);
        // 前年同期
        var prevRow = jdbcTemplate.queryForMap(
                "SELECT COALESCE(SUM(total_amount), 0) AS revenue "
                + "FROM orders WHERE status != 'CANCELLED' "
                + "AND created_at >= CURRENT_TIMESTAMP - INTERVAL '1 year' - (CAST(? AS integer) * INTERVAL '1 day') "
                + "AND created_at < CURRENT_TIMESTAMP - INTERVAL '1 year'",
                days);

        double current = ((Number) currentRow.get("revenue")).doubleValue();
        double prev = ((Number) prevRow.get("revenue")).doubleValue();
        double growthRate = prev > 0 ? current / prev : 1.0;

        return ResponseEntity.ok(Map.of(
                "days", days,
                "currentRevenue", current,
                "previousRevenue", prev,
                "growthRate", growthRate));
    }
}
