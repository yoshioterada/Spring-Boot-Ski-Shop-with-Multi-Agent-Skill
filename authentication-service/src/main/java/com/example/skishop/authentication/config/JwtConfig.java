package com.example.skishop.authentication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtConfig {
    private long accessTokenExpiry = 900L;   // 15 minutes
    private long refreshTokenExpiry = 86400L; // 24 hours

    public long getAccessTokenExpiry() { return accessTokenExpiry; }
    public void setAccessTokenExpiry(long accessTokenExpiry) { this.accessTokenExpiry = accessTokenExpiry; }
    public long getRefreshTokenExpiry() { return refreshTokenExpiry; }
    public void setRefreshTokenExpiry(long refreshTokenExpiry) { this.refreshTokenExpiry = refreshTokenExpiry; }
}
