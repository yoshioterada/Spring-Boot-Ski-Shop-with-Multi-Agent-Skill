package com.example.skishop.mailsend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services")
public record ServicesProperties(UserManagement userManagement) {

    public record UserManagement(String url) {
    }
}
