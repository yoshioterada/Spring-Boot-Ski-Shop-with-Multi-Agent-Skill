package com.example.skishop.inventory.controller;

import com.example.skishop.inventory.dto.*;
import com.example.skishop.inventory.service.ProductService;
import com.example.skishop.inventory.service.SearchAnalyticsService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;
    private final SearchAnalyticsService searchAnalyticsService;

    public ProductController(ProductService productService, SearchAnalyticsService searchAnalyticsService) {
        this.productService = productService;
        this.searchAnalyticsService = searchAnalyticsService;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        log.info("Create product request: {}", request.sku());
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.created(URI.create("/api/v1/products/" + response.id())).body(response);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PutMapping("/products/{id}")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable String id,
                                                          @Valid @RequestBody UpdateProductRequest request) {
        log.info("Update product request: {}", id);
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String id) {
        log.info("Delete product request: {}", id);
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products")
    public ResponseEntity<Page<ProductResponse>> listProducts(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.listProducts(pageable));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable String id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/products/sku/{sku}")
    public ResponseEntity<ProductResponse> getProductBySku(@PathVariable String sku) {
        return ResponseEntity.ok(productService.getProductBySku(sku));
    }

    @GetMapping("/products/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String enhancedQuery,
            @RequestParam(defaultValue = "inventory-product-search") String source,
            @PageableDefault(size = 20) Pageable pageable) {
        long start = System.nanoTime();
        Page<ProductResponse> result = productService.searchProducts(q, category, pageable);
        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        // 検索ログを非同期で記録 (失敗しても応答を妨げない)
        searchAnalyticsService.logSearchAsync(q, enhancedQuery, source, result.getTotalElements(), durationMs);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/products/category/{categoryId}")
    public ResponseEntity<Page<ProductResponse>> listByCategory(@PathVariable String categoryId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.listByCategory(categoryId, pageable));
    }

    @PostMapping("/products/batch")
    public ResponseEntity<List<ProductResponse>> getProductsByIds(@RequestBody List<String> productIds) {
        return ResponseEntity.ok(productService.getProductsByIds(productIds));
    }

    /**
     * SKU リストから商品を一括取得する内部向けエンドポイント。
     * sales-management が売上分析でカテゴリ集計する際に使用する。
     */
    @PostMapping("/products/batch-by-sku")
    public ResponseEntity<List<ProductResponse>> getProductsBySkus(@RequestBody List<String> skus) {
        return ResponseEntity.ok(productService.getProductsBySkus(skus));
    }

    // --- Inventory endpoints ---

    @GetMapping("/inventory/{productId}")
    public ResponseEntity<ProductResponse> getInventory(@PathVariable String productId) {
        return ResponseEntity.ok(productService.getProductById(productId));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/inventory/reserve")
    public ResponseEntity<ProductResponse> reserveStock(@Valid @RequestBody ReserveStockRequest request) {
        log.info("Reserve stock: product={}, qty={}", request.productId(), request.quantity());
        return ResponseEntity.ok(productService.reserveStock(request));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/inventory/release")
    public ResponseEntity<ProductResponse> releaseStock(@Valid @RequestBody ReleaseStockRequest request) {
        log.info("Release stock: product={}, qty={}", request.productId(), request.quantity());
        return ResponseEntity.ok(productService.releaseStock(request));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/inventory/stock-in")
    public ResponseEntity<ProductResponse> stockIn(@Valid @RequestBody StockInRequest request) {
        log.info("Stock in: product={}, qty={}", request.productId(), request.quantity());
        return ResponseEntity.ok(productService.stockIn(request));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/inventory/stock-out")
    public ResponseEntity<ProductResponse> stockOut(@Valid @RequestBody StockOutRequest request) {
        log.info("Stock out: product={}, qty={}", request.productId(), request.quantity());
        return ResponseEntity.ok(productService.stockOut(request));
    }

    @GetMapping("/inventory/low-stock")
    public ResponseEntity<Page<ProductResponse>> getLowStockProducts(
            @RequestParam(defaultValue = "10") int threshold,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.getLowStockProducts(threshold, pageable));
    }

    /**
     * F4 滞留在庫レーダー用: 全商品の SKU・在庫数を返す。
     * ai-support-service の DeadStockService から内部 API キーで呼び出される。
     */
    @GetMapping("/inventory/all")
    public ResponseEntity<List<ProductResponse>> getAllInventory() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @PostMapping("/inventory/batch")
    public ResponseEntity<List<ProductResponse>> getInventoryBatch(@RequestBody List<String> productIds) {
        return ResponseEntity.ok(productService.getProductsByIds(productIds));
    }

    // --- Price endpoint ---

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PutMapping("/prices/{productId}")
    public ResponseEntity<ProductResponse> updatePrice(@PathVariable String productId,
                                                        @Valid @RequestBody UpdatePriceRequest request) {
        return ResponseEntity.ok(productService.updatePrice(productId, request));
    }
}
