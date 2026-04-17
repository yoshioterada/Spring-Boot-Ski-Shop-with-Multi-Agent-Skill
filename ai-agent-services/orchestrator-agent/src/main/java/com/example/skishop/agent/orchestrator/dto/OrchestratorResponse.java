package com.example.skishop.agent.orchestrator.dto;

import java.time.Instant;

public record OrchestratorResponse(
        String userId,
        String sessionId,
        QuoteSummary quote,
        String intentSummary,
        String weatherSummary,
        String equipmentRecommendation,
        String couponSummary,
        String orchestrationSummary,
        Instant generatedAt
) {}
