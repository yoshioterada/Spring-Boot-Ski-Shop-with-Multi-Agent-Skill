package com.example.skishop.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record ProcessPaymentRequest(
        @NotBlank(message = "支払い方法IDは必須です")
        String paymentMethodId
) {}
