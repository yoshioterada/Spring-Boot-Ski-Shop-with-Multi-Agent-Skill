package com.example.skishop.ai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Document(collection = "chat_sessions")
public class ChatSession {

    @Id
    private String id;
    @Indexed
    private String userId;
    private SessionType sessionType;
    private SessionStatus status;
    private String intent;
    private double satisfactionScore;
    private String resolution;
    private String category;
    private String priority;
    private String assignedAgent;
    private List<String> tags = new ArrayList<>();
    private List<ChatMessage> messages = new ArrayList<>();
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastActivity;

    public ChatSession() {
        this.id = UUID.randomUUID().toString();
        this.status = SessionStatus.ACTIVE;
        this.priority = "NORMAL";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.lastActivity = Instant.now();
    }

    public ChatSession(String userId, SessionType sessionType) {
        this();
        this.userId = userId;
        this.sessionType = sessionType;
    }

    public void addMessage(ChatMessage message) {
        this.messages.add(message);
        this.lastActivity = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public SessionType getSessionType() { return sessionType; }
    public SessionStatus getStatus() { return status; }
    public String getIntent() { return intent; }
    public double getSatisfactionScore() { return satisfactionScore; }
    public List<ChatMessage> getMessages() { return messages; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastActivity() { return lastActivity; }

    public void setStatus(SessionStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
    public String getResolution() { return resolution; }
    public String getCategory() { return category; }
    public String getPriority() { return priority; }
    public String getAssignedAgent() { return assignedAgent; }
    public List<String> getTags() { return tags; }

    public void setIntent(String intent) { this.intent = intent; }
    public void setSatisfactionScore(double satisfactionScore) { this.satisfactionScore = satisfactionScore; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public void setCategory(String category) { this.category = category; }
    public void setPriority(String priority) { this.priority = priority; }
    public void setAssignedAgent(String assignedAgent) { this.assignedAgent = assignedAgent; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public enum SessionType {
        SUPPORT, RECOMMENDATION, INQUIRY, COMPLAINT
    }

    public enum SessionStatus {
        ACTIVE, COMPLETED, EXPIRED, ESCALATED
    }
}
