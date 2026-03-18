package com.example.skishop.authentication.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "is_success", nullable = false)
    private boolean isSuccess;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    protected LoginAttempt() {}

    public LoginAttempt(UUID userId, LocalDateTime timestamp, String ipAddress,
                        String userAgent, boolean isSuccess, String failureReason) {
        this.userId = userId;
        this.timestamp = timestamp;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.isSuccess = isSuccess;
        this.failureReason = failureReason;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public boolean isSuccess() { return isSuccess; }
    public String getFailureReason() { return failureReason; }
}
