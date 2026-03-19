package com.example.skishop.mailsend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "azure.communication")
public record AzureCommunicationProperties(
        String endpoint,
        String connectionString,
        String senderAddress
) {
}
