package com.example.skishop.ai.service;

import com.example.skishop.ai.config.AiAnalyzerProperties;
import com.example.skishop.ai.dto.WeeklySummaryResponse;
import com.example.skishop.ai.dto.WeeklySummaryResponse.*;
import com.example.skishop.ai.model.WeeklySummary;
import com.example.skishop.ai.model.WeeklySummary.HighlightEntry;
import com.example.skishop.ai.repository.WeeklySummaryRepository;
import com.example.skishop.ai.util.PromptSanitizer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * F3 週次サマリーサービス (spec § 4.2 / impl-plan P1→P2).
 * <p>
 * sales / user サービスから KPI を取得し、wow / yoy を計算して MongoDB にキャッシュする.
 * P2: ChatClient でナラティブを生成し、PII チェック + 文字数検証を実施.
 */
@Service
public class WeeklySummaryService {

    private static final Logger log = LoggerFactory.getLogger(WeeklySummaryService.class);
    private static final String NARRATIVE_FALLBACK = "（AI ナラティブの生成に失敗しました。しばらくしてから再生成をお試しください。）";

    /** D-F3-04: 800 字 ±20% (640〜960) */
    private static final int NARRATIVE_MIN_LENGTH = 640;
    private static final int NARRATIVE_MAX_LENGTH = 960;

    /** PII 検出正規表現 (D-COM-02) */
    private static final Pattern PII_EMAIL = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PII_POSTAL = Pattern.compile("〒\\d{3}-?\\d{4}");
    private static final Pattern PII_PHONE = Pattern.compile("0\\d{1,4}-?\\d{1,4}-?\\d{4}");

    private final WeeklySummaryRepository repository;
    private final WebClient salesWebClient;
    private final WebClient userWebClient;
    private final AiAnalyzerProperties props;
    private final ChatClient chatClient;
    private final LlmAuditService auditService;
    private final PromptSanitizer promptSanitizer;
    private final ZeroHitOpportunityService zeroHitService;

    /** 手動 Refresh のレート制限用タイムスタンプ (D-F3-03) */
    private final ConcurrentHashMap<String, Instant> lastRefreshMap = new ConcurrentHashMap<>();

    public WeeklySummaryService(
            WeeklySummaryRepository repository,
            @Qualifier("salesWebClient") WebClient salesWebClient,
            @Qualifier("userWebClient") WebClient userWebClient,
            AiAnalyzerProperties props,
            ChatClient.Builder chatClientBuilder,
            LlmAuditService auditService,
            PromptSanitizer promptSanitizer,
            ZeroHitOpportunityService zeroHitService) {
        this.repository = repository;
        this.salesWebClient = salesWebClient;
        this.userWebClient = userWebClient;
        this.props = props;
        this.chatClient = chatClientBuilder.build();
        this.auditService = auditService;
        this.promptSanitizer = promptSanitizer;
        this.zeroHitService = zeroHitService;
    }

    /**
     * キャッシュ付き週次サマリー取得. MongoDB にヒットすればそれを返す.
     * ただし KPI がすべて 0 の場合はサービス起動時の取得失敗によるキャッシュと
     * みなし、削除して再生成する（スタートアップ競合状態の自己修復）.
     */
    public WeeklySummaryResponse getWeeklySummary() {
        LocalDate weekStart = currentWeekStart();
        Optional<WeeklySummary> cached = repository.findByWeekStartDate(weekStart);
        if (cached.isPresent()) {
            WeeklySummary doc = cached.get();
            // KPI がすべて 0 = 初回生成時にサービス呼び出し失敗の可能性がある
            if (doc.getRevenue() == 0 && doc.getOrders() == 0 && doc.getUniqueCustomers() == 0) {
                log.info("Cached weekly summary for {} has all-zero KPIs, regenerating...", weekStart);
                repository.deleteById(doc.getId());
                return generateAndSave(weekStart);
            }
            log.debug("Cache hit for weekStart={}", weekStart);
            return toResponse(doc, true);
        }
        log.info("Cache miss for weekStart={}, generating...", weekStart);
        return generateAndSave(weekStart);
    }

