package com.example.skishop.inventory.controller;

import com.example.skishop.inventory.dto.request.CreateCategoryRequest;
import com.example.skishop.inventory.dto.request.UpdateCategoryRequest;
import com.example.skishop.inventory.dto.response.CategoryResponse;
import com.example.skishop.inventory.dto.response.ProductResponse;
import com.example.skishop.inventory.service.CategoryService;
import com.example.skishop.inventory.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * カテゴリ REST コントローラー。
 */
@RestController
@RequestMapping("/api/v1/categories")
@Validated
public class CategoryController {

    private final CategoryService categoryService;
    private final ProductService productService;

    public CategoryController(CategoryService categoryService, ProductService productService) {
        this.categoryService = categoryService;
        this.productService = productService;
    }

    /**
     * カテゴリ一覧取得（公開エンドポイント）。
     */
    // 認可不要: 公開エンドポイント（一般向けカテゴリ一覧）
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        return ResponseEntity.ok(categoryService.findAll());
    }

    /**
     * カテゴリ詳細取得（公開エンドポイント）。
     */
    // 認可不要: 公開エンドポイント（一般向けカテゴリ詳細）
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategory(
            @PathVariable @Positive(message = "カテゴリ ID は正の整数を指定してください") Long id) {
        return ResponseEntity.ok(categoryService.findById(id));
    }

    /**
     * カテゴリ別商品一覧（公開エンドポイント）。
     */
    // 認可不要: 公開エンドポイント（一般向けカテゴリ別商品一覧）
    @GetMapping("/{id}/products")
    public ResponseEntity<Page<ProductResponse>> getProductsByCategory(
            @PathVariable @Positive(message = "カテゴリ ID は正の整数を指定してください") Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.findByCategory(id, pageable));
    }

    /**
     * カテゴリ登録（管理者のみ）。
     */
    @PostMapping
    @PreAuthorize("hasRole('INVENTORY_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {
        var created = categoryService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/categories/" + created.id()))
                .body(created);
    }

    /**
     * カテゴリ更新（管理者のみ）。
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('INVENTORY_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable @Positive(message = "カテゴリ ID は正の整数を指定してください") Long id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }
}
