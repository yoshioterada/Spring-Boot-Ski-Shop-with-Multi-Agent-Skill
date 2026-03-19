package com.example.skishop.ai.controller;

import com.example.skishop.ai.dto.*;
import com.example.skishop.ai.service.AnalyticsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/user-behavior")
    public ResponseEntity<UserBehaviorResponse> getUserBehavior(
            @RequestParam String userId) {
        return ResponseEntity.ok(analyticsService.getUserBehavior(userId));
    }

    @GetMapping("/sales-forecast")
    public ResponseEntity<SalesForecastResponse> getSalesForecast(
            @RequestParam String productId,
            @RequestParam(defaultValue = "WEEKLY") String period,
            @RequestParam(defaultValue = "4") int horizon) {
        return ResponseEntity.ok(analyticsService.getSalesForecast(productId, period, horizon));
    }

    @GetMapping("/product-performance")
    public ResponseEntity<ProductPerformanceResponse> getProductPerformance(
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(analyticsService.getProductPerformance(productId, category));
    }

    @GetMapping("/trends")
    public ResponseEntity<TrendAnalysisResponse> getTrends(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String timeframe) {
        return ResponseEntity.ok(analyticsService.getTrends(category, timeframe));
    }

    @GetMapping("/customer-segments")
    public ResponseEntity<CustomerSegmentResponse> getCustomerSegments(
            @RequestParam(required = false) String segmentType) {
        return ResponseEntity.ok(analyticsService.getCustomerSegments(segmentType));
    }

    @PostMapping("/custom-report")
    public ResponseEntity<CustomReportResponse> generateCustomReport(
            @Valid @RequestBody CustomReportRequest request) {
        return ResponseEntity.ok(analyticsService.generateCustomReport(request));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestParam(defaultValue = "overview") String dashboardType) {
        return ResponseEntity.ok(analyticsService.getDashboard(dashboardType));
    }
}
