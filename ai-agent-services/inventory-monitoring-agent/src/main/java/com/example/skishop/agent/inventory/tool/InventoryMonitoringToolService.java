package com.example.skishop.agent.inventory.tool;

import com.example.skishop.agent.common.dto.InventoryAlert;
import com.example.skishop.agent.common.dto.InventoryStatus;
import com.example.skishop.agent.common.dto.ReservationRequest;
import com.example.skishop.agent.common.dto.ReservationResult;
import com.example.skishop.agent.inventory.client.InventoryManagementClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class InventoryMonitoringToolService {

    private static final Logger log = LoggerFactory.getLogger(InventoryMonitoringToolService.class);
    static final int LOW_STOCK_THRESHOLD = 5;

    private final InventoryManagementClient inventoryClient;

    public InventoryMonitoringToolService(InventoryManagementClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    @Tool(description = """
            指定した商品ID リストの在庫状況を確認し AVAILABLE/LOW_STOCK/OUT_OF_STOCK を返す。
            """)
    public List<InventoryStatus> checkInventoryAvailability(
            @ToolParam(description = "商品 ID リスト") List<String> productIds,
            @ToolParam(description = "必要数量") int requiredQuantity) {
        if (productIds == null || productIds.isEmpty()) return List.of();
        int qty = requiredQuantity > 0 ? requiredQuantity : 1;
        log.info("Tool checkInventoryAvailability: productIds={}, qty={}", productIds.size(), qty);
        return productIds.stream()
                .map(id -> enrichWithRouting(inventoryClient.getStock(id), qty))
                .toList();
    }

    @Tool(description = """
            注文 ID に紐づけて在庫を一時予約ロックする。
            """)
    public ReservationResult reserveInventory(
            @ToolParam(description = "注文 ID") String orderId,
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "予約アイテムリスト") List<ReservationRequest.ReservationItem> items,
            @ToolParam(description = "TTL (分)") int ttlMinutes) {
        log.info("Tool reserveInventory: orderId={}, items={}", orderId, items == null ? 0 : items.size());
        if (items == null || items.isEmpty()) {
            return new ReservationResult(null, orderId, false, List.of(), List.of(), Instant.now());
        }
        var request = new ReservationRequest(orderId, userId, items, ttlMinutes > 0 ? ttlMinutes : 30);
        return inventoryClient.reserve(request);
    }

    @Tool(description = """
            在庫が閾値（5）未満の全商品アラートを取得する。
            """)
    public List<InventoryAlert> getLowStockAlerts() {
        log.info("Tool getLowStockAlerts called");
        return inventoryClient.getLowStockItems(LOW_STOCK_THRESHOLD);
    }

    @Tool(description = """
            在庫切れ商品の代替候補を最大5件返す。
            """)
    public List<String> getAlternativeProducts(
            @ToolParam(description = "在庫切れ商品 ID") String outOfStockProductId,
            @ToolParam(description = "カテゴリ") String category,
            @ToolParam(description = "スキルレベル") String skillLevel) {
        log.info("Tool getAlternativeProducts: productId={}, category={}", outOfStockProductId, category);
        return inventoryClient.findAlternatives(outOfStockProductId, category, skillLevel);
    }

    /**
     * Routing Workflow: 在庫状態に応じた付加情報を生成。
     */
    static InventoryStatus enrichWithRouting(InventoryStatus rawStatus, int required) {
        String status;
        int stock = rawStatus.stockQuantity();
        if (stock == 0 || stock < required) {
            status = "OUT_OF_STOCK";
        } else if (stock < LOW_STOCK_THRESHOLD) {
            status = "LOW_STOCK";
        } else {
            status = "AVAILABLE";
        }

        boolean isReservable = !"OUT_OF_STOCK".equals(status);
        InventoryAlert alert = "LOW_STOCK".equals(status)
                ? new InventoryAlert(
                        UUID.randomUUID().toString(),
                        rawStatus.productId(),
                        "WARNING",
                        "在庫が残り %d 点です".formatted(stock),
                        stock, LOW_STOCK_THRESHOLD, Instant.now())
                : null;

        return new InventoryStatus(
                rawStatus.productId(), rawStatus.productName(),
                stock, status, isReservable,
                rawStatus.estimatedRestockDate(), rawStatus.alternativeProductIds(), alert);
    }
}
