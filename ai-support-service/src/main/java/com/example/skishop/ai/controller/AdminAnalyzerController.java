package com.example.skishop.ai.controller;

import com.example.skishop.ai.dto.*;
import com.example.skishop.ai.service.AdminAnalyzerService;
import com.example.skishop.ai.service.DeadStockService;
import com.example.skishop.ai.service.SeasonalForecastService;
import com.example.skishop.ai.service.WeeklySummaryService;
import com.example.skishop.ai.service.ZeroHitOpportunityService;

import java.util.Map;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 分析管理者用 API (spec § 4.1 / D-COM-04).
 * F1 チャット (SSE) + F2 季節予測 + F3 週次サマリー + F4 滞留在庫レーダー + F5 機会発見レーダー.
 */
@RestController
@RequestMapping("/api/v1/admin/ai-analyzer")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyzerController {

    private static final Logger log = LoggerFactory.getLogger(AdminAnalyzerController.class);
    private static final long SSE_TIMEOUT = 60_000L;

    private final WeeklySummaryService weeklySummaryService;
    private final AdminAnalyzerService analyzerService;
    private final SeasonalForecastService forecastService;
    private final DeadStockService deadStockService;
    private final ZeroHitOpportunityService zeroHitService;

    public AdminAnalyzerController(WeeklySummaryService weeklySummaryService,
                                    AdminAnalyzerService analyzerService,
                                    SeasonalForecastService forecastService,
                                    DeadStockService deadStockService,
                                    ZeroHitOpportunityService zeroHitService) {
        this.weeklySummaryService = weeklySummaryService;
        this.analyzerService = analyzerService;
        this.forecastService = forecastService;
        this.deadStockService = deadStockService;
        this.zeroHitService = zeroHitService;
    }

    /**
     * F1 対話分析 — SSE ストリーム (spec § 4.2.1).
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequest request,
                           @AuthenticationPrincipal UserDetails user) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String userId = user != null ? user.getUsername() : "anonymous";
        log.info("Chat stream started: sessionId={}, userId={}", request.sessionId(), userId);

        // 非同期で実行
        Thread.ofVirtual().name("chat-stream-" + userId).start(
                () -> analyzerService.chatStream(request, emitter, userId));
        return emitter;
    }

    /**
     * 週次サマリーの取得 (キャッシュヒット時 &lt; 200ms).
     */
    @GetMapping("/weekly-summary")
    public ResponseEntity<WeeklySummaryResponse> getWeeklySummary() {
        return ResponseEntity.ok(weeklySummaryService.getWeeklySummary());
    }

    /**
     * 週次サマリーの強制再生成 (D-F3-03: 1 時間に 1 回制限).
     *
     * @return 200 if refreshed, 429 if rate-limited
     */
    @PostMapping("/weekly-summary/refresh")
    public ResponseEntity<WeeklySummaryResponse> refreshWeeklySummary() {
        return weeklySummaryService.refreshWeeklySummary()
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.info("Refresh rate-limited");
                    return ResponseEntity.status(429).build();
                });
    }

    /**
     * F2 季節予測 (spec § 4.2.3 / D-F2-01 二段階予測).
     */
    @PostMapping("/seasonal-forecast")
    public ResponseEntity<SeasonalForecastResponse> seasonalForecast(
            @Valid @RequestBody SeasonalForecastRequest request) {
        return ResponseEntity.ok(forecastService.generateForecast(request));
    }

    /**
     * F4 滞留在庫一覧 (spec § 19.4).
     */
    @GetMapping("/dead-stock")
    public ResponseEntity<DeadStockResponse> getDeadStock() {
        return ResponseEntity.ok(deadStockService.getDeadStock());
    }

    /**
     * F4 クーポン発行 (D-F4-03: approverUserId = JWT subject).
     */
    @PostMapping("/dead-stock/{sku}/issue-coupon")
    public ResponseEntity<Map<String, Object>> issueCoupon(
            @PathVariable String sku,
            @Valid @RequestBody IssueCouponRequest request,
            @AuthenticationPrincipal UserDetails user) {
        String approverUserId = user != null ? user.getUsername() : null;
        if (approverUserId == null) {
            return ResponseEntity.status(403).build();
        }
        var result = deadStockService.issueCoupon(sku, request.discountPct(), request.memo(), approverUserId);
        return ResponseEntity.ok(result);
    }

    /**
     * F5 ゼロヒット機会一覧 (spec § 20.5.1).
     */
    @GetMapping("/zero-hit-opportunities")
    public ResponseEntity<ZeroHitOpportunityResponse> getZeroHitOpportunities(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "1") int minSearchCount,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(zeroHitService.getOpportunities(days, minSearchCount, category, limit));
    }

    /**
     * F5 dismiss (spec § 20.5.2, D-F5-05).
     */
    @PostMapping("/zero-hit-opportunities/{rank}/dismiss")
    public ResponseEntity<Void> dismissZeroHit(
            @PathVariable int rank,
            @Valid @RequestBody ZeroHitDismissRequest request,
            @AuthenticationPrincipal UserDetails user) {
        String userId = user != null ? user.getUsername() : "anonymous";
        var currentData = zeroHitService.getOpportunities(30, 3, null, 100);
        zeroHitService.dismiss(rank, request.reason(), request.memo(), userId, currentData);
        return ResponseEntity.ok().build();
    }
}
