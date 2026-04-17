package com.example.skishop.agent.common.dto;

import java.time.Instant;

public record WeatherAlertData(
        String alertType,
        String severity,
        String headline,
        String description,
        Instant effectiveFrom,
        Instant effectiveUntil
) {}
