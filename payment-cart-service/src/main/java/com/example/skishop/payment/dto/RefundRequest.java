package com.example.skishop.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record RefundRequest(
        @DecimalMin(value = "0", message = "返金額は0以上である必要があります")
        BigDecimal amount,

        String reason
) {}
