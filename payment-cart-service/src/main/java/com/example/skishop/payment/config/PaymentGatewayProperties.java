package com.example.skishop.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "skishop.payment.gateway")
public class PaymentGatewayProperties {

    private static final String DEFAULT_PROVIDER = "simulated";
    private static final long DEFAULT_WEBHOOK_TOLERANCE_SECONDS = 300L;

    private String provider = DEFAULT_PROVIDER;
    private String webhookSecret;
    private boolean requireWebhookSignature = true;
    private long webhookToleranceSeconds = DEFAULT_WEBHOOK_TOLERANCE_SECONDS;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public boolean isRequireWebhookSignature() {
        return requireWebhookSignature;
    }

    public void setRequireWebhookSignature(boolean requireWebhookSignature) {
        this.requireWebhookSignature = requireWebhookSignature;
    }

    public long getWebhookToleranceSeconds() {
        return webhookToleranceSeconds;
    }

    public void setWebhookToleranceSeconds(long webhookToleranceSeconds) {
        this.webhookToleranceSeconds = webhookToleranceSeconds;
    }
}