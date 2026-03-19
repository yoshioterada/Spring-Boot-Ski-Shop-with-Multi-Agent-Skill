package com.example.skishop.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentIntentRequest(
        @NotNull(message = "ユーザーIDは必須です")
        UUID userId,

        @NotNull(message = "金額は必須です")
        @DecimalMin(value = "1", message = "金額は1以上である必要があります")
        BigDecimal amount,

        @NotBlank(message = "支払い方法は必須です")
        String paymentMethod,

        UUID orderId
) {}
