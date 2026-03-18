package com.example.skishop.authentication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {
    private int maxLoginAttempts = 5;
    private Duration accountLockDuration = Duration.ofMinutes(15);

    public int getMaxLoginAttempts() { return maxLoginAttempts; }
    public void setMaxLoginAttempts(int maxLoginAttempts) { this.maxLoginAttempts = maxLoginAttempts; }
    public Duration getAccountLockDuration() { return accountLockDuration; }
    public void setAccountLockDuration(Duration accountLockDuration) { this.accountLockDuration = accountLockDuration; }
}
