package com.example.skishop.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO 群: 売上分析エンドポイントの応答型を集約。
 * - SalesAnalyticsSummary: 売上分析タブのサマリ。
 * - SalesTrendsResponse:   トレンド分析タブのデータ。
 */
public final class SalesAnalyticsDto {

    private SalesAnalyticsDto() {}

    public record SalesAnalyticsSummary(
            int days,
            BigDecimal totalRevenue,
            long totalOrders,
            BigDecimal averageOrderValue,
            List<DailyRevenuePoint> dailyRevenue,
            List<TopProduct> topProducts,
            List<CategoryRevenue> categoryRevenue
    ) {}

    public record DailyRevenuePoint(
            LocalDate date,
            BigDecimal revenue,
            long orders
    ) {}

    public record TopProduct(
            String productId,
            String productName,
            long quantity,
            BigDecimal revenue
    ) {}

    public record CategoryRevenue(
            String categoryId,
            String categoryName,
            BigDecimal revenue,
            long orders
    ) {}

    public record SalesTrendsResponse(
            int days,
            List<TrendPoint> trends,
            List<CategoryRevenue> categories
    ) {}

    public record TrendPoint(
            LocalDate date,
            BigDecimal revenue,
            long orders,
            long uniqueCustomers
    ) {}
}