    /**
     * 管理者による強制再生成 (D-F3-03: 1 時間に 1 回制限).
     *
     * @return empty if rate-limited, else the fresh summary
     */
    public Optional<WeeklySummaryResponse> refreshWeeklySummary() {
        LocalDate weekStart = currentWeekStart();
        String key = weekStart.toString();
        Instant now = Instant.now();
        Instant last = lastRefreshMap.get(key);
        int limitMinutes = props.getWeeklySummary().getRefreshRateLimitMinutes();

        if (last != null && Duration.between(last, now).toMinutes() < limitMinutes) {
            log.warn("Rate limited: last refresh for {} was at {}", key, last);
            return Optional.empty();
        }

        lastRefreshMap.put(key, now);
        WeeklySummaryResponse response = generateAndSave(weekStart);
        return Optional.of(response);
    }

    /**
     * スケジューラから呼ばれる. 当週分がなければ生成.
     */
    public void ensureCurrentWeek() {
        LocalDate weekStart = currentWeekStart();
        if (repository.findByWeekStartDate(weekStart).isEmpty()) {
            log.info("Scheduler: generating weekly summary for {}", weekStart);
            generateAndSave(weekStart);
        } else {
            log.info("Scheduler: weekly summary for {} already exists", weekStart);
        }
    }

    // ========== Private ==========

    private WeeklySummaryResponse generateAndSave(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate prevWeekStart = weekStart.minusWeeks(1);
        LocalDate yoyWeekStart = weekStart.minusYears(1);

        // Fetch KPIs from sales & user services
        Map<String, Object> thisSales = fetchSalesSummary(weekStart, weekEnd);
        Map<String, Object> prevSales = fetchSalesSummary(prevWeekStart, prevWeekStart.plusDays(6));
        Map<String, Object> yoySales  = fetchSalesSummary(yoyWeekStart, yoyWeekStart.plusDays(6));
        Map<String, Object> thisUsers = fetchUserSummary(weekStart, weekEnd);
        Map<String, Object> prevUsers = fetchUserSummary(prevWeekStart, prevWeekStart.plusDays(6));
        Map<String, Object> yoyUsers  = fetchUserSummary(yoyWeekStart, yoyWeekStart.plusDays(6));

        long revenue   = toLong(thisSales, "totalRevenue");
        long ordersCnt = toLong(thisSales, "totalOrders");
        long uu        = toLong(thisUsers, "activeUsers");
        long aov       = ordersCnt > 0 ? revenue / ordersCnt : 0;

        long prevRevenue   = toLong(prevSales, "totalRevenue");
        long prevOrders    = toLong(prevSales, "totalOrders");
        long prevUu        = toLong(prevUsers, "activeUsers");
        long prevAov       = prevOrders > 0 ? prevRevenue / prevOrders : 0;

        long yoyRevenue    = toLong(yoySales, "totalRevenue");
        long yoyOrders     = toLong(yoySales, "totalOrders");
        long yoyUu         = toLong(yoyUsers, "activeUsers");
        long yoyAov        = yoyOrders > 0 ? yoyRevenue / yoyOrders : 0;

        // Compute wow / yoy ratios
        double revenueWow = ratio(revenue, prevRevenue);
        double revenueYoy = ratio(revenue, yoyRevenue);
        double ordersWow  = ratio(ordersCnt, prevOrders);
        double ordersYoy  = ratio(ordersCnt, yoyOrders);
        double uuWow      = ratio(uu, prevUu);
        double uuYoy      = ratio(uu, yoyUu);
        double aovWow     = ratio(aov, prevAov);
        double aovYoy     = ratio(aov, yoyAov);

        // Build highlights (P1: placeholder)
        List<HighlightEntry> highlights = buildHighlights(revenue, prevRevenue, ordersCnt, prevOrders);

        // Generate AI narrative (P2: ChatClient 呼出, D-F3-04: 800 字 ±20%)
        String narrative = generateNarrative(weekStart, weekEnd,
                revenue, ordersCnt, uu, aov,
                revenueWow, ordersWow, uuWow, aovWow,
                revenueYoy, ordersYoy, uuYoy, aovYoy,
                highlights);

        // Persist to MongoDB
        WeeklySummary doc = new WeeklySummary();
        doc.setWeekStartDate(weekStart);
        doc.setWeekEndDate(weekEnd);
        doc.setGeneratedAt(Instant.now());
        doc.setRevenue(revenue);
        doc.setOrders(ordersCnt);
        doc.setUniqueCustomers(uu);
        doc.setAvgOrderValue(aov);
        doc.setRevenueWow(revenueWow);
        doc.setRevenueYoy(revenueYoy);
        doc.setOrdersWow(ordersWow);
        doc.setOrdersYoy(ordersYoy);
        doc.setUniqueCustomersWow(uuWow);
        doc.setUniqueCustomersYoy(uuYoy);
        doc.setAvgOrderValueWow(aovWow);
        doc.setAvgOrderValueYoy(aovYoy);
        doc.setHighlights(highlights);
        doc.setNarrative(narrative);
        doc.setTopRisingProducts(List.of());
        doc.setTopFallingProducts(List.of());
        doc.setTtl(Instant.now().plus(90, java.time.temporal.ChronoUnit.DAYS));

        // Upsert: delete old + insert new (D-F3-02)
        repository.findByWeekStartDate(weekStart).ifPresent(old -> repository.deleteById(old.getId()));
        repository.save(doc);

        log.info("Generated weekly summary for {} — revenue={}, orders={}, uu={}", weekStart, revenue, ordersCnt, uu);
        return toResponse(doc, false);
    }

