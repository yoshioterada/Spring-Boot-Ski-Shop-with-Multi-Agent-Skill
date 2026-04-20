package com.example.skishop.usermanagement.controller;

import com.example.skishop.usermanagement.dto.UserAnalyticsDto.UserAnalyticsSummary;
import com.example.skishop.usermanagement.service.UserAnalyticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理画面ユーザー分析エンドポイント。
 * gateway の {@code /api/v1/admin/users/**} ルートを再利用するため、
 * 既存の {@link AdminUserController} と同じプレフィックス配下に配置する。
 */
@RestController
@RequestMapping("/api/v1/admin/users/analytics")
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
public class UserAnalyticsController {

    private final UserAnalyticsService analyticsService;

    public UserAnalyticsController(UserAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<UserAnalyticsSummary> summary(
            @RequestParam(name = "days", defaultValue = "30") @Min(1) @Max(365) int days) {
        return ResponseEntity.ok(analyticsService.getSummary(days));
    }
}
