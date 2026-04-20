package com.example.skishop.ai.service;

import com.example.skishop.ai.dto.SeasonalForecastRequest;
import com.example.skishop.ai.dto.SeasonalForecastRequest.Horizon;
import com.example.skishop.ai.dto.SeasonalForecastResponse;
import com.example.skishop.ai.dto.SeasonalForecastResponse.CategoryForecast;
import com.example.skishop.ai.forecast.SeasonalForecaster;
import com.example.skishop.ai.forecast.SeasonalForecaster.ForecastResult;
import com.example.skishop.ai.tool.AnalyticsToolFunctions;
import com.example.skishop.ai.util.PromptSanitizer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * F2 季節予測サービス (spec § 4.2.3 / impl-plan P4).
 * <p>
 * Step 1 (Java 確定計算) → Step 2 (LLM ナラティブ) の二段階予測。
 * <p>
 * ADR: D-F2-01 (二段階), D-F2-03 (信頼区間固定), D-F2-04 (assumptions 必須),
 * D-F2-05 (気象失敗時フェイルオープン)
 */
@Service
public class SeasonalForecastService {

    private static final Logger log = LoggerFactory.getLogger(SeasonalForecastService.class);

    private static final String NARRATIVE_SYSTEM_PROMPT = """
            あなたはスキー EC ショップの販売アナリスト AI です。
            以下の Step 1 予測結果と assumptions を基に、日本語で簡潔なナラティブを生成してください。
            - 数値を変更したり、新たな数値を生成したりしないこと（D-F2-01, D-COM-01）
            - 信頼区間の値を修正しないこと（D-F2-03）
            - 気象データが提供されている場合は「気象長期予報」として引用すること
            - 仕入の最終判断は管理者が行う。材料の提示のみ。
            - 400〜600 文字で簡潔に。
            """;

    private static final String NARRATIVE_FALLBACK = "現在 AI ナラティブを生成できません。Step 1 の数値データをご参照ください。";

    private final SeasonalForecaster forecaster;
    private final ChatClient chatClient;
    private final AnalyticsToolFunctions toolFunctions;
    private final LlmAuditService auditService;
    private final PromptSanitizer sanitizer;

    public SeasonalForecastService(
            SeasonalForecaster forecaster,
            ChatClient.Builder chatClientBuilder,
            AnalyticsToolFunctions toolFunctions,
            LlmAuditService auditService,
            PromptSanitizer sanitizer
    ) {
        this.forecaster = forecaster;
        this.chatClient = chatClientBuilder.build();
        this.toolFunctions = toolFunctions;
        this.auditService = auditService;
        this.sanitizer = sanitizer;
    }

    /**
     * F2 季節予測を生成する。
     */
    public SeasonalForecastResponse generateForecast(SeasonalForecastRequest request) {
        // Step 1: Java 確定計算
        ForecastResult step1 = forecaster.forecast(request.horizon(), request.categories());

        List<String> assumptions = new ArrayList<>(step1.assumptions());

        // 気象データ取得（considerWeather=true の場合、D-F2-05: 失敗時フェイルオープン）
        String weatherInfo = null;
        if (request.considerWeather()) {
            weatherInfo = fetchWeatherSafe(assumptions);
        }

        // Step 2: LLM ナラティブ生成
        String narrative = generateNarrative(step1, weatherInfo, assumptions);

        // assumptions が空なら最低 1 件追加（D-F2-04）
        if (assumptions.isEmpty()) {
            assumptions.add("予測はデフォルトパラメータに基づく推定です");
        }

        return new SeasonalForecastResponse(
                step1.horizonLabel(),
                Instant.now(),
                step1.categories(),
                narrative,
                assumptions
        );
    }

    /**
     * 気象 Tool 呼出（D-F2-05: 失敗時はフェイルオープン）。
     */
    private String fetchWeatherSafe(List<String> assumptions) {
        try {
            var result = toolFunctions.getWeatherForecast("kanto", 12);
            if (result != null && result.weeks() != null && !result.weeks().isEmpty()) {
                String summary = result.weeks().stream()
                        .map(w -> w.weekStart() + ": " + w.condition() + " " + w.avgTemp() + "℃")
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("データなし");
                assumptions.add("気象長期予報: " + summary);
                return summary;
            }
        } catch (Exception e) {
            log.warn("気象データ取得失敗、フェイルオープンで続行", e);
        }
        assumptions.add("気象データなし（取得失敗のためフェイルオープン）");
        return null;
    }

    /**
     * Step 2: LLM ナラティブ生成（CircuitBreaker + フォールバック）。
     */
    @CircuitBreaker(name = "azureOpenAi", fallbackMethod = "fallbackNarrative")
    String generateNarrative(ForecastResult step1, String weatherInfo, List<String> assumptions) {
        long start = System.currentTimeMillis();
        try {
            String userPrompt = buildNarrativePrompt(step1, weatherInfo);
            String wrapped = sanitizer.wrapAsUserMessage(userPrompt);

            String narrative = chatClient.prompt()
                    .system(NARRATIVE_SYSTEM_PROMPT)
                    .user(wrapped)
                    .call()
                    .content();

            long duration = System.currentTimeMillis() - start;
            String promptHash = sanitizer.hash(userPrompt);
            auditService.record("F2", promptHash, "seasonal-forecast",
                    userPrompt.length() / 4, // rough token estimate
                    narrative != null ? narrative.length() / 4 : 0,
                    duration, true, null);

            return narrative != null ? narrative : NARRATIVE_FALLBACK;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            auditService.record("F2", "error", "seasonal-forecast", 0, 0, duration, false, e.getMessage());
            throw e;
        }
    }

    @SuppressWarnings("unused")
    String fallbackNarrative(ForecastResult step1, String weatherInfo, List<String> assumptions, Throwable t) {
        log.warn("ナラティブ生成フォールバック: {}", t.getMessage());
        return NARRATIVE_FALLBACK;
    }

    private String buildNarrativePrompt(ForecastResult step1, String weatherInfo) {
        var sb = new StringBuilder();
        sb.append("## 予測期間: ").append(step1.horizonLabel()).append("\n\n");
        sb.append("### カテゴリ別予測\n");

        for (CategoryForecast cat : step1.categories()) {
            sb.append("- **").append(cat.categoryName()).append("** (").append(cat.categoryId()).append(")\n");
            sb.append("  - 予測需要: ").append(cat.predictedDemandUnits()).append(" 個\n");
            sb.append("  - 信頼区間: ").append(cat.confidenceLow()).append("〜").append(cat.confidenceHigh()).append("\n");
            sb.append("  - YoY: ").append(String.format("%+.1f%%", cat.yoyGrowth() * 100)).append("\n");
            if (!cat.topSkus().isEmpty()) {
                sb.append("  - 上位 SKU:\n");
                for (var sku : cat.topSkus()) {
                    sb.append("    - ").append(sku.sku())
                            .append(": 予測 ").append(sku.predictedUnits())
                            .append(", 在庫 ").append(sku.stockNow())
                            .append(", 推奨発注 ").append(sku.recommendOrder()).append("\n");
                }
            }
        }

        if (weatherInfo != null) {
            sb.append("\n### 気象長期予報\n").append(weatherInfo).append("\n");
        }

        sb.append("\n### assumptions\n");
        for (String a : step1.assumptions()) {
            sb.append("- ").append(a).append("\n");
        }

        sb.append("\n上記データに基づいて、仕入計画のアドバイスとリスク要因をナラティブとして生成してください。");
        return sb.toString();
    }
}