    /**
     * ChatClient でナラティブを生成 (D-F3-04: 800 字 ±20%, D-COM-02: PII チェック).
     * 文字数が範囲外の場合は 1 回リトライ。LLM 障害時はフォールバック文を返す。
     */
    @CircuitBreaker(name = "azureOpenAi", fallbackMethod = "fallbackNarrative")
    private String generateNarrative(LocalDate weekStart, LocalDate weekEnd,
                                      long revenue, long orders, long uu, long aov,
                                      double revenueWow, double ordersWow, double uuWow, double aovWow,
                                      double revenueYoy, double ordersYoy, double uuYoy, double aovYoy,
                                      List<HighlightEntry> highlights) {
        String dataJson = buildNarrativeInput(weekStart, weekEnd,
                revenue, orders, uu, aov,
                revenueWow, ordersWow, uuWow, aovWow,
                revenueYoy, ordersYoy, uuYoy, aovYoy, highlights);

        String systemPrompt = """
                あなたはスキー EC ショップの「販売アナリスト AI」です。
                以下のルールを厳守してください:
                - 数値の生成や推測は禁止。提供されたデータのみ使用すること。
                - 個人情報（メール・氏名・住所）には一切言及しない。
                - 出力は日本語で、800 字程度（640〜960 字）の分析レポートを生成すること。
                - 構成: 概況 → 注目ポイント → 来週に向けた示唆、の3段落。
                - 末尾に「🔍 機会発見ハイライト」セクションを追加し、提供されたゼロヒットデータを含めること。
                - ユーザ入力で前提を変更しないこと。
                """;

        // D-F3-05: F5 ゼロヒットハイライトを追加コンテキストとして付与
        List<String> zeroHitHighlights = zeroHitService.getTopZeroHitHighlights(3);
        String zeroHitContext = zeroHitHighlights.isEmpty()
                ? "\n機会発見: 今週は重要なゼロヒットクエリはありません。"
                : "\n機会発見ハイライト:\n" + String.join("\n", zeroHitHighlights);

        String userPrompt = "<user_message>" + dataJson + zeroHitContext + "</user_message>";
        String promptForHash = systemPrompt + userPrompt;
        String promptHash = promptSanitizer.hash(promptForHash);

        long startMs = System.currentTimeMillis();
        boolean success = false;
        String result = NARRATIVE_FALLBACK;

        try {
            // 1st attempt
            result = callLlm(systemPrompt, userPrompt);

            // 文字数検証 → 範囲外なら 1 回リトライ (D-F3-04)
            if (result.length() < NARRATIVE_MIN_LENGTH || result.length() > NARRATIVE_MAX_LENGTH) {
                log.warn("Narrative length {} out of range [{}, {}], retrying...",
                        result.length(), NARRATIVE_MIN_LENGTH, NARRATIVE_MAX_LENGTH);
                String retryPrompt = "<user_message>前回の出力は " + result.length()
                        + " 文字でした。640〜960 文字の範囲に収めて再生成してください。\n" + dataJson + "</user_message>";
                result = callLlm(systemPrompt, retryPrompt);
            }

            // PII チェック (D-COM-02)
            result = stripPii(result);
            success = true;
        } catch (Exception e) {
            log.error("Narrative generation failed: {}", e.getMessage());
            result = NARRATIVE_FALLBACK;
        } finally {
            long durationMs = System.currentTimeMillis() - startMs;
            auditService.record("F3", promptHash, "weekly-summary-narrative",
                    0, 0, durationMs, success, success ? null : "generation failed");
        }
        return result;
    }

