package com.example.skishop.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record UpdatePriceRequest(
        @NotNull(message = "通常価格は必須です")
        @DecimalMin(value = "0")
        BigDecimal regularPrice,

        @DecimalMin(value = "0")
        BigDecimal salePrice,

        Instant saleStartDate,
        Instant saleEndDate
) {}
