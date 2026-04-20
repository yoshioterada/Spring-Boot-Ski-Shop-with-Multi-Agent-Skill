package com.example.skishop.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * F2 季節予測リクエスト (spec § 4.2.3).
 */
public record SeasonalForecastRequest(
        @NotNull Horizon horizon,
        List<String> categories,
        boolean considerWeather
) {
    public enum Horizon {
        NEXT_MONTH, NEXT_SEASON, NEXT_YEAR
    }
}
