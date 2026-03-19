package com.example.skishop.mailsend.consumer;

import com.example.skishop.mailsend.config.MailProperties;
import com.example.skishop.mailsend.service.MailService;
import com.example.skishop.mailsend.service.UserInfoResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MailEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MailService mailService = mock(MailService.class);
    private final UserInfoResolver userInfoResolver = mock(UserInfoResolver.class);
    private final MailProperties mailProps = new MailProperties(new MailProperties.Retry(3, 10, 2.0), "http://localhost:3000");
    private final MailEventConsumer consumer = new MailEventConsumer(objectMapper, mailService, userInfoResolver, mailProps);

    @Test
    @DisplayName("USER_REGISTERED を受信した場合、メール送信処理に委譲する")
    void should_delegateToMailService_when_userRegisteredConsumed() {
        // Arrange
        String json = "{" +
                "\"eventId\":\"e1\"," +
                "\"eventType\":\"USER_REGISTERED\"," +
                "\"producer\":\"authentication-service\"," +
                "\"correlationId\":\"c1\"," +
                "\"payload\":{" +
                "\"userId\":\"" + UUID.randomUUID() + "\"," +
                "\"email\":\"test@example.com\"," +
                "\"firstName\":\"太郎\"," +
                "\"lastName\":\"山田\"," +
                "\"role\":\"USER\"," +
                "\"verificationToken\":\"t1\"" +
                "}" +
                "}";

        // Act
        consumer.accept(MessageBuilder.withPayload(json).build());

        // Assert
        verify(mailService).sendInbound(any());
        verifyNoInteractions(userInfoResolver);
    }

    @Test
    @DisplayName("PASSWORD_RESET_REQUESTED を受信した場合、パスワードリセットメールを送信する")
    void should_delegateToMailService_when_passwordResetConsumed() {
        // Arrange
        var captor = ArgumentCaptor.forClass(MailService.InboundMail.class);
        String json = """
                {
                  "eventId": "e2",
                  "eventType": "PASSWORD_RESET_REQUESTED",
                  "producer": "authentication-service",
                  "correlationId": "c2",
                  "payload": {
                    "email": "reset@example.com",
                    "firstName": "次郎",
                    "resetToken": "rst-token-abc"
                  }
                }
                """;

        // Act
        consumer.accept(MessageBuilder.withPayload(json).build());

        // Assert
        verify(mailService).sendInbound(captor.capture());
        var mail = captor.getValue();
        assertThat(mail.templateName()).isEqualTo("password-reset");
        assertThat(mail.recipientEmail()).isEqualTo("reset@example.com");
        assertThat(mail.variables()).containsKey("resetUrl");
        assertThat((String) mail.variables().get("resetUrl")).contains("rst-token-abc");
        verifyNoInteractions(userInfoResolver);
    }

    @Test
    @DisplayName("user.verified を受信した場合、ウェルカムメールを送信する")
    void should_delegateToMailService_when_userVerifiedConsumed() {
        // Arrange
        var captor = ArgumentCaptor.forClass(MailService.InboundMail.class);
        String json = """
                {
                  "eventId": "e3",
                  "eventType": "user.verified",
                  "producer": "authentication-service",
                  "correlationId": "c3",
                  "payload": {
                    "email": "verified@example.com",
                    "firstName": "三郎",
                    "lastName": "佐藤"
                  }
                }
                """;

        // Act
        consumer.accept(MessageBuilder.withPayload(json).build());

        // Assert
        verify(mailService).sendInbound(captor.capture());
        var mail = captor.getValue();
        assertThat(mail.templateName()).isEqualTo("welcome");
        assertThat(mail.recipientEmail()).isEqualTo("verified@example.com");
        assertThat(mail.recipientName()).isEqualTo("佐藤 三郎");
        verifyNoInteractions(userInfoResolver);
    }

    @Test
    @DisplayName("OrderCreated を受信した場合、ユーザー情報を解決して注文確認メールを送信する")
    void should_resolveUserAndDelegate_when_orderCreatedConsumed() {
        // Arrange
        var captor = ArgumentCaptor.forClass(MailService.InboundMail.class);
        var customerId = UUID.randomUUID();
        when(userInfoResolver.resolve(any()))
                .thenReturn(new UserInfoResolver.UserInfo(UUID.randomUUID(), "customer@example.com", "花子", "鈴木"));

        String json = """
                {
                  "eventId": "e4",
                  "eventType": "OrderCreated",
                  "producer": "sales-management-service",
                  "correlationId": "c4",
                  "payload": {
                    "customerId": "%s",
                    "orderNumber": "ORD-001",
                    "totalAmount": 15000
                  }
                }
                """.formatted(customerId);

        // Act
        consumer.accept(MessageBuilder.withPayload(json).build());

        // Assert
        verify(userInfoResolver).resolve(customerId);
        verify(mailService).sendInbound(captor.capture());
        var mail = captor.getValue();
        assertThat(mail.templateName()).isEqualTo("order-confirmation");
        assertThat(mail.recipientEmail()).isEqualTo("customer@example.com");
        assertThat(mail.variables()).containsEntry("orderNumber", "ORD-001");
    }

    @Test
    @DisplayName("OrderCancelled を受信した場合、ユーザー情報を解決して注文キャンセルメールを送信する")
    void should_resolveUserAndDelegate_when_orderCancelledConsumed() {
        // Arrange
        var captor = ArgumentCaptor.forClass(MailService.InboundMail.class);
        var customerId = UUID.randomUUID();
        when(userInfoResolver.resolve(any()))
                .thenReturn(new UserInfoResolver.UserInfo(UUID.randomUUID(), "customer@example.com", "花子", "鈴木"));

        String json = """
                {
                  "eventId": "e5",
                  "eventType": "OrderCancelled",
                  "producer": "sales-management-service",
                  "correlationId": "c5",
                  "payload": {
                    "customerId": "%s",
                    "orderNumber": "ORD-002",
                    "totalAmount": 8000
                  }
                }
                """.formatted(customerId);

        // Act
        consumer.accept(MessageBuilder.withPayload(json).build());

        // Assert
        verify(userInfoResolver).resolve(customerId);
        verify(mailService).sendInbound(captor.capture());
        var mail = captor.getValue();
        assertThat(mail.templateName()).isEqualTo("order-cancelled");
        assertThat(mail.recipientEmail()).isEqualTo("customer@example.com");
        assertThat(mail.variables()).containsEntry("orderNumber", "ORD-002");
    }

    @Test
    @DisplayName("ShipmentStatusUpdated(SHIPPED) を受信した場合、発送通知メールを送信する")
    void should_sendShipmentNotification_when_shipmentStatusIsShipped() {
        // Arrange
        var captor = ArgumentCaptor.forClass(MailService.InboundMail.class);
        var customerId = UUID.randomUUID();
        when(userInfoResolver.resolve(any()))
                .thenReturn(new UserInfoResolver.UserInfo(UUID.randomUUID(), "customer@example.com", "花子", "鈴木"));

        String json = """
                {
                  "eventId": "e6",
                  "eventType": "ShipmentStatusUpdated",
                  "producer": "inventory-management-service",
                  "correlationId": "c6",
                  "payload": {
                    "customerId": "%s",
                    "orderNumber": "ORD-003",
                    "status": "SHIPPED",
                    "trackingNumber": "TRK-12345",
                    "carrier": "ヤマト運輸"
                  }
                }
                """.formatted(customerId);

        // Act
        consumer.accept(MessageBuilder.withPayload(json).build());

        // Assert
        verify(userInfoResolver).resolve(customerId);
        verify(mailService).sendInbound(captor.capture());
        var mail = captor.getValue();
        assertThat(mail.templateName()).isEqualTo("shipment-notification");
        assertThat(mail.recipientEmail()).isEqualTo("customer@example.com");
        assertThat(mail.variables())
                .containsEntry("trackingNumber", "TRK-12345")
                .containsEntry("carrier", "ヤマト運輸");
    }

    @Test
    @DisplayName("ShipmentStatusUpdated(DELIVERED) を受信した場合、配達完了メールを送信する")
    void should_sendDeliveryConfirmation_when_shipmentStatusIsDelivered() {
        // Arrange
        var captor = ArgumentCaptor.forClass(MailService.InboundMail.class);
        var customerId = UUID.randomUUID();
        when(userInfoResolver.resolve(any()))
                .thenReturn(new UserInfoResolver.UserInfo(UUID.randomUUID(), "customer@example.com", "花子", "鈴木"));

        String json = """
                {
                  "eventId": "e7",
                  "eventType": "ShipmentStatusUpdated",
                  "producer": "inventory-management-service",
                  "correlationId": "c7",
                  "payload": {
                    "customerId": "%s",
                    "orderNumber": "ORD-004",
                    "status": "DELIVERED",
                    "trackingNumber": "TRK-67890",
                    "carrier": "佐川急便"
                  }
                }
                """.formatted(customerId);

        // Act
        consumer.accept(MessageBuilder.withPayload(json).build());

        // Assert
        verify(userInfoResolver).resolve(customerId);
        verify(mailService).sendInbound(captor.capture());
        var mail = captor.getValue();
        assertThat(mail.templateName()).isEqualTo("delivery-confirmation");
        assertThat(mail.recipientEmail()).isEqualTo("customer@example.com");
        assertThat(mail.variables())
                .containsEntry("trackingNumber", "TRK-67890")
                .containsEntry("carrier", "佐川急便");
    }

    @Test
    @DisplayName("user.email_changed を受信した場合、メールアドレス変更確認メールを送信する")
    void should_delegateToMailService_when_emailChangedConsumed() {
        // Arrange
        var captor = ArgumentCaptor.forClass(MailService.InboundMail.class);
        String json = """
                {
                  "eventId": "e8",
                  "eventType": "user.email_changed",
                  "producer": "authentication-service",
                  "correlationId": "c8",
                  "payload": {
                    "newEmail": "newemail@example.com",
                    "firstName": "四郎",
                    "verificationToken": "chg-token-xyz"
                  }
                }
                """;

        // Act
        consumer.accept(MessageBuilder.withPayload(json).build());

        // Assert
        verify(mailService).sendInbound(captor.capture());
        var mail = captor.getValue();
        assertThat(mail.templateName()).isEqualTo("email-change-verification");
        assertThat(mail.recipientEmail()).isEqualTo("newemail@example.com");
        assertThat(mail.variables()).containsKey("verifyUrl");
        assertThat((String) mail.variables().get("verifyUrl")).contains("chg-token-xyz");
        verifyNoInteractions(userInfoResolver);
    }

    @Test
    @DisplayName("未知のイベントタイプの場合、メール送信処理を呼び出さない")
    void should_ignoreEvent_when_unknownEventType() {
        // Arrange
        String json = """
                {
                  "eventId": "e9",
                  "eventType": "SOME_UNKNOWN_TYPE",
                  "producer": "unknown-service",
                  "correlationId": "c9",
                  "payload": {
                    "key": "value"
                  }
                }
                """;

        // Act
        consumer.accept(MessageBuilder.withPayload(json).build());

        // Assert
        verify(mailService, never()).sendInbound(any());
        verifyNoInteractions(userInfoResolver);
    }

    @Test
    @DisplayName("USER_REGISTERED で verificationToken が欠落している場合、メール送信処理を呼び出さない")
    void should_skipEvent_when_payloadMissingRequiredFields() {
        // Arrange
        String json = """
                {
                  "eventId": "e10",
                  "eventType": "USER_REGISTERED",
                  "producer": "authentication-service",
                  "correlationId": "c10",
                  "payload": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "firstName": "太郎",
                    "lastName": "山田",
                    "role": "USER"
                  }
                }
                """.formatted(UUID.randomUUID());

        // Act
        consumer.accept(MessageBuilder.withPayload(json).build());

        // Assert
        verify(mailService, never()).sendInbound(any());
    }
}
