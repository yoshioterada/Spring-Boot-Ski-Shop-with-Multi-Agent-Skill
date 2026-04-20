package com.example.skishop.usermanagement.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 管理画面ユーザー分析タブ用 DTO 群。
 */
public final class UserAnalyticsDto {

    private UserAnalyticsDto() {}

    public record UserAnalyticsSummary(
            int days,
            long totalUsers,
            long newUsersInPeriod,
            long activeUsers,
            Map<String, Long> statusBreakdown,
            List<DailyRegistrationPoint> registrations,
            List<UserSegment> segments
    ) {}

    public record DailyRegistrationPoint(
            LocalDate date,
            long registrations
    ) {}

    public record UserSegment(
            String segmentId,
            String label,
            long count,
            double ratio
    ) {}
}
