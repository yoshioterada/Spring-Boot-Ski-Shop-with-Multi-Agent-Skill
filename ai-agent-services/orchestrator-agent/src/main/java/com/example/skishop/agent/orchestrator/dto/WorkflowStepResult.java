package com.example.skishop.agent.orchestrator.dto;

public record WorkflowStepResult(
        String stepName,
        boolean isSuccess,
        String summary,
        Object data
) {}
