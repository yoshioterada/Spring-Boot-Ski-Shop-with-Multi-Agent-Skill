package com.example.skishop.inventory.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "inventory_release_logs")
@CompoundIndex(
        name = "uq_inventory_release_reference_sku_reason",
        def = "{'referenceId': 1, 'sku': 1, 'reason': 1}",
        unique = true)
public class InventoryReleaseLog {

    @Id
    private String id;

    private String referenceId;
    private String sku;
    private int quantity;
    private String reason;
    private Instant releasedAt;

    protected InventoryReleaseLog() {}

    public InventoryReleaseLog(String referenceId, String sku, int quantity, String reason) {
        this.referenceId = referenceId;
        this.sku = sku;
        this.quantity = quantity;
        this.reason = reason;
        this.releasedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getReferenceId() { return referenceId; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public String getReason() { return reason; }
    public Instant getReleasedAt() { return releasedAt; }
}