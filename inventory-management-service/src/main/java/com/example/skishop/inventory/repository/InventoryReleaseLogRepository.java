package com.example.skishop.inventory.repository;

import com.example.skishop.inventory.model.InventoryReleaseLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryReleaseLogRepository extends MongoRepository<InventoryReleaseLog, String> {

    boolean existsByReferenceIdAndSkuAndReason(String referenceId, String sku, String reason);
}