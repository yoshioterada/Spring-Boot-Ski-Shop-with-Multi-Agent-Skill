package com.example.skishop.usermanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePreferenceRequest(
        @NotBlank(message = "値は必須です")
        @Size(max = 1000)
        String value,

        @Size(max = 50)
        String type
) {}
