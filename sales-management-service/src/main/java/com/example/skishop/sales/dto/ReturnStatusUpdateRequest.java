package com.example.skishop.sales.dto;

import jakarta.validation.constraints.NotBlank;

public record ReturnStatusUpdateRequest(
        @NotBlank(message = "ステータスは必須です")
        String status
) {}
