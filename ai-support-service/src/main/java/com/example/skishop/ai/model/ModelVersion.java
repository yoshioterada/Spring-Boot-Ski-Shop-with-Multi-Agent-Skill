package com.example.skishop.ai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Document(collection = "model_versions")
public class ModelVersion {

    @Id
    private String id;
    private String modelTrainingId;
    private String version;
    private String modelPath;
    private Instant deployedAt;
    private boolean isActive;
    private Map<String, Object> performance = new HashMap<>();
    private Map<String, Object> metadata = new HashMap<>();

    public ModelVersion() {
        this.id = UUID.randomUUID().toString();
    }

    public ModelVersion(String modelTrainingId, String version, String modelPath) {
        this();
        this.modelTrainingId = modelTrainingId;
        this.version = version;
        this.modelPath = modelPath;
        this.isActive = false;
    }

    public String getId() { return id; }
    public String getModelTrainingId() { return modelTrainingId; }
    public String getVersion() { return version; }
    public String getModelPath() { return modelPath; }
    public Instant getDeployedAt() { return deployedAt; }
    public boolean isActive() { return isActive; }
    public Map<String, Object> getPerformance() { return performance; }
    public Map<String, Object> getMetadata() { return metadata; }

    public void setDeployedAt(Instant deployedAt) { this.deployedAt = deployedAt; }
    public void setActive(boolean active) { this.isActive = active; }
    public void setPerformance(Map<String, Object> performance) { this.performance = performance; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
