package com.example.skishop.inventory.controller;

import com.example.skishop.inventory.dto.SearchAnalyticsDto.SearchAnalyticsSummary;
import com.example.skishop.inventory.service.SearchAnalyticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 検索分析エンドポイント。
 * gateway の {@code /api/v1/products/**} ルートを再利用するため、
 * パスは {@code /api/v1/products/analytics/**} 配下に配置する。
 */
@RestController
@RequestMapping("/api/v1/products/analytics")
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
public class SearchAnalyticsController {

    private final SearchAnalyticsService analyticsService;

    public SearchAnalyticsController(SearchAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/search-summary")
    public ResponseEntity<SearchAnalyticsSummary> searchSummary(
            @RequestParam(name = "days", defaultValue = "30") @Min(1) @Max(365) int days,
            @RequestParam(name = "limit", defaultValue = "10") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(analyticsService.getSummary(days, limit));
    }

    /**
     * F5 機会発見レーダー用: ゼロヒット検索クエリの集約を返す。
     * ai-support-service の ZeroHitOpportunityService から内部 API キーで呼び出される。
     */
    @GetMapping("/zero-hit-queries")
    public ResponseEntity<List<Map<String, Object>>> zeroHitQueries(
            @RequestParam(name = "days", defaultValue = "30") @Min(1) @Max(365) int days,
            @RequestParam(name = "minCount", defaultValue = "3") @Min(1) int minCount,
            @RequestParam(name = "limit", defaultValue = "50") @Min(1) @Max(200) int limit) {
        return ResponseEntity.ok(analyticsService.getZeroHitAggregation(days, minCount, limit));
    }
}
