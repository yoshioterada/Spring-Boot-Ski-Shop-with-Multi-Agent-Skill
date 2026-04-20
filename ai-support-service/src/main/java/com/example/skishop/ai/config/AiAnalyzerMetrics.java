package com.example.skishop.ai.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI Analyzer メトリクス (P7: spec § 10 / impl-plan P7.2.1).
 * <p>
 * 6 メトリクス:
 * - ai_analyzer_requests_total{endpoint, status}
 * - ai_analyzer_latency_seconds{endpoint}
 * - ai_analyzer_tokens_total{endpoint, model}
 * - ai_analyzer_cost_usd_total{endpoint}
 * - dead_stock_coupon_issued_total{severity}
 * - zero_hit_opportunities_dismissed_total{reason}
 */
@Component
public class AiAnalyzerMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

    public AiAnalyzerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** ai_analyzer_requests_total */
    public void incrementRequests(String endpoint, String status) {
        Counter.builder("ai_analyzer_requests_total")
                .description("Total AI Analyzer requests")
                .tag("endpoint", endpoint)
                .tag("status", status)
                .register(registry)
                .increment();
    }

    /** ai_analyzer_latency_seconds */
    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordLatency(Timer.Sample sample, String endpoint) {
        var timer = timers.computeIfAbsent(endpoint, ep ->
                Timer.builder("ai_analyzer_latency_seconds")
                        .description("AI Analyzer endpoint latency")
                        .tag("endpoint", ep)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(registry));
        sample.stop(timer);
    }

    /** ai_analyzer_tokens_total */
    public void recordTokens(String endpoint, String model, long tokens) {
        Counter.builder("ai_analyzer_tokens_total")
                .description("Total tokens consumed by AI Analyzer")
                .tag("endpoint", endpoint)
                .tag("model", model)
                .register(registry)
                .increment(tokens);
    }

    /** ai_analyzer_cost_usd_total (approx: $0.01 per 1K tokens) */
    public void recordCost(String endpoint, long tokens) {
        Counter.builder("ai_analyzer_cost_usd_total")
                .description("Estimated LLM cost in USD")
                .tag("endpoint", endpoint)
                .register(registry)
                .increment(tokens * 0.00001);
    }

    /** dead_stock_coupon_issued_total */
    public void incrementCouponIssued(String severity) {
        Counter.builder("dead_stock_coupon_issued_total")
                .description("Dead stock coupons issued")
                .tag("severity", severity)
                .register(registry)
                .increment();
    }

    /** zero_hit_opportunities_dismissed_total */
    public void incrementZeroHitDismissed(String reason) {
        Counter.builder("zero_hit_opportunities_dismissed_total")
                .description("Zero-hit opportunities dismissed")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }
}
