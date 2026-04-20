package com.example.skishop.ai.forecast;

import com.example.skishop.ai.dto.SeasonalForecastRequest.Horizon;
import com.example.skishop.ai.dto.SeasonalForecastResponse.CategoryForecast;
import com.example.skishop.ai.dto.SeasonalForecastResponse.SkuForecast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.IntStream;

/**
 * F2 Step 1: 確定的予測 (Java 計算のみ、LLM 不使用).
 * <p>
 * 月次需要[m] = 過去 2 年同月平均 × 全体成長率 × 季節係数
 * <p>
 * ADR: D-F2-01 (二段階予測), D-F2-02 (季節係数マスタ化), D-F2-03 (信頼区間 ±1.5σ 固定)
 */
@Component
public class SeasonalForecaster {

    private static final Logger log = LoggerFactory.getLogger(SeasonalForecaster.class);
    private static final double CONFIDENCE_SIGMA = 1.5;
    private static final int LOOKBACK_YEARS = 2;

    private final WebClient salesWebClient;
    private final WebClient inventoryWebClient;

    public SeasonalForecaster(
            @Qualifier("salesWebClient") WebClient salesWebClient,
            @Qualifier("inventoryWebClient") WebClient inventoryWebClient
    ) {
        this.salesWebClient = salesWebClient;
        this.inventoryWebClient = inventoryWebClient;
    }

    /**
     * Step 1 計算結果。LLM に渡す前の確定値。
     */
    public record ForecastResult(
            String horizonLabel,
            List<CategoryForecast> categories,
            List<String> assumptions
    ) {}

    /**
     * カテゴリ別月次予測を計算する。
     *
     * @param horizon      予測期間
     * @param categoryIds  対象カテゴリ (null/empty = 全カテゴリ)
     * @return Step 1 の確定計算結果
     */
    public ForecastResult forecast(Horizon horizon, List<String> categoryIds) {
        List<String> assumptions = new ArrayList<>();
        assumptions.add("過去 " + LOOKBACK_YEARS + " シーズンの月次販売を基に需要を推定");

        // 季節係数取得（sales-management-service 経由、D-F2-02）
        Map<String, Map<Integer, Double>> weights = fetchSeasonalWeights();
        assumptions.add("季節係数は seasonal_weights マスタテーブルから取得");

        // 対象カテゴリ決定
        List<String> targetCategories = (categoryIds == null || categoryIds.isEmpty())
                ? new ArrayList<>(weights.keySet())
                : categoryIds;

        // 対象月リスト
        List<YearMonth> forecastMonths = resolveForecastMonths(horizon);
        String horizonLabel = buildHorizonLabel(horizon, forecastMonths);

        // 過去データ取得
        Map<String, Map<Integer, List<Long>>> historicalData = fetchHistoricalMonthlySales(targetCategories);

        // 全体成長率（直近 90 日 YoY）
        double growthRate = fetchYoyGrowthRate();
        assumptions.add("全体成長率: " + String.format("%.1f%%", (growthRate - 1.0) * 100) + "（直近 90 日 YoY）");

        // カテゴリ別予測
        List<CategoryForecast> forecasts = new ArrayList<>();
        for (String catId : targetCategories) {
            Map<Integer, Double> catWeights = weights.getOrDefault(catId, Map.of());
            Map<Integer, List<Long>> catHistory = historicalData.getOrDefault(catId, Map.of());

            int totalPredicted = 0;
            int totalLow = 0;
            int totalHigh = 0;
            int totalLastYear = 0;

            for (YearMonth ym : forecastMonths) {
                int month = ym.getMonthValue();
                double weight = catWeights.getOrDefault(month, 1.0);
                List<Long> pastValues = catHistory.getOrDefault(month, List.of());

                double avg = pastValues.isEmpty() ? 0 : pastValues.stream().mapToLong(Long::longValue).average().orElse(0);
                double predicted = avg * growthRate * weight;

                // 信頼区間: 残差の ±1.5σ (D-F2-03)
                double stddev = computeStddev(pastValues, avg);
                double margin = CONFIDENCE_SIGMA * stddev * weight;

                totalPredicted += Math.max(0, (int) Math.round(predicted));
                totalLow += Math.max(0, (int) Math.round(predicted - margin));
                totalHigh += Math.max(0, (int) Math.round(predicted + margin));

                // 昨年同月（YoY 計算用）
                if (!pastValues.isEmpty()) {
                    totalLastYear += pastValues.getLast();
                }
            }

            double yoyGrowth = totalLastYear > 0 ? (double) totalPredicted / totalLastYear - 1.0 : 0.0;

            // SKU 別レコメンド
            List<SkuForecast> topSkus = fetchTopSkuForecasts(catId, totalPredicted, forecastMonths.size());

            forecasts.add(new CategoryForecast(
                    catId,
                    categoryDisplayName(catId),
                    totalPredicted,
                    totalLow,
                    totalHigh,
                    Math.round(yoyGrowth * 1000.0) / 1000.0,
                    topSkus
            ));
        }

        return new ForecastResult(horizonLabel, forecasts, assumptions);
    }

    // ── Private helpers ──

