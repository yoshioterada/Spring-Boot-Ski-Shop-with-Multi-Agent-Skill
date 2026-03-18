package com.example.skishop.inventory.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 商品更新リクエスト DTO。null フィールドは更新しない。
 */
public record UpdateProductRequest(
        @Size(min = 1, max = 200, message = "商品名は 1〜200 文字で入力してください")
        String name,

        @Size(max = 2000, message = "商品説明は 2000 文字以内で入力してください")
        String description,

        @Size(max = 100, message = "ブランド名は 100 文字以内で入力してください")
        String brand,

        Long categoryId,

        @DecimalMin(value = "0.00", message = "価格は 0 以上で入力してください")
        BigDecimal price,

        @DecimalMin(value = "0.00", message = "原価は 0 以上で入力してください")
        BigDecimal cost,

        BigDecimal weight,

        @Size(max = 100, message = "サイズは 100 文字以内で入力してください")
        String dimensions,

        Boolean isActive
) {
}
