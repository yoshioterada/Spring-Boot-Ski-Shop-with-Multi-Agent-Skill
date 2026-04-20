package com.example.skishop.usermanagement.consumer;

import com.example.skishop.usermanagement.service.UserRegistrationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Consumer;

public class UserEventConsumer implements Consumer<String> {

    private static final Logger log = LoggerFactory.getLogger(UserEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final UserRegistrationService userRegistrationService;

    public UserEventConsumer(ObjectMapper objectMapper,
                             UserRegistrationService userRegistrationService) {
        this.objectMapper = objectMapper;
        this.userRegistrationService = userRegistrationService;
    }

    @Override
    public void accept(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = text(root, "eventType");

            if (!"USER_REGISTERED".equals(eventType)) {
                return;
            }

            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                log.warn("USER_REGISTERED event has no payload, skipping");
                return;
            }

            String userIdStr = text(payload, "userId");
            String email = text(payload, "email");
            String firstName = text(payload, "firstName");
            String lastName = text(payload, "lastName");
            String verificationToken = text(payload, "verificationToken");

            if (email == null || verificationToken == null) {
                log.warn("USER_REGISTERED event missing required fields, skipping");
                return;
            }

            UUID userId = null;
            if (userIdStr != null) {
                try {
                    userId = UUID.fromString(userIdStr);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid userId in event payload: {}", userIdStr);
                }
            }

            log.info("Processing USER_REGISTERED event for email: {}", maskEmail(email));
            userRegistrationService.handleUserRegistered(userId, email, firstName, lastName, verificationToken);

        } catch (Exception e) {
            log.error("Failed to process user event: {}", e.getMessage(), e);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : null;
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIdx = email.indexOf('@');
        return email.substring(0, Math.min(2, atIdx)) + "***" + email.substring(atIdx);
    }
}
