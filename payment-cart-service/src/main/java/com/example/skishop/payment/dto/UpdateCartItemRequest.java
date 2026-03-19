package com.example.skishop.payment.dto;

import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(
        @Min(value = 1, message = "数量は1以上である必要があります")
        int quantity
) {}
