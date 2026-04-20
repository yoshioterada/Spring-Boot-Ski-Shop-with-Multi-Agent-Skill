package com.example.skishop.sales.service;

import com.example.skishop.sales.client.InventoryClient;
import com.example.skishop.sales.dto.SalesAnalyticsDto.CategoryRevenue;
import com.example.skishop.sales.dto.SalesAnalyticsDto.DailyRevenuePoint;
import com.example.skishop.sales.dto.SalesAnalyticsDto.SalesAnalyticsSummary;
import com.example.skishop.sales.dto.SalesAnalyticsDto.SalesTrendsResponse;
import com.example.skishop.sales.dto.SalesAnalyticsDto.TopProduct;
import com.example.skishop.sales.dto.SalesAnalyticsDto.TrendPoint;
import com.example.skishop.sales.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 売上分析サービス。
 * - 期間 (days) を入力に、orders/order_items を集計して管理画面ダッシュボード用 DTO を生成する。
 * - カテゴリ別売上は inventory-management-service から商品メタを取得して算出する。
 */
@Service
@Transactional(readOnly = true)
public class SalesAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(SalesAnalyticsService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final int DEFAULT_TOP_PRODUCT_LIMIT = 10;
    private static final int CATEGORY_LOOKUP_PRODUCT_CAP = 500;

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;

    public SalesAnalyticsService(OrderRepository orderRepository, InventoryClient inventoryClient) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
    }

    public SalesAnalyticsSummary getSummary(int days, int topProductLimit) {
        int safeDays = clampDays(days);
        int limit = topProductLimit <= 0 ? DEFAULT_TOP_PRODUCT_LIMIT : Math.min(topProductLimit, 50);
        Instant since = Instant.now().minus(safeDays, ChronoUnit.DAYS);

        // ----- 集計クエリ -----
        List<Object[]> totalsRows = orderRepository.totalRevenueSince(since);
        Object[] totals = totalsRows.isEmpty() ? null : totalsRows.get(0);
        BigDecimal totalRevenue = toBigDecimal(totals != null ? totals[0] : null);
        long totalOrders = toLong(totals != null ? totals[1] : null);

        List<DailyRevenuePoint> dailyRevenue = mapDailyRevenue(orderRepository.sumDailyRevenue(since), safeDays);
        List<TopProduct> topProducts = mapTopProducts(orderRepository.topProductsSince(since, limit));
        List<CategoryRevenue> categoryRevenue = aggregateCategoryRevenue(orderRepository.productRevenueSince(since));

        BigDecimal aov = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new SalesAnalyticsSummary(safeDays, totalRevenue, totalOrders, aov,
                dailyRevenue, topProducts, categoryRevenue);
    }

    public SalesTrendsResponse getTrends(int days) {
        int safeDays = clampDays(days);
        Instant since = Instant.now().minus(safeDays, ChronoUnit.DAYS);

        List<DailyRevenuePoint> daily = mapDailyRevenue(orderRepository.sumDailyRevenue(since), safeDays);
        Map<LocalDate, Long> uniqueByDate = mapDailyUniqueCustomers(orderRepository.countDailyUniqueCustomers(since));

        List<TrendPoint> trends = daily.stream()
                .map(p -> new TrendPoint(p.date(), p.revenue(), p.orders(),
                        uniqueByDate.getOrDefault(p.date(), 0L)))
                .toList();

        List<CategoryRevenue> categories = aggregateCategoryRevenue(orderRepository.productRevenueSince(since));
        return new SalesTrendsResponse(safeDays, trends, categories);
    }

    // ---------------------- helpers ----------------------

    private int clampDays(int days) {
        if (days <= 0) return 14;
        return Math.min(days, 365);
    }

    /**
     * 0 件の日付も含めて連続した時系列を生成する。
     */
    private List<DailyRevenuePoint> mapDailyRevenue(List<Object[]> rows, int days) {
        Map<LocalDate, DailyRevenuePoint> map = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate date = toLocalDate(row[0]);
            BigDecimal revenue = toBigDecimal(row[1]);
            long orders = toLong(row[2]);
            map.put(date, new DailyRevenuePoint(date, revenue, orders));
        }
        LocalDate today = LocalDate.now(ZONE);
        List<DailyRevenuePoint> result = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            result.add(map.getOrDefault(d, new DailyRevenuePoint(d, BigDecimal.ZERO, 0L)));
        }
        return result;
    }

    private Map<LocalDate, Long> mapDailyUniqueCustomers(List<Object[]> rows) {
        Map<LocalDate, Long> map = new HashMap<>(rows.size());
        for (Object[] row : rows) {
            map.put(toLocalDate(row[0]), toLong(row[1]));
        }
        return map;
    }

    private List<TopProduct> mapTopProducts(List<Object[]> rows) {
        List<TopProduct> list = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String productId = (String) row[0];
            String productName = (String) row[1];
            long quantity = toLong(row[2]);
            BigDecimal revenue = toBigDecimal(row[3]);
            list.add(new TopProduct(productId, productName, quantity, revenue));
        }
        return list;
    }

    /**
     * 商品単位の売上を inventory-service の categoryId に集約する。
     * - 件数が CATEGORY_LOOKUP_PRODUCT_CAP を超える場合は売上上位 N 件のみ正確に集計し、
     *   残りは "uncategorized" としてまとめる。
     */
    private List<CategoryRevenue> aggregateCategoryRevenue(List<Object[]> productRows) {
        if (productRows.isEmpty()) {
            return List.of();
        }
        // 上位 CAP 件のみ inventory-service に問い合わせ
        List<Object[]> sorted = productRows.stream()
                .sorted((a, b) -> toBigDecimal(b[2]).compareTo(toBigDecimal(a[2])))
                .toList();
        List<Object[]> head = sorted.size() > CATEGORY_LOOKUP_PRODUCT_CAP
                ? sorted.subList(0, CATEGORY_LOOKUP_PRODUCT_CAP)
                : sorted;
        List<Object[]> tail = sorted.size() > CATEGORY_LOOKUP_PRODUCT_CAP
                ? sorted.subList(CATEGORY_LOOKUP_PRODUCT_CAP, sorted.size())
                : List.of();

        List<String> productIds = head.stream().map(r -> (String) r[0]).collect(Collectors.toList());
        Map<String, InventoryClient.ProductSummary> products = inventoryClient.fetchProducts(productIds);
        Map<String, String> categoryNames = products.isEmpty() ? Map.of() : inventoryClient.fetchCategoryNames();

        // categoryId 毎に集計
        Map<String, BigDecimal> revenueByCategory = new LinkedHashMap<>();
        Map<String, Long> ordersByCategory = new LinkedHashMap<>();
        for (Object[] row : head) {
            String productId = (String) row[0];
            long quantity = toLong(row[1]);
            BigDecimal revenue = toBigDecimal(row[2]);
            InventoryClient.ProductSummary summary = products.get(productId);
            String categoryId = (summary != null && summary.categoryId() != null)
                    ? summary.categoryId() : "uncategorized";
            revenueByCategory.merge(categoryId, revenue, BigDecimal::add);
            ordersByCategory.merge(categoryId, quantity, Long::sum);
        }
        if (!tail.isEmpty()) {
            BigDecimal tailRevenue = tail.stream()
                    .map(r -> toBigDecimal(r[2]))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long tailOrders = tail.stream().mapToLong(r -> toLong(r[1])).sum();
            revenueByCategory.merge("uncategorized", tailRevenue, BigDecimal::add);
            ordersByCategory.merge("uncategorized", tailOrders, Long::sum);
            log.debug("Truncated category aggregation: {} extra products grouped into 'uncategorized'", tail.size());
        }

        Set<String> categoryIds = revenueByCategory.keySet();
        return categoryIds.stream()
                .map(cid -> new CategoryRevenue(
                        cid,
                        "uncategorized".equals(cid) ? "未分類" : categoryNames.getOrDefault(cid, cid),
                        revenueByCategory.get(cid),
                        ordersByCategory.getOrDefault(cid, 0L)))
                .sorted((a, b) -> b.revenue().compareTo(a.revenue()))
                .toList();
    }

    private static LocalDate toLocalDate(Object o) {
        if (o instanceof Date d) {
            return d.toLocalDate();
        }
        if (o instanceof LocalDate ld) {
            return ld;
        }
        if (o instanceof java.time.LocalDateTime ldt) {
            return ldt.toLocalDate();
        }
        if (o instanceof Instant i) {
            return i.atZone(ZONE).toLocalDate();
        }
        throw new IllegalArgumentException("Unsupported date type: " + (o == null ? "null" : o.getClass()));
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(o.toString());
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }
}
