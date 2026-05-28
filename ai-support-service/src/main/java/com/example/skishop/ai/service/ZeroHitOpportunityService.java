package com.example.skishop.ai.service;

import com.example.skishop.ai.config.AiAnalyzerProperties;
import com.example.skishop.ai.dto.ZeroHitOpportunityResponse;
import com.example.skishop.ai.dto.ZeroHitOpportunityResponse.*;
import com.example.skishop.ai.model.ZeroHitDismissal;
import com.example.skishop.ai.repository.ZeroHitDismissalRepository;
import com.example.skishop.ai.util.PromptSanitizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * F5 機会発見レーダーサービス (spec § 20).
 * <p>
 * inventory-management-service の search_logs (MongoDB) からゼロヒットクエリを集約し、
 * LLM でカテゴリ推定 + 仕入候補を生成する。
 * <p>
 * ADR: D-F5-01 (架空SKU禁止), D-F5-02 (searchProductCatalog Tool), D-F5-03 (クエリ正規化),
 * D-F5-04 (minSearchCount >= 3), D-F5-05 (dismiss TTL 90日), D-F5-06 (損失推定式),
 * D-F5-07 (PII なし), D-F5-08 (UI注記)
 */
@Service
public class ZeroHitOpportunityService {

    private static final Logger log = LoggerFactory.getLogger(ZeroHitOpportunityService.class);

    // D-F5-03: クエリ正規化パターン
    private static final Pattern FULLWIDTH_DIGITS = Pattern.compile("[０-９]");
    private static final Pattern FULLWIDTH_ALPHA = Pattern.compile("[Ａ-Ｚａ-ｚ]");
    private static final Pattern SPACE_BEFORE_UNIT = Pattern.compile("\\s*(cm|mm|kg|inch)\\b", Pattern.CASE_INSENSITIVE);

    private static final String NARRATIVE_SYSTEM = """
            あなたはスキー EC ショップの品揃えアナリスト AI です。
            以下のゼロヒット検索データから、仕入候補を提案してください。
            ルール:
            - 架空の SKU 番号は絶対に生成しないこと。ブランド名・モデル系列のみ提案。
            - 数値（検索回数等）は提供データそのままを転記。推測値の付加禁止。
            - 出力は JSON 形式で以下のフィールド:
              estimatedCategory, estimatedGenderTarget, priority(HIGH/MEDIUM/LOW),
              narrative(日本語2-3文), suggestedBrands(配列: brand, model)
            """;

    private static final String NARRATIVE_FALLBACK = "AI 分析は一時的に利用できません。";

    private final WebClient inventoryWebClient;
    private final AiAnalyzerProperties props;
    private final ChatClient chatClient;
    private final ZeroHitDismissalRepository dismissalRepo;
    private final LlmAuditService auditService;
    private final PromptSanitizer sanitizer;
    private final ObjectMapper objectMapper;

    public ZeroHitOpportunityService(
            @Qualifier("inventoryWebClient") WebClient inventoryWebClient,
            AiAnalyzerProperties props,
            ChatClient.Builder chatClientBuilder,
            ZeroHitDismissalRepository dismissalRepo,
            LlmAuditService auditService,
            PromptSanitizer sanitizer,
            ObjectMapper objectMapper
    ) {
        this.inventoryWebClient = inventoryWebClient;
        this.props = props;
        this.chatClient = chatClientBuilder.build();
        this.dismissalRepo = dismissalRepo;
        this.auditService = auditService;
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
    }

