package com.example.skishop.agent.common.dto;

/**
 * スキー適性評価結果。Weather Agent が算出し、Equipment Matching / Dynamic Pricing / Orchestrator が参照する。
 */
public record SkiFeasibilityResult(
        String feasibility,             // "HIGH" / "MEDIUM" / "LOW"
        int score,                      // 0〜100
        String recommendedGearLevel,    // "EXTREME_COLD" / "COLD" / "MODERATE"
        String snowCondition,           // "POWDER" / "GROOMED" / "ICY" / "SLUSH" 等
        String overallCondition         // "EXCELLENT" / "GOOD" / "POOR"（Dynamic Pricing が価格係数決定に使用）
) {}
