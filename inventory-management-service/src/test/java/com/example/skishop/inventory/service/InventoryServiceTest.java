package com.example.skishop.inventory.service;

import com.example.skishop.inventory.dto.request.BulkInventoryUpdateRequest;
import com.example.skishop.inventory.dto.request.UpdateInventoryRequest;
import com.example.skishop.inventory.event.InventoryEventPublisher;
import com.example.skishop.inventory.model.Inventory;
import com.example.skishop.inventory.model.Product;
import com.example.skishop.inventory.repository.InventoryRepository;
import com.example.skishop.inventory.repository.ProductRepository;
import com.example.skishop.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @InjectMocks
    private InventoryService inventoryService;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryEventPublisher eventPublisher;

    private Product sampleProduct;
    private Inventory sampleInventory;

    @BeforeEach
    void setUp() {
        sampleProduct = new Product();
        sampleProduct.setId(1L);
        sampleProduct.setSku("SKI-001");
        sampleProduct.setName("テストスキー板");
        sampleProduct.setPrice(new BigDecimal("50000.00"));
        sampleProduct.setIsActive(true);

        sampleInventory = new Inventory();
        sampleInventory.setId(1L);
        sampleInventory.setProduct(sampleProduct);
        sampleInventory.setStockQuantity(100);
        sampleInventory.setReservedQuantity(10);
        sampleInventory.setWarehouseId("MAIN");
        sampleInventory.setReorderLevel(20);
    }

    @Test
    @DisplayName("有効な商品 ID が指定された場合、在庫一覧を返す")
    void should_returnInventories_when_validProductIdProvided() {
        // Arrange
        when(productRepository.existsById(1L)).thenReturn(true);
        when(inventoryRepository.findByProductIdWithProduct(1L)).thenReturn(List.of(sampleInventory));

        // Act
        var result = inventoryService.findByProductId(1L);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(1L);
        assertThat(result.get(0).stockQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("存在しない商品 ID が指定された場合、ResourceNotFoundException をスローする")
    void should_throwResourceNotFoundException_when_productNotFound() {
        // Arrange
        when(productRepository.existsById(999L)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> inventoryService.findByProductId(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product")
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("在庫不足商品一覧を取得した場合、在庫が発注点以下の商品を返す")
    void should_returnLowStockInventories_when_findLowStockCalled() {
        // Arrange
        sampleInventory.setStockQuantity(5);
        sampleInventory.setReservedQuantity(0);
        sampleInventory.setReorderLevel(10);
        when(inventoryRepository.findLowStockInventories()).thenReturn(List.of(sampleInventory));

        // Act
        var result = inventoryService.findLowStockInventories();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isLowStock()).isTrue();
    }

    @Test
    @DisplayName("有効なリクエストで在庫を更新した場合、更新された在庫を返す")
    void should_returnUpdatedInventory_when_validUpdateRequestProvided() {
        // Arrange
        var request = new UpdateInventoryRequest(50, 5, "MAIN", 15);
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(inventoryRepository.findByProductIdAndWarehouseId(1L, "MAIN"))
                .thenReturn(Optional.of(sampleInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(sampleInventory);

        // Act
        var result = inventoryService.updateInventory(1L, request);

        // Assert
        assertThat(result).isNotNull();
        verify(eventPublisher).publishInventoryUpdated(any());
    }

    @Test
    @DisplayName("在庫更新後に在庫不足になった場合、LowStockAlert イベントを発行する")
    void should_publishLowStockAlert_when_stockBelowReorderLevel() {
        // Arrange
        var request = new UpdateInventoryRequest(5, 0, "MAIN", 10);
        sampleInventory.setStockQuantity(5);
        sampleInventory.setReservedQuantity(0);
        sampleInventory.setReorderLevel(10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(inventoryRepository.findByProductIdAndWarehouseId(1L, "MAIN"))
                .thenReturn(Optional.of(sampleInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(sampleInventory);

        // Act
        inventoryService.updateInventory(1L, request);

        // Assert
        verify(eventPublisher).publishInventoryUpdated(any());
        verify(eventPublisher).publishLowStockAlert(any());
    }

    @Test
    @DisplayName("新しい倉庫 ID で在庫を更新した場合、新しい在庫レコードが作成される")
    void should_createNewInventory_when_newWarehouseIdProvided() {
        // Arrange
        var request = new UpdateInventoryRequest(100, 0, "WAREHOUSE-2", 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(inventoryRepository.findByProductIdAndWarehouseId(1L, "WAREHOUSE-2"))
                .thenReturn(Optional.empty());
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> {
            var i = (Inventory) inv.getArgument(0);
            i.setId(2L);
            return i;
        });

        // Act
        var result = inventoryService.updateInventory(1L, request);

        // Assert
        assertThat(result).isNotNull();
        verify(inventoryRepository).save(any(Inventory.class));
    }

    @Test
    @DisplayName("存在しない商品 ID で在庫更新した場合、ResourceNotFoundException をスローする")
    void should_throwResourceNotFoundException_when_updatingNonExistentProduct() {
        // Arrange
        var request = new UpdateInventoryRequest(50, 0, "MAIN", 10);
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> inventoryService.updateInventory(999L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product");
    }

    @Test
    @DisplayName("一括更新リクエストで複数商品の在庫を更新した場合、全て更新される")
    void should_updateAllInventories_when_bulkUpdateRequestProvided() {
        // Arrange
        var product2 = new Product();
        product2.setId(2L);
        product2.setSku("SKI-002");
        product2.setName("テストスキーブーツ");
        product2.setIsActive(true);

        var inventory2 = new Inventory();
        inventory2.setId(2L);
        inventory2.setProduct(product2);
        inventory2.setStockQuantity(50);
        inventory2.setWarehouseId("MAIN");
        inventory2.setReorderLevel(5);

        var request = new BulkInventoryUpdateRequest(List.of(
                new BulkInventoryUpdateRequest.BulkInventoryItem(1L, "MAIN", 100),
                new BulkInventoryUpdateRequest.BulkInventoryItem(2L, "MAIN", 50)
        ));

        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product2));
        when(inventoryRepository.findByProductIdAndWarehouseId(1L, "MAIN"))
                .thenReturn(Optional.of(sampleInventory));
        when(inventoryRepository.findByProductIdAndWarehouseId(2L, "MAIN"))
                .thenReturn(Optional.of(inventory2));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        var results = inventoryService.bulkUpdate(request);

        // Assert
        assertThat(results).hasSize(2);
    }
}
