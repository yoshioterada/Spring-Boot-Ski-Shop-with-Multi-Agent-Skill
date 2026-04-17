package com.example.skishop.agent.common.dto;

import java.time.Instant;
import java.util.List;

public record WeatherAgentResponse(
        String location,
        CurrentWeatherData currentWeather,
        WeatherForecastData forecast,
        SkiConditionsData skiConditions,
        List<WeatherAlertData> alerts,
        String aiSummary,
        String skiFeasibilityAssessment,
        Instant generatedAt
) {}
