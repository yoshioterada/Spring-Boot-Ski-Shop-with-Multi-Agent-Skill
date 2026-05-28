package com.example.skishop.inventory.dto;

import java.time.Instant;

public record RecommendationFeedbackResponse(
        String id,
        String userId,
        String sessionId,
        String recommendationId,
        String productId,
        String feedbackType,
        String source,
        Instant createdAt
) {}
