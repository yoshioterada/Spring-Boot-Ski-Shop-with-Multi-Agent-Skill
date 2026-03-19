package com.example.skishop.ai.repository;

import com.example.skishop.ai.model.DemandForecast;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DemandForecastRepository extends MongoRepository<DemandForecast, String> {

    List<DemandForecast> findByProductIdAndPeriod(String productId, String period);

    List<DemandForecast> findByCategory(String category);
}
