package com.example.skishop.agent.common.dto;

import java.time.Instant;

public record CustomerIntentResult(
        String userId,
        String sessionId,
        IntentCategory primaryIntent,
        ExtractedConstraints constraints,
        UserPurchaseHistory purchaseHistory,
        String intentSummary,
        double confidenceScore,
        Instant analyzedAt
) {}
