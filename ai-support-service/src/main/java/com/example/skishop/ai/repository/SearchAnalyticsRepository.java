package com.example.skishop.ai.repository;

import com.example.skishop.ai.model.SearchAnalytics;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SearchAnalyticsRepository extends MongoRepository<SearchAnalytics, String> {
}
