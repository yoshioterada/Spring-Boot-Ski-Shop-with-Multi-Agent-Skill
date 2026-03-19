package com.example.skishop.ai.dto;

import com.example.skishop.ai.model.ProductRecommendation;

import java.util.List;

public record TrendingProductResponse(
        String category,
        String timeframe,
        List<ProductRecommendation> trendingProducts,
        int totalResults
) {}
