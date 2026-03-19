package com.example.skishop.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record ModelDeploymentRequest(
        @NotBlank(message = "モデルバージョンIDは必須です")
        String modelVersionId,

        boolean activateImmediately
) {}
