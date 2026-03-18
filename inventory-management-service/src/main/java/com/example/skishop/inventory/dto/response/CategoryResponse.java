package com.example.skishop.inventory.dto.response;

import com.example.skishop.inventory.model.Category;

/**
 * カテゴリレスポンス DTO。
 */
public record CategoryResponse(
        Long id,
        String name,
        String description,
        Long parentId,
        String parentName,
        Integer level,
        String path,
        String imageUrl,
        Boolean isActive
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getParent() != null ? category.getParent().getId() : null,
                category.getParent() != null ? category.getParent().getName() : null,
                category.getLevel(),
                category.getPath(),
                category.getImageUrl(),
                category.getIsActive()
        );
    }
}
