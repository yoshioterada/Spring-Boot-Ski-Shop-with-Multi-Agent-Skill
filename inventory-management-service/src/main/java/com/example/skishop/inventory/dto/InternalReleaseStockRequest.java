package com.example.skishop.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InternalReleaseStockRequest(
        @NotBlank(message = "SKUは必須です")
        @Size(max = 100, message = "SKUは100文字以内である必要があります")
        String sku,

        @Min(value = 1, message = "数量は1以上である必要があります")
        int quantity,

        @NotBlank(message = "理由は必須です")
        @Size(max = 100, message = "理由は100文字以内である必要があります")
        String reason,

        @NotBlank(message = "参照IDは必須です")
        @Size(max = 100, message = "参照IDは100文字以内である必要があります")
        String referenceId
) {}