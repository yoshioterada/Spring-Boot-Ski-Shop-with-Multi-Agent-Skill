package com.example.skishop.inventory.service;

import com.example.skishop.inventory.dto.event.InventoryUpdatedEvent;
import com.example.skishop.inventory.dto.event.LowStockAlertEvent;
import com.example.skishop.inventory.dto.request.BulkInventoryUpdateRequest;
import com.example.skishop.inventory.dto.request.UpdateInventoryRequest;
import com.example.skishop.inventory.dto.response.InventoryResponse;
import com.example.skishop.inventory.event.InventoryEventPublisher;
import com.example.skishop.inventory.model.Inventory;
import com.example.skishop.inventory.repository.InventoryRepository;
import com.example.skishop.inventory.repository.ProductRepository;
import com.example.skishop.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 在庫サービス。在庫の参照・更新機能を提供する。
 * 在庫更新は楽観的ロック (@Version) を使用して同時更新を制御する。
 */
@Service
@Transactional(readOnly = true)
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final String DEFAULT_WAREHOUSE_ID = "MAIN";

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final InventoryEventPublisher eventPublisher;

    public InventoryService(
            InventoryRepository inventoryRepository,
            ProductRepository productRepository,
            InventoryEventPublisher eventPublisher) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 商品の在庫状況を取得する。
     */
    public List<InventoryResponse> findByProductId(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product", productId);
        }
        return inventoryRepository.findByProductIdWithProduct(productId).stream()
                .map(InventoryResponse::from)
                .toList();
    }

    /**
     * 在庫不足商品一覧を取得する（管理者向け）。
     */
    public List<InventoryResponse> findLowStockInventories() {
        return inventoryRepository.findLowStockInventories().stream()
                .map(InventoryResponse::from)
                .toList();
    }

    /**
     * 商品の在庫を更新する（管理者のみ）。
     * 楽観的ロックにより同時更新を防ぐ。
     */
    @Transactional
    public InventoryResponse updateInventory(Long productId, UpdateInventoryRequest request) {
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        var inventory = inventoryRepository
                .findByProductIdAndWarehouseId(productId, request.warehouseId())
                .orElseGet(() -> {
                    var newInventory = new Inventory();
                    newInventory.setProduct(product);
                    newInventory.setWarehouseId(request.warehouseId());
                    return newInventory;
                });

        inventory.setStockQuantity(request.stockQuantity());
        if (request.reservedQuantity() != null) {
            inventory.setReservedQuantity(request.reservedQuantity());
        }
        if (request.reorderLevel() != null) {
            inventory.setReorderLevel(request.reorderLevel());
        }

        var saved = inventoryRepository.save(inventory);
        log.info("在庫を更新しました: productId={}, warehouseId={}, stock={}",
                productId, request.warehouseId(), saved.getStockQuantity());

        eventPublisher.publishInventoryUpdated(new InventoryUpdatedEvent(
                saved.getId(),
                product.getId(),
                product.getSku(),
                saved.getWarehouseId(),
                saved.getStockQuantity(),
                saved.getAvailableQuantity(),
                saved.getReservedQuantity(),
                OffsetDateTime.now()
        ));

        if (saved.isLowStock()) {
            eventPublisher.publishLowStockAlert(new LowStockAlertEvent(
                    saved.getId(),
                    product.getId(),
                    product.getSku(),
                    product.getName(),
                    saved.getWarehouseId(),
                    saved.getAvailableQuantity(),
                    saved.getReorderLevel(),
                    OffsetDateTime.now()
            ));
        }

        return InventoryResponse.from(saved);
    }

    /**
     * 在庫を一括更新する（管理者のみ）。
     */
    @Transactional
    public List<InventoryResponse> bulkUpdate(BulkInventoryUpdateRequest request) {
        return request.items().stream()
                .map(item -> {
                    var updateRequest = new UpdateInventoryRequest(
                            item.stockQuantity(),
                            null,
                            item.warehouseId(),
                            null
                    );
                    return updateInventory(item.productId(), updateRequest);
                })
                .toList();
    }
}
