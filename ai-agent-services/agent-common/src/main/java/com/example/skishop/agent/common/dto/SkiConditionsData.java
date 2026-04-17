package com.example.skishop.agent.common.dto;

public record SkiConditionsData(
        double snowDepthCm,
        double freshSnowLast24hCm,
        double freshSnowLast72hCm,
        String snowQuality,
        boolean isLiftsLikelyOpen,
        String overallCondition,
        String grooomingStatus,
        double visibilityKm,
        String avalancheRisk
) {}