    /**
     * ゼロヒット機会一覧を取得 (spec § 20.5.1).
     */
    public ZeroHitOpportunityResponse getOpportunities(int days, int minSearchCount, String category, int limit) {
        // Clamp parameters
        final int effectiveDays = Math.max(7, Math.min(90, days));
        final int effectiveMinCount = Math.max(1, minSearchCount);
        final int effectiveLimit = Math.max(1, Math.min(100, limit));

        // search_logs からゼロヒット集約を取得
        List<Map<String, Object>> fetched = fetchZeroHitAggregation(effectiveDays, effectiveMinCount, effectiveLimit * 2);
        var availability = fetched == null
                ? com.example.skishop.ai.dto.DataAvailability.unavailable(List.of("inventory-service"))
                : com.example.skishop.ai.dto.DataAvailability.available();
        List<Map<String, Object>> rawData = fetched == null ? List.of() : fetched;

        // D-F5-05: dismiss 済みキーワードを除外
        Set<String> dismissed = dismissalRepo.findAll().stream()
                .map(ZeroHitDismissal::getNormalizedKeyword)
                .collect(Collectors.toSet());

        // 正規化 + 集約 + 除外
        Map<String, AggregatedQuery> aggregated = new LinkedHashMap<>();
        for (var entry : rawData) {
            String rawKeyword = (String) entry.getOrDefault("keyword", "");
            String normalized = normalizeQuery(rawKeyword);
            if (normalized.isBlank() || dismissed.contains(normalized)) continue;

            aggregated.merge(normalized,
                    new AggregatedQuery(normalized,
                            ((Number) entry.getOrDefault("searchCount", 0)).longValue(),
                            parseInstantSafe(entry.get("lastSearchedAt"))),

                    (a, b) -> new AggregatedQuery(a.keyword, a.searchCount + b.searchCount,
                            a.lastSearchedAt.isAfter(b.lastSearchedAt) ? a.lastSearchedAt : b.lastSearchedAt));
        }

        // D-F5-04: minSearchCount フィルタ
        List<AggregatedQuery> filtered = aggregated.values().stream()
                .filter(q -> q.searchCount >= effectiveMinCount)
                .sorted(Comparator.comparingLong(AggregatedQuery::searchCount).reversed())
                .limit(effectiveLimit)
                .toList();

        // Build opportunities with LLM narrative
        List<Opportunity> opportunities = new ArrayList<>();
        long totalLoss = 0;
        long totalVolume = 0;
        String topCategory = "";

        for (int i = 0; i < filtered.size(); i++) {
            var q = filtered.get(i);
            // D-F5-06: 機会損失推定
            double avgPrice = 50000; // category_avg_price default (スキー用品)
            long loss = Math.round(q.searchCount * props.getZeroHit().getConversionRate() * avgPrice);
            totalLoss += loss;
            totalVolume += q.searchCount;

            opportunities.add(new Opportunity(
                    i + 1,
                    q.keyword,
                    q.searchCount,
                    q.lastSearchedAt,
                    null, null, loss,
                    q.searchCount >= 30 ? "HIGH" : (q.searchCount >= 10 ? "MEDIUM" : "LOW"),
                    null,
                    List.of(),
                    List.of()
            ));

            if (i == 0) topCategory = "cat-ski"; // default
        }

        // LLM enrichment for top items
        opportunities = enrichWithLlm(opportunities);

        var summary = new Summary(
                filtered.size(), totalVolume, totalLoss, topCategory.isEmpty() ? "unknown" : topCategory);

        return new ZeroHitOpportunityResponse(Instant.now(), effectiveDays, summary, opportunities, availability);
    }

    /**
     * Dismiss a zero-hit query (spec § 20.5.2, D-F5-05).
     */
    public void dismiss(int rank, String reason, String memo, String userId,
                        ZeroHitOpportunityResponse currentData) {
        if (rank < 1 || rank > currentData.opportunities().size()) {
            throw new IllegalArgumentException("Invalid rank: " + rank);
        }
        var opp = currentData.opportunities().get(rank - 1);
        var dismissal = new ZeroHitDismissal(
                opp.normalizedKeyword(), reason, memo, userId,
                props.getZeroHit().getDismissTtlDays());
        dismissalRepo.save(dismissal);
    }

    /**
     * F3 統合用: 今週のゼロヒット トップ3 を取得 (spec § 20.8, D-F3-05).
     */
    public List<String> getTopZeroHitHighlights(int topN) {
        try {
            var response = getOpportunities(7, props.getZeroHit().getMinSearchCount(), null, topN);
            return response.opportunities().stream()
                    .map(o -> "「%s」で %d 件のゼロヒット (推定損失 ¥%,d)".formatted(
                            o.normalizedKeyword(), o.searchCount(), o.estimatedLossJpy()))
                    .toList();
        } catch (Exception e) {
            log.warn("ゼロヒットハイライト取得失敗", e);
            return List.of();
        }
    }

    // ── クエリ正規化 (D-F5-03) ──

