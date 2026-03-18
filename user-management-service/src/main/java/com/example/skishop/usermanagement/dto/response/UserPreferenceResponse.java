package com.example.skishop.usermanagement.dto.response;

import java.util.UUID;

public record UserPreferenceResponse(
    Long id,
    UUID userId,
    String language,
    String currency
) {}
