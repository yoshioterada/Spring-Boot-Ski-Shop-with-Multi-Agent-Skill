package com.example.skishop.point.dto;

import com.example.skishop.point.model.TierDefinition.TierLevel;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UserTierResponse(
        UUID id,
        UUID userId,
        TierLevel tierLevel,
        String tierName,
        int totalEarned,
        int currentBalance,
        double pointMultiplier,
        Map<String, Object> benefits,
        TierLevel nextTier,
        int pointsToNextTier,
        Instant tierUpgradedAt,
        Instant createdAt
) {}
