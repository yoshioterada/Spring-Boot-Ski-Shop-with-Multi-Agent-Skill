package com.example.skishop.ai.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * AI Analyzer 共通プロパティ（spec § 22 ADR D-COM-06 / D-F1-01 / D-F3-02 / D-F4-01-04 / D-F5-04-05）.
 * <p>
 * すべての値は環境変数または application.properties から注入され、
 * コードへのハードコードを禁止する（D-F4-02 ほか）.
 */
@Validated
@ConfigurationProperties(prefix = "ai-analyzer")
public class AiAnalyzerProperties {

    private boolean enabled = true;

    @Min(1)
    private int cacheTtlMinutes = 60;

    @Min(1)
    private int maxHistoryTurns = 8;

    @Min(1)
    private int monthlyCostLimitUsd = 30;

    @NotNull
    private WeeklySummary weeklySummary = new WeeklySummary();

    @NotNull
    private DeadStock deadStock = new DeadStock();

    @NotNull
    private ZeroHit zeroHit = new ZeroHit();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getCacheTtlMinutes() { return cacheTtlMinutes; }
    public void setCacheTtlMinutes(int cacheTtlMinutes) { this.cacheTtlMinutes = cacheTtlMinutes; }

    public int getMaxHistoryTurns() { return maxHistoryTurns; }
    public void setMaxHistoryTurns(int maxHistoryTurns) { this.maxHistoryTurns = maxHistoryTurns; }

    public int getMonthlyCostLimitUsd() { return monthlyCostLimitUsd; }
    public void setMonthlyCostLimitUsd(int monthlyCostLimitUsd) { this.monthlyCostLimitUsd = monthlyCostLimitUsd; }

    public WeeklySummary getWeeklySummary() { return weeklySummary; }
    public void setWeeklySummary(WeeklySummary weeklySummary) { this.weeklySummary = weeklySummary; }

    public DeadStock getDeadStock() { return deadStock; }
    public void setDeadStock(DeadStock deadStock) { this.deadStock = deadStock; }

    public ZeroHit getZeroHit() { return zeroHit; }
    public void setZeroHit(ZeroHit zeroHit) { this.zeroHit = zeroHit; }

    public static class WeeklySummary {
        private String cron = "0 0 7 * * MON";
        private String zone = "Asia/Tokyo";
        @Min(1)
        private int refreshRateLimitMinutes = 60;

        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
        public String getZone() { return zone; }
        public void setZone(String zone) { this.zone = zone; }
        public int getRefreshRateLimitMinutes() { return refreshRateLimitMinutes; }
        public void setRefreshRateLimitMinutes(int refreshRateLimitMinutes) {
            this.refreshRateLimitMinutes = refreshRateLimitMinutes;
        }
    }

    public static class DeadStock {
        /** D-F4-01: 推奨割引率の上限。Java 側で強制クリップ. */
        @Min(0)
        private int discountPctMax = 40;

        /** D-F4-04: 同一 SKU の重複発行ブロック日数. */
        @Min(1)
        private int duplicateBlockDays = 30;

        /** D-F4-02: カテゴリ別弾力性係数（ハードコード禁止）. */
        private Map<String, Double> elasticity = new HashMap<>();

        public int getDiscountPctMax() { return discountPctMax; }
        public void setDiscountPctMax(int discountPctMax) { this.discountPctMax = discountPctMax; }
        public int getDuplicateBlockDays() { return duplicateBlockDays; }
        public void setDuplicateBlockDays(int duplicateBlockDays) {
            this.duplicateBlockDays = duplicateBlockDays;
        }
        public Map<String, Double> getElasticity() { return elasticity; }
        public void setElasticity(Map<String, Double> elasticity) { this.elasticity = elasticity; }

        /** カテゴリ未登録時は default を返却. */
        public double elasticityFor(String categoryId) {
            return elasticity.getOrDefault(categoryId,
                    elasticity.getOrDefault("default", -1.5));
        }
    }

    public static class ZeroHit {
        /** D-F5-04: 集計対象は >= 3 件のクエリのみ. */
        @Min(1)
        private int minSearchCount = 3;

        /** D-F5-05: dismiss は 90 日で自動失効. */
        @Min(1)
        private int dismissTtlDays = 90;

        /** D-F5-06: 機会損失推定の固定 conversion rate. */
        private double conversionRate = 0.05;

        public int getMinSearchCount() { return minSearchCount; }
        public void setMinSearchCount(int minSearchCount) { this.minSearchCount = minSearchCount; }
        public int getDismissTtlDays() { return dismissTtlDays; }
        public void setDismissTtlDays(int dismissTtlDays) { this.dismissTtlDays = dismissTtlDays; }
        public double getConversionRate() { return conversionRate; }
        public void setConversionRate(double conversionRate) { this.conversionRate = conversionRate; }
    }
}
