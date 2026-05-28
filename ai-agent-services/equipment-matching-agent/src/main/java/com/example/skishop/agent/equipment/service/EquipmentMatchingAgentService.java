package com.example.skishop.agent.equipment.service;

import com.example.skishop.agent.common.dto.EquipmentMatchRequest;
import com.example.skishop.agent.common.dto.EquipmentMatchResult;
import com.example.skishop.agent.common.dto.ProductCandidate;
import com.example.skishop.agent.common.dto.RankedProduct;
import com.example.skishop.agent.equipment.tool.EquipmentMatchingToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EquipmentMatchingAgentService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentMatchingAgentService.class);

    private static final String SYSTEM_PROMPT = """
            あなたはスキー用品の専門アドバイザー AI エージェントです。
            提供されているツールを使い、以下の手順で最適な製品を推奨してください。

            推奨手順（各ツールを **1 回のみ** 呼び出す）:
            1. filterByBodyMeasurements で体型に合わない製品を 1 度だけ除外
            2. rankProducts で最終ランキングを 1 度だけ生成
            3. 推奨理由を 300 文字以内の aiRecommendationSummary に

            **絶対に守る制約**:
            - searchInventoryCandidates は呼び出さない（既に上位で取得済み）。
            - scoreByWeatherConditions は呼び出さない（時間がかかるためスキップ）。
            - 各ツールは 1 回ずつのみ呼び出す。複数回の呼び出し・別パラメータでの再試行は禁止。
            - 結果が空・不十分でも追加呼び出しせず、その情報をもとに最終 JSON を返す。
            - **候補リスト先頭に近い商品ほど、行き先・スキルレベル・体型に対する事前適合度が高い** ため、
              同点ランキング時はリスト順序を優先せよ。
            """;

    /**
     * 主要リゾート → 想定地形・雪質マップ。商品の weatherSuitability と照合する。
     */
    private static final Map<String, String> RESORT_TERRAIN = Map.ofEntries(
            Map.entry("ニセコ", "POWDER"),
            Map.entry("niseko", "POWDER"),
            Map.entry("白馬", "POWDER"),
            Map.entry("hakuba", "POWDER"),
            Map.entry("八方尾根", "POWDER"),
            Map.entry("野沢温泉", "POWDER"),
            Map.entry("nozawa", "POWDER"),
            Map.entry("志賀高原", "ALL_CONDITIONS"),
            Map.entry("shiga", "ALL_CONDITIONS"),
            Map.entry("苗場", "GROOMED"),
            Map.entry("naeba", "GROOMED"),
            Map.entry("万座", "GROOMED"),
            Map.entry("草津", "GROOMED"),
            Map.entry("ハンターマウンテン", "GROOMED"),
            Map.entry("軽井沢", "GROOMED"));

    private final ChatClient chatClient;
    private final EquipmentMatchingToolService toolService;
    private final RecommendationCandidateService candidateService;
    private final RecommendationScoringService scoringService;

    public EquipmentMatchingAgentService(
            @Qualifier("equipmentAgentChatClient") ChatClient chatClient,
            EquipmentMatchingToolService toolService) {
        this.chatClient = chatClient;
        this.toolService = toolService;
        this.candidateService = new RecommendationCandidateService(toolService);
        this.scoringService = new RecommendationScoringService();
    }

    public EquipmentMatchResult match(EquipmentMatchRequest request) {
        log.info("EquipmentMatchingAgent match: userId={}, categories={}, destination={}, skill={}",
                request.userId(), request.desiredCategories(), request.destination(), request.skillLevel());

        // 予算は複数カテゴリの合計に対する上限のため、カテゴリ単位の検索では適用しない。
        // 個別カテゴリの最低価格より総予算が小さいケースで結果が空になるのを避ける目的。
        // 総予算超過のチェックは LLM の rankProducts ステップで行う。
        List<ProductCandidate> allCandidates = candidateService.generateCandidates(request);
        log.info("EquipmentMatchingAgent: {} candidates found", allCandidates.size());

        // 行き先・スキルレベル・ユーザに応じた事前ランキング（多様性確保）
        // LLM へ渡す候補は上位 30 件に制限（reasoning model のタイムアウト防止）
        List<ProductCandidate> ranked = scoringService.scoreAndRank(allCandidates, request).stream().limit(30).toList();
        log.info("EquipmentMatchingAgent: {} candidates after preRank+limit", ranked.size());

        try {
            EquipmentMatchResult result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserPrompt(request, ranked))
                    .tools(toolService)
                    .call()
                    .entity(EquipmentMatchResult.class);
            if (result != null) {
                return sanitizeResult(result, request, ranked);
            }
            log.warn("EquipmentMatchingAgent returned null result, using deterministic fallback");
        } catch (RuntimeException ex) {
            log.warn("EquipmentMatchingAgent LLM call failed, using deterministic fallback: {}", ex.getMessage());
        }

        return deterministicFallback(request, ranked);
    }

    /**
     * 候補商品を以下の総合スコアで降順ソートする:
     * - スキル適合度（完全一致 +30 / 隣接 +18 / ALL +12）
     * - 行き先地形適合度（完全一致 +20 / ALL_CONDITIONS +8）
     * - 在庫数 (+ min(stock,5))
     * - 多様性ジッタ: (userId + destination + skillLevel + 日付) を seed とした擬似乱数で ±3
     * 同条件の連続実行では同じ結果（再現性 ✅）、別 destination/skillLevel では別商品（変化 ✅）。
     */
    static List<ProductCandidate> preRank(List<ProductCandidate> candidates, EquipmentMatchRequest request) {
        return new RecommendationScoringService().scoreAndRank(candidates, request);
    }

    static double totalScore(ProductCandidate c, String skillLevel, String terrain, Double jitter) {
        double s = 50.0;
        s += EquipmentMatchingToolService.skillAffinityScore(skillLevel, c.skillLevelSuitability());
        // 地形適合度
        if (terrain != null && terrain.equals(c.weatherSuitability())) s += 20.0;
        else if ("ALL_CONDITIONS".equals(c.weatherSuitability())) s += 8.0;
        if (c.isAvailable()) s += 10.0;
        s += Math.min(5, c.stockQuantity()) * 1.0;
        if (jitter != null) s += jitter;
        return s;
    }

    /** リクエストから決定論的シード値を生成する。日付は日単位で丸める。 */
    static long diversitySeed(EquipmentMatchRequest request) {
        String key = String.join("|",
                nullToEmpty(request.userId()),
                nullToEmpty(request.destination()),
                nullToEmpty(request.skillLevel()),
                LocalDate.now().toString());
        return key.hashCode();
    }

    static String terrainOf(String destination) {
        if (destination == null || destination.isBlank()) return "ALL_CONDITIONS";
        String norm = destination.toLowerCase(Locale.ROOT);
        for (var e : RESORT_TERRAIN.entrySet()) {
            if (destination.contains(e.getKey()) || norm.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                return e.getValue();
            }
        }
        return "ALL_CONDITIONS";
    }

    static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private EquipmentMatchResult deterministicFallback(EquipmentMatchRequest request, List<ProductCandidate> ranked) {
        var body = request.bodyMeasurements();
        List<ProductCandidate> sizeCompatible = toolService.filterByBodyMeasurements(
                ranked,
                body == null ? null : body.heightCm(),
                body == null ? null : body.weightKg(),
                body == null || body.footSizeCm() == null ? null : body.footSizeCm().doubleValue());

        List<RankedProduct> recommendations = toolService.rankProducts(
                sizeCompatible,
                request.skillLevel(),
                request.budgetYen());
        BigDecimal total = recommendations.stream()
                .map(RankedProduct::estimatedPrice)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean withinBudget = request.budgetYen() == null
                || total.compareTo(BigDecimal.valueOf(request.budgetYen())) <= 0;

        String summary = recommendations.isEmpty()
                ? "在庫・販売状態を満たす商品候補が見つかりませんでした。条件を広げて再検索してください。"
                : "LLM 推薦理由の生成に失敗したため、在庫候補と決定論的スコアリングに基づくランキングを返しています。";

        return new EquipmentMatchResult(
                request.userId(),
                recommendations,
                summary,
                total.doubleValue(),
                withinBudget,
                Instant.now());
    }

    private EquipmentMatchResult sanitizeResult(EquipmentMatchResult result,
                                                EquipmentMatchRequest request,
                                                List<ProductCandidate> ranked) {
        Set<String> allowedProductIds = ranked.stream()
                .map(ProductCandidate::productId)
                .collect(java.util.stream.Collectors.toSet());
        List<RankedProduct> sourceRecommendations = result.recommendations() == null
                ? List.of()
                : result.recommendations();
        List<RankedProduct> validRecommendations = sourceRecommendations.stream()
                .filter(r -> r.product() != null)
                .filter(r -> allowedProductIds.contains(r.product().productId()))
                .filter(r -> r.product().isAvailable())
                .toList();

        if (validRecommendations.size() == sourceRecommendations.size()) {
            return result;
        }
        if (validRecommendations.isEmpty()) {
            log.warn("EquipmentMatchingAgent LLM result contained no valid inventory products, using deterministic fallback");
            return deterministicFallback(request, ranked);
        }

        java.util.concurrent.atomic.AtomicInteger rankCounter = new java.util.concurrent.atomic.AtomicInteger(1);
        List<RankedProduct> reRanked = validRecommendations.stream()
                .map(r -> new RankedProduct(
                        rankCounter.getAndIncrement(),
                        r.product(),
                        r.matchScore(),
                        r.matchReason(),
                        r.estimatedPrice(),
                        r.isWeatherOptimal()))
                .toList();
        BigDecimal total = reRanked.stream()
                .map(RankedProduct::estimatedPrice)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean withinBudget = request.budgetYen() == null
                || total.compareTo(BigDecimal.valueOf(request.budgetYen())) <= 0;

        String summary = result.aiRecommendationSummary() == null
                ? "在庫候補外の商品を除外して推薦を返しています。"
                : result.aiRecommendationSummary() + " 在庫候補外の商品は除外済みです。";
        return new EquipmentMatchResult(
                result.userId() == null ? request.userId() : result.userId(),
                reRanked,
                summary,
                total.doubleValue(),
                withinBudget,
                result.generatedAt() == null ? Instant.now() : result.generatedAt());
    }

    static String buildUserPrompt(EquipmentMatchRequest request, List<ProductCandidate> candidates) {
        String candidateList = candidates.isEmpty()
                ? "（候補なし）"
                : candidates.stream()
                        .map(c -> "- productId=%s, name=%s, brand=%s, category=%s, basePrice=%s, stock=%d, skillLevel=%s, weather=%s, tags=%s, available=%s"
                                .formatted(c.productId(), c.productName(), c.brand(), c.category(),
                                        c.basePrice(), c.stockQuantity(), c.skillLevelSuitability(),
                                        c.weatherSuitability(), c.tags(), c.isAvailable()))
                        .reduce((a, b) -> a + "\n" + b).orElse("");

        return """
                ユーザーID: %s
                スキルレベル: %s
                希望カテゴリ: %s
                予算（合計）: %s円
                行き先: %s
                在庫候補（%d件、行き先・スキルレベル適合度で事前ソート済み。先頭ほど推奨度高い。
                必ずこのリストの productId のみを使うこと）:
                %s

                最適な製品セットを推奨してください。
                ※ rankProducts には上記の在庫候補リスト全件をそのまま candidates 引数として渡すこと。
                ※ 合計予算を超えないように組合せを選び、超える場合はその旨を aiRecommendationSummary に明記。
                ※ 同等スコアの商品が複数ある場合は、リスト先頭側を優先せよ（行き先・スキルへの事前適合度が高い順）。
                """.formatted(
                request.userId(),
                request.skillLevel(),
                String.join("・", request.desiredCategories()),
                request.budgetYen() != null ? request.budgetYen() : "未指定",
                request.destination() != null ? request.destination() : "未指定",
                candidates.size(),
                candidateList);
    }
}
