package com.example.skishop.agent.orchestrator.tool;

import com.example.skishop.agent.common.dto.BulkPricingRequest;
import com.example.skishop.agent.common.dto.CartItemPricing;
import com.example.skishop.agent.common.dto.CouponOptimizationRequest;
import com.example.skishop.agent.common.dto.EquipmentMatchRequest;
import com.example.skishop.agent.common.dto.ReservationRequest;
import com.example.skishop.agent.orchestrator.client.PaymentCartClient;
import com.example.skishop.agent.orchestrator.invoker.WorkerAgentInvoker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrator が GPT に渡す唯一の Tool セット。
 * モード非依存（{@link WorkerAgentInvoker} 経由で in-JVM / REST が自動切替）。
 */
public class OrchestratorWorkerTools {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorWorkerTools.class);

    private final WorkerAgentInvoker invoker;
    private final PaymentCartClient paymentCart;
    private final ObjectMapper objectMapper;

    public OrchestratorWorkerTools(WorkerAgentInvoker invoker,
                                    PaymentCartClient paymentCart,
                                    ObjectMapper objectMapper) {
        this.invoker = invoker;
        this.paymentCart = paymentCart;
        this.objectMapper = objectMapper;
    }

    // ─── Customer Intent ────────────────────────────────────────

    @Tool(description = """
            顧客の自然言語リクエストを解析し、意図・制約（場所/日程/予算/スキルレベル/カテゴリ）を構造化抽出。
            ワークフローの最初に必ず呼び出すこと。
            """)
    public String analyzeCustomerIntent(
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "顧客の自然言語メッセージ") String userMessage,
            @ToolParam(description = "セッション ID（任意）") @Nullable String sessionId) {
        log.info("Tool analyzeCustomerIntent: userId={}", userId);
        return toJson(invoker.invokeCustomerIntent(userId, userMessage, sessionId));
    }

    // ─── Weather ────────────────────────────────────────────────

    @Tool(description = """
            指定したスキーリゾート/場所の気象情報・積雪状況・スキー適性を取得する。
            destination が判明した時点で呼び出す。
            """)
    public String getWeatherAndSkiConditions(
            @ToolParam(description = "場所名（例: Naeba, Niigata, Japan）") String location,
            @ToolParam(description = "リゾート名（任意）") @Nullable String resort,
            @ToolParam(description = "予報日数（デフォルト 7）") @Nullable Integer forecastDays) {
        log.info("Tool getWeatherAndSkiConditions: location={}", location);
        return toJson(invoker.invokeWeather(location, resort, forecastDays));
    }

    // ─── Equipment Matching ─────────────────────────────────────

    @Tool(description = """
            顧客のスキルレベル・予算・気象に基づき、最適なスキー用品をランキング推奨する。
            categories は次の canonical category ID をカンマ区切りで指定する:
              cat-ski (スキー板) / cat-boots (ブーツ) / cat-wear (ウェア) /
              cat-gloves (グローブ) / cat-goggles (ゴーグル) /
              cat-helmets (ヘルメット) / cat-poles (ポール)
            """)
    public String matchEquipment(
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "スキルレベル: BEGINNER/INTERMEDIATE/ADVANCED/EXPERT") String skillLevel,
            @ToolParam(description = "希望カテゴリ ID をカンマ区切り（cat-ski, cat-wear など）") String categories,
            @ToolParam(description = "予算（円、任意）") @Nullable Integer budgetYen,
            @ToolParam(description = "行き先リゾート名") @Nullable String destination,
            @ToolParam(description = "推奨数量（デフォルト 1）") int quantity) {
        log.info("Tool matchEquipment: userId={}, skill={}, qty={}", userId, skillLevel, quantity);
        var request = new EquipmentMatchRequest(
                userId,
                skillLevel,
                /* bodyMeasurements */ null,
                Arrays.stream(categories.split(",")).map(String::trim).toList(),
                budgetYen,
                destination,
                /* includeRental */ false,
                /* includePurchase */ true,
                quantity > 0 ? quantity : 1);
        return toJson(invoker.invokeEquipmentMatching(request));
    }

    // ─── Inventory Check ────────────────────────────────────────

    @Tool(description = """
            指定商品 ID リストの在庫状況（AVAILABLE/LOW_STOCK/OUT_OF_STOCK）を確認する。
            """)
    public String checkInventoryAvailability(
            @ToolParam(description = "商品 ID のカンマ区切り") String productIds,
            @ToolParam(description = "必要数量（デフォルト 1）") int quantity) {
        log.info("Tool checkInventoryAvailability: productIds={}", productIds);
        var ids = Arrays.stream(productIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return toJson(invoker.invokeInventoryCheck(ids, quantity > 0 ? quantity : 1));
    }

    // ─── Inventory Reservation ──────────────────────────────────

    @Tool(description = """
            注文に対して在庫を一時予約ロックする（30分有効）。カート確定直前に呼び出す。
            """)
    public String reserveInventory(
            @ToolParam(description = "注文 ID") String orderId,
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "商品ID:数量 のカンマ区切り（例: prod1:2,prod2:1）") String productQuantities) {
        log.info("Tool reserveInventory: orderId={}", orderId);
        var items = parseProductQuantities(productQuantities).entrySet().stream()
                .map(e -> new ReservationRequest.ReservationItem(e.getKey(), e.getValue()))
                .toList();
        var request = new ReservationRequest(orderId, userId, items, 30);
        return toJson(invoker.invokeInventoryReservation(request));
    }

    // ─── Dynamic Pricing ────────────────────────────────────────

    @Tool(description = """
            カート内の全商品の動的価格を一括算出する（需要・天候・在庫・顧客ティア反映）。
            """)
    public String calculateDynamicPrices(
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "顧客ティア: BRONZE/SILVER/GOLD/PLATINUM") String customerTier,
            @ToolParam(description = "商品ID:数量 のカンマ区切り") String productQuantities,
            @ToolParam(description = "リゾート名（気象調整用）") @Nullable String resortLocation) {
        log.info("Tool calculateDynamicPrices: userId={}, tier={}", userId, customerTier);
        var items = parseProductQuantities(productQuantities).entrySet().stream()
                .map(e -> new BulkPricingRequest.BulkPricingItem(e.getKey(), e.getValue()))
                .toList();
        var request = new BulkPricingRequest(userId, customerTier, items, resortLocation);
        return toJson(invoker.invokeDynamicPricing(request));
    }

    // ─── Coupon Optimization ────────────────────────────────────

    @Tool(description = """
            カート内容に対して最適なクーポン・ポイント組み合わせを選択する。
            cartItemsEncoded は productId|name|category|qty|unitPrice をセミコロン区切りで指定。
            """)
    public String optimizeCoupons(
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "注文 ID") String orderId,
            @ToolParam(description = "カートアイテム構造化文字列") String cartItemsEncoded,
            @ToolParam(description = "顧客ティア") String customerTier,
            @ToolParam(description = "ポイント使用希望") boolean usePoints,
            @ToolParam(description = "入力済みクーポンコード（任意）") @Nullable String couponCode) {
        log.info("Tool optimizeCoupons: userId={}, orderId={}", userId, orderId);
        var cartItems = parseCartItemsEncoded(cartItemsEncoded);
        var request = new CouponOptimizationRequest(userId, orderId, cartItems, customerTier, usePoints, couponCode);
        return toJson(invoker.invokeCouponOptimization(request));
    }

    // ─── Payment Cart Build ─────────────────────────────────────

    @Tool(description = """
            最終決定したカートを payment-cart-service に構築し、注文を確定する。
            cartItemsEncoded は productId|name|qty|unitPrice をセミコロン区切りで指定。
            """)
    public String buildCart(
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "注文 ID") String orderId,
            @ToolParam(description = "カートアイテム構造化文字列（productId|name|qty|unitPrice）") String cartItemsEncoded,
            @ToolParam(description = "適用済みクーポン割引額") BigDecimal couponDiscount,
            @ToolParam(description = "適用済みポイント割引額") BigDecimal pointDiscount) {
        log.info("Tool buildCart: userId={}, orderId={}", userId, orderId);
        return toJson(paymentCart.buildCart(userId, orderId, cartItemsEncoded, couponDiscount, pointDiscount));
    }

    // ─── Helpers ────────────────────────────────────────────────

    String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Tool result JSON シリアライズ失敗: {}", e.getMessage());
            throw new IllegalStateException("Tool result serialization failed", e);
        }
    }

    static Map<String, Integer> parseProductQuantities(String encoded) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) return map;
        for (String s : encoded.split(",")) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split(":");
            String key = parts[0].trim();
            int qty = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
            map.merge(key, qty, Integer::sum);
        }
        return map;
    }

    static List<CartItemPricing> parseCartItemsEncoded(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        return Arrays.stream(encoded.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    String[] p = s.split("\\|");
                    if (p.length < 5) {
                        throw new IllegalArgumentException(
                                "cartItemsEncoded must be productId|name|category|qty|unitPrice; got: " + s);
                    }
                    int qty = Integer.parseInt(p[3].trim());
                    BigDecimal unit = new BigDecimal(p[4].trim());
                    return new CartItemPricing(
                            p[0].trim(), p[1].trim(), p[2].trim(), qty, unit,
                            unit.multiply(BigDecimal.valueOf(qty)));
                })
                .toList();
    }
}
