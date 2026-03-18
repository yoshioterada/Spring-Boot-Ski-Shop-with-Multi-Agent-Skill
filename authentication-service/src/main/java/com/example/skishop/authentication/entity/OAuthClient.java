package com.example.skishop.authentication.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "oauth_clients",
    uniqueConstraints = @UniqueConstraint(name = "uq_oauth_clients_client_id", columnNames = "client_id"))
public class OAuthClient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "client_secret", nullable = false)
    private String clientSecret;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @ElementCollection
    @CollectionTable(name = "oauth_client_redirect_uris", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "redirect_uri")
    private List<String> redirectUris = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "oauth_client_grant_types", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "grant_type")
    private List<String> allowedGrantTypes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "oauth_client_scopes", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "scope")
    private List<String> scopes = new ArrayList<>();

    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected OAuthClient() {}

    public UUID getId() { return id; }
    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getRedirectUris() { return redirectUris; }
    public List<String> getAllowedGrantTypes() { return allowedGrantTypes; }
    public List<String> getScopes() { return scopes; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