    List<YearMonth> resolveForecastMonths(Horizon horizon) {
        LocalDate now = LocalDate.now();
        return switch (horizon) {
            case NEXT_MONTH -> List.of(YearMonth.from(now.plusMonths(1)));
            case NEXT_SEASON -> {
                // 冬季: 10月〜3月
                int year = now.getMonthValue() >= 4 ? now.getYear() : now.getYear() - 1;
                yield IntStream.rangeClosed(10, 15)
                        .mapToObj(m -> m <= 12 ? YearMonth.of(year, m) : YearMonth.of(year + 1, m - 12))
                        .toList();
            }
            case NEXT_YEAR -> IntStream.rangeClosed(1, 12)
                    .mapToObj(m -> YearMonth.of(now.getYear() + 1, m))
                    .toList();
        };
    }

    private String buildHorizonLabel(Horizon horizon, List<YearMonth> months) {
        return switch (horizon) {
            case NEXT_MONTH -> months.getFirst().getYear() + "年" + months.getFirst().getMonthValue() + "月";
            case NEXT_SEASON -> {
                YearMonth first = months.getFirst();
                YearMonth last = months.getLast();
                yield first.getYear() + "-" + last.getYear() + " 冬季シーズン (" + first.getMonthValue() + "月〜" + last.getMonthValue() + "月)";
            }
            case NEXT_YEAR -> (months.getFirst().getYear()) + "年 通年";
        };
    }

    double computeStddev(List<Long> values, double mean) {
        if (values.size() < 2) return mean * 0.2; // データ不足時フォールバック: 平均の 20%
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum() / (values.size() - 1);
        return Math.sqrt(variance);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<Integer, Double>> fetchSeasonalWeights() {
        try {
            var result = salesWebClient.get()
                    .uri("/api/v1/admin/orders/analytics/seasonal-weights")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            Map<String, Map<Integer, Double>> weights = new HashMap<>();
            if (result != null) {
                for (Map<String, Object> row : result) {
                    String catId = (String) row.get("categoryId");
                    int month = ((Number) row.get("month")).intValue();
                    double weight = ((Number) row.get("weight")).doubleValue();
                    weights.computeIfAbsent(catId, k -> new HashMap<>()).put(month, weight);
                }
            }
            return weights;
        } catch (Exception e) {
            log.warn("seasonal_weights 取得失敗、デフォルト weight=1.0 で続行", e);
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<Integer, List<Long>>> fetchHistoricalMonthlySales(List<String> categories) {
        try {
            var result = salesWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/admin/orders/analytics/monthly-sales")
                            .queryParam("years", LOOKBACK_YEARS)
                            .queryParam("categories", String.join(",", categories))
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Map<String, Long>>>() {})
                    .block();

            Map<String, Map<Integer, List<Long>>> historical = new HashMap<>();
            if (result != null) {
                for (var catEntry : result.entrySet()) {
                    Map<Integer, List<Long>> monthMap = new HashMap<>();
                    for (var monthEntry : catEntry.getValue().entrySet()) {
                        int month = Integer.parseInt(monthEntry.getKey());
                        monthMap.computeIfAbsent(month, k -> new ArrayList<>()).add(monthEntry.getValue());
                    }
                    historical.put(catEntry.getKey(), monthMap);
                }
            }
            return historical;
        } catch (Exception e) {
            log.warn("過去月次売上取得失敗、空データで続行", e);
            return Map.of();
        }
    }

    private double fetchYoyGrowthRate() {
        try {
            var result = salesWebClient.get()
                    .uri("/api/v1/admin/orders/analytics/yoy-growth?days=90")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
            if (result != null && result.containsKey("growthRate")) {
                return ((Number) result.get("growthRate")).doubleValue();
            }
        } catch (Exception e) {
            log.warn("YoY 成長率取得失敗、デフォルト 1.0 で続行", e);
        }
        return 1.0; // フォールバック: 成長なし
    }

    @SuppressWarnings("unchecked")
    private List<SkuForecast> fetchTopSkuForecasts(String categoryId, int totalPredicted, int months) {
        try {
            var result = inventoryWebClient.get()
                    .uri("/inventory?category=" + categoryId + "&limit=5")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            if (result == null) return List.of();

            return result.stream()
                    .map(item -> {
                        String sku = (String) item.get("sku");
                        int stockNow = ((Number) item.getOrDefault("quantity", 0)).intValue();
                        // SKU 別予測 = カテゴリ全体予測 / SKU 数（簡易配分）
                        int predictedUnits = Math.max(1, totalPredicted / Math.max(1, result.size()));
                        int recommendOrder = Math.max(0, predictedUnits - stockNow);
                        return new SkuForecast(sku, predictedUnits, stockNow, recommendOrder);
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("SKU 在庫取得失敗: category={}", categoryId, e);
            return List.of();
        }
    }

    private String categoryDisplayName(String categoryId) {
        return switch (categoryId) {
            case "cat-ski" -> "スキー本体";
            case "cat-boots" -> "スキーブーツ";
            case "cat-wear" -> "ウェア";
            case "cat-gloves" -> "グローブ";
            case "cat-goggles" -> "ゴーグル";
            case "cat-helmets" -> "ヘルメット";
            case "cat-poles" -> "ポール";
            default -> categoryId;
        };
    }
}
