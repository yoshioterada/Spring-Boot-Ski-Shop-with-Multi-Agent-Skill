package com.example.skishop.inventory.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 在庫更新リクエスト DTO。
 */
public record UpdateInventoryRequest(
        @NotNull(message = "在庫数は必須です")
        @Min(value = 0, message = "在庫数は 0 以上で入力してください")
        Integer stockQuantity,

        @Min(value = 0, message = "予約数は 0 以上で入力してください")
        Integer reservedQuantity,

        @NotBlank(message = "倉庫 ID は必須です")
        @Size(min = 1, max = 50, message = "倉庫 ID は 1〜50 文字で入力してください")
        String warehouseId,

        @Min(value = 0, message = "発注点は 0 以上で入力してください")
        Integer reorderLevel
) {
}
