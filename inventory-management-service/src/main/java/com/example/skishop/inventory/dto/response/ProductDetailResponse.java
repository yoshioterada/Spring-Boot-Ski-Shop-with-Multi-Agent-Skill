package com.example.skishop.inventory.dto.response;

import com.example.skishop.inventory.model.Product;
import com.example.skishop.inventory.model.ProductAttribute;
import com.example.skishop.inventory.model.ProductImage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 商品詳細レスポンス DTO。画像・属性情報を含む。
 */
public record ProductDetailResponse(
        Long id,
        String sku,
        String name,
        String description,
        String brand,
        Long categoryId,
        String categoryName,
        BigDecimal price,
        BigDecimal cost,
        BigDecimal weight,
        String dimensions,
        Boolean isActive,
        List<ImageInfo> images,
        List<AttributeInfo> attributes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record ImageInfo(
            Long id,
            String imageUrl,
            String altText,
            Integer sortOrder,
            Boolean isPrimary
    ) {
        public static ImageInfo from(ProductImage image) {
            return new ImageInfo(
                    image.getId(),
                    image.getImageUrl(),
                    image.getAltText(),
                    image.getSortOrder(),
                    image.getIsPrimary()
            );
        }
    }

    public record AttributeInfo(
            Long id,
            String attributeName,
            String attributeValue,
            Boolean isFilterable,
            Boolean isSortable
    ) {
        public static AttributeInfo from(ProductAttribute attr) {
            return new AttributeInfo(
                    attr.getId(),
                    attr.getAttributeName(),
                    attr.getAttributeValue(),
                    attr.getIsFilterable(),
                    attr.getIsSortable()
            );
        }
    }

    public static ProductDetailResponse from(Product product) {
        var images = product.getImages().stream()
                .map(ImageInfo::from)
                .toList();
        var attributes = product.getAttributes().stream()
                .map(AttributeInfo::from)
                .toList();

        return new ProductDetailResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getBrand(),
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getPrice(),
                product.getCost(),
                product.getWeight(),
                product.getDimensions(),
                product.getIsActive(),
                images,
                attributes,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
