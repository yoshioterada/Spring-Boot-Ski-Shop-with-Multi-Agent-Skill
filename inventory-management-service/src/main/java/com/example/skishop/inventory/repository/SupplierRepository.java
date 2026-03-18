package com.example.skishop.inventory.repository;

import com.example.skishop.inventory.model.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * サプライヤーリポジトリ。
 */
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
}
