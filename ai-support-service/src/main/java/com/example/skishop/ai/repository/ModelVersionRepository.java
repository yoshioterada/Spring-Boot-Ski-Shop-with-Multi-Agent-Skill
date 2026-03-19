package com.example.skishop.ai.repository;

import com.example.skishop.ai.model.ModelVersion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ModelVersionRepository extends MongoRepository<ModelVersion, String> {

    List<ModelVersion> findByModelTrainingId(String modelTrainingId);

    Optional<ModelVersion> findByVersionAndIsActiveTrue(String version);

    List<ModelVersion> findByIsActiveTrue();
}
