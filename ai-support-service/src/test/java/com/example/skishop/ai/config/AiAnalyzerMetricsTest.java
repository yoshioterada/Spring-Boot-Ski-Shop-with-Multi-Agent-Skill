package com.example.skishop.ai.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiAnalyzerMetrics テスト (P7: 6 メトリクスの動作確認).
 */
class AiAnalyzerMetricsTest {

    private MeterRegistry registry;
    private AiAnalyzerMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiAnalyzerMetrics(registry);
    }

    @Test
    void incrementRequests_createsCounter() {
        metrics.incrementRequests("chat", "200");
        metrics.incrementRequests("chat", "200");
        var counter = registry.find("ai_analyzer_requests_total")
                .tag("endpoint", "chat").tag("status", "200").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2);
    }

    @Test
    void recordLatency_createsTimer() {
        var sample = metrics.startTimer();
        metrics.recordLatency(sample, "weekly-summary");
        var timer = registry.find("ai_analyzer_latency_seconds")
                .tag("endpoint", "weekly-summary").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void recordTokens_createsCounter() {
        metrics.recordTokens("chat", "gpt-5", 150);
        var counter = registry.find("ai_analyzer_tokens_total")
                .tag("endpoint", "chat").tag("model", "gpt-5").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(150);
    }

    @Test
    void recordCost_createsCounter() {
        metrics.recordCost("chat", 10000);
        var counter = registry.find("ai_analyzer_cost_usd_total")
                .tag("endpoint", "chat").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThan(0);
    }

    @Test
    void incrementCouponIssued_createsCounter() {
        metrics.incrementCouponIssued("CRITICAL");
        var counter = registry.find("dead_stock_coupon_issued_total")
                .tag("severity", "CRITICAL").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void incrementZeroHitDismissed_createsCounter() {
        metrics.incrementZeroHitDismissed("NOT_OUR_TARGET");
        var counter = registry.find("zero_hit_opportunities_dismissed_total")
                .tag("reason", "NOT_OUR_TARGET").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void allSixMetricsRegistered() {
        metrics.incrementRequests("test", "200");
        metrics.recordLatency(metrics.startTimer(), "test");
        metrics.recordTokens("test", "gpt-5", 100);
        metrics.recordCost("test", 100);
        metrics.incrementCouponIssued("HIGH");
        metrics.incrementZeroHitDismissed("OTHER");

        assertThat(registry.find("ai_analyzer_requests_total").counter()).isNotNull();
        assertThat(registry.find("ai_analyzer_latency_seconds").timer()).isNotNull();
        assertThat(registry.find("ai_analyzer_tokens_total").counter()).isNotNull();
        assertThat(registry.find("ai_analyzer_cost_usd_total").counter()).isNotNull();
        assertThat(registry.find("dead_stock_coupon_issued_total").counter()).isNotNull();
        assertThat(registry.find("zero_hit_opportunities_dismissed_total").counter()).isNotNull();
    }
}
