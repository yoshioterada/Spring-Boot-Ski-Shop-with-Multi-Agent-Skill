package com.example.skishop.ai.dto;

import java.util.List;

public record SearchResponse(
        String query,
        String processedQuery,
        List<SearchResult> results,
        int totalResults,
        long responseTimeMs
) {
    public record SearchResult(
            String productId,
            String name,
            double relevanceScore,
            String snippet
    ) {}
}
