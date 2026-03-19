package com.example.skishop.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(
        @NotBlank(message = "リフレッシュトークンは必須です")
        String refreshToken
) {}
