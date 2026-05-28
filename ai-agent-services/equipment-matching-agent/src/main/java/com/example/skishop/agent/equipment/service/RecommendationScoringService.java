package com.example.skishop.agent.equipment.service;

import com.example.skishop.agent.common.dto.EquipmentMatchRequest;
import com.example.skishop.agent.common.dto.ProductCandidate;
import com.example.skishop.agent.equipment.tool.EquipmentMatchingToolService;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RecommendationScoringService {

    public List<ProductCandidate> scoreAndRank(List<ProductCandidate> candidates, EquipmentMatchRequest request) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        String terrain = EquipmentMatchingAgentService.terrainOf(request.destination());
        long seed = diversitySeed(request);
        Random rng = new Random(seed);
        Map<String, Double> jitter = new java.util.HashMap<>();
        for (ProductCandidate c : candidates) {
            jitter.put(c.productId(), (rng.nextDouble() - 0.5) * 6.0);
        }
        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble((ProductCandidate c) -> totalScore(c, request.skillLevel(), terrain, jitter.get(c.productId())))
                        .reversed())
                .toList();
    }

    static double totalScore(ProductCandidate c, String skillLevel, String terrain, Double jitter) {
        double score = 50.0;
        score += EquipmentMatchingToolService.skillAffinityScore(skillLevel, c.skillLevelSuitability());
        if (terrain != null && terrain.equals(c.weatherSuitability())) score += 20.0;
        else if ("ALL_CONDITIONS".equals(c.weatherSuitability())) score += 8.0;
        if (c.isAvailable()) score += 10.0;
        score += Math.min(5, c.stockQuantity());
        if (jitter != null) score += jitter;
        return score;
    }

    static long diversitySeed(EquipmentMatchRequest request) {
        String key = String.join("|",
                nullToEmpty(request.userId()),
                nullToEmpty(request.destination()),
                nullToEmpty(request.skillLevel()),
                LocalDate.now().toString());
        return key.hashCode();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
