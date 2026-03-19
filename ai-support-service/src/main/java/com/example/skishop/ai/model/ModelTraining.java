package com.example.skishop.ai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Document(collection = "model_training")
public class ModelTraining {

    @Id
    private String id;
    private String modelType;
    private String algorithm;
    private Instant startTime;
    private Instant endTime;
    private String status;
    private Map<String, Object> parameters = new HashMap<>();
    private Map<String, Object> metrics = new HashMap<>();
    private String version;
    private List<String> features = new ArrayList<>();
    private long trainingDataSize;
    private Double validationScore;
    private String notes;
    private String createdBy;

    public ModelTraining() {
        this.id = UUID.randomUUID().toString();
        this.startTime = Instant.now();
        this.status = "PENDING";
    }

    public ModelTraining(String modelType, String algorithm, String version) {
        this();
        this.modelType = modelType;
        this.algorithm = algorithm;
        this.version = version;
    }

    public String getId() { return id; }
    public String getModelType() { return modelType; }
    public String getAlgorithm() { return algorithm; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public String getStatus() { return status; }
    public Map<String, Object> getParameters() { return parameters; }
    public Map<String, Object> getMetrics() { return metrics; }
    public String getVersion() { return version; }
    public List<String> getFeatures() { return features; }
    public long getTrainingDataSize() { return trainingDataSize; }
    public Double getValidationScore() { return validationScore; }
    public String getNotes() { return notes; }
    public String getCreatedBy() { return createdBy; }

    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public void setStatus(String status) { this.status = status; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
    public void setFeatures(List<String> features) { this.features = features; }
    public void setTrainingDataSize(long trainingDataSize) { this.trainingDataSize = trainingDataSize; }
    public void setValidationScore(Double validationScore) { this.validationScore = validationScore; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