    /**
     * 全角/半角統一、サイズ表記正規化.
     */
    String normalizeQuery(String raw) {
        if (raw == null) return "";
        String s = raw.strip();
        // 全角数字 → 半角
        var sb = new StringBuilder(s);
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c >= '０' && c <= '９') sb.setCharAt(i, (char) (c - '０' + '0'));
            else if (c >= 'Ａ' && c <= 'Ｚ') sb.setCharAt(i, (char) (c - 'Ａ' + 'A'));
            else if (c >= 'ａ' && c <= 'ｚ') sb.setCharAt(i, (char) (c - 'ａ' + 'a'));
        }
        s = sb.toString();
        // 全角スペース → 半角
        s = s.replace('\u3000', ' ');
        // 小文字化
        s = s.toLowerCase(Locale.ROOT);
        // サイズ単位前のスペース除去: "165 cm" → "165cm"
        s = SPACE_BEFORE_UNIT.matcher(s).replaceAll("$1");
        // 連続スペースを1つに
        s = s.replaceAll("\\s+", " ").strip();
        return s;
    }

    // ── Private ──

    /**
     * lastSearchedAt を安全に Instant へ変換する。
     * inventory-management-service の MongoDB aggregation が BsonDateTime を Long (エポックミリ秒) や
     * ISO-8601 文字列など複数の型で返す可能性があるため、各型に対応する。
     */
    private static Instant parseInstantSafe(Object raw) {
        if (raw == null) return Instant.now();
        if (raw instanceof Number num) {
            return Instant.ofEpochMilli(num.longValue());
        }
        String s = raw.toString();
        // ISO-8601 文字列の場合
        try {
            return Instant.parse(s);
        } catch (Exception ignored) {
            // 数値文字列 (エポックミリ秒) の場合
            try {
                return Instant.ofEpochMilli(Long.parseLong(s));
            } catch (NumberFormatException e) {
                return Instant.now();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchZeroHitAggregation(int days, int minSearchCount, int limit) {
        try {
            return inventoryWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/products/analytics/zero-hit-queries")
                            .queryParam("days", days)
                            .queryParam("minCount", minSearchCount)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();
        } catch (Exception e) {
            log.warn("ゼロヒット集約取得失敗", e);
            return null;
        }
    }

    @CircuitBreaker(name = "azureOpenAi", fallbackMethod = "fallbackEnrich")
    private List<Opportunity> enrichWithLlm(List<Opportunity> opportunities) {
        if (opportunities.isEmpty()) return opportunities;

        // top 5 のみ LLM で enrichment
        int enrichCount = Math.min(5, opportunities.size());
        var enriched = new ArrayList<>(opportunities);

        for (int i = 0; i < enrichCount; i++) {
            var opp = opportunities.get(i);
            long start = System.currentTimeMillis();
            String userPrompt = "キーワード: %s, 検索回数: %d".formatted(opp.normalizedKeyword(), opp.searchCount());
            String wrapped = sanitizer.wrapAsUserMessage(userPrompt);

            try {
                String response = chatClient.prompt()
                        .system(NARRATIVE_SYSTEM)
                        .user(wrapped)
                        .call()
                        .content();

                long duration = System.currentTimeMillis() - start;
                auditService.record("F5", sanitizer.hash(userPrompt), "zero-hit-enrich",
                        userPrompt.length() / 4, response != null ? response.length() / 4 : 0,
                        duration, true, null);

                // Parse JSON response from LLM to extract individual fields
                enriched.set(i, parseLlmResponse(opp, response));
            } catch (Exception e) {
                log.warn("LLM enrichment 失敗: rank={}", opp.rank(), e);
            }
        }
        return enriched;
    }

    private static final String FIELD_SUGGESTED_BRANDS = "suggestedBrands";

    /**
     * LLM の JSON レスポンスをパースし、Opportunity の各フィールドに反映する。
     */
    private Opportunity parseLlmResponse(Opportunity opp, String response) {
        if (response == null || response.isBlank()) {
            return withNarrative(opp, NARRATIVE_FALLBACK);
        }

        // LLM が markdown コードブロックで囲む場合があるため除去
        String json = response.strip();
        if (json.startsWith("```")) {
            json = json.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("(?s)\\s*```$", "");
        }

        try {
            JsonNode root = objectMapper.readTree(json);

            String category = textOrDefault(root, "estimatedCategory", opp.estimatedCategory());
            String gender = textOrDefault(root, "estimatedGenderTarget", opp.estimatedGenderTarget());
            String priority = textOrDefault(root, "priority", opp.priority());
            String narrative = textOrDefault(root, "narrative", NARRATIVE_FALLBACK);

            List<BrandSuggestion> brands = parseBrands(root);

            return new Opportunity(
                    opp.rank(), opp.normalizedKeyword(), opp.searchCount(),
                    opp.lastSearchedAt(), category, gender,
                    opp.estimatedLossJpy(), priority, narrative,
                    brands.isEmpty() ? opp.suggestedBrands() : brands,
                    opp.relatedExistingSkus());
        } catch (JsonProcessingException e) {
            log.warn("LLM レスポンスの JSON パース失敗。raw を narrative として使用: {}", e.getMessage());
            return withNarrative(opp, response);
        }
    }

    private static String textOrDefault(JsonNode root, String field, String defaultValue) {
        return root.has(field) ? root.get(field).asText() : defaultValue;
    }

    private static List<BrandSuggestion> parseBrands(JsonNode root) {
        if (!root.has(FIELD_SUGGESTED_BRANDS) || !root.get(FIELD_SUGGESTED_BRANDS).isArray()) {
            return List.of();
        }
        List<BrandSuggestion> brands = new ArrayList<>();
        for (JsonNode b : root.get(FIELD_SUGGESTED_BRANDS)) {
            brands.add(new BrandSuggestion(
                    b.has("brand") ? b.get("brand").asText() : "",
                    b.has("model") ? b.get("model").asText() : ""));
        }
        return brands;
    }

    private static Opportunity withNarrative(Opportunity opp, String narrative) {
        return new Opportunity(
                opp.rank(), opp.normalizedKeyword(), opp.searchCount(),
                opp.lastSearchedAt(), opp.estimatedCategory(), opp.estimatedGenderTarget(),
                opp.estimatedLossJpy(), opp.priority(), narrative,
                opp.suggestedBrands(), opp.relatedExistingSkus());
    }

    @SuppressWarnings("unused")
    private List<Opportunity> fallbackEnrich(List<Opportunity> opportunities, Throwable t) {
        log.warn("ゼロヒット LLM enrichment フォールバック: {}", t.getMessage());
        return opportunities;
    }

    private record AggregatedQuery(String keyword, long searchCount, Instant lastSearchedAt) {}
}
