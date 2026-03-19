package com.example.skishop.authentication.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "oauth_scopes",
    uniqueConstraints = @UniqueConstraint(name = "uq_oauth_scopes_name", columnNames = "name"))
public class OAuthScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    protected OAuthScope() {}

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isDefault() { return isDefault; }
}
