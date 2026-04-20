package com.example.skishop.ai.service;

import com.example.skishop.ai.config.AiAnalyzerProperties;
import com.example.skishop.ai.dto.DeadStockResponse;
import com.example.skishop.ai.dto.DeadStockResponse.DeadStockItem;
import com.example.skishop.ai.dto.DeadStockResponse.Severity;
import com.example.skishop.ai.util.PromptSanitizer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * F4 滞留在庫レーダーサービス (spec § 19 / impl-plan P5).
 * <p>
 * mv_sku_velocity から滞留候補を抽出し、severity 判定 + 推奨割引率を Java で計算。
 * <p>
 * ADR: D-F4-01 (割引率 0-40% クリップ), D-F4-02 (弾力性外出し),
 * D-F4-03 (approver確認), D-F4-04 (30日重複ブロック), D-F4-05 (監査記録),
 * D-F4-06 (MV使用), D-F4-07 (severity 3段階)
 */
@Service
public class DeadStockService {

    private static final Logger log = LoggerFactory.getLogger(DeadStockService.class);
    private static final int DISCOUNT_PCT_MAX = 40;
    private static final int DISCOUNT_PCT_MIN = 0;

    private static final String NARRATIVE_SYSTEM = """
            あなたはスキー EC ショップの在庫アナリスト AI です。
            以下の滞留在庫データから、2-3 文で簡潔にアドバイスしてください。
            数値の生成・推測は禁止。提供データのみ使用すること。
            """;

    private static final String NARRATIVE_FALLBACK = "AI 分析は一時的に利用できません。データをご参照ください。";

    private final WebClient salesWebClient;
    private final WebClient inventoryWebClient;
    private final WebClient couponWebClient;
    private final AiAnalyzerProperties props;
    private final ChatClient chatClient;
    private final LlmAuditService auditService;
    private final PromptSanitizer sanitizer;

    public DeadStockService(
            @Qualifier("salesWebClient") WebClient salesWebClient,
            @Qualifier("inventoryWebClient") WebClient inventoryWebClient,
            @Qualifier("couponWebClient") WebClient couponWebClient,
            AiAnalyzerProperties props,
            ChatClient.Builder chatClientBuilder,
            LlmAuditService auditService,
            PromptSanitizer sanitizer
    ) {
        this.salesWebClient = salesWebClient;
        this.inventoryWebClient = inventoryWebClient;
        this.couponWebClient = couponWebClient;
        this.props = props;
        this.chatClient = chatClientBuilder.build();
        this.auditService = auditService;
        this.sanitizer = sanitizer;
    }

    /**
     * 滞留在庫一覧を取得。
     */
    public DeadStockResponse getDeadStock() {
        // mv_sku_velocity から取得 (D-F4-06)
        List<Map<String, Object>> velocityData = fetchSkuVelocity();
        List<Map<String, Object>> inventoryData = fetchInventoryLevels();

        List<DeadStockItem> items = new ArrayList<>();

        for (Map<String, Object> vel : velocityData) {
            String sku = (String) vel.get("sku");
            long sales30 = ((Number) vel.getOrDefault("sales30", 0)).longValue();
            long sales90 = ((Number) vel.getOrDefault("sales90", 0)).longValue();

            // 在庫数取得
            int stock = inventoryData.stream()
                    .filter(inv -> sku.equals(inv.get("sku")))
                    .findFirst()
                    .map(inv -> ((Number) inv.getOrDefault("stockQuantity",
                            inv.getOrDefault("quantity", 0))).intValue())
                    .orElse(0);

            if (stock == 0) continue;

            // DoS (Days of Supply)
            double dailySales = sales30 > 0 ? sales30 / 30.0 : (sales90 > 0 ? sales90 / 90.0 : 0.01);
            int daysOfSupply = (int) Math.round(stock / dailySales);

            // Severity 判定 (D-F4-07: 3段階固定)
            Severity severity = judgeSeverity(daysOfSupply, sales30);

            // 推奨割引率 Java 計算 + クリップ (D-F4-01)
            String categoryId = (String) vel.getOrDefault("categoryId", "default");
            double suggestedPct = calculateDiscountPct(daysOfSupply, categoryId);

            // 商品名は在庫データ (inventoryData) から取得。velocity には name カラムが存在しないため。
            String name = inventoryData.stream()
                    .filter(inv -> sku.equals(inv.get("sku")))
                    .findFirst()
                    .map(inv -> (String) inv.getOrDefault("name", sku))
                    .orElse(sku);
            items.add(new DeadStockItem(sku, name, stock, sales30, sales90, daysOfSupply, severity, suggestedPct, null));
        }

        // severity 順にソート
        items.sort((a, b) -> a.severity().compareTo(b.severity()));

        // LLM ナラティブ生成
        String narrative = generateNarrativeSafe(items);

        return new DeadStockResponse(Instant.now(), items, narrative);
    }

