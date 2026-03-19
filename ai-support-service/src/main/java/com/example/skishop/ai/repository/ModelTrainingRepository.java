package com.example.skishop.ai.repository;

import com.example.skishop.ai.model.ModelTraining;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ModelTrainingRepository extends MongoRepository<ModelTraining, String> {

    List<ModelTraining> findByModelType(String modelType);

    List<ModelTraining> findByStatus(String status);
}
