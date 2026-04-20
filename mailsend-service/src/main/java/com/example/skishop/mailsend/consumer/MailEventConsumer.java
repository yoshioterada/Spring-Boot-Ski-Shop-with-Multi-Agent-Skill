package com.example.skishop.mailsend.consumer;

import com.example.skishop.mailsend.config.MailProperties;
import com.example.skishop.mailsend.service.MailService;
import com.example.skishop.mailsend.service.UserInfoResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class MailEventConsumer implements Consumer<String> {

    private static final Logger log = LoggerFactory.getLogger(MailEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final MailService mailService;
    private final UserInfoResolver userInfoResolver;
    private final MailProperties mailProperties;

    public MailEventConsumer(ObjectMapper objectMapper,
                             MailService mailService,
                             UserInfoResolver userInfoResolver,
                             MailProperties mailProperties) {
        this.objectMapper = objectMapper;
        this.mailService = mailService;
        this.userInfoResolver = userInfoResolver;
        this.mailProperties = mailProperties;
    }

    @Override
    public void accept(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventId = text(root, "eventId");
            String eventType = text(root, "eventType");
            String correlationId = text(root, "correlationId");
            JsonNode eventPayload = root.get("payload");

            if (eventId == null || eventType == null || eventPayload == null || eventPayload.isNull()) {
                log.warn("Invalid event payload skipped: {}", payload);
                return;
            }

            Inbound resolved = resolve(eventType, eventPayload);
            if (resolved == null) {
                log.info("Unknown eventType ignored: {}", eventType);
                return;
            }

            var inboundMail = new MailService.InboundMail(
                    eventId,
                    eventType,
                    correlationId != null ? correlationId : UUID.randomUUID().toString(),
                    resolved.recipientEmail,
                    resolved.recipientName,
                    resolved.templateName,
                    resolved.subject,
                    resolved.variables
            );

            mailService.sendInbound(inboundMail);
        } catch (Exception e) {
            log.error("Failed to consume mail event", e);
            throw new RuntimeException(e);
        }
    }

    private Inbound resolve(String eventType, JsonNode payload) {
        return switch (eventType) {
            case "USER_REGISTERED" -> userRegistered(payload);
            case "PASSWORD_RESET_REQUESTED" -> passwordReset(payload);
            case "user.verified" -> welcome(payload);
            case "OrderCreated" -> orderConfirmation(payload);
            case "OrderCancelled" -> orderCancelled(payload);
            case "ShipmentStatusUpdated" -> shipment(payload);
            case "user.email_changed" -> emailChange(payload);
            default -> null;
        };
    }

    private Inbound userRegistered(JsonNode payload) {
        String email = text(payload, "email");
        String firstName = text(payload, "firstName");
        String lastName = text(payload, "lastName");
        String token = text(payload, "verificationToken");

        if (email == null || token == null) {
            return null;
        }

        var variables = new HashMap<String, Object>();
        variables.put("firstName", firstName != null ? firstName : "お客様");
        variables.put("lastName", lastName);
        variables.put("verifyUrl", mailProperties.baseUrl() + "/verify-email?token=" + token);

        return new Inbound(email, fullName(firstName, lastName), "email-verification", "メールアドレス確認のお願い", variables);
    }

    private Inbound passwordReset(JsonNode payload) {
        String email = text(payload, "email");
        String firstName = text(payload, "firstName");
        String token = text(payload, "resetToken");
        if (email == null || token == null) {
            return null;
        }

        var variables = new HashMap<String, Object>();
        variables.put("firstName", firstName != null ? firstName : "お客様");
        variables.put("resetUrl", mailProperties.baseUrl() + "/reset-password?token=" + token);

        return new Inbound(email, firstName, "password-reset", "パスワード再設定のご案内", variables);
    }

    private Inbound welcome(JsonNode payload) {
        String email = text(payload, "email");
        String firstName = text(payload, "firstName");
        String lastName = text(payload, "lastName");
        if (email == null) {
            return null;
        }

        var variables = new HashMap<String, Object>();
        variables.put("firstName", firstName != null ? firstName : "お客様");
        variables.put("lastName", lastName);

        return new Inbound(email, fullName(firstName, lastName), "welcome", "Azure SkiShop へようこそ", variables);
    }

    private Inbound orderConfirmation(JsonNode payload) {
        UUID customerId = uuid(payload, "customerId");
        String orderNumber = text(payload, "orderNumber");
        String totalAmount = payload.hasNonNull("totalAmount") ? payload.get("totalAmount").asText() : null;

        if (customerId == null || orderNumber == null) {
            return null;
        }

        var user = userInfoResolver.resolve(customerId);

        var variables = new HashMap<String, Object>();
        variables.put("orderNumber", orderNumber);
        variables.put("totalAmount", totalAmount);
        variables.put("recipientName", fullName(user.firstName(), user.lastName()));

        return new Inbound(user.email(), fullName(user.firstName(), user.lastName()),
                "order-confirmation", "ご注文確認", variables);
    }

    private Inbound orderCancelled(JsonNode payload) {
        UUID customerId = uuid(payload, "customerId");
        String orderNumber = text(payload, "orderNumber");
        String totalAmount = payload.hasNonNull("totalAmount") ? payload.get("totalAmount").asText() : null;

        if (customerId == null || orderNumber == null) {
            return null;
        }

        var user = userInfoResolver.resolve(customerId);

        var variables = new HashMap<String, Object>();
        variables.put("orderNumber", orderNumber);
        variables.put("totalAmount", totalAmount);
        variables.put("recipientName", fullName(user.firstName(), user.lastName()));

        return new Inbound(user.email(), fullName(user.firstName(), user.lastName()),
                "order-cancelled", "ご注文キャンセル確認", variables);
    }

    private Inbound shipment(JsonNode payload) {
        UUID customerId = uuid(payload, "customerId");
        String orderNumber = text(payload, "orderNumber");
        String status = text(payload, "status");
        String trackingNumber = text(payload, "trackingNumber");
        String carrier = text(payload, "carrier");

        if (customerId == null || orderNumber == null || status == null) {
            return null;
        }

        if (!status.equalsIgnoreCase("SHIPPED") && !status.equalsIgnoreCase("DELIVERED")) {
            return null;
        }

        var user = userInfoResolver.resolve(customerId);

        var variables = new HashMap<String, Object>();
        variables.put("orderNumber", orderNumber);
        variables.put("trackingNumber", trackingNumber);
        variables.put("carrier", carrier);
        variables.put("recipientName", fullName(user.firstName(), user.lastName()));

        if (status.equalsIgnoreCase("SHIPPED")) {
            return new Inbound(user.email(), fullName(user.firstName(), user.lastName()),
                    "shipment-notification", "発送のお知らせ", variables);
        }

        return new Inbound(user.email(), fullName(user.firstName(), user.lastName()),
                "delivery-confirmation", "配達完了のお知らせ", variables);
    }

    private Inbound emailChange(JsonNode payload) {
        String newEmail = text(payload, "newEmail");
        String firstName = text(payload, "firstName");
        String token = text(payload, "verificationToken");
        if (newEmail == null || token == null) {
            return null;
        }

        var variables = new HashMap<String, Object>();
        variables.put("firstName", firstName != null ? firstName : "お客様");
        variables.put("verifyUrl", mailProperties.baseUrl() + "/verify-email?token=" + token);

        return new Inbound(newEmail, firstName, "email-change-verification", "メールアドレス変更の確認", variables);
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String fullName(String firstName, String lastName) {
        if (firstName == null && lastName == null) {
            return null;
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return lastName + " " + firstName;
    }

    private record Inbound(String recipientEmail, String recipientName, String templateName, String subject,
                           Map<String, Object> variables) {
    }
}