    /**
     * クーポン発行 (D-F4-03, D-F4-04, D-F4-05).
     */
    public Map<String, Object> issueCoupon(String sku, double discountPct, String memo, String approverUserId) {
        // D-F4-01: 強制クリップ
        double clippedPct = Math.max(DISCOUNT_PCT_MIN, Math.min(DISCOUNT_PCT_MAX, discountPct));

        // D-F4-04: 30日以内の重複チェック
        if (hasDuplicateWithin30Days(sku)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "同一 SKU に対して 30 日以内にクーポンが発行済みです");
        }

        // coupon-service へ発行
        Map<String, Object> couponResult = couponWebClient.post()
                .uri("/api/v1/coupons")
                .bodyValue(Map.of(
                        "sku", sku,
                        "discountPct", clippedPct,
                        "type", "DEAD_STOCK",
                        "memo", memo != null ? memo : ""
                ))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        // D-F4-05: dead_stock_actions に記録
        recordAction(sku, couponResult, clippedPct, discountPct, approverUserId, memo);

        return couponResult;
    }

    // ── Severity 判定 (D-F4-07) ──

    Severity judgeSeverity(int daysOfSupply, long sales30) {
        if (daysOfSupply > 180 || sales30 == 0) return Severity.CRITICAL;
        if (daysOfSupply > 90) return Severity.HIGH;
        return Severity.MEDIUM;
    }

    // ── 割引率計算 (D-F4-01, D-F4-02) ──

    double calculateDiscountPct(int daysOfSupply, String categoryId) {
        double elasticity = props.getDeadStock().elasticityFor(categoryId);
        // 基本式: baseDiscount = log(DoS/30) * |elasticity| * 10
        double base = Math.log(Math.max(1, daysOfSupply) / 30.0) * Math.abs(elasticity) * 10;
        double pct = Math.round(base * 10.0) / 10.0;
        // D-F4-01: クリップ
        return Math.max(DISCOUNT_PCT_MIN, Math.min(DISCOUNT_PCT_MAX, pct));
    }

    // ── Private helpers ──

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchSkuVelocity() {
        try {
            return salesWebClient.get()
                    .uri("/api/v1/admin/orders/analytics/sku-velocity")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();
        } catch (Exception e) {
            log.warn("SKU velocity 取得失敗", e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchInventoryLevels() {
        try {
            return inventoryWebClient.get()
                    .uri("/api/v1/inventory/all")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();
        } catch (Exception e) {
            log.warn("在庫データ取得失敗", e);
            return List.of();
        }
    }

    private boolean hasDuplicateWithin30Days(String sku) {
        try {
            var result = couponWebClient.get()
                    .uri("/api/v1/dead-stock-actions?sku=" + sku + "&days=" + props.getDeadStock().getDuplicateBlockDays())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();
            return result != null && !result.isEmpty();
        } catch (Exception e) {
            log.warn("重複チェック失敗、安全側で拒否", e);
            return true; // 安全側: チェック失敗時は重複扱い
        }
    }

    private void recordAction(String sku, Map<String, Object> couponResult, double actualPct, double aiSuggestedPct, String approverUserId, String memo) {
        try {
            couponWebClient.post()
                    .uri("/api/v1/dead-stock-actions")
                    .bodyValue(Map.of(
                            "sku", sku,
                            "couponId", couponResult != null ? couponResult.getOrDefault("id", "") : "",
                            "aiSuggestedPct", aiSuggestedPct,
                            "actualPct", actualPct,
                            "approverUserId", approverUserId,
                            "memo", memo != null ? memo : ""
                    ))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
        } catch (Exception e) {
            log.error("dead_stock_actions 記録失敗: sku={}", sku, e);
        }
    }

    @CircuitBreaker(name = "azureOpenAi", fallbackMethod = "fallbackNarrative")
    private String generateNarrativeSafe(List<DeadStockItem> items) {
        if (items.isEmpty()) return "滞留在庫は検出されませんでした。";

        long start = System.currentTimeMillis();
        var sb = new StringBuilder("滞留在庫データ:\n");
        for (var item : items.subList(0, Math.min(10, items.size()))) {
            sb.append("- ").append(item.sku()).append(": 在庫 ").append(item.stock())
                    .append(", DoS ").append(item.daysOfSupply())
                    .append(", severity ").append(item.severity())
                    .append(", 推奨割引 ").append(item.suggestedDiscountPct()).append("%\n");
        }
        String userPrompt = sb.toString();
        String wrapped = sanitizer.wrapAsUserMessage(userPrompt);

        String narrative = chatClient.prompt()
                .system(NARRATIVE_SYSTEM)
                .user(wrapped)
                .call()
                .content();

        long duration = System.currentTimeMillis() - start;
        auditService.record("F4", sanitizer.hash(userPrompt), "dead-stock",
                userPrompt.length() / 4, narrative != null ? narrative.length() / 4 : 0,
                duration, true, null);

        return narrative != null ? narrative : NARRATIVE_FALLBACK;
    }

    @SuppressWarnings("unused")
    private String fallbackNarrative(List<DeadStockItem> items, Throwable t) {
        log.warn("滞留在庫ナラティブフォールバック: {}", t.getMessage());
        return NARRATIVE_FALLBACK;
    }
}
