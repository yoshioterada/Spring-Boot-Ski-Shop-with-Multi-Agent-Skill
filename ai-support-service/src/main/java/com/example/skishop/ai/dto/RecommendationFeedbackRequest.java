package com.example.skishop.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record RecommendationFeedbackRequest(
        @NotBlank(message = "レコメンデーションIDは必須です")
        String recommendationId,

        String userId,

        boolean helpful,

        String comment
) {}
