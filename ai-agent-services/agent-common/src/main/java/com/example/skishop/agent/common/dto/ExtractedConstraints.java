package com.example.skishop.agent.common.dto;

import java.time.LocalDate;
import java.util.List;

public record ExtractedConstraints(
        String destination,
        LocalDate tripStartDate,
        LocalDate tripEndDate,
        Integer groupSize,
        String skillLevel,           // "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT"
        Integer budgetYen,
        boolean includesRental,
        boolean includesPurchase,
        List<String> productCategories
) {}
