package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.Map;

public record UserBehaviorResponse(
        String userId,
        Map<String, Object> metrics,
        Map<String, Object> preferences,
        String loyaltyTier,
        double totalSpent,
        Instant analyzedAt
) {}
