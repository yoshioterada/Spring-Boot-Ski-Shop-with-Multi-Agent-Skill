package com.example.skishop.payment.service;

import com.example.skishop.common.event.DomainEvent;
import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.payment.dto.*;
import com.example.skishop.payment.gateway.PaymentGatewayClient;
import com.example.skishop.payment.gateway.PaymentGatewayClient.PaymentGatewayStatus;
import com.example.skishop.payment.model.Payment;
import com.example.skishop.payment.model.PaymentWebhookEvent;
import com.example.skishop.payment.repository.PaymentRepository;
import com.example.skishop.payment.repository.PaymentWebhookEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String EVENT_PRODUCER = "payment-service";
    private static final String PAYMENT_RESOURCE = "Payment";

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final EventPublisher eventPublisher;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentWebhookEventRepository webhookEventRepository,
                          PaymentGatewayClient paymentGatewayClient,
                          EventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.paymentGatewayClient = paymentGatewayClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PaymentResponse createPaymentIntent(CreatePaymentIntentRequest request) {
        log.info("Creating payment intent: userId={}, amount={}", request.userId(), request.amount());

        var payment = new Payment(request.userId(), request.amount(), request.paymentMethod());
        payment.setOrderId(request.orderId());
        payment = paymentRepository.save(payment);

        var gatewayIntent = paymentGatewayClient.createIntent(new PaymentGatewayClient.CreateGatewayPaymentIntentCommand(
                payment.getId(), payment.getUserId(), payment.getOrderId(), payment.getAmount(),
                payment.getCurrency(), payment.getPaymentMethod()));
        payment.setGatewayProvider(paymentGatewayClient.provider());
        payment.setGatewayPaymentId(gatewayIntent.gatewayPaymentId());
        payment.setPaymentIntentId(gatewayIntent.paymentIntentId());
        payment.setRawGatewayStatus(gatewayIntent.rawStatus());
        payment.setGatewayResponse(gatewayIntent.rawResponse());
        payment = paymentRepository.save(payment);

        eventPublisher.publish(DomainEvent.create("PaymentIntentCreated", EVENT_PRODUCER,
                PaymentEventPayload.from(payment)));

        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse processPayment(UUID paymentId, ProcessPaymentRequest request) {
        if (log.isInfoEnabled()) {
            log.info("Processing payment: {}, methodId={}", paymentId, request.paymentMethodId());
        }
        Payment payment = findPaymentOrThrow(paymentId);

        if (payment.getStatus() != Payment.PaymentStatus.PENDING) {
            throw new BusinessRuleViolationException("INVALID_PAYMENT_STATUS",
                    "支払いはステータス PENDING のみ処理可能です。現在のステータス: " + payment.getStatus());
        }

        var gatewayResult = paymentGatewayClient.capture(new PaymentGatewayClient.ConfirmGatewayPaymentCommand(
                payment.getId(), payment.getGatewayPaymentId(), payment.getPaymentIntentId(),
                payment.getAmount(), payment.getCurrency(), request.paymentMethodId()));
        applyGatewayResult(payment, gatewayResult.status(), gatewayResult.rawStatus(),
                gatewayResult.rawResponse(), gatewayResult.failureCode(), gatewayResult.failureReason());
        payment = paymentRepository.save(payment);

        publishPaymentOutcome(payment);

        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId) {
        return toResponse(findPaymentOrThrow(paymentId));
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPaymentHistory(UUID userId, Pageable pageable) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::toResponse);
    }

    @Transactional
    public PaymentResponse refundPayment(UUID paymentId, RefundRequest request) {
        if (log.isInfoEnabled()) {
            log.info("Processing refund for payment: {}, reason={}", paymentId, request.reason());
        }
        Payment payment = findPaymentOrThrow(paymentId);

        if (payment.getStatus() != Payment.PaymentStatus.CAPTURED
                && payment.getStatus() != Payment.PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BusinessRuleViolationException("INVALID_REFUND_STATUS",
                    "返金はステータス CAPTURED または PARTIALLY_REFUNDED のみ可能です。現在のステータス: " + payment.getStatus());
        }

        BigDecimal refundAmount = (request.amount() != null) ? request.amount() : payment.getAmount();
        if (refundAmount.signum() <= 0 || refundAmount.compareTo(payment.getAmount().subtract(payment.getRefundedAmount())) > 0) {
            throw new BusinessRuleViolationException("INVALID_REFUND_AMOUNT", "返金額が不正です");
        }

        var refundResult = paymentGatewayClient.refund(new PaymentGatewayClient.GatewayRefundCommand(
                payment.getId(), payment.getGatewayPaymentId(), payment.getAmount(), refundAmount, request.reason()));
        BigDecimal totalRefunded = payment.getRefundedAmount().add(refundAmount);
        Payment.PaymentStatus nextStatus = totalRefunded.compareTo(payment.getAmount()) >= 0
            ? Payment.PaymentStatus.REFUNDED
            : Payment.PaymentStatus.PARTIALLY_REFUNDED;
        transitionStatus(payment, nextStatus);
        payment.setRefundedAmount(totalRefunded);
        payment.setGatewayResponse(refundResult.rawResponse());
        payment = paymentRepository.save(payment);

        eventPublisher.publish(DomainEvent.create("RefundProcessed", EVENT_PRODUCER,
                PaymentEventPayload.from(payment, refundAmount)));

        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse handleWebhook(String payload, String signature) {
        log.info("Handling payment webhook, signaturePresent={}", signature != null && !signature.isBlank());
        var event = paymentGatewayClient.verifyAndParseWebhook(payload, signature);
        PaymentWebhookEvent webhookEvent = recordWebhookEvent(event, payload);
        if (webhookEvent.getStatus() == PaymentWebhookEvent.ProcessingStatus.PROCESSED) {
                if (log.isInfoEnabled()) {
                log.info("Duplicate payment webhook ignored: provider={}, eventId={}",
                    paymentGatewayClient.provider(), event.eventId());
                }
            return webhookEvent.getPaymentId() == null ? null : getPayment(webhookEvent.getPaymentId());
        }

        try {
            Payment payment = findPaymentForWebhook(event);
            StatusTransitionResult transitionResult = applyGatewayResult(payment, event.status(), event.rawStatus(), event.rawPayload(),
                    event.failureCode(), event.failureReason());
            if (transitionResult.ignored()) {
                webhookEvent.markIgnored(payment.getId(), transitionResult.reason());
            } else {
                if (transitionResult.changed()) {
                    paymentRepository.save(Objects.requireNonNull(payment));
                    publishPaymentOutcome(payment);
                }
                webhookEvent.markProcessed(payment.getId());
            }
            webhookEventRepository.save(webhookEvent);
            return toResponse(payment);
        } catch (RuntimeException e) {
            webhookEvent.markFailed(e.getMessage());
            webhookEventRepository.save(webhookEvent);
            throw e;
        }
    }

    private Payment findPaymentOrThrow(UUID paymentId) {
        UUID requiredPaymentId = Objects.requireNonNull(paymentId);
        return paymentRepository.findById(requiredPaymentId)
            .orElseThrow(() -> new ResourceNotFoundException(PAYMENT_RESOURCE, requiredPaymentId.toString()));
    }

    private Payment findPaymentForWebhook(PaymentGatewayClient.VerifiedWebhookEvent event) {
        if (event.paymentIntentId() != null && !event.paymentIntentId().isBlank()) {
            return paymentRepository.findByPaymentIntentId(event.paymentIntentId())
                    .orElseThrow(() -> new ResourceNotFoundException(PAYMENT_RESOURCE, event.paymentIntentId()));
        }
        if (event.gatewayPaymentId() != null && !event.gatewayPaymentId().isBlank()) {
            return paymentRepository.findByGatewayPaymentId(event.gatewayPaymentId())
                    .orElseThrow(() -> new ResourceNotFoundException(PAYMENT_RESOURCE, event.gatewayPaymentId()));
        }
        throw new BusinessRuleViolationException("INVALID_WEBHOOK", "Payment identifier is required in webhook payload");
    }

    private PaymentWebhookEvent recordWebhookEvent(PaymentGatewayClient.VerifiedWebhookEvent event, String payload) {
        return webhookEventRepository.findByProviderAndEventId(paymentGatewayClient.provider(), event.eventId())
                .orElseGet(() -> {
                    try {
                        return webhookEventRepository.save(new PaymentWebhookEvent(
                                paymentGatewayClient.provider(), event.eventId(), event.eventType(), sha256(payload)));
                    } catch (DataIntegrityViolationException duplicate) {
                        return webhookEventRepository.findByProviderAndEventId(paymentGatewayClient.provider(), event.eventId())
                                .orElseThrow(() -> duplicate);
                    }
                });
    }

    private StatusTransitionResult applyGatewayResult(Payment payment,
                                                      PaymentGatewayStatus gatewayStatus,
                                                      String rawStatus,
                                                      String rawResponse,
                                                      String failureCode,
                                                      String failureReason) {
        Instant now = Instant.now();
        Payment.PaymentStatus nextStatus = toPaymentStatus(gatewayStatus);
        StatusTransitionResult transitionResult = transitionStatus(payment, nextStatus);
        if (transitionResult.ignored()) {
            return transitionResult;
        }

        payment.setGatewayProvider(paymentGatewayClient.provider());
        payment.setRawGatewayStatus(rawStatus);
        payment.setGatewayResponse(rawResponse);
        payment.setFailureCode(failureCode);
        payment.setFailureReason(failureReason);

        switch (nextStatus) {
            case AUTHORIZED -> {
                if (transitionResult.changed()) {
                    payment.setAuthorizedAt(now);
                }
            }
            case CAPTURED -> {
                if (transitionResult.changed()) {
                    payment.setCapturedAt(now);
                    payment.setCompletedAt(now);
                }
            }
            case FAILED -> {
                if (transitionResult.changed()) {
                    payment.setFailedAt(now);
                    payment.setCompletedAt(now);
                }
            }
            case CANCELLED -> {
                if (transitionResult.changed()) {
                    payment.setCompletedAt(now);
                }
            }
            case REFUNDED -> {
                if (transitionResult.changed()) {
                    payment.setCompletedAt(now);
                }
            }
            case PENDING, REQUIRES_ACTION, PARTIALLY_REFUNDED -> {
                if (log.isDebugEnabled()) {
                    log.debug("No timestamp update required for payment status {}", nextStatus);
                }
            }
        }
        return transitionResult;
    }

    private Payment.PaymentStatus toPaymentStatus(PaymentGatewayStatus gatewayStatus) {
        return switch (gatewayStatus) {
            case REQUIRES_ACTION -> Payment.PaymentStatus.REQUIRES_ACTION;
            case AUTHORIZED -> Payment.PaymentStatus.AUTHORIZED;
            case CAPTURED -> Payment.PaymentStatus.CAPTURED;
            case FAILED -> Payment.PaymentStatus.FAILED;
            case CANCELLED -> Payment.PaymentStatus.CANCELLED;
            case REFUNDED -> Payment.PaymentStatus.REFUNDED;
            default -> Payment.PaymentStatus.PENDING;
        };
    }

    private StatusTransitionResult transitionStatus(Payment payment, Payment.PaymentStatus nextStatus) {
        Payment.PaymentStatus currentStatus = payment.getStatus();
        if (currentStatus == nextStatus) {
            return StatusTransitionResult.noChangeResult();
        }
        if (isStaleWebhookTransition(currentStatus, nextStatus)) {
            return StatusTransitionResult.ignoredResult("Stale webhook transition ignored: " + currentStatus + " -> " + nextStatus);
        }
        if (!isAllowedTransition(currentStatus, nextStatus)) {
            throw new BusinessRuleViolationException("INVALID_PAYMENT_STATUS_TRANSITION",
                    "Payment status transition is not allowed: " + currentStatus + " -> " + nextStatus);
        }
        payment.setStatus(nextStatus);
        return StatusTransitionResult.changedResult();
    }

    private boolean isAllowedTransition(Payment.PaymentStatus currentStatus, Payment.PaymentStatus nextStatus) {
        return switch (currentStatus) {
            case PENDING -> nextStatus == Payment.PaymentStatus.REQUIRES_ACTION
                    || nextStatus == Payment.PaymentStatus.AUTHORIZED
                    || nextStatus == Payment.PaymentStatus.CAPTURED
                    || nextStatus == Payment.PaymentStatus.FAILED
                    || nextStatus == Payment.PaymentStatus.CANCELLED;
            case REQUIRES_ACTION -> nextStatus == Payment.PaymentStatus.AUTHORIZED
                    || nextStatus == Payment.PaymentStatus.CAPTURED
                    || nextStatus == Payment.PaymentStatus.FAILED
                    || nextStatus == Payment.PaymentStatus.CANCELLED;
            case AUTHORIZED -> nextStatus == Payment.PaymentStatus.CAPTURED
                    || nextStatus == Payment.PaymentStatus.FAILED
                    || nextStatus == Payment.PaymentStatus.CANCELLED;
            case CAPTURED -> nextStatus == Payment.PaymentStatus.PARTIALLY_REFUNDED
                    || nextStatus == Payment.PaymentStatus.REFUNDED;
            case PARTIALLY_REFUNDED -> nextStatus == Payment.PaymentStatus.PARTIALLY_REFUNDED
                    || nextStatus == Payment.PaymentStatus.REFUNDED;
            case FAILED, CANCELLED, REFUNDED -> false;
        };
    }

    private boolean isStaleWebhookTransition(Payment.PaymentStatus currentStatus, Payment.PaymentStatus nextStatus) {
        boolean currentIsSuccessful = currentStatus == Payment.PaymentStatus.CAPTURED
                || currentStatus == Payment.PaymentStatus.PARTIALLY_REFUNDED
                || currentStatus == Payment.PaymentStatus.REFUNDED;
        boolean nextIsPreSuccessOrFailure = nextStatus == Payment.PaymentStatus.PENDING
                || nextStatus == Payment.PaymentStatus.REQUIRES_ACTION
                || nextStatus == Payment.PaymentStatus.AUTHORIZED
                || nextStatus == Payment.PaymentStatus.FAILED
                || nextStatus == Payment.PaymentStatus.CANCELLED;
        return currentIsSuccessful && nextIsPreSuccessOrFailure;
    }

    private void publishPaymentOutcome(Payment payment) {
        if (payment.getStatus() == Payment.PaymentStatus.CAPTURED) {
            var payload = PaymentEventPayload.from(payment);
            eventPublisher.publish(DomainEvent.create("PaymentCaptured", EVENT_PRODUCER, payload));
            eventPublisher.publish(DomainEvent.create("PaymentProcessed", EVENT_PRODUCER, payload));
        } else if (payment.getStatus() == Payment.PaymentStatus.FAILED
                || payment.getStatus() == Payment.PaymentStatus.CANCELLED) {
            eventPublisher.publish(DomainEvent.create("PaymentFailed", EVENT_PRODUCER, PaymentEventPayload.from(payment)));
        }
    }

    private static String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getId(), p.getUserId(), p.getOrderId(), p.getPaymentIntentId(),
                p.getStatus().name(), p.getAmount(), p.getCurrency(), p.getPaymentMethod(),
                p.getGatewayProvider(), p.getRefundedAmount(), p.getCompletedAt(), p.getCreatedAt());
    }

    public record PaymentEventPayload(
            UUID paymentId,
            UUID userId,
            UUID orderId,
            String paymentIntentId,
            String gatewayPaymentId,
            BigDecimal amount,
            String status,
            String provider
    ) {
        static PaymentEventPayload from(Payment payment) {
            return from(payment, payment.getAmount());
        }

        static PaymentEventPayload from(Payment payment, BigDecimal amount) {
            return new PaymentEventPayload(payment.getId(), payment.getUserId(), payment.getOrderId(),
                    payment.getPaymentIntentId(), payment.getGatewayPaymentId(), amount,
                    payment.getStatus().name(), payment.getGatewayProvider());
        }
    }

    private record StatusTransitionResult(boolean changed, boolean ignored, String reason) {

        private static StatusTransitionResult changedResult() {
            return new StatusTransitionResult(true, false, null);
        }

        private static StatusTransitionResult noChangeResult() {
            return new StatusTransitionResult(false, false, null);
        }

        private static StatusTransitionResult ignoredResult(String reason) {
            return new StatusTransitionResult(false, true, reason);
        }
    }
}
