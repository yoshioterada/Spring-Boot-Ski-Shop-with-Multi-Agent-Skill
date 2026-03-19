package com.example.skishop.point.dto;

import com.example.skishop.point.model.TierDefinition.TierLevel;

import java.util.UUID;

public record PointBalanceResponse(
        UUID userId,
        int totalEarned,
        int totalRedeemed,
        int currentBalance,
        int expiringPoints,
        TierLevel tierLevel,
        String tierName,
        double pointMultiplier,
        TierLevel nextTier,
        int pointsToNextTier
) {}
