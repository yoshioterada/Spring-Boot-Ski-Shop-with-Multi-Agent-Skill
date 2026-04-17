package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;

public record RankedProduct(
        int rank,
        ProductCandidate product,
        double matchScore,
        String matchReason,
        BigDecimal estimatedPrice,
        boolean isWeatherOptimal
) {}
