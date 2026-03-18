package com.example.skishop.inventory.controller;

import com.example.skishop.inventory.dto.request.BulkInventoryUpdateRequest;
import com.example.skishop.inventory.dto.request.UpdateInventoryRequest;
import com.example.skishop.inventory.dto.response.InventoryResponse;
import com.example.skishop.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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

import java.util.List;

/**
 * 在庫 REST コントローラー。
 */
@RestController
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * 商品の在庫状況確認（公開エンドポイント）。
     */
    // 認可不要: 公開エンドポイント（一般向け在庫確認）
    @GetMapping("/api/v1/products/{id}/inventory")
    public ResponseEntity<List<InventoryResponse>> getProductInventory(
            @PathVariable @Positive(message = "商品 ID は正の整数を指定してください") Long id) {
        return ResponseEntity.ok(inventoryService.findByProductId(id));
    }

    /**
     * 商品の在庫更新（管理者のみ）。
     */
    @PutMapping("/api/v1/products/{id}/inventory")
    @PreAuthorize("hasRole('INVENTORY_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<InventoryResponse> updateInventory(
            @PathVariable @Positive(message = "商品 ID は正の整数を指定してください") Long id,
            @Valid @RequestBody UpdateInventoryRequest request) {
        return ResponseEntity.ok(inventoryService.updateInventory(id, request));
    }

    /**
     * 在庫不足商品一覧取得（管理者のみ）。
     */
    @GetMapping("/api/v1/inventory/low-stock")
    @PreAuthorize("hasRole('INVENTORY_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<List<InventoryResponse>> getLowStockInventories() {
        return ResponseEntity.ok(inventoryService.findLowStockInventories());
    }

    /**
     * 在庫一括更新（管理者のみ）。
     */
    @PostMapping("/api/v1/inventory/bulk-update")
    @PreAuthorize("hasRole('INVENTORY_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<List<InventoryResponse>> bulkUpdateInventory(
            @Valid @RequestBody BulkInventoryUpdateRequest request) {
        return ResponseEntity.ok(inventoryService.bulkUpdate(request));
    }
}
