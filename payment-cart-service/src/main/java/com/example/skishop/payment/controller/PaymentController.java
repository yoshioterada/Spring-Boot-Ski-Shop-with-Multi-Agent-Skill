package com.example.skishop.payment.controller;

import com.example.skishop.common.security.SecurityUtils;
import com.example.skishop.payment.dto.*;
import com.example.skishop.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/intent")
    public ResponseEntity<PaymentResponse> createPaymentIntent(@Valid @RequestBody CreatePaymentIntentRequest request) {
        SecurityUtils.verifyOwnershipOrAdmin(request.userId());
        log.info("Create payment intent: userId={}", request.userId());
        PaymentResponse response = paymentService.createPaymentIntent(request);
        return ResponseEntity.created(URI.create("/api/v1/payments/" + response.id())).body(response);
    }

    @PostMapping("/{paymentId}/process")
    public ResponseEntity<PaymentResponse> processPayment(@PathVariable UUID paymentId,
                                                           @Valid @RequestBody ProcessPaymentRequest request) {
        PaymentResponse existing = paymentService.getPayment(paymentId);
        SecurityUtils.verifyOwnershipOrAdmin(existing.userId());
        return ResponseEntity.ok(paymentService.processPayment(paymentId, request));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        PaymentResponse response = paymentService.getPayment(paymentId);
        SecurityUtils.verifyOwnershipOrAdmin(response.userId());
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("#userId == authentication.principal or hasRole('ADMIN')")
    @GetMapping("/history")
    public ResponseEntity<Page<PaymentResponse>> getPaymentHistory(@RequestParam UUID userId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(paymentService.getPaymentHistory(userId, pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<PaymentResponse> refundPayment(@PathVariable UUID paymentId,
                                                          @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(paymentService.refundPayment(paymentId, request));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String payload,
                                                 @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        log.info("Payment webhook received");
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok("received");
    }
}
