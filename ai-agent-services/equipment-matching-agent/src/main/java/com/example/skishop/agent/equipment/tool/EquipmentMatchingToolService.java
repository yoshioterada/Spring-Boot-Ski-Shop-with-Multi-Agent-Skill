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
import java.util.Locale;
import java.util.Map;

public class EquipmentMatchingToolService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentMatchingToolService.class);

    /**
     * LLM が渡しがちなカテゴリ別名 → 在庫サービスが受け付ける canonical category ID へのマッピング。
     * 在庫サービスは categoryId（cat-xxx 形式）の完全一致でフィルタするため、
     * "SKI" "ski" "板" のような表現を吸収する。
     */
    private static final Map<String, String> CATEGORY_ALIASES = Map.ofEntries(
            Map.entry("ski", "cat-ski"),
            Map.entry("skis", "cat-ski"),
            Map.entry("スキー", "cat-ski"),
            Map.entry("スキー板", "cat-ski"),
            Map.entry("板", "cat-ski"),
            Map.entry("boots", "cat-boots"),
            Map.entry("boot", "cat-boots"),
            Map.entry("ski boots", "cat-boots"),
            Map.entry("ブーツ", "cat-boots"),
            Map.entry("スキーブーツ", "cat-boots"),
            Map.entry("wear", "cat-wear"),
            Map.entry("ski wear", "cat-wear"),
            Map.entry("apparel", "cat-wear"),
            Map.entry("ウェア", "cat-wear"),
            Map.entry("スキーウェア", "cat-wear"),
            Map.entry("jacket", "cat-wear"),
            Map.entry("gloves", "cat-gloves"),
            Map.entry("glove", "cat-gloves"),
            Map.entry("グローブ", "cat-gloves"),
            Map.entry("手袋", "cat-gloves"),
            Map.entry("goggles", "cat-goggles"),
            Map.entry("goggle", "cat-goggles"),
            Map.entry("ゴーグル", "cat-goggles"),
            Map.entry("helmet", "cat-helmets"),
            Map.entry("helmets", "cat-helmets"),
            Map.entry("ヘルメット", "cat-helmets"),
            Map.entry("pole", "cat-poles"),
            Map.entry("poles", "cat-poles"),
            Map.entry("ストック", "cat-poles"),
            Map.entry("ポール", "cat-poles"));

    private final InventoryClient inventoryClient;
    private final WeatherInvoker weatherInvoker;

    public EquipmentMatchingToolService(InventoryClient inventoryClient,
                                         WeatherInvoker weatherInvoker) {
        this.inventoryClient = inventoryClient;
        this.weatherInvoker = weatherInvoker;
    }

    /**
     * 入力カテゴリを在庫サービスが受け付ける canonical な categoryId へ正規化する。
     * 既に "cat-xxx" 形式ならそのまま、別名なら対応する ID へ変換、未知ならそのまま返す。
     */
    static String normalizeCategory(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return trimmed;
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("cat-")) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        String alias = CATEGORY_ALIASES.get(trimmed.toLowerCase(Locale.ROOT));
        if (alias != null) return alias;
        // 末尾 "s" を取った単数形でも試す
        if (trimmed.length() > 1 && trimmed.endsWith("s")) {
            String singular = trimmed.substring(0, trimmed.length() - 1).toLowerCase(Locale.ROOT);
            String aliasS = CATEGORY_ALIASES.get(singular);
            if (aliasS != null) return aliasS;
        }
        return trimmed;
    }

    @Tool(description = """
            スキルレベルと商品カテゴリを指定して、在庫ありの商品候補を最大20件取得する。
            category は次のいずれかの canonical ID を使用すること:
              cat-ski (スキー板) / cat-boots (ブーツ) / cat-wear (ウェア) /
              cat-gloves (グローブ) / cat-goggles (ゴーグル) /
              cat-helmets (ヘルメット) / cat-poles (ポール)
            skillLevel: BEGINNER / INTERMEDIATE / ADVANCED / EXPERT
            """)
    public List<ProductCandidate> searchInventoryCandidates(
            @ToolParam(description = "商品カテゴリ ID (cat-ski / cat-boots / cat-wear / cat-gloves / cat-goggles / cat-helmets / cat-poles)") String category,
            @ToolParam(description = "スキルレベル") String skillLevel,
            @ToolParam(description = "最大予算（円）") @Nullable Integer maxBudgetYen) {
        String normalized = normalizeCategory(category);
        if (!java.util.Objects.equals(normalized, category)) {
            log.info("Tool searchInventoryCandidates: category={} -> normalized={}, skill={}", category, normalized, skillLevel);
        } else {
            log.info("Tool searchInventoryCandidates: category={}, skill={}", category, skillLevel);
        }
        return inventoryClient.searchBySkillAndCategory(normalized, skillLevel, maxBudgetYen);
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

    /** スキルレベルの順序関係（隣接判定用）。 */
    private static final List<String> SKILL_ORDER = List.of("BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT");

    /**
     * skillLevel と商品 skillLevelSuitability の適合度スコア（0〜30）。
     * 完全一致 +30 / 隣接レベル +18 / 2 段階差 +5 / 商品が ALL +12。
     */
    public static int skillAffinityScore(String requestedSkill, String productSkill) {
        if (productSkill == null) return 0;
        if ("ALL".equals(productSkill)) return 12;
        if (requestedSkill == null) return 8;
        if (requestedSkill.equals(productSkill)) return 30;
        int reqIdx = SKILL_ORDER.indexOf(requestedSkill.toUpperCase(java.util.Locale.ROOT));
        int prodIdx = SKILL_ORDER.indexOf(productSkill.toUpperCase(java.util.Locale.ROOT));
        if (reqIdx < 0 || prodIdx < 0) return 0;
        int diff = Math.abs(reqIdx - prodIdx);
        if (diff == 1) return 18;
        if (diff == 2) return 5;
        return 0;
    }

    static RankedProduct scoreProduct(ProductCandidate product, String skillLevel) {
        double score = 50.0;
        score += skillAffinityScore(skillLevel, product.skillLevelSuitability());
        if (product.isAvailable()) score += 10.0;
        // 在庫量が多いほど僅かに加点（同点回避用、最大 +5）
        score += Math.min(5, product.stockQuantity()) * 1.0;
        String suit = product.skillLevelSuitability() == null ? "ALL" : product.skillLevelSuitability();
        String reason = "スキルレベル要求「%s」に対し商品適性は「%s」。在庫: %d 個。"
                .formatted(skillLevel, suit, product.stockQuantity());
        return new RankedProduct(0, product, score, reason, product.basePrice(), false);
    }
}
