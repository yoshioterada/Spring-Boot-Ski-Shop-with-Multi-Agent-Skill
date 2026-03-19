package com.example.skishop.ai.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record ModelTrainingRequest(
        @NotBlank(message = "モデルタイプは必須です")
        String modelType,

        @NotBlank(message = "アルゴリズムは必須です")
        String algorithm,

        Map<String, Object> parameters,

        List<String> features
) {}
