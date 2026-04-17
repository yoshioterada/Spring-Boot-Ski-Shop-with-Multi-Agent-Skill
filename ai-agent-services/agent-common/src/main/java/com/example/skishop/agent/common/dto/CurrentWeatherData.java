package com.example.skishop.agent.common.dto;

public record CurrentWeatherData(
        double temperatureCelsius,
        double feelsLikeCelsius,
        double humidity,
        double windSpeedKph,
        String windDirection,
        double visibilityKm,
        String weatherCode,
        String weatherDescription,
        boolean isSnowing,
        double snowfallMm
) {}
