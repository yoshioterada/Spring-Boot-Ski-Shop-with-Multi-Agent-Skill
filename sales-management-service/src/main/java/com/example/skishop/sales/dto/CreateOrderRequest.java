package com.example.skishop.sales.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull(message = "顧客IDは必須です")
        UUID customerId,

        @NotEmpty(message = "注文アイテムは最低1つ必要です")
        List<OrderItemRequest> items,

        @Size(max = 500)
        String shippingAddress,

        String notes
) {
    public record OrderItemRequest(
            @NotBlank String productId,
            @NotBlank String productName,
            String productSku,
            @Min(1) int quantity,
            @NotNull @DecimalMin("0") BigDecimal unitPrice
    ) {}
}
