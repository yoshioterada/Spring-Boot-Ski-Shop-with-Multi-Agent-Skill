package com.example.skishop.inventory.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "recommendation_feedback")
public class RecommendationFeedback {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String sessionId;

    @Indexed
    private String recommendationId;

    @Indexed
    private String productId;

    private FeedbackType feedbackType;
    private String source;

    @CreatedDate
    private Instant createdAt;

    public RecommendationFeedback() {}

    public RecommendationFeedback(String userId,
                                  String sessionId,
                                  String recommendationId,
                                  String productId,
                                  FeedbackType feedbackType,
                                  String source) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.recommendationId = recommendationId;
        this.productId = productId;
        this.feedbackType = feedbackType;
        this.source = source;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getSessionId() { return sessionId; }
    public String getRecommendationId() { return recommendationId; }
    public String getProductId() { return productId; }
    public FeedbackType getFeedbackType() { return feedbackType; }
    public String getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }

    public enum FeedbackType {
        CLICK, CONVERSION
    }
}
