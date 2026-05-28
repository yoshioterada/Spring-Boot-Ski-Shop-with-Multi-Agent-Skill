package com.example.skishop.inventory.repository;

import com.example.skishop.inventory.model.RecommendationFeedback;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RecommendationFeedbackRepository extends MongoRepository<RecommendationFeedback, String> {
}
