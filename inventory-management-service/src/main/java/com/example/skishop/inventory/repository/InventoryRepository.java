package com.example.skishop.inventory.repository;

import com.example.skishop.inventory.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 在庫リポジトリ。
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductIdAndWarehouseId(Long productId, String warehouseId);

    List<Inventory> findByProductId(Long productId);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product WHERE i.availableQuantity <= i.reorderLevel")
    List<Inventory> findLowStockInventories();

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product p WHERE p.id = :productId")
    List<Inventory> findByProductIdWithProduct(@Param("productId") Long productId);
}
