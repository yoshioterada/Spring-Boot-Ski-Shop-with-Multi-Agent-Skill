package com.example.skishop.ai.repository;

import com.example.skishop.ai.model.BehaviorMetrics;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BehaviorMetricsRepository extends MongoRepository<BehaviorMetrics, String> {

    List<BehaviorMetrics> findByUserId(String userId);

    List<BehaviorMetrics> findByUserIdAndMetricType(String userId, String metricType);
}
