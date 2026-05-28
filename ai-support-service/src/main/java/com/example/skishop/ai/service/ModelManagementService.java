package com.example.skishop.ai.service;

import com.example.skishop.ai.dto.*;
import com.example.skishop.ai.model.ModelTraining;
import com.example.skishop.ai.model.ModelVersion;
import com.example.skishop.ai.repository.ModelTrainingRepository;
import com.example.skishop.ai.repository.ModelVersionRepository;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class ModelManagementService {

    private static final Logger log = LoggerFactory.getLogger(ModelManagementService.class);
    private static final String MODEL_TRAINING_RESOURCE = "ModelTraining";
    private static final String TRAINING_DATA_SIZE_METRIC = "trainingDataSize";
    private static final String VALIDATION_SCORE_METRIC = "validationScore";

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

        if (log.isInfoEnabled()) {
            log.info("Started model training: id={}, type={}, algorithm={}",
                training.getId(), request.modelType(), request.algorithm());
        }

        return toTrainingJobResponse(runTrainingJob(training));
    }

    public TrainingJobResponse getTrainingStatus(String trainingId) {
        String requiredTrainingId = Objects.requireNonNull(trainingId);
        ModelTraining training = modelTrainingRepository.findById(requiredTrainingId)
            .orElseThrow(() -> new ResourceNotFoundException(MODEL_TRAINING_RESOURCE, requiredTrainingId));
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
        String modelVersionId = Objects.requireNonNull(request.modelVersionId());
        ModelVersion version = modelVersionRepository.findById(modelVersionId)
            .orElseThrow(() -> new ResourceNotFoundException("ModelVersion", modelVersionId));
        String modelTrainingId = Objects.requireNonNull(version.getModelTrainingId());
        ModelTraining training = modelTrainingRepository.findById(modelTrainingId)
            .orElseThrow(() -> new ResourceNotFoundException(MODEL_TRAINING_RESOURCE, modelTrainingId));

        if (!"COMPLETED".equals(training.getStatus())) {
            throw new BusinessRuleViolationException(
                "MODEL_VERSION_NOT_READY",
                "Completed training is required before model deployment: " + training.getId());
        }

        if (request.activateImmediately()) {
            deactivateActiveVersionsForModelType(training.getModelType(), version.getId());
            version.setActive(true);
            version.setDeployedAt(Instant.now());
        }
        version = modelVersionRepository.save(version);

        if (log.isInfoEnabled()) {
            log.info("Deployed model version: id={}, active={}", version.getId(), version.isActive());
        }
        return toModelVersionResponse(version);
    }

    public ModelPerformanceResponse getModelPerformance(String modelType, String version) {
        ModelVersion modelVersion = resolveModelVersion(modelType, version)
                .orElseThrow(() -> new ResourceNotFoundException("ModelVersion", version != null ? version : modelType));
        String modelTrainingId = Objects.requireNonNull(modelVersion.getModelTrainingId());
        ModelTraining training = modelTrainingRepository.findById(modelTrainingId)
            .orElseThrow(() -> new ResourceNotFoundException(MODEL_TRAINING_RESOURCE, modelTrainingId));

        Map<String, Object> metrics = new HashMap<>(modelVersion.getPerformance());
        if (metrics.isEmpty()) {
            metrics.putAll(training.getMetrics());
        }
        metrics.put("modelVersionId", modelVersion.getId());
        metrics.put("trainingId", training.getId());
        metrics.put("active", modelVersion.isActive());
        metrics.put("modelPath", modelVersion.getModelPath());
        metrics.put("deployedAt", modelVersion.getDeployedAt());
        metrics.put(TRAINING_DATA_SIZE_METRIC, training.getTrainingDataSize());
        metrics.put(VALIDATION_SCORE_METRIC, training.getValidationScore());

        log.info("Retrieved model performance for type={}, version={}, active={}",
                modelType, modelVersion.getVersion(), modelVersion.isActive());
        return new ModelPerformanceResponse(modelType, modelVersion.getVersion(), metrics, Instant.now());
    }

    public String resolveActiveModelDescriptor(String modelType) {
        return findActiveVersionForModelType(modelType)
                .map(version -> modelType + ":" + version.getVersion())
                .orElse(modelType + ":baseline");
    }

    private ModelTraining runTrainingJob(ModelTraining training) {
        try {
            Map<String, Object> metrics = buildTrainingMetrics(training);
            String artifactPath = buildArtifactPath(training);

            training.setTrainingDataSize(toLong(metrics.get(TRAINING_DATA_SIZE_METRIC)));
            training.setValidationScore(toDouble(metrics.get(VALIDATION_SCORE_METRIC)));
            training.setMetrics(metrics);
            training.setStatus("COMPLETED");
            training.setEndTime(Instant.now());
            training = modelTrainingRepository.save(training);

            ModelVersion version = new ModelVersion(training.getId(), training.getVersion(), artifactPath);
            version.setPerformance(metrics);
            version.setMetadata(Map.of(
                    "modelType", training.getModelType(),
                    "algorithm", training.getAlgorithm(),
                    "featureCount", training.getFeatures().size(),
                    "createdAt", Instant.now().toString()
            ));
            modelVersionRepository.save(version);

                if (log.isInfoEnabled()) {
                log.info("Completed model training: id={}, version={}, artifact={}",
                    training.getId(), training.getVersion(), artifactPath);
                }
            return training;
        } catch (RuntimeException e) {
            training.setStatus("FAILED");
            training.setEndTime(Instant.now());
            training.setMetrics(Map.of("failureReason", e.getMessage()));
            modelTrainingRepository.save(training);
            throw e;
        }
    }

    private Map<String, Object> buildTrainingMetrics(ModelTraining training) {
        int featureCount = training.getFeatures().isEmpty() ? 1 : training.getFeatures().size();
        long trainingDataSize = resolveTrainingDataSize(training, featureCount);
        double algorithmWeight = switch (training.getAlgorithm().toLowerCase()) {
            case "collaborative_filtering" -> 0.04;
            case "semantic_search", "semantic-ranking" -> 0.03;
            case "hybrid" -> 0.05;
            default -> 0.02;
        };
        double validationScore = Math.min(0.98, 0.72 + algorithmWeight + (featureCount * 0.015));

        Map<String, Object> metrics = new HashMap<>();
        metrics.put(TRAINING_DATA_SIZE_METRIC, trainingDataSize);
        metrics.put(VALIDATION_SCORE_METRIC, round(validationScore));
        metrics.put("precision", round(Math.max(0.0, validationScore - 0.04)));
        metrics.put("recall", round(Math.max(0.0, validationScore - 0.06)));
        metrics.put("ndcg", round(Math.min(0.99, validationScore + 0.03)));
        metrics.put("artifactPath", buildArtifactPath(training));
        metrics.put("algorithm", training.getAlgorithm());
        metrics.put("featureCount", featureCount);
        metrics.put("completedAt", Instant.now().toString());
        return metrics;
    }

    private long resolveTrainingDataSize(ModelTraining training, int featureCount) {
        Object configuredSize = training.getParameters().get(TRAINING_DATA_SIZE_METRIC);
        if (configuredSize instanceof Number number) {
            return Math.max(1L, number.longValue());
        }
        return Math.max(100L, featureCount * 250L);
    }

    private String buildArtifactPath(ModelTraining training) {
        return "/models/%s/%s/artifact.json".formatted(
                training.getModelType().toLowerCase(), training.getVersion());
    }

    private Optional<ModelVersion> resolveModelVersion(String modelType, String version) {
        if (version == null || version.isBlank()) {
            return findActiveVersionForModelType(modelType);
        }
        return modelTrainingRepository.findByModelType(modelType).stream()
                .flatMap(training -> modelVersionRepository.findByModelTrainingId(training.getId()).stream())
                .filter(modelVersion -> version.equals(modelVersion.getVersion()))
                .findFirst();
    }

    private Optional<ModelVersion> findActiveVersionForModelType(String modelType) {
        return modelTrainingRepository.findByModelType(modelType).stream()
                .flatMap(training -> modelVersionRepository.findByModelTrainingId(training.getId()).stream())
                .filter(ModelVersion::isActive)
                .findFirst();
    }

    private void deactivateActiveVersionsForModelType(String modelType, String activeVersionId) {
        modelTrainingRepository.findByModelType(modelType).stream()
                .flatMap(training -> modelVersionRepository.findByModelTrainingId(training.getId()).stream())
                .filter(ModelVersion::isActive)
                .filter(version -> !version.getId().equals(activeVersionId))
                .forEach(version -> {
                    version.setActive(false);
                    modelVersionRepository.save(version);
                });
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
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
