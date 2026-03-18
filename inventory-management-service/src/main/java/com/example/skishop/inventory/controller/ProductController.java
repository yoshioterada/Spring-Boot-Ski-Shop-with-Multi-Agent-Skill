package com.example.skishop.inventory.controller;

import com.example.skishop.inventory.dto.request.CreateProductRequest;
import com.example.skishop.inventory.dto.request.UpdateProductRequest;
import com.example.skishop.inventory.dto.response.ProductDetailResponse;
import com.example.skishop.inventory.dto.response.ProductResponse;
import com.example.skishop.inventory.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;

/**
 * 商品 REST コントローラー。
 * 一般向け（参照系）と管理者向け（更新系）エンドポイントを提供する。
 */
@RestController
@RequestMapping("/api/v1/products")
@Validated
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 商品一覧取得（公開エンドポイント）。
     */
    // 認可不要: 公開エンドポイント（一般向け商品一覧）
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> listProducts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(productService.findAll(pageable));
    }

    /**
     * 商品詳細取得（公開エンドポイント）。
     */
    // 認可不要: 公開エンドポイント（一般向け商品詳細）
    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> getProduct(
            @PathVariable @Positive(message = "商品 ID は正の整数を指定してください") Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    /**
     * 商品検索（公開エンドポイント）。
     */
    // 認可不要: 公開エンドポイント（一般向け商品検索）
    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String brand,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(
                productService.search(keyword, categoryId, minPrice, maxPrice, brand, pageable));
    }

    /**
     * 商品登録（管理者のみ）。
     */
    @PostMapping
    @PreAuthorize("hasRole('INVENTORY_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<ProductDetailResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        var created = productService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/products/" + created.id()))
                .body(created);
    }

    /**
     * 商品更新（管理者のみ）。
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('INVENTORY_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<ProductDetailResponse> updateProduct(
            @PathVariable @Positive(message = "商品 ID は正の整数を指定してください") Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    /**
     * 商品削除（論理削除、管理者のみ）。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INVENTORY_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable @Positive(message = "商品 ID は正の整数を指定してください") Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
