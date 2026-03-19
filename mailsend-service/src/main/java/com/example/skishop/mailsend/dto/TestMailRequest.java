package com.example.skishop.mailsend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record TestMailRequest(
        @NotBlank String recipientEmail,
        @NotBlank String templateName,
        @NotNull Map<String, Object> variables
) {
}
