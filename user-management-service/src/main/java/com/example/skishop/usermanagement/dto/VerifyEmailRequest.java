package com.example.skishop.usermanagement.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank(message = "トークンは必須です")
        String token
) {}
