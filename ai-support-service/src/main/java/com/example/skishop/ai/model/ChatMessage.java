package com.example.skishop.ai.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class ChatMessage {

    private String id;
    private MessageRole role;
    private String content;
    private String messageType;
    private Instant timestamp;
    private Map<String, Object> metadata;
    private double sentimentScore;
    private String intent;

    public ChatMessage() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public ChatMessage(MessageRole role, String content) {
        this();
        this.role = role;
        this.content = content;
        this.messageType = "TEXT";
    }

    public String getId() { return id; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public String getMessageType() { return messageType; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, Object> getMetadata() { return metadata; }

    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public double getSentimentScore() { return sentimentScore; }
    public void setSentimentScore(double sentimentScore) { this.sentimentScore = sentimentScore; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public enum MessageRole {
        USER, ASSISTANT, SYSTEM
    }
}
