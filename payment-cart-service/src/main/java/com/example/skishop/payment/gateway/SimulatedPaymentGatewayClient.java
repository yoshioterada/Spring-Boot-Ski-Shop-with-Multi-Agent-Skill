package com.example.skishop.payment.gateway;

import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.payment.config.PaymentGatewayProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
public class SimulatedPaymentGatewayClient implements PaymentGatewayClient {

    private static final String SIGNATURE_TIMESTAMP_KEY = "t";
    private static final String SIGNATURE_VALUE_KEY = "v1";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String INVALID_WEBHOOK_SIGNATURE = "INVALID_WEBHOOK_SIGNATURE";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_SUCCEEDED = "succeeded";

    private final ObjectMapper objectMapper;
    private final PaymentGatewayProperties properties;

    public SimulatedPaymentGatewayClient(ObjectMapper objectMapper, PaymentGatewayProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String provider() {
        return properties.getProvider();
    }

    @Override
    public GatewayPaymentIntent createIntent(CreateGatewayPaymentIntentCommand command) {
        UUID paymentId = command.paymentId() == null ? UUID.randomUUID() : command.paymentId();
        String gatewayPaymentId = "sim_" + paymentId.toString().replace("-", "");
        String paymentIntentId = "pi_" + UUID.randomUUID().toString().replace("-", "");
        String rawResponse = """
                {"provider":"simulated","status":"requires_confirmation","gatewayPaymentId":"%s","paymentIntentId":"%s"}
                """.formatted(gatewayPaymentId, paymentIntentId).trim();
        return new GatewayPaymentIntent(gatewayPaymentId, paymentIntentId, "requires_confirmation", rawResponse);
    }

    @Override
    public GatewayPaymentResult capture(ConfirmGatewayPaymentCommand command) {
        if ("pm_fail".equalsIgnoreCase(command.paymentMethodId())) {
            return new GatewayPaymentResult(
                    PaymentGatewayStatus.FAILED,
                    STATUS_FAILED,
                    "{\"provider\":\"simulated\",\"status\":\"failed\",\"failureCode\":\"card_declined\"}",
                    "card_declined",
                    "Simulated card decline");
        }

        return new GatewayPaymentResult(
                PaymentGatewayStatus.CAPTURED,
                STATUS_SUCCEEDED,
                "{\"provider\":\"simulated\",\"status\":\"succeeded\"}",
                null,
                null);
    }

    @Override
    public GatewayRefundResult refund(GatewayRefundCommand command) {
        String refundId = "re_" + UUID.randomUUID().toString().replace("-", "");
        String rawResponse = """
                {"provider":"simulated","status":"succeeded","refundId":"%s","amount":%s}
                """.formatted(refundId, command.refundAmount()).trim();
        return new GatewayRefundResult(refundId, rawResponse);
    }

    @Override
    public VerifiedWebhookEvent verifyAndParseWebhook(String payload, String signature) {
        verifyWebhookSignature(payload, signature);
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventId = text(root, "id", text(root, "eventId", null));
            if (eventId == null || eventId.isBlank()) {
                throw new BusinessRuleViolationException("INVALID_WEBHOOK", "Webhook event id is required");
            }

            String eventType = text(root, "type", text(root, "eventType", "payment_intent.succeeded"));
            JsonNode data = root.has("data") && root.get("data").has("object") ? root.get("data").get("object") : root;
            String gatewayPaymentId = text(data, "gatewayPaymentId", text(data, "gateway_payment_id", null));
            String paymentIntentId = text(data, "paymentIntentId", text(data, "payment_intent_id", text(data, "paymentIntent", null)));
            String rawStatus = text(data, "status", inferStatus(eventType));
            BigDecimal amount = decimal(data, "amount");
            PaymentGatewayStatus status = toGatewayStatus(eventType, rawStatus);

            return new VerifiedWebhookEvent(
                    eventId,
                    eventType,
                    gatewayPaymentId,
                    paymentIntentId,
                    status,
                    amount,
                    rawStatus,
                    payload,
                    text(data, "failureCode", text(data, "failure_code", null)),
                    text(data, "failureReason", text(data, "failure_reason", null)));
        } catch (BusinessRuleViolationException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessRuleViolationException("INVALID_WEBHOOK", "Webhook payload cannot be parsed: " + e.getMessage());
        }
    }

