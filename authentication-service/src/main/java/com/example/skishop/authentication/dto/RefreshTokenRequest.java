package com.example.skishop.authentication.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
    @NotBlank(message = "リフレッシュトークンは必須です")
    String refreshToken
) {}
