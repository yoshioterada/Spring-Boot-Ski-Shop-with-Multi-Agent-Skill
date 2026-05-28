package com.example.skishop.payment.gateway;

import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.payment.config.PaymentGatewayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedPaymentGatewayClientTest {

    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    private SimulatedPaymentGatewayClient gatewayClient;

    @BeforeEach
    void setUp() {
        PaymentGatewayProperties properties = new PaymentGatewayProperties();
        properties.setWebhookSecret(WEBHOOK_SECRET);
        properties.setRequireWebhookSignature(true);
        properties.setWebhookToleranceSeconds(300);
        gatewayClient = new SimulatedPaymentGatewayClient(new ObjectMapper(), properties);
    }

    @Test
    @DisplayName("正しい署名のWebhookを検証してイベントを返す")
    void should_returnEvent_when_signatureIsValid() {
        // Arrange
        String payload = """
                {"id":"evt_1","type":"payment_intent.succeeded","paymentIntentId":"pi_test","status":"succeeded","amount":10000}
                """.trim();

        // Act
        PaymentGatewayClient.VerifiedWebhookEvent event = gatewayClient.verifyAndParseWebhook(payload, signature(payload));

        // Assert
        assertThat(event.eventId()).isEqualTo("evt_1");
        assertThat(event.status()).isEqualTo(PaymentGatewayClient.PaymentGatewayStatus.CAPTURED);
        assertThat(event.paymentIntentId()).isEqualTo("pi_test");
    }

    @Test
    @DisplayName("署名なしWebhookは拒否される")
    void should_throwException_when_signatureIsMissing() {
        // Arrange
        String payload = "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\"}";

        // Act & Assert
        assertThatThrownBy(() -> gatewayClient.verifyAndParseWebhook(payload, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Webhook signature is required");
    }

    @Test
    @DisplayName("改ざんされたpayloadは署名不一致で拒否される")
    void should_throwException_when_payloadIsTampered() {
        // Arrange
        String originalPayload = "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\"}";
        String tamperedPayload = "{\"id\":\"evt_1\",\"type\":\"payment_intent.payment_failed\"}";
        String originalSignature = signature(originalPayload);

        // Act & Assert
        assertThatThrownBy(() -> gatewayClient.verifyAndParseWebhook(tamperedPayload, originalSignature))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Webhook signature is invalid");
    }

    @Test
    @DisplayName("古いtimestampの署名は拒否される")
    void should_throwException_when_signatureTimestampExpired() {
        // Arrange
        String payload = "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\"}";
        long expiredTimestamp = Instant.now().minusSeconds(600).getEpochSecond();
        String expiredSignature = signature(payload, expiredTimestamp);

        // Act & Assert
        assertThatThrownBy(() -> gatewayClient.verifyAndParseWebhook(payload, expiredSignature))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("outside tolerance");
    }

    @Test
    @DisplayName("不正な形式の署名は拒否される")
    void should_throwException_when_signatureMalformed() {
        // Arrange
        String payload = "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\"}";

        // Act & Assert
        assertThatThrownBy(() -> gatewayClient.verifyAndParseWebhook(payload, "invalid-signature"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("format is invalid");
    }

    private String signature(String payload) {
        return signature(payload, Instant.now().getEpochSecond());
    }

    private String signature(String payload, long timestamp) {
        try {
            String timestampText = Long.toString(timestamp);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestampText + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestampText + ",v1=" + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign webhook payload", e);
        }
    }
}