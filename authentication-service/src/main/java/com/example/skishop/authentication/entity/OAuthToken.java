package com.example.skishop.authentication.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "oauth_tokens",
    uniqueConstraints = @UniqueConstraint(name = "uq_oauth_tokens_access_token", columnNames = "access_token"))
public class OAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "access_token", nullable = false, unique = true)
    private String accessToken;

    @Column(name = "refresh_token")
    private String refreshToken;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "user_id")
    private UUID userId;

    @ElementCollection
    @CollectionTable(name = "oauth_token_scopes", joinColumns = @JoinColumn(name = "token_id"))
    @Column(name = "scope")
    private List<String> scopes = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    protected OAuthToken() {}

    public OAuthToken(String accessToken, String refreshToken, String clientId, UUID userId,
                      List<String> scopes, LocalDateTime expiresAt) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.clientId = clientId;
        this.userId = userId;
        this.scopes = scopes;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getClientId() { return clientId; }
    public UUID getUserId() { return userId; }
    public List<String> getScopes() { return scopes; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
}
