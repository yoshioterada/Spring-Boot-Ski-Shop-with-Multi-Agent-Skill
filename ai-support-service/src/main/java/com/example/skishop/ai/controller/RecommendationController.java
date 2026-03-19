package com.example.skishop.ai.controller;

import com.example.skishop.ai.dto.FeedbackResponse;
import com.example.skishop.ai.dto.RecommendationFeedbackRequest;
import com.example.skishop.ai.dto.RecommendationResponse;
import com.example.skishop.ai.dto.SimilarProductResponse;
import com.example.skishop.ai.dto.TrendingProductResponse;
import com.example.skishop.ai.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<RecommendationResponse> getRecommendations(
            @PathVariable String userId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(recommendationService.getPersonalizedRecommendations(userId, limit, category));
    }

    @GetMapping("/similar/{productId}")
    public ResponseEntity<SimilarProductResponse> getSimilarProducts(
            @PathVariable String productId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String userId) {
        return ResponseEntity.ok(recommendationService.getSimilarProducts(productId, limit, userId));
    }

    @GetMapping("/trending")
    public ResponseEntity<TrendingProductResponse> getTrendingProducts(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String timeframe) {
        return ResponseEntity.ok(recommendationService.getTrendingProducts(category, limit, timeframe));
    }

    @PostMapping("/feedback")
    public ResponseEntity<FeedbackResponse> recordFeedback(
            @Valid @RequestBody RecommendationFeedbackRequest request) {
        return ResponseEntity.ok(recommendationService.recordFeedback(request));
    }

    @PutMapping("/{recommendationId}/click")
    public ResponseEntity<Void> recordClick(@PathVariable String recommendationId) {
        recommendationService.recordClick(recommendationId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<Page<RecommendationResponse>> getHistory(
            @PathVariable String userId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(recommendationService.getRecommendationHistory(userId, pageable));
    }
}
