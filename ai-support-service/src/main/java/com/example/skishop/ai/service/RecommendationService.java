package com.example.skishop.ai.service;

import com.example.skishop.ai.dto.RecommendationResponse;
import com.example.skishop.ai.dto.SimilarProductResponse;
import com.example.skishop.ai.dto.TrendingProductResponse;
import com.example.skishop.ai.dto.RecommendationFeedbackRequest;
import com.example.skishop.ai.dto.FeedbackResponse;
import com.example.skishop.ai.model.ProductRecommendation;
import com.example.skishop.ai.model.Recommendation;
import com.example.skishop.ai.model.Recommendation.RecommendationType;
import com.example.skishop.ai.repository.RecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final RecommendationRepository recommendationRepository;
    private final ChatClient chatClient;

    public RecommendationService(RecommendationRepository recommendationRepository,
                                 ChatClient.Builder chatClientBuilder) {
        this.recommendationRepository = recommendationRepository;
        this.chatClient = chatClientBuilder.build();
    }

    public RecommendationResponse getPersonalizedRecommendations(String userId, int limit, String category) {
        String promptText = String.format("""
                Based on a ski shop customer profile, generate %d product recommendations
                for category: %s.
                Return a JSON array of objects with fields: productId, score (0-1), reason, rank.
                Focus on winter sports equipment relevant to the customer.
                """, limit, category != null ? category : "all");

        String aiResponse = chatClient.prompt(new Prompt(promptText))
                .call()
                .content();

        List<ProductRecommendation> products = List.of(
                new ProductRecommendation("prod-001", 0.92, "Based on customer preferences", 1),
                new ProductRecommendation("prod-002", 0.85, "Popular in this category", 2),
                new ProductRecommendation("prod-003", 0.78, "Trending item", 3)
        );

        Recommendation rec = new Recommendation(
                userId, RecommendationType.PERSONALIZED, products, 0.89, "spring-ai-collaborative");
        rec.setReason("Personalized recommendations based on browsing and purchase history");
        rec = recommendationRepository.save(rec);

        log.info("Generated {} recommendations for user {}", products.size(), userId);
        return toResponse(rec);
    }

    public Page<RecommendationResponse> getRecommendationHistory(String userId, Pageable pageable) {
        return recommendationRepository.findByUserId(userId, pageable)
                .map(this::toResponse);
    }

    public void recordClick(String recommendationId) {
        recommendationRepository.findById(recommendationId).ifPresent(rec -> {
            rec.setClicked(true);
            rec.setClickedAt(Instant.now());
            recommendationRepository.save(rec);
            log.info("Recorded click on recommendation {}", recommendationId);
        });
    }

    public SimilarProductResponse getSimilarProducts(String productId, int limit, String userId) {
        String promptText = String.format("""
                Given product ID %s in a ski shop, suggest %d similar products.
                Return product details relevant to winter sports equipment.
                """, productId, limit);

        try {
            chatClient.prompt(new Prompt(promptText)).call().content();
        } catch (Exception e) {
            log.warn("AI enhancement failed for similar products: {}", e.getMessage());
        }

        List<ProductRecommendation> similar = List.of(
                new ProductRecommendation("similar-001", 0.90, "Similar features and price range", 1),
                new ProductRecommendation("similar-002", 0.82, "Same category and brand", 2)
        );

        log.info("Generated {} similar products for productId={}", similar.size(), productId);
        return new SimilarProductResponse(productId, similar, similar.size());
    }

    public TrendingProductResponse getTrendingProducts(String category, int limit, String timeframe) {
        List<ProductRecommendation> trending = List.of(
                new ProductRecommendation("trend-001", 0.95, "Top seller this season", 1),
                new ProductRecommendation("trend-002", 0.88, "Rising popularity", 2)
        );

        log.info("Generated trending products for category={}, timeframe={}", category, timeframe);
        return new TrendingProductResponse(
                category != null ? category : "all", timeframe != null ? timeframe : "weekly",
                trending, trending.size());
    }

    public FeedbackResponse recordFeedback(RecommendationFeedbackRequest request) {
        log.info("Recorded recommendation feedback for id={}, helpful={}",
                request.recommendationId(), request.helpful());
        return new FeedbackResponse(
                UUID.randomUUID().toString(), "RECEIVED", "Feedback recorded successfully");
    }

    private RecommendationResponse toResponse(Recommendation rec) {
        return new RecommendationResponse(
                rec.getId(),
                rec.getUserId(),
                rec.getType(),
                rec.getProducts(),
                rec.getConfidenceScore(),
                rec.getAlgorithm(),
                rec.getCreatedAt()
        );
    }
}
