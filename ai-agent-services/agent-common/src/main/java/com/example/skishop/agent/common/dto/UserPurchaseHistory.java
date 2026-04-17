package com.example.skishop.agent.common.dto;

import java.util.List;

public record UserPurchaseHistory(
        String userId,
        List<String> previouslyPurchasedCategories,
        String lastKnownSkillLevel,
        int totalPurchaseCount,
        String customerTier      // "BRONZE" | "SILVER" | "GOLD" | "PLATINUM"
) {}
