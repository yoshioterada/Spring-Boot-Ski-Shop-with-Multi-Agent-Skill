package com.example.skishop.inventory.event;

import com.example.skishop.inventory.dto.event.InventoryUpdatedEvent;
import com.example.skishop.inventory.dto.event.LowStockAlertEvent;
import com.example.skishop.inventory.dto.event.ProductCreatedEvent;
import com.example.skishop.inventory.dto.event.ProductUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 在庫管理サービスの Kafka イベントパブリッシャー。
 */
@Component
public class InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);

    private static final String TOPIC_PRODUCT_CREATED = "inventory.product.created";
    private static final String TOPIC_PRODUCT_UPDATED = "inventory.product.updated";
    private static final String TOPIC_INVENTORY_UPDATED = "inventory.stock.updated";
    private static final String TOPIC_LOW_STOCK_ALERT = "inventory.stock.low-alert";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventoryEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishProductCreated(ProductCreatedEvent event) {
        log.info("ProductCreated イベントを発行: productId={}, sku={}", event.productId(), event.sku());
        kafkaTemplate.send(TOPIC_PRODUCT_CREATED, String.valueOf(event.productId()), event);
    }

    public void publishProductUpdated(ProductUpdatedEvent event) {
        log.info("ProductUpdated イベントを発行: productId={}", event.productId());
        kafkaTemplate.send(TOPIC_PRODUCT_UPDATED, String.valueOf(event.productId()), event);
    }

    public void publishInventoryUpdated(InventoryUpdatedEvent event) {
        log.info("InventoryUpdated イベントを発行: productId={}, warehouseId={}, available={}",
                event.productId(), event.warehouseId(), event.availableQuantity());
        kafkaTemplate.send(TOPIC_INVENTORY_UPDATED, String.valueOf(event.productId()), event);
    }

    public void publishLowStockAlert(LowStockAlertEvent event) {
        log.warn("LowStockAlert イベントを発行: productId={}, sku={}, available={}, reorderLevel={}",
                event.productId(), event.productSku(), event.availableQuantity(), event.reorderLevel());
        kafkaTemplate.send(TOPIC_LOW_STOCK_ALERT, String.valueOf(event.productId()), event);
    }
}