    private void verifyWebhookSignature(String payload, String signature) {
        if (!properties.isRequireWebhookSignature()) {
            return;
        }
        if (signature == null || signature.isBlank()) {
            throw new BusinessRuleViolationException(INVALID_WEBHOOK_SIGNATURE, "Webhook signature is required");
        }
        if (properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()) {
            throw new BusinessRuleViolationException(INVALID_WEBHOOK_SIGNATURE, "Webhook secret is not configured");
        }

        Map<String, String> signatureParts = parseSignature(signature);
        String timestampText = signatureParts.get(SIGNATURE_TIMESTAMP_KEY);
        String expectedSignature = signatureParts.get(SIGNATURE_VALUE_KEY);
        if (timestampText == null || expectedSignature == null) {
            throw new BusinessRuleViolationException(INVALID_WEBHOOK_SIGNATURE, "Webhook signature format is invalid");
        }

        long timestamp = parseTimestamp(timestampText);
        long ageSeconds = Math.abs(Instant.now().getEpochSecond() - timestamp);
        if (ageSeconds > properties.getWebhookToleranceSeconds()) {
            throw new BusinessRuleViolationException(INVALID_WEBHOOK_SIGNATURE, "Webhook signature timestamp is outside tolerance");
        }

        byte[] actualSignature = decodeHex(expectedSignature);
        byte[] calculatedSignature = calculateSignature(timestampText, payload);
        if (!MessageDigest.isEqual(actualSignature, calculatedSignature)) {
            throw new BusinessRuleViolationException(INVALID_WEBHOOK_SIGNATURE, "Webhook signature is invalid");
        }
    }

    private Map<String, String> parseSignature(String signature) {
        Map<String, String> signatureParts = new HashMap<>();
        for (String part : signature.split(",")) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length == 2) {
                signatureParts.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return signatureParts;
    }

    private long parseTimestamp(String timestampText) {
        try {
            return Long.parseLong(timestampText);
        } catch (NumberFormatException e) {
            throw new BusinessRuleViolationException(INVALID_WEBHOOK_SIGNATURE, "Webhook signature timestamp is invalid");
        }
    }

    private byte[] decodeHex(String signature) {
        try {
            return HexFormat.of().parseHex(signature);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolationException(INVALID_WEBHOOK_SIGNATURE, "Webhook signature value is invalid");
        }
    }

    private byte[] calculateSignature(String timestampText, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return mac.doFinal((timestampText + "." + payload).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new BusinessRuleViolationException(INVALID_WEBHOOK_SIGNATURE, "Webhook signature cannot be verified");
        }
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asText();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText());
    }

    private static String inferStatus(String eventType) {
        return switch (eventType) {
            case "payment_intent.succeeded", "payment.captured" -> STATUS_SUCCEEDED;
            case "payment_intent.payment_failed", "payment.failed" -> STATUS_FAILED;
            case "payment_intent.canceled", "payment.cancelled" -> "canceled";
            default -> "processing";
        };
    }

    private static PaymentGatewayStatus toGatewayStatus(String eventType, String rawStatus) {
        String normalized = (rawStatus == null ? eventType : rawStatus).toLowerCase();
        if (normalized.contains(STATUS_SUCCEEDED) || normalized.contains("captured")) {
            return PaymentGatewayStatus.CAPTURED;
        }
        if (normalized.contains(STATUS_FAILED)) {
            return PaymentGatewayStatus.FAILED;
        }
        if (normalized.contains("cancel")) {
            return PaymentGatewayStatus.CANCELLED;
        }
        if (normalized.contains("refund")) {
            return PaymentGatewayStatus.REFUNDED;
        }
        if (normalized.contains("authoriz")) {
            return PaymentGatewayStatus.AUTHORIZED;
        }
        return PaymentGatewayStatus.PENDING;
    }
}
