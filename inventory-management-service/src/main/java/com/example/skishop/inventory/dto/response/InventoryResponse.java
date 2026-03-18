package com.example.skishop.inventory.dto.response;

import com.example.skishop.inventory.model.Inventory;

import java.time.OffsetDateTime;

/**
 * 在庫情報レスポンス DTO。
 */
public record InventoryResponse(
        Long id,
        Long productId,
        String productName,
        String productSku,
        Integer stockQuantity,
        Integer reservedQuantity,
        Integer availableQuantity,
        String warehouseId,
        Integer reorderLevel,
        Boolean isLowStock,
        OffsetDateTime updatedAt
) {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getProduct() != null ? inventory.getProduct().getId() : null,
                inventory.getProduct() != null ? inventory.getProduct().getName() : null,
                inventory.getProduct() != null ? inventory.getProduct().getSku() : null,
                inventory.getStockQuantity(),
                inventory.getReservedQuantity(),
                inventory.getAvailableQuantity(),
                inventory.getWarehouseId(),
                inventory.getReorderLevel(),
                inventory.isLowStock(),
                inventory.getUpdatedAt()
        );
    }
}
