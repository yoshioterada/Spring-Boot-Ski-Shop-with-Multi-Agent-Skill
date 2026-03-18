package com.example.skishop.inventory.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 商品登録リクエスト DTO。
 */
public record CreateProductRequest(
        @NotBlank(message = "SKU は必須です")
        @Size(min = 1, max = 100, message = "SKU は 1〜100 文字で入力してください")
        @Pattern(regexp = "^[A-Z0-9_-]+$", message = "SKU は英大文字・数字・ハイフン・アンダースコアのみ使用可能です")
        String sku,

        @NotBlank(message = "商品名は必須です")
        @Size(min = 1, max = 200, message = "商品名は 1〜200 文字で入力してください")
        String name,

        @Size(max = 2000, message = "商品説明は 2000 文字以内で入力してください")
        String description,

        @Size(max = 100, message = "ブランド名は 100 文字以内で入力してください")
        String brand,

        Long categoryId,

        @NotNull(message = "価格は必須です")
        @DecimalMin(value = "0.00", message = "価格は 0 以上で入力してください")
        BigDecimal price,

        @DecimalMin(value = "0.00", message = "原価は 0 以上で入力してください")
        BigDecimal cost,

        @Positive(message = "重量は正の値で入力してください")
        BigDecimal weight,

        @Size(max = 100, message = "サイズは 100 文字以内で入力してください")
        String dimensions
) {
}
