package com.example.skishop.payment.service;

import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.payment.dto.CreatePaymentIntentRequest;
import com.example.skishop.payment.dto.PaymentResponse;
import com.example.skishop.payment.dto.ProcessPaymentRequest;
import com.example.skishop.payment.dto.RefundRequest;
import com.example.skishop.payment.model.Payment;
import com.example.skishop.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private EventPublisher eventPublisher;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, eventPublisher);
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
            verify(eventPublisher).publish(any());
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

            assertThat(response.status()).isEqualTo("REFUNDED");
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
        @DisplayName("Webhookハンドラが正常に呼び出せる")
        void should_handleWebhook_without_error() {
            PaymentResponse result = paymentService.handleWebhook("{\"event\":\"test\"}", "sig_test");

            assertThat(result).isNull();
        }
    }
}
