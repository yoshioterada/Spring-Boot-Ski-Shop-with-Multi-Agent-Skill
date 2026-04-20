package com.example.skishop.inventory.controller;

import com.example.skishop.inventory.model.Product;
import com.example.skishop.inventory.repository.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Multi-Agent Worker (InventoryClient / InventoryManagementClient / ProductCatalogClient) 向け内部 API。
 * MongoDB の Product コレクションから候補抽出を行う。
 */
@RestController
@RequestMapping("/api/v1/internal")
public class InternalInventoryController {

    private final ProductRepository productRepository;

    public InternalInventoryController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** equipment-matching-agent.InventoryClient.searchBySkillAndCategory */
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/products/search")
    public ResponseEntity<List<Map<String, Object>>> search(
            @RequestParam String category,
            @RequestParam(required = false) String skillLevel,
            @RequestParam(required = false) Integer maxPrice) {
        List<Map<String, Object>> result = productRepository.findAll().stream()
                .filter(p -> category == null || category.isBlank()
                        || category.equalsIgnoreCase(p.getCategoryId()))
                .filter(p -> maxPrice == null
                        || (p.getRegularPrice() != null
                                && p.getRegularPrice().compareTo(BigDecimal.valueOf(maxPrice)) <= 0))
                .filter(p -> matchesSkillLevel(p.getTags(), skillLevel))
                .limit(20)
                .map(InternalInventoryController::toProductCandidate)
                .toList();
        return ResponseEntity.ok(result);
    }

    /** dynamic-pricing-agent.ProductCatalogClient.getBasePrice */
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/products/{productId}")
    public ResponseEntity<Map<String, Object>> getProduct(@PathVariable String productId) {
        return productRepository.findById(productId)
                .map(p -> ResponseEntity.ok(Map.<String, Object>of(
                        "productId", p.getId(),
                        "basePrice", p.getRegularPrice() == null ? BigDecimal.ZERO : p.getRegularPrice(),
                        "salePrice", p.getSalePrice() == null ? BigDecimal.ZERO : p.getSalePrice(),
                        "name", p.getName() == null ? "" : p.getName())))
                .orElse(ResponseEntity.ok(Map.of("productId", productId, "basePrice", BigDecimal.ZERO)));
    }

    /** dynamic-pricing-agent.ProductCatalogClient.getStockCount */
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/products/{productId}/stock")
    public ResponseEntity<Map<String, Object>> getStock(@PathVariable String productId) {
        int qty = productRepository.findById(productId).map(Product::getStockQuantity).orElse(0);
        return ResponseEntity.ok(Map.of("productId", productId, "stockQuantity", qty));
    }

    /** inventory-monitoring-agent.InventoryManagementClient.getInventoryStatus */
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/inventory/{productId}")
    public ResponseEntity<Map<String, Object>> getInventoryStatus(@PathVariable String productId) {
        return productRepository.findById(productId)
                .map(p -> {
                    int avail = Math.max(0, p.getStockQuantity() - p.getReservedQuantity());
                    String status = avail == 0 ? "OUT_OF_STOCK" : (avail < 5 ? "LOW_STOCK" : "AVAILABLE");
                    Map<String, Object> body = new HashMap<>();
                    body.put("productId", p.getId());
                    body.put("productName", p.getName());
                    body.put("stockQuantity", avail);
                    body.put("availabilityStatus", status);
                    body.put("isReservable", avail > 0);
                    body.put("estimatedRestockDate", null);
                    body.put("alternativeProductIds", List.of());
                    body.put("alert", null);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("productId", productId);
                    body.put("productName", "");
                    body.put("stockQuantity", 0);
                    body.put("availabilityStatus", "OUT_OF_STOCK");
                    body.put("isReservable", false);
                    body.put("estimatedRestockDate", null);
                    body.put("alternativeProductIds", List.of());
                    body.put("alert", null);
                    return ResponseEntity.ok(body);
                });
    }

