package com.example.skishop.ai.dto;

import com.example.skishop.ai.model.ProductRecommendation;

import java.util.List;

public record SimilarProductResponse(
        String productId,
        List<ProductRecommendation> similarProducts,
        int totalResults
) {}
