package com.example.skishop.inventory.dto.response;

import com.example.skishop.inventory.model.Product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 商品一覧・基本情報レスポンス DTO。
 */
public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        String brand,
        Long categoryId,
        String categoryName,
        BigDecimal price,
        Boolean isActive,
        String primaryImageUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ProductResponse from(Product product) {
        var primaryImageUrl = product.getImages().stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .findFirst()
                .map(img -> img.getImageUrl())
                .orElse(null);

        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getBrand(),
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getPrice(),
                product.getIsActive(),
                primaryImageUrl,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
