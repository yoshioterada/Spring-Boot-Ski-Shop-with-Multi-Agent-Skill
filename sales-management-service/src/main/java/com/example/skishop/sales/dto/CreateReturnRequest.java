package com.example.skishop.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateReturnRequest(
        @NotNull(message = "注文IDは必須です")
        UUID orderId,

        @NotNull(message = "顧客IDは必須です")
        UUID customerId,

        @NotBlank(message = "返品理由は必須です")
        @Size(max = 500)
        String reason
) {}
