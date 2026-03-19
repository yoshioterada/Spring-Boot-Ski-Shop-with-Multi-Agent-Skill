package com.example.skishop.ai.repository;

import com.example.skishop.ai.model.Recommendation;
import com.example.skishop.ai.model.Recommendation.RecommendationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecommendationRepository extends MongoRepository<Recommendation, String> {

    List<Recommendation> findByUserIdAndType(String userId, RecommendationType type);

    Page<Recommendation> findByUserId(String userId, Pageable pageable);
}
