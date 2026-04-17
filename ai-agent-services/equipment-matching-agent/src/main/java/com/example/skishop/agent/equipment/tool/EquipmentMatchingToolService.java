package com.example.skishop.agent.equipment.tool;

import com.example.skishop.agent.common.dto.ProductCandidate;
import com.example.skishop.agent.common.dto.RankedProduct;
import com.example.skishop.agent.common.invoker.WeatherInvoker;
import com.example.skishop.agent.equipment.client.InventoryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;

import java.util.List;

public class EquipmentMatchingToolService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentMatchingToolService.class);
    private final InventoryClient inventoryClient;
    private final WeatherInvoker weatherInvoker;

    public EquipmentMatchingToolService(InventoryClient inventoryClient,
                                         WeatherInvoker weatherInvoker) {
        this.inventoryClient = inventoryClient;
        this.weatherInvoker = weatherInvoker;
    }

    @Tool(description = """
            スキルレベルと商品カテゴリを指定して、在庫ありの商品候補を最大20件取得する。
            skillLevel: BEGINNER / INTERMEDIATE / ADVANCED / EXPERT
            """)
    public List<ProductCandidate> searchInventoryCandidates(
            @ToolParam(description = "商品カテゴリ") String category,
            @ToolParam(description = "スキルレベル") String skillLevel,
            @ToolParam(description = "最大予算（円）") @Nullable Integer maxBudgetYen) {
        log.info("Tool searchInventoryCandidates: category={}, skill={}", category, skillLevel);
        return inventoryClient.searchBySkillAndCategory(category, skillLevel, maxBudgetYen);
    }

    @Tool(description = """
            身長・体重・靴のサイズに基づいて製品リストをフィルタリングする。
            """)
    public List<ProductCandidate> filterByBodyMeasurements(
            @ToolParam(description = "商品候補リスト") List<ProductCandidate> candidates,
            @ToolParam(description = "身長(cm)") @Nullable Integer heightCm,
            @ToolParam(description = "体重(kg)") @Nullable Integer weightKg,
            @ToolParam(description = "靴サイズ(cm)") @Nullable Double footSizeCm) {
        if (candidates == null) return List.of();
        log.info("Tool filterByBodyMeasurements: candidates={}, height={}", candidates.size(), heightCm);
        return candidates.stream()
                .filter(p -> isSizeCompatible(p, heightCm, weightKg, footSizeCm))
                .toList();
    }

    @Tool(description = """
            指定リゾートの気象コンディションに基づいて製品をスコアリングし、適合順にソートする。
            """)
    public List<ProductCandidate> scoreByWeatherConditions(
            @ToolParam(description = "商品候補リスト") List<ProductCandidate> candidates,
            @ToolParam(description = "スキーリゾートの場所") String resortLocation) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        log.info("Tool scoreByWeatherConditions: candidates={}, resort={}", candidates.size(), resortLocation);
        var feasibility = weatherInvoker.getFeasibility(resortLocation);
        String snowCondition = feasibility == null ? "ALL_CONDITIONS" : feasibility.snowCondition();
        return candidates.stream()
                .sorted((a, b) -> Integer.compare(weatherScore(b, snowCondition), weatherScore(a, snowCondition)))
                .toList();
    }

    @Tool(description = """
            スキルレベル適合度・気象適合度・在庫状況・価格を総合スコアリングし、
            上位10件をランキング形式で返す。最後に呼ぶ。
            """)
    public List<RankedProduct> rankProducts(
            @ToolParam(description = "商品候補リスト") List<ProductCandidate> candidates,
            @ToolParam(description = "スキルレベル") String skillLevel,
            @ToolParam(description = "最大予算（円）") @Nullable Integer maxBudgetYen) {
        if (candidates == null) return List.of();
        log.info("Tool rankProducts: candidates={}, skill={}", candidates.size(), skillLevel);

        java.util.concurrent.atomic.AtomicInteger rankCounter = new java.util.concurrent.atomic.AtomicInteger(1);
        return candidates.stream()
                .filter(p -> maxBudgetYen == null || p.basePrice() == null
                        || p.basePrice().intValue() <= maxBudgetYen)
                .map(p -> scoreProduct(p, skillLevel))
                .sorted((a, b) -> Double.compare(b.matchScore(), a.matchScore()))
                .limit(10)
                .map(rp -> new RankedProduct(rankCounter.getAndIncrement(),
                        rp.product(), rp.matchScore(), rp.matchReason(), rp.estimatedPrice(), rp.isWeatherOptimal()))
                .toList();
    }

    static boolean isSizeCompatible(ProductCandidate product, Integer height, Integer weight, Double footSize) {
        if ("スキー板".equals(product.category()) && height != null) {
            var attrs = product.attributes();
            if (attrs != null && attrs.containsKey("length")) {
                try {
                    int len = Integer.parseInt(attrs.get("length").replace("cm", "").trim());
                    int minLen = (int) (height * 0.85);
                    int maxLen = (int) (height * 1.0);
                    return len >= minLen && len <= maxLen;
                } catch (NumberFormatException e) {
                    return true;
                }
            }
        }
        return true;
    }

    static int weatherScore(ProductCandidate product, String snowCondition) {
        if ("POWDER".equals(snowCondition) && "POWDER".equals(product.weatherSuitability())) return 20;
        if ("ALL_CONDITIONS".equals(product.weatherSuitability())) return 10;
        return 0;
    }

    static RankedProduct scoreProduct(ProductCandidate product, String skillLevel) {
        double score = 50.0;
        if (skillLevel != null && skillLevel.equals(product.skillLevelSuitability())) score += 30.0;
        if ("ALL".equals(product.skillLevelSuitability())) score += 15.0;
        if (product.isAvailable()) score += 10.0;
        String reason = "スキルレベル「%s」に適しており、在庫があります。".formatted(skillLevel);
        return new RankedProduct(0, product, score, reason, product.basePrice(), false);
    }
}
