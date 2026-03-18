package com.example.skishop.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 一括在庫更新リクエスト DTO。
 */
public record BulkInventoryUpdateRequest(
        @NotEmpty(message = "更新対象の在庫情報は必須です")
        List<@Valid @NotNull BulkInventoryItem> items
) {
    /**
     * 一括更新の個別アイテム。
     */
    public record BulkInventoryItem(
            @NotNull(message = "商品 ID は必須です")
            Long productId,

            @NotNull(message = "倉庫 ID は必須です")
            String warehouseId,

            @NotNull(message = "在庫数は必須です")
            Integer stockQuantity
    ) {
    }
}
