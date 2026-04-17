package com.example.skishop.agent.common.dto;

public record BodyMeasurements(
        Integer heightCm,
        Integer weightKg,
        Integer footSizeCm,
        String stance       // "REGULAR" | "GOOFY"
) {}
