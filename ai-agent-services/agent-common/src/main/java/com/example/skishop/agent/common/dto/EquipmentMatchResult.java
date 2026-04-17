package com.example.skishop.agent.common.dto;

import java.time.Instant;
import java.util.List;

public record EquipmentMatchResult(
        String userId,
        List<RankedProduct> recommendations,
        String aiRecommendationSummary,
        double totalEstimatedBudget,
        boolean withinBudget,
        Instant generatedAt
) {}
