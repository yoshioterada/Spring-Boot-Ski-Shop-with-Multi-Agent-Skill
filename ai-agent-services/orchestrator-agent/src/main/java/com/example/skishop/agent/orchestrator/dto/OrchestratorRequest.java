package com.example.skishop.agent.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrchestratorRequest(
        @NotBlank String userId,
        @NotBlank @Size(max = 2000) String message,
        String sessionId,
        String couponCode,
        boolean usePoints
) {}
