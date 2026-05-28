package com.example.skishop.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGatewayClient {

    String provider();

    GatewayPaymentIntent createIntent(CreateGatewayPaymentIntentCommand command);

    GatewayPaymentResult capture(ConfirmGatewayPaymentCommand command);

    GatewayRefundResult refund(GatewayRefundCommand command);

    VerifiedWebhookEvent verifyAndParseWebhook(String payload, String signature);

    record CreateGatewayPaymentIntentCommand(
            UUID paymentId,
            UUID userId,
            UUID orderId,
            BigDecimal amount,
            String currency,
            String paymentMethod
    ) {}

    record GatewayPaymentIntent(
            String gatewayPaymentId,
            String paymentIntentId,
            String rawStatus,
            String rawResponse
    ) {}

    record ConfirmGatewayPaymentCommand(
            UUID paymentId,
            String gatewayPaymentId,
            String paymentIntentId,
            BigDecimal amount,
            String currency,
            String paymentMethodId
    ) {}

    record GatewayPaymentResult(
            PaymentGatewayStatus status,
            String rawStatus,
            String rawResponse,
            String failureCode,
            String failureReason
    ) {}

    record GatewayRefundCommand(
            UUID paymentId,
            String gatewayPaymentId,
            BigDecimal originalAmount,
            BigDecimal refundAmount,
            String reason
    ) {}

    record GatewayRefundResult(
            String gatewayRefundId,
            String rawResponse
    ) {}

    record VerifiedWebhookEvent(
            String eventId,
            String eventType,
            String gatewayPaymentId,
            String paymentIntentId,
            PaymentGatewayStatus status,
            BigDecimal amount,
            String rawStatus,
            String rawPayload,
            String failureCode,
            String failureReason
    ) {}

    enum PaymentGatewayStatus {
        PENDING,
        REQUIRES_ACTION,
        AUTHORIZED,
        CAPTURED,
        FAILED,
        CANCELLED,
        REFUNDED
    }
}
