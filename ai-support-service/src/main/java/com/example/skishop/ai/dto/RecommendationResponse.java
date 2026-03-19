package com.example.skishop.ai.dto;

import com.example.skishop.ai.model.ProductRecommendation;
import com.example.skishop.ai.model.Recommendation.RecommendationType;

import java.time.Instant;
import java.util.List;

public record RecommendationResponse(
        String id,
        String userId,
        RecommendationType type,
        List<ProductRecommendation> products,
        double confidenceScore,
        String algorithm,
        Instant createdAt
) {}
