package com.example.skishop.ai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * LLM 呼出の監査ログ (D-COM-08: プロンプト原文は保存せず SHA-256 ハッシュのみ).
 */
@Document(collection = "llm_audit_logs")
public class LlmAuditLog {

    @Id
    private String id;

    @Indexed
    private String traceId;

    private String endpoint;
    private String userId;
    private String promptHash; // SHA-256 of prompt
    private int inputTokenCount;
    private int outputTokenCount;
    private double costUsd;
    private long durationMs;
    private String featureId; // F1, F2, F3, F4, F5
    private boolean success;
    private String errorMessage;

    @Indexed(expireAfter = "0s")
    private Instant ttl;

    private Instant createdAt;

    public LlmAuditLog() {
        this.createdAt = Instant.now();
    }

    // ── Getters / Setters ──

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPromptHash() { return promptHash; }
    public void setPromptHash(String promptHash) { this.promptHash = promptHash; }

    public int getInputTokenCount() { return inputTokenCount; }
    public void setInputTokenCount(int inputTokenCount) { this.inputTokenCount = inputTokenCount; }

    public int getOutputTokenCount() { return outputTokenCount; }
    public void setOutputTokenCount(int outputTokenCount) { this.outputTokenCount = outputTokenCount; }

    public double getCostUsd() { return costUsd; }
    public void setCostUsd(double costUsd) { this.costUsd = costUsd; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getFeatureId() { return featureId; }
    public void setFeatureId(String featureId) { this.featureId = featureId; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getTtl() { return ttl; }
    public void setTtl(Instant ttl) { this.ttl = ttl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
