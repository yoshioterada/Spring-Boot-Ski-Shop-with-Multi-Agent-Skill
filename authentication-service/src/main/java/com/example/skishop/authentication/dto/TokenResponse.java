package com.example.skishop.authentication.dto;

public record TokenResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    String scope
) {
    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, "read write");
    }
}
