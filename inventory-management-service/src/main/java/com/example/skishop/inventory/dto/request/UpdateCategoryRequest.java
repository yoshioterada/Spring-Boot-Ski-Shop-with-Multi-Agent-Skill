package com.example.skishop.inventory.dto.request;

import jakarta.validation.constraints.Size;

/**
 * カテゴリ更新リクエスト DTO。null フィールドは更新しない。
 */
public record UpdateCategoryRequest(
        @Size(min = 1, max = 100, message = "カテゴリ名は 1〜100 文字で入力してください")
        String name,

        @Size(max = 500, message = "説明は 500 文字以内で入力してください")
        String description,

        @Size(max = 500, message = "画像 URL は 500 文字以内で入力してください")
        String imageUrl,

        Boolean isActive
) {
}
