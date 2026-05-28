package com.example.skishop.payment.service;

import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.payment.dto.CreatePaymentIntentRequest;
import com.example.skishop.payment.dto.PaymentResponse;
import com.example.skishop.payment.dto.ProcessPaymentRequest;
import com.example.skishop.payment.dto.RefundRequest;
import com.example.skishop.payment.config.PaymentGatewayProperties;
import com.example.skishop.payment.gateway.SimulatedPaymentGatewayClient;
import com.example.skishop.payment.model.Payment;
import com.example.skishop.payment.model.PaymentWebhookEvent;
import com.example.skishop.payment.repository.PaymentRepository;
import com.example.skishop.payment.repository.PaymentWebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentWebhookEventRepository webhookEventRepository;
    @Mock private EventPublisher eventPublisher;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, webhookEventRepository,
                new SimulatedPaymentGatewayClient(new ObjectMapper(), paymentGatewayProperties()), eventPublisher);
    }

    @Nested
    @DisplayName("支払いインテント作成")
    class CreatePaymentIntent {

        @Test
        @DisplayName("有効なリクエストで支払いインテント作成が成功する")
        void should_createPaymentIntent_when_validRequest() {
            UUID userId = UUID.randomUUID();
            var request = new CreatePaymentIntentRequest(userId, BigDecimal.valueOf(59800), "CREDIT_CARD", null);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentResponse response = paymentService.createPaymentIntent(request);

            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(59800));
            assertThat(response.status()).isEqualTo("PENDING");
            verify(eventPublisher).publish(any());
        }
    }

    @Nested
    @DisplayName("支払い処理")
    class ProcessPayment {

        @Test
        @DisplayName("PENDING状態の支払い処理が成功しCAPTURED状態になる")
        void should_processPayment_when_statusPending() {
            UUID paymentId = UUID.randomUUID();
            var payment = new Payment(UUID.randomUUID(), BigDecimal.valueOf(10000), "CREDIT_CARD");
            var request = new ProcessPaymentRequest("pm_test123");
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentResponse response = paymentService.processPayment(paymentId, request);

            assertThat(response.status()).isEqualTo("CAPTURED");
            assertThat(response.gatewayProvider()).isEqualTo("simulated");
            assertThat(response.completedAt()).isNotNull();
            verify(eventPublisher, times(2)).publish(any());
        }

        @Test
        @DisplayName("CAPTURED状態からの支払い処理で例外がスローされる")
        void should_throwException_when_paymentAlreadyProcessed() {
            UUID paymentId = UUID.randomUUID();
            var payment = new Payment(UUID.randomUUID(), BigDecimal.valueOf(10000), "CREDIT_CARD");
            payment.setStatus(Payment.PaymentStatus.CAPTURED);
            var request = new ProcessPaymentRequest("pm_test123");
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.processPayment(paymentId, request))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("存在しない支払いIDで例外がスローされる")
        void should_throwNotFound_when_paymentNotExists() {
            UUID paymentId = UUID.randomUUID();
            var request = new ProcessPaymentRequest("pm_test123");
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.processPayment(paymentId, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("返金処理")
    class RefundPayment {

        @Test
        @DisplayName("CAPTURED状態の全額返金が成功する")
        void should_refund_when_statusCaptured() {
            UUID paymentId = UUID.randomUUID();
            var payment = new Payment(UUID.randomUUID(), BigDecimal.valueOf(10000), "CREDIT_CARD");
            payment.setStatus(Payment.PaymentStatus.CAPTURED);
            var request = new RefundRequest(null, "お客様都合");
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentResponse response = paymentService.refundPayment(paymentId, request);

            assertThat(response.status()).isEqualTo("REFUNDED");
            assertThat(response.refundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("部分返金が成功する")
        void should_partialRefund_when_amountSpecified() {
            UUID paymentId = UUID.randomUUID();
            var payment = new Payment(UUID.randomUUID(), BigDecimal.valueOf(10000), "CREDIT_CARD");
            payment.setStatus(Payment.PaymentStatus.CAPTURED);
            var request = new RefundRequest(BigDecimal.valueOf(3000), "一部返品");
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentResponse response = paymentService.refundPayment(paymentId, request);

            assertThat(response.status()).isEqualTo("PARTIALLY_REFUNDED");
            assertThat(response.refundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }

        @Test
        @DisplayName("PENDING状態の返金で例外がスローされる")
        void should_throwException_when_refundOnPending() {
            UUID paymentId = UUID.randomUUID();
            var payment = new Payment(UUID.randomUUID(), BigDecimal.valueOf(10000), "CREDIT_CARD");
            var request = new RefundRequest(null, "テスト");
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.refundPayment(paymentId, request))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    @DisplayName("支払い取得・履歴")
    class GetPayment {

        @Test
        @DisplayName("存在しない支払いIDでResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_paymentNotExists() {
            UUID paymentId = UUID.randomUUID();
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPayment(paymentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("支払い履歴取得が成功する")
        void should_returnHistory_when_validUserId() {
            UUID userId = UUID.randomUUID();
            var payment = new Payment(userId, BigDecimal.valueOf(10000), "CREDIT_CARD");
            Pageable pageable = PageRequest.of(0, 20);
            Page<Payment> page = new PageImpl<>(List.of(payment), pageable, 1);
            when(paymentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(page);

            Page<PaymentResponse> result = paymentService.getPaymentHistory(userId, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().getFirst().userId()).isEqualTo(userId);
        }
    }

    @Nested
    @DisplayName("Webhook処理")
    class HandleWebhook {

        @Test
        @DisplayName("Webhookで決済成功を一度だけ反映できる")
        void should_capturePayment_when_webhookSucceeded() {
            var payment = new Payment(UUID.randomUUID(), BigDecimal.valueOf(10000), "CREDIT_CARD");
            String payload = """
                    {"id":"evt_1","type":"payment_intent.succeeded","paymentIntentId":"%s","status":"succeeded","amount":10000}
                    """.formatted(payment.getPaymentIntentId());
            when(webhookEventRepository.findByProviderAndEventId("simulated", "evt_1")).thenReturn(Optional.empty());
            when(webhookEventRepository.save(any(PaymentWebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));
            when(paymentRepository.findByPaymentIntentId(payment.getPaymentIntentId())).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentResponse result = paymentService.handleWebhook(payload, signature(payload));

            assertThat(result.status()).isEqualTo("CAPTURED");
            verify(eventPublisher, times(2)).publish(any());
        }

        @Test
        @DisplayName("重複Webhookは二重処理されない")
        void should_ignoreDuplicateWebhook_when_alreadyProcessed() {
            UUID paymentId = UUID.randomUUID();
            var webhook = new PaymentWebhookEvent("simulated", "evt_dup", "payment_intent.succeeded", "hash");
            webhook.markProcessed(paymentId);
            var payment = new Payment(UUID.randomUUID(), BigDecimal.valueOf(10000), "CREDIT_CARD");
            when(webhookEventRepository.findByProviderAndEventId("simulated", "evt_dup")).thenReturn(Optional.of(webhook));
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

            PaymentResponse result = paymentService.handleWebhook(
                    "{\"id\":\"evt_dup\",\"type\":\"payment_intent.succeeded\",\"paymentIntentId\":\"pi_test\",\"status\":\"succeeded\"}",
                    signature("{\"id\":\"evt_dup\",\"type\":\"payment_intent.succeeded\",\"paymentIntentId\":\"pi_test\",\"status\":\"succeeded\"}"));

            assertThat(result).isNotNull();
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        @DisplayName("署名なしWebhookは拒否される")
        void should_rejectWebhook_when_signatureMissing() {
            var payment = new Payment(UUID.randomUUID(), BigDecimal.valueOf(10000), "CREDIT_CARD");
            String payload = """
                    {"id":"evt_missing_sig","type":"payment_intent.succeeded","paymentIntentId":"%s","status":"succeeded"}
                    """.formatted(payment.getPaymentIntentId());

            assertThatThrownBy(() -> paymentService.handleWebhook(payload, null))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Webhook signature is required");

            verify(webhookEventRepository, never()).save(any(PaymentWebhookEvent.class));
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        @DisplayName("Webhookで決済失敗を反映できる")
        void should_markPaymentFailed_when_webhookFailed() {
            var payment = new Payment(UUID.randomUUID(), BigDecimal.valueOf(10000), "CREDIT_CARD");
            String payload = """
                    {"id":"evt_failed","type":"payment_intent.payment_failed","paymentIntentId":"%s","status":"failed"}
                    """.formatted(payment.getPaymentIntentId());
            when(webhookEventRepository.findByProviderAndEventId("simulated", "evt_failed")).thenReturn(Optional.empty());
            when(webhookEventRepository.save(any(PaymentWebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));
            when(paymentRepository.findByPaymentIntentId(payment.getPaymentIntentId())).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentResponse result = paymentService.handleWebhook(payload, signature(payload));

            assertThat(result.status()).isEqualTo("FAILED");
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("CAPTURED後に遅延した失敗Webhookが来ても状態を巻き戻さない")
        void should_ignoreStaleFailedWebhook_when_paymentAlreadyCaptured() {
            var payment = new Payment(UUID.randomUUID(), BigDecimal.valueOf(10000), "CREDIT_CARD");
            payment.setStatus(Payment.PaymentStatus.CAPTURED);
            String payload = """
                    {"id":"evt_stale_failed","type":"payment_intent.payment_failed","paymentIntentId":"%s","status":"failed"}
                    """.formatted(payment.getPaymentIntentId());
            when(webhookEventRepository.findByProviderAndEventId("simulated", "evt_stale_failed")).thenReturn(Optional.empty());
            when(webhookEventRepository.save(any(PaymentWebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));
            when(paymentRepository.findByPaymentIntentId(payment.getPaymentIntentId())).thenReturn(Optional.of(payment));

            PaymentResponse result = paymentService.handleWebhook(payload, signature(payload));

            assertThat(result.status()).isEqualTo("CAPTURED");
            verify(paymentRepository, never()).save(any(Payment.class));
            verify(eventPublisher, never()).publish(any());
            ArgumentCaptor<PaymentWebhookEvent> webhookCaptor = ArgumentCaptor.forClass(PaymentWebhookEvent.class);
            verify(webhookEventRepository, times(2)).save(webhookCaptor.capture());
            assertThat(webhookCaptor.getAllValues().getLast().getStatus()).isEqualTo(PaymentWebhookEvent.ProcessingStatus.IGNORED);
        }

        @Test
        @DisplayName("FAILED後に成功Webhookが来ても不正遷移として拒否される")
        void should_rejectCapturedWebhook_when_paymentAlreadyFailed() {
            var payment = new Payment(UUID.randomUUID(), BigDecimal.valueOf(10000), "CREDIT_CARD");
            payment.setStatus(Payment.PaymentStatus.FAILED);
            String payload = """
                    {"id":"evt_failed_to_captured","type":"payment_intent.succeeded","paymentIntentId":"%s","status":"succeeded"}
                    """.formatted(payment.getPaymentIntentId());
            when(webhookEventRepository.findByProviderAndEventId("simulated", "evt_failed_to_captured")).thenReturn(Optional.empty());
            when(webhookEventRepository.save(any(PaymentWebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));
            when(paymentRepository.findByPaymentIntentId(payment.getPaymentIntentId())).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.handleWebhook(payload, signature(payload)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Payment status transition is not allowed");

            verify(paymentRepository, never()).save(any(Payment.class));
            verify(eventPublisher, never()).publish(any());
            ArgumentCaptor<PaymentWebhookEvent> webhookCaptor = ArgumentCaptor.forClass(PaymentWebhookEvent.class);
            verify(webhookEventRepository, times(2)).save(webhookCaptor.capture());
            assertThat(webhookCaptor.getAllValues().getLast().getStatus()).isEqualTo(PaymentWebhookEvent.ProcessingStatus.FAILED);
        }
    }

    private PaymentGatewayProperties paymentGatewayProperties() {
        PaymentGatewayProperties properties = new PaymentGatewayProperties();
        properties.setWebhookSecret(WEBHOOK_SECRET);
        properties.setRequireWebhookSignature(true);
        properties.setWebhookToleranceSeconds(300);
        return properties;
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
