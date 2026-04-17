package com.example.skishop.agent.common.dto;

import java.time.LocalDate;
import java.util.List;

public record WeatherForecastData(
        List<DailyForecast> dailyForecasts
) {
    public record DailyForecast(
            LocalDate date,
            double maxTempCelsius,
            double minTempCelsius,
            double precipitationMm,
            double snowfallCm,
            double snowDepthCm,
            double windSpeedMaxKph,
            String weatherDescription,
            int weatherCode,
            double uvIndex
    ) {}
}
