package com.example.skishop.ai.service;

import com.example.skishop.ai.dto.*;
import com.example.skishop.ai.model.ModelTraining;
import com.example.skishop.ai.model.ModelVersion;
import com.example.skishop.ai.repository.ModelTrainingRepository;
import com.example.skishop.ai.repository.ModelVersionRepository;
import com.example.skishop.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ModelManagementService {

    private static final Logger log = LoggerFactory.getLogger(ModelManagementService.class);

    private final ModelTrainingRepository modelTrainingRepository;
    private final ModelVersionRepository modelVersionRepository;

    public ModelManagementService(ModelTrainingRepository modelTrainingRepository,
                                  ModelVersionRepository modelVersionRepository) {
        this.modelTrainingRepository = modelTrainingRepository;
        this.modelVersionRepository = modelVersionRepository;
    }

    public TrainingJobResponse trainModel(ModelTrainingRequest request) {
        String version = "v" + System.currentTimeMillis();
        ModelTraining training = new ModelTraining(request.modelType(), request.algorithm(), version);
        if (request.parameters() != null) {
            training.setParameters(request.parameters());
        }
        if (request.features() != null) {
            training.setFeatures(request.features());
        }
        training.setStatus("RUNNING");
        training = modelTrainingRepository.save(training);

        log.info("Started model training: id={}, type={}, algorithm={}",
                training.getId(), request.modelType(), request.algorithm());

        return toTrainingJobResponse(training);
    }

    public TrainingJobResponse getTrainingStatus(String trainingId) {
        ModelTraining training = modelTrainingRepository.findById(trainingId)
                .orElseThrow(() -> new ResourceNotFoundException("ModelTraining", trainingId));
        return toTrainingJobResponse(training);
    }

    public List<ModelVersionResponse> getModelVersions(String modelType) {
        List<ModelTraining> trainings = modelTrainingRepository.findByModelType(modelType);
        return trainings.stream()
                .flatMap(t -> modelVersionRepository.findByModelTrainingId(t.getId()).stream())
                .map(this::toModelVersionResponse)
                .toList();
    }

    public ModelVersionResponse deployModel(ModelDeploymentRequest request) {
        ModelVersion version = modelVersionRepository.findById(request.modelVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("ModelVersion", request.modelVersionId()));

        if (request.activateImmediately()) {
            version.setActive(true);
            version.setDeployedAt(Instant.now());
        }
        version = modelVersionRepository.save(version);

        log.info("Deployed model version: id={}, active={}", version.getId(), version.isActive());
        return toModelVersionResponse(version);
    }

    public ModelPerformanceResponse getModelPerformance(String modelType, String version) {
        log.info("Retrieved model performance for type={}, version={}", modelType, version);
        return new ModelPerformanceResponse(modelType, version, Map.of(), Instant.now());
    }

    private TrainingJobResponse toTrainingJobResponse(ModelTraining training) {
        return new TrainingJobResponse(
                training.getId(), training.getModelType(), training.getAlgorithm(),
                training.getStatus(), training.getVersion(), training.getMetrics(),
                training.getStartTime(), training.getEndTime());
    }

    private ModelVersionResponse toModelVersionResponse(ModelVersion version) {
        return new ModelVersionResponse(
                version.getId(), version.getModelTrainingId(), version.getVersion(),
                version.isActive(), version.getPerformance(), version.getDeployedAt());
    }
}
