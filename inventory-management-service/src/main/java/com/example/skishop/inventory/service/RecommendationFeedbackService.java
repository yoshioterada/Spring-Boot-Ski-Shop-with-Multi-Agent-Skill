package com.example.skishop.inventory.service;

import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.inventory.dto.RecommendationFeedbackRequest;
import com.example.skishop.inventory.dto.RecommendationFeedbackResponse;
import com.example.skishop.inventory.model.Product;
import com.example.skishop.inventory.model.RecommendationFeedback;
import com.example.skishop.inventory.repository.ProductRepository;
import com.example.skishop.inventory.repository.RecommendationFeedbackRepository;
import org.springframework.stereotype.Service;

@Service
public class RecommendationFeedbackService {

    private final RecommendationFeedbackRepository feedbackRepository;
    private final ProductRepository productRepository;

    public RecommendationFeedbackService(RecommendationFeedbackRepository feedbackRepository,
                                         ProductRepository productRepository) {
        this.feedbackRepository = feedbackRepository;
        this.productRepository = productRepository;
    }

    public RecommendationFeedbackResponse record(RecommendationFeedbackRequest request) {
        Product product = productRepository.findById(request.productId())
                .filter(p -> p.getStatus() == Product.ProductStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));

        var feedback = new RecommendationFeedback(
                blankToNull(request.userId()),
                blankToNull(request.sessionId()),
                blankToNull(request.recommendationId()),
                product.getId(),
                RecommendationFeedback.FeedbackType.valueOf(request.feedbackType()),
                blankToNull(request.source()));
        return toResponse(feedbackRepository.save(feedback));
    }

    private RecommendationFeedbackResponse toResponse(RecommendationFeedback feedback) {
        return new RecommendationFeedbackResponse(
                feedback.getId(),
                feedback.getUserId(),
                feedback.getSessionId(),
                feedback.getRecommendationId(),
                feedback.getProductId(),
                feedback.getFeedbackType().name(),
                feedback.getSource(),
                feedback.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
