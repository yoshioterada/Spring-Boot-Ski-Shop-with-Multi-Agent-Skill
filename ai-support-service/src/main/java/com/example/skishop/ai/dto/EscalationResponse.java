package com.example.skishop.ai.dto;

import java.time.Instant;

public record EscalationResponse(
        String sessionId,
        String status,
        String assignedAgent,
        Instant escalatedAt
) {}
