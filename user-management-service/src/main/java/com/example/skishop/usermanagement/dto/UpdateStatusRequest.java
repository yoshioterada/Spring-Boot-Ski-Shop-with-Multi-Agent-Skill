package com.example.skishop.usermanagement.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateStatusRequest(
        @NotBlank(message = "ステータスは必須です")
        String status
) {}
