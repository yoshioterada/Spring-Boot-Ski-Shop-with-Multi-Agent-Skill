package com.example.skishop.inventory.controller;

import com.example.skishop.inventory.dto.RecommendationFeedbackRequest;
import com.example.skishop.inventory.dto.RecommendationFeedbackResponse;
import com.example.skishop.inventory.service.RecommendationFeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationFeedbackController {

    private final RecommendationFeedbackService feedbackService;

    public RecommendationFeedbackController(RecommendationFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/feedback")
    public ResponseEntity<RecommendationFeedbackResponse> recordFeedback(
            @Valid @RequestBody RecommendationFeedbackRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feedbackService.record(request));
    }
}
