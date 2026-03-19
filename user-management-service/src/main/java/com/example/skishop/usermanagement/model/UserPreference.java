package com.example.skishop.usermanagement.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "pref_key", nullable = false, length = 100)
    private String prefKey;

    @Column(name = "pref_value", length = 1000)
    private String prefValue;

    @Column(name = "pref_type", length = 50)
    private String prefType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserPreference() {}

    public UserPreference(UUID userId, String prefKey, String prefValue, String prefType) {
        this.userId = userId;
        this.prefKey = prefKey;
        this.prefValue = prefValue;
        this.prefType = prefType;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getPrefKey() { return prefKey; }
    public String getPrefValue() { return prefValue; }
    public String getPrefType() { return prefType; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setPrefValue(String prefValue) { this.prefValue = prefValue; }
    public void setPrefType(String prefType) { this.prefType = prefType; }
}
