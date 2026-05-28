package com.example.skishop.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RecommendationFeedbackRequest(
        String userId,
        String sessionId,
        String recommendationId,

        @NotBlank(message = "商品IDは必須です")
        String productId,

        @NotBlank(message = "フィードバック種別は必須です")
        @Pattern(regexp = "CLICK|CONVERSION", message = "feedbackType は CLICK または CONVERSION を指定してください")
        String feedbackType,

        String source
) {}
