package com.example.skishop.agent.equipment.service;

import com.example.skishop.agent.common.dto.EquipmentMatchRequest;
import com.example.skishop.agent.common.dto.ProductCandidate;
import com.example.skishop.agent.equipment.tool.EquipmentMatchingToolService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RecommendationCandidateService {

    private final EquipmentMatchingToolService toolService;

    public RecommendationCandidateService(EquipmentMatchingToolService toolService) {
        this.toolService = toolService;
    }

    public List<ProductCandidate> generateCandidates(EquipmentMatchRequest request) {
        List<CompletableFuture<List<ProductCandidate>>> futures = request.desiredCategories().stream()
                .map(category -> CompletableFuture.supplyAsync(() ->
                        toolService.searchInventoryCandidates(category, request.skillLevel(), null)))
                .toList();

        List<ProductCandidate> candidates = new ArrayList<>();
        futures.forEach(f -> candidates.addAll(f.join()));
        return candidates.stream()
                .filter(ProductCandidate::isAvailable)
                .toList();
    }
}
