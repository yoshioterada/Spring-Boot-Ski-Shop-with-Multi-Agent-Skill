package com.example.skishop.mailsend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateServiceTest {

    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        templateService = new TemplateService(engine);
    }

    @Test
    @DisplayName("テンプレートをレンダリングし、HTMLとプレーンテキストを生成できる")
    void should_renderTemplateAndGeneratePlainText_when_validVariablesProvided() {
        // Arrange
        var variables = Map.<String, Object>of(
                "subject", "メールアドレス確認のお願い",
                "baseUrl", "http://localhost:3000",
                "firstName", "太郎",
                "verifyUrl", "http://localhost:3000/verify-email?token=abc"
        );

        // Act
        var rendered = templateService.render("email-verification", variables);

        // Assert
        assertThat(rendered.html()).contains("verify-email?token=abc");
        assertThat(rendered.plainText()).doesNotContain("<html");
        assertThat(rendered.plainText()).contains("メールアドレス");
    }

    @Test
    @DisplayName("ウェルカムテンプレートを正しくレンダリングできる")
    void should_renderWelcomeTemplate_when_validVariables() {
        // Arrange
        var variables = Map.<String, Object>of(
                "subject", "ご登録ありがとうございます",
                "baseUrl", "http://localhost:3000",
                "firstName", "太郎",
                "lastName", "山田"
        );

        // Act
        var rendered = templateService.render("welcome", variables);

        // Assert
        assertThat(rendered.html()).isNotBlank();
        assertThat(rendered.html()).contains("太郎");
        assertThat(rendered.plainText()).doesNotContain("<html");
    }

    @Test
    @DisplayName("パスワードリセットテンプレートを正しくレンダリングできる")
    void should_renderPasswordResetTemplate_when_validVariables() {
        // Arrange
        var variables = Map.<String, Object>of(
                "subject", "パスワードリセットのご案内",
                "baseUrl", "http://localhost:3000",
                "firstName", "太郎",
                "resetUrl", "http://localhost:3000/reset-password?token=xyz123"
        );

        // Act
        var rendered = templateService.render("password-reset", variables);

        // Assert
        assertThat(rendered.html()).isNotBlank();
        assertThat(rendered.html()).contains("http://localhost:3000/reset-password?token=xyz123");
        assertThat(rendered.plainText()).doesNotContain("<html");
    }

    @Test
    @DisplayName("注文確認テンプレートを正しくレンダリングできる")
    void should_renderOrderConfirmationTemplate_when_validVariables() {
        // Arrange
        var variables = Map.<String, Object>of(
                "subject", "ご注文の確認",
                "baseUrl", "http://localhost:3000",
                "orderNumber", "ORD-20260318-001",
                "totalAmount", "¥15,000",
                "recipientName", "山田太郎"
        );

        // Act
        var rendered = templateService.render("order-confirmation", variables);

        // Assert
        assertThat(rendered.html()).isNotBlank();
        assertThat(rendered.html()).contains("ORD-20260318-001");
        assertThat(rendered.plainText()).doesNotContain("<html");
    }

    @Test
    @DisplayName("注文キャンセルテンプレートを正しくレンダリングできる")
    void should_renderOrderCancelledTemplate_when_validVariables() {
        // Arrange
        var variables = Map.<String, Object>of(
                "subject", "ご注文のキャンセル完了",
                "baseUrl", "http://localhost:3000",
                "orderNumber", "ORD-20260318-002",
                "totalAmount", "¥8,500",
                "recipientName", "山田太郎"
        );

        // Act
        var rendered = templateService.render("order-cancelled", variables);

        // Assert
        assertThat(rendered.html()).isNotBlank();
        assertThat(rendered.html()).contains("ORD-20260318-002");
        assertThat(rendered.plainText()).doesNotContain("<html");
    }

    @Test
    @DisplayName("出荷通知テンプレートを正しくレンダリングできる")
    void should_renderShipmentNotificationTemplate_when_validVariables() {
        // Arrange
        var variables = Map.<String, Object>of(
                "subject", "商品の発送完了のお知らせ",
                "baseUrl", "http://localhost:3000",
                "orderNumber", "ORD-20260318-003",
                "trackingNumber", "TRK-987654321",
                "carrier", "ヤマト運輸",
                "recipientName", "山田太郎"
        );

        // Act
        var rendered = templateService.render("shipment-notification", variables);

        // Assert
        assertThat(rendered.html()).isNotBlank();
        assertThat(rendered.html()).contains("TRK-987654321");
        assertThat(rendered.plainText()).doesNotContain("<html");
    }

    @Test
    @DisplayName("配送完了テンプレートを正しくレンダリングできる")
    void should_renderDeliveryConfirmationTemplate_when_validVariables() {
        // Arrange
        var variables = Map.<String, Object>of(
                "subject", "商品のお届け完了のお知らせ",
                "baseUrl", "http://localhost:3000",
                "orderNumber", "ORD-20260318-004",
                "recipientName", "山田太郎"
        );

        // Act
        var rendered = templateService.render("delivery-confirmation", variables);

        // Assert
        assertThat(rendered.html()).isNotBlank();
        assertThat(rendered.html()).contains("ORD-20260318-004");
        assertThat(rendered.plainText()).doesNotContain("<html");
    }

    @Test
    @DisplayName("メールアドレス変更確認テンプレートを正しくレンダリングできる")
    void should_renderEmailChangeVerificationTemplate_when_validVariables() {
        // Arrange
        var variables = Map.<String, Object>of(
                "subject", "メールアドレス変更の確認",
                "baseUrl", "http://localhost:3000",
                "firstName", "太郎",
                "verifyUrl", "http://localhost:3000/verify-email-change?token=def456"
        );

        // Act
        var rendered = templateService.render("email-change-verification", variables);

        // Assert
        assertThat(rendered.html()).isNotBlank();
        assertThat(rendered.html()).contains("http://localhost:3000/verify-email-change?token=def456");
        assertThat(rendered.plainText()).doesNotContain("<html");
    }
}