    /** inventory-monitoring-agent.InventoryManagementClient.reserve */
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @PostMapping("/inventory/reserve")
    public ResponseEntity<Map<String, Object>> reserve(@RequestBody Map<String, Object> body) {
        String orderId = String.valueOf(body.getOrDefault("orderId", UUID.randomUUID().toString()));
        // 暫定: 全商品を予約成功扱い
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        List<String> reserved = items.stream().map(i -> String.valueOf(i.get("productId"))).toList();
        return ResponseEntity.ok(Map.of(
                "reservationId", UUID.randomUUID().toString(),
                "orderId", orderId,
                "isFullyReserved", true,
                "reservedProductIds", reserved,
                "failedProductIds", List.of(),
                "expiresAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(1800))));
    }

    /** inventory-monitoring-agent.InventoryManagementClient.getLowStockAlerts */
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/inventory/alerts")
    public ResponseEntity<List<Map<String, Object>>> getAlerts(
            @RequestParam(name = "threshold", defaultValue = "10") int threshold) {
        List<Map<String, Object>> result = productRepository.findAll().stream()
                .filter(p -> (p.getStockQuantity() - p.getReservedQuantity()) <= threshold)
                .limit(50)
                .map(p -> Map.<String, Object>of(
                        "alertId", UUID.randomUUID().toString(),
                        "productId", p.getId(),
                        "severity", (p.getStockQuantity() - p.getReservedQuantity()) <= 0 ? "CRITICAL" : "WARNING",
                        "message", "在庫が閾値以下",
                        "currentStock", Math.max(0, p.getStockQuantity() - p.getReservedQuantity()),
                        "threshold", threshold,
                        "generatedAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now())))
                .toList();
        return ResponseEntity.ok(result);
    }

    /** inventory-monitoring-agent.InventoryManagementClient.findAlternatives */
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/inventory/{productId}/alternatives")
    public ResponseEntity<List<String>> findAlternatives(@PathVariable String productId) {
        var origin = productRepository.findById(productId);
        if (origin.isEmpty()) return ResponseEntity.ok(List.of());
        String category = origin.get().getCategoryId();
        List<String> ids = productRepository.findAll().stream()
                .filter(p -> !p.getId().equals(productId))
                .filter(p -> category != null && category.equals(p.getCategoryId()))
                .filter(p -> p.getStockQuantity() - p.getReservedQuantity() > 0)
                .limit(5)
                .map(Product::getId)
                .toList();
        return ResponseEntity.ok(ids);
    }

    private static Map<String, Object> toProductCandidate(Product p) {
        Map<String, Object> m = new HashMap<>();
        m.put("productId", p.getId());
        m.put("productName", p.getName());
        m.put("category", p.getCategoryId());
        m.put("brand", p.getBrand());
        m.put("basePrice", p.getRegularPrice() == null ? BigDecimal.ZERO : p.getRegularPrice());
        m.put("isAvailable", (p.getStockQuantity() - p.getReservedQuantity()) > 0);
        m.put("stockQuantity", Math.max(0, p.getStockQuantity() - p.getReservedQuantity()));
        m.put("skillLevelSuitability", deriveSkillLevelSuitability(p.getTags()));
        m.put("weatherSuitability", deriveWeatherSuitability(p.getTags()));
        m.put("attributes", p.getAttributes() == null ? Map.of() : p.getAttributes());
        m.put("tags", p.getTags() == null ? List.of() : p.getTags());
        return m;
    }

    /** リクエスト skillLevel と商品 tags の許容関係。 */
    private static final Map<String, Set<String>> SKILL_TAG_MATCH = Map.of(
            "BEGINNER",     Set.of("初心者", "初中級者"),
            "INTERMEDIATE", Set.of("中級者", "中上級者", "初中級者"),
            "ADVANCED",     Set.of("上級者", "中上級者"),
            "EXPERT",       Set.of("上級者"));

    /** 商品 tag から canonical な skill 区分を推定する。 */
    static String deriveSkillLevelSuitability(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "ALL";
        boolean hasExpert = tags.contains("上級者");
        boolean hasAdv = tags.contains("中上級者");
        boolean hasInter = tags.contains("中級者");
        boolean hasBegInter = tags.contains("初中級者");
        boolean hasBeginner = tags.contains("初心者");
        // 排他的に最も特徴的な区分を 1 つ選ぶ
        if (hasExpert && !hasAdv && !hasInter && !hasBegInter && !hasBeginner) return "EXPERT";
        if (hasAdv) return "ADVANCED";
        if (hasInter || hasBegInter) return "INTERMEDIATE";
        if (hasBeginner) return "BEGINNER";
        return "ALL";
    }

    /** 商品 tag から地形・雪質適合度を推定する。 */
    static String deriveWeatherSuitability(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "ALL_CONDITIONS";
        if (tags.contains("パウダー")) return "POWDER";
        if (tags.contains("バックカントリー")) return "POWDER";
        if (tags.contains("カービング") || tags.contains("基礎") || tags.contains("レーシング")
                || tags.contains("技術選")) return "GROOMED";
        if (tags.contains("オールマウンテン") || tags.contains("フリーライド")
                || tags.contains("フリースタイル")) return "ALL_CONDITIONS";
        return "ALL_CONDITIONS";
    }

    /**
     * 商品 tags がリクエスト skillLevel に適合するか判定する。
     * - skillLevel 指定なし → 常に true
     * - 商品にスキル系タグがない → ALL 扱いで true
     * - 商品にスキル系タグがあれば、許容セットに 1 件以上含まれるか判定
     */
    static boolean matchesSkillLevel(List<String> tags, String skillLevel) {
        if (skillLevel == null || skillLevel.isBlank()) return true;
        Set<String> allowed = SKILL_TAG_MATCH.get(skillLevel.toUpperCase(Locale.ROOT));
        if (allowed == null) return true;
        if (tags == null || tags.isEmpty()) return true;
        boolean hasAnySkillTag = tags.stream().anyMatch(t ->
                "初心者".equals(t) || "初中級者".equals(t) || "中級者".equals(t)
                        || "中上級者".equals(t) || "上級者".equals(t));
        if (!hasAnySkillTag) return true; // 万能商品扱い
        return tags.stream().anyMatch(allowed::contains);
    }
}
