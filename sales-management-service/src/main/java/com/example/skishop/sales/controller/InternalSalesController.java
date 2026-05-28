package com.example.skishop.sales.controller;

import com.example.skishop.sales.client.InventoryClient;
import com.example.skishop.sales.repository.OrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Multi-Agent Worker (SalesManagementClient) 向け内部 API。
 * 動的価格決定エージェントが過去 N 日間の販売数を要求する。
 */
@RestController
@RequestMapping("/api/v1/internal/sales")
public class InternalSalesController {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;

    public InternalSalesController(OrderRepository orderRepository, InventoryClient inventoryClient) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
    }

    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/{productId}/count")
    public ResponseEntity<Map<String, Object>> getSalesCount(
            @PathVariable String productId,
            @RequestParam(name = "days", defaultValue = "30") int days) {
        // TODO: OrderRepository.countByProductIdAndCreatedAtAfter を呼び出す。現状スタブ。
        return ResponseEntity.ok(Map.of(
                "productId", productId,
                "days", days,
                "count", 0));
    }

    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/users/{userId}/summary")
    public ResponseEntity<UserSalesSummary> getUserSalesSummary(@PathVariable UUID userId) {
        Object[] summary = orderRepository.customerPurchaseSummary(userId).stream()
                .findFirst()
                .orElse(new Object[]{0L, BigDecimal.ZERO});
        List<String> topProductIds = orderRepository.topPurchasedProductIdsByCustomer(userId, 10).stream()
                .map(row -> String.valueOf(row[0]))
                .toList();
        var productMap = inventoryClient.fetchProducts(topProductIds);
        var categoryNames = inventoryClient.fetchCategoryNames();
        List<String> purchasedCategories = topProductIds.stream()
                .map(productMap::get)
                .filter(java.util.Objects::nonNull)
                .map(product -> categoryNames.getOrDefault(product.categoryId(), product.categoryId()))
                .filter(category -> category != null && !category.isBlank())
                .distinct()
                .limit(5)
                .toList();
        if (purchasedCategories.isEmpty()) {
            purchasedCategories = orderRepository.topPurchasedLabelsByCustomer(userId, 5).stream()
                    .map(row -> String.valueOf(row[0]))
                    .toList();
        }

        long orderCount = ((Number) summary[0]).longValue();
        BigDecimal totalAmount = summary[1] instanceof BigDecimal value
                ? value
                : new BigDecimal(String.valueOf(summary[1]));
        return ResponseEntity.ok(new UserSalesSummary(userId, orderCount, totalAmount, purchasedCategories));
    }

    public record UserSalesSummary(
            UUID userId,
            long orderCount,
            BigDecimal totalPurchasedAmount,
            List<String> purchasedCategories
    ) {}
}