    @SuppressWarnings("unused")
    private String fallbackNarrative(LocalDate weekStart, LocalDate weekEnd,
                                      long revenue, long orders, long uu, long aov,
                                      double revenueWow, double ordersWow, double uuWow, double aovWow,
                                      double revenueYoy, double ordersYoy, double uuYoy, double aovYoy,
                                      List<HighlightEntry> highlights, Throwable t) {
        log.warn("CircuitBreaker fallback for narrative: {}", t.getMessage());
        return NARRATIVE_FALLBACK;
    }

    private String callLlm(String systemPrompt, String userPrompt) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }

    private String buildNarrativeInput(LocalDate weekStart, LocalDate weekEnd,
                                        long revenue, long orders, long uu, long aov,
                                        double revenueWow, double ordersWow, double uuWow, double aovWow,
                                        double revenueYoy, double ordersYoy, double uuYoy, double aovYoy,
                                        List<HighlightEntry> highlights) {
        StringBuilder sb = new StringBuilder();
        sb.append("週次レポートデータ (").append(weekStart).append(" 〜 ").append(weekEnd).append(")\n");
        sb.append("売上: ¥").append(String.format("%,d", revenue));
        sb.append(" (WoW: ").append(formatPct(revenueWow)).append(", YoY: ").append(formatPct(revenueYoy)).append(")\n");
        sb.append("注文数: ").append(String.format("%,d", orders));
        sb.append(" (WoW: ").append(formatPct(ordersWow)).append(", YoY: ").append(formatPct(ordersYoy)).append(")\n");
        sb.append("ユニーク顧客数: ").append(String.format("%,d", uu));
        sb.append(" (WoW: ").append(formatPct(uuWow)).append(", YoY: ").append(formatPct(uuYoy)).append(")\n");
        sb.append("平均注文額: ¥").append(String.format("%,d", aov));
        sb.append(" (WoW: ").append(formatPct(aovWow)).append(", YoY: ").append(formatPct(aovYoy)).append(")\n");
        if (highlights != null && !highlights.isEmpty()) {
            sb.append("ハイライト:\n");
            highlights.forEach(h -> sb.append("- ").append(h.icon()).append(" ").append(h.text()).append("\n"));
        }
        return sb.toString();
    }

    /** PII を除去 (D-COM-02) */
    private String stripPii(String text) {
        String cleaned = PII_EMAIL.matcher(text).replaceAll("[REDACTED]");
        cleaned = PII_POSTAL.matcher(cleaned).replaceAll("[REDACTED]");
        cleaned = PII_PHONE.matcher(cleaned).replaceAll("[REDACTED]");
        return cleaned;
    }

    private static String formatPct(double ratio) {
        return String.format("%+.1f%%", ratio * 100);
    }

    @CircuitBreaker(name = "salesService", fallbackMethod = "fallbackSalesSummary")
    @Retry(name = "serviceCall")
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchSalesSummary(LocalDate from, LocalDate to) {
        try {
            int days = (int) java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
            return salesWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/admin/orders/analytics/summary")
                            .queryParam("days", days)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(8));
        } catch (Exception e) {
            log.warn("Failed to fetch sales summary for {}-{}: {}", from, to, e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unused")
    private Map<String, Object> fallbackSalesSummary(LocalDate from, LocalDate to, Throwable t) {
        log.warn("Sales service circuit breaker open for {}-{}: {}", from, to, t.getMessage());
        return Map.of();
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "fallbackUserSummary")
    @Retry(name = "serviceCall")
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchUserSummary(LocalDate from, LocalDate to) {
        try {
            int days = (int) java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
            return userWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/admin/users/analytics/summary")
                            .queryParam("days", days)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(8));
        } catch (Exception e) {
            log.warn("Failed to fetch user summary for {}-{}: {}", from, to, e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unused")
    private Map<String, Object> fallbackUserSummary(LocalDate from, LocalDate to, Throwable t) {
        log.warn("User service circuit breaker open for {}-{}: {}", from, to, t.getMessage());
        return Map.of();
    }

    private List<HighlightEntry> buildHighlights(long revenue, long prevRevenue, long orders, long prevOrders) {
        List<HighlightEntry> list = new ArrayList<>();
        double revenueChange = ratio(revenue, prevRevenue);
        if (revenueChange > 0.1) {
            list.add(new HighlightEntry("📈", String.format("売上が前週比 +%.0f%% と好調", revenueChange * 100)));
        } else if (revenueChange < -0.1) {
            list.add(new HighlightEntry("📉", String.format("売上が前週比 %.0f%%。要因分析を推奨", revenueChange * 100)));
        }
        double ordersChange = ratio(orders, prevOrders);
        if (ordersChange > 0.1) {
            list.add(new HighlightEntry("📈", String.format("注文数が前週比 +%.0f%%", ordersChange * 100)));
        } else if (ordersChange < -0.1) {
            list.add(new HighlightEntry("⚠️", String.format("注文数が前週比 %.0f%%。在庫確認を推奨", ordersChange * 100)));
        }
        if (list.isEmpty()) {
            list.add(new HighlightEntry("ℹ️", "今週は大きな変動はありません"));
        }
        return list;
    }

    static double ratio(long current, long previous) {
        if (previous == 0) return current > 0 ? 1.0 : 0.0;
        return (double) (current - previous) / previous;
    }

    static long toLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    static LocalDate currentWeekStart() {
        return LocalDate.now(ZoneId.of("Asia/Tokyo"))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private WeeklySummaryResponse toResponse(WeeklySummary doc, boolean cacheHit) {
        return new WeeklySummaryResponse(
                doc.getWeekStartDate(),
                doc.getWeekEndDate(),
                doc.getGeneratedAt(),
                cacheHit,
                new Kpis(
                        new KpiValue(doc.getRevenue(), doc.getRevenueWow(), doc.getRevenueYoy()),
                        new KpiValue(doc.getOrders(), doc.getOrdersWow(), doc.getOrdersYoy()),
                        new KpiValue(doc.getUniqueCustomers(), doc.getUniqueCustomersWow(), doc.getUniqueCustomersYoy()),
                        new KpiValue(doc.getAvgOrderValue(), doc.getAvgOrderValueWow(), doc.getAvgOrderValueYoy())
                ),
                doc.getHighlights() == null ? List.of() :
                        doc.getHighlights().stream()
                                .map(h -> new Highlight(h.icon(), h.text()))
                                .toList(),
                doc.getNarrative(),
                doc.getTopRisingProducts() == null ? List.of() :
                        doc.getTopRisingProducts().stream()
                                .map(p -> new ProductMovement(p.sku(), p.name(), p.sales(), p.changeRate()))
                                .toList(),
                doc.getTopFallingProducts() == null ? List.of() :
                        doc.getTopFallingProducts().stream()
                                .map(p -> new ProductMovement(p.sku(), p.name(), p.sales(), p.changeRate()))
                                .toList()
        );
    }
}
