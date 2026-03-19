package com.example.skishop.payment.service;

import com.example.skishop.common.event.DomainEvent;
import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.payment.dto.*;
import com.example.skishop.payment.model.Payment;
import com.example.skishop.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final EventPublisher eventPublisher;

    public PaymentService(PaymentRepository paymentRepository, EventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PaymentResponse createPaymentIntent(CreatePaymentIntentRequest request) {
        log.info("Creating payment intent: userId={}, amount={}", request.userId(), request.amount());

        var payment = new Payment(request.userId(), request.amount(), request.paymentMethod());
        payment.setOrderId(request.orderId());
        payment = paymentRepository.save(payment);

        eventPublisher.publish(DomainEvent.create("PaymentIntentCreated", "payment-service",
                new PaymentEventPayload(payment.getId(), payment.getPaymentIntentId(), payment.getAmount())));

        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse processPayment(UUID paymentId, ProcessPaymentRequest request) {
        log.info("Processing payment: {}, methodId={}", paymentId, request.paymentMethodId());
        Payment payment = findPaymentOrThrow(paymentId);

        if (payment.getStatus() != Payment.PaymentStatus.PENDING) {
            throw new BusinessRuleViolationException("INVALID_PAYMENT_STATUS",
                    "支払いはステータス PENDING のみ処理可能です。現在のステータス: " + payment.getStatus());
        }

        // Simulated payment processing
        payment.setStatus(Payment.PaymentStatus.CAPTURED);
        payment.setGatewayResponse("{\"status\":\"success\",\"processor\":\"simulated\"}");
        payment.setGatewayProvider("simulated");
        payment.setCompletedAt(java.time.Instant.now());
        payment = paymentRepository.save(payment);

        eventPublisher.publish(DomainEvent.create("PaymentProcessed", "payment-service",
                new PaymentEventPayload(payment.getId(), payment.getPaymentIntentId(), payment.getAmount())));

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
        log.info("Processing refund for payment: {}, reason={}", paymentId, request.reason());
        Payment payment = findPaymentOrThrow(paymentId);

        if (payment.getStatus() != Payment.PaymentStatus.CAPTURED) {
            throw new BusinessRuleViolationException("INVALID_REFUND_STATUS",
                    "返金はステータス CAPTURED のみ可能です。現在のステータス: " + payment.getStatus());
        }

        java.math.BigDecimal refundAmount = (request.amount() != null) ? request.amount() : payment.getAmount();
        payment.setRefundedAmount(refundAmount);
        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        payment = paymentRepository.save(payment);

        eventPublisher.publish(DomainEvent.create("RefundProcessed", "payment-service",
                new PaymentEventPayload(payment.getId(), payment.getPaymentIntentId(), refundAmount)));

        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse handleWebhook(String payload, String signature) {
        log.info("Handling payment webhook, signature={}", signature);
        // Stub: In production, verify signature and parse payload from payment gateway
        return null;
    }

    private Payment findPaymentOrThrow(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId.toString()));
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getId(), p.getUserId(), p.getOrderId(), p.getPaymentIntentId(),
                p.getStatus().name(), p.getAmount(), p.getCurrency(), p.getPaymentMethod(),
                p.getGatewayProvider(), p.getRefundedAmount(), p.getCompletedAt(), p.getCreatedAt());
    }

    public record PaymentEventPayload(UUID paymentId, String paymentIntentId, java.math.BigDecimal amount) {}
}
