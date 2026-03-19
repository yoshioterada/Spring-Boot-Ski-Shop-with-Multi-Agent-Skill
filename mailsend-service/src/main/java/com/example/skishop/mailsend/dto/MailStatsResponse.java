package com.example.skishop.mailsend.dto;

import java.util.Map;

public record MailStatsResponse(
        long totalSent,
        long totalFailed,
        long totalPending,
        double successRate,
        Map<String, Long> sentByTemplate
) {
}
