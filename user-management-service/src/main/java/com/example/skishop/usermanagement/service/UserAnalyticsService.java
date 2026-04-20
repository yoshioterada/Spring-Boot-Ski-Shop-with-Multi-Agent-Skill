package com.example.skishop.usermanagement.service;

import com.example.skishop.usermanagement.dto.UserAnalyticsDto.DailyRegistrationPoint;
import com.example.skishop.usermanagement.dto.UserAnalyticsDto.UserAnalyticsSummary;
import com.example.skishop.usermanagement.dto.UserAnalyticsDto.UserSegment;
import com.example.skishop.usermanagement.model.UserProfile;
import com.example.skishop.usermanagement.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ユーザー分析サービス。
 * - 期間 (days) を入力に user_profiles を集計して管理画面ダッシュボード用 DTO を生成する。
 */
@Service
@Transactional(readOnly = true)
public class UserAnalyticsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private final UserProfileRepository repository;

    public UserAnalyticsService(UserProfileRepository repository) {
        this.repository = repository;
    }

    public UserAnalyticsSummary getSummary(int days) {
        int safeDays = clampDays(days);
        Instant since = Instant.now().minus(safeDays, ChronoUnit.DAYS);

        long totalUsers = repository.count();
        long newUsers = repository.countNewUsersSince(since);

        Map<String, Long> statusBreakdown = new LinkedHashMap<>();
        long active = 0L;
        for (Object[] row : repository.countByStatus()) {
            String status = String.valueOf(row[0]);
            long count = toLong(row[1]);
            statusBreakdown.put(status, count);
            if (UserProfile.UserStatus.ACTIVE.name().equals(status)) {
                active = count;
            }
        }

        List<DailyRegistrationPoint> registrations = mapDailyRegistrations(
                repository.countDailyRegistrations(since), safeDays);

        List<UserSegment> segments = buildSegments(statusBreakdown, totalUsers);

        return new UserAnalyticsSummary(safeDays, totalUsers, newUsers, active,
                statusBreakdown, registrations, segments);
    }

    private List<DailyRegistrationPoint> mapDailyRegistrations(List<Object[]> rows, int days) {
        Map<LocalDate, Long> map = new HashMap<>(rows.size());
        for (Object[] row : rows) {
            map.put(toLocalDate(row[0]), toLong(row[1]));
        }
        LocalDate today = LocalDate.now(ZONE);
        List<DailyRegistrationPoint> result = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            result.add(new DailyRegistrationPoint(d, map.getOrDefault(d, 0L)));
        }
        return result;
    }

    private List<UserSegment> buildSegments(Map<String, Long> statusBreakdown, long totalUsers) {
        if (totalUsers == 0) return List.of();
        List<UserSegment> list = new ArrayList<>(statusBreakdown.size());
        for (Map.Entry<String, Long> e : statusBreakdown.entrySet()) {
            String label = labelFor(e.getKey());
            double ratio = (double) e.getValue() / (double) totalUsers;
            list.add(new UserSegment(e.getKey(), label, e.getValue(), ratio));
        }
        return list;
    }

    private String labelFor(String status) {
        return switch (status) {
            case "ACTIVE" -> "アクティブ";
            case "PENDING_VERIFICATION" -> "未認証";
            case "SUSPENDED" -> "停止中";
            case "DEACTIVATED" -> "退会済み";
            default -> status;
        };
    }

    private int clampDays(int days) {
        if (days <= 0) return 30;
        return Math.min(days, 365);
    }

    private static LocalDate toLocalDate(Object o) {
        if (o instanceof Date d) return d.toLocalDate();
        if (o instanceof LocalDate ld) return ld;
        if (o instanceof java.time.LocalDateTime ldt) return ldt.toLocalDate();
        if (o instanceof Instant i) return i.atZone(ZONE).toLocalDate();
        throw new IllegalArgumentException("Unsupported date type: " + (o == null ? "null" : o.getClass()));
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }
}
