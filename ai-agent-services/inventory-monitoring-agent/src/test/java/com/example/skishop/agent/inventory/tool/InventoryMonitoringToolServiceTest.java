package com.example.skishop.agent.inventory.tool;

import com.example.skishop.agent.common.dto.InventoryAlert;
import com.example.skishop.agent.common.dto.InventoryStatus;
import com.example.skishop.agent.common.dto.ReservationRequest;
import com.example.skishop.agent.common.dto.ReservationResult;
import com.example.skishop.agent.inventory.client.InventoryManagementClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryMonitoringToolServiceTest {

    private InventoryManagementClient client;
    private InventoryMonitoringToolService tool;

    @BeforeEach
    void setUp() {
        client = mock(InventoryManagementClient.class);
        tool = new InventoryMonitoringToolService(client);
    }

    private InventoryStatus stock(String id, int qty) {
        return new InventoryStatus(id, "P-" + id, qty, "AVAILABLE", true, null, List.of(), null);
    }

    @Test
    void check_routes_to_AVAILABLE_when_stock_above_threshold() {
        when(client.getStock("p1")).thenReturn(stock("p1", 10));
        var result = tool.checkInventoryAvailability(List.of("p1"), 1);
        assertThat(result.get(0).availabilityStatus()).isEqualTo("AVAILABLE");
        assertThat(result.get(0).isReservable()).isTrue();
        assertThat(result.get(0).alert()).isNull();
    }

    @Test
    void check_routes_to_LOW_STOCK_when_under_threshold() {
        when(client.getStock("p2")).thenReturn(stock("p2", 3));
        var result = tool.checkInventoryAvailability(List.of("p2"), 1);
        assertThat(result.get(0).availabilityStatus()).isEqualTo("LOW_STOCK");
        assertThat(result.get(0).isReservable()).isTrue();
        assertThat(result.get(0).alert()).isNotNull();
        assertThat(result.get(0).alert().severity()).isEqualTo("WARNING");
    }

    @Test
    void check_routes_to_OUT_OF_STOCK_when_zero() {
        when(client.getStock("p3")).thenReturn(stock("p3", 0));
        var result = tool.checkInventoryAvailability(List.of("p3"), 1);
        assertThat(result.get(0).availabilityStatus()).isEqualTo("OUT_OF_STOCK");
        assertThat(result.get(0).isReservable()).isFalse();
        assertThat(result.get(0).alert()).isNull();
    }

    @Test
    void check_routes_to_OUT_OF_STOCK_when_required_exceeds_stock() {
        when(client.getStock("p4")).thenReturn(stock("p4", 2));
        var result = tool.checkInventoryAvailability(List.of("p4"), 5);
        assertThat(result.get(0).availabilityStatus()).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    void check_uses_default_qty_for_invalid_quantity() {
        when(client.getStock("p5")).thenReturn(stock("p5", 10));
        var result = tool.checkInventoryAvailability(List.of("p5"), 0);
        assertThat(result.get(0).availabilityStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void check_handles_null_or_empty_productIds() {
        assertThat(tool.checkInventoryAvailability(null, 1)).isEmpty();
        assertThat(tool.checkInventoryAvailability(List.of(), 1)).isEmpty();
    }

    @Test
    void reserve_delegates_with_default_ttl() {
        when(client.reserve(any())).thenReturn(new ReservationResult("r1", "o1", true,
                List.of("p1"), List.of(), Instant.now()));
        var items = List.of(new ReservationRequest.ReservationItem("p1", 1));
        var result = tool.reserveInventory("o1", "u1", items, 0);
        assertThat(result.reservationId()).isEqualTo("r1");
        verify(client).reserve(any());
    }

    @Test
    void reserve_returns_failure_for_empty_items() {
        var result = tool.reserveInventory("o1", "u1", List.of(), 30);
        assertThat(result.isFullyReserved()).isFalse();
        assertThat(result.failedProductIds()).isEmpty();
    }

    @Test
    void reserve_handles_null_items() {
        var result = tool.reserveInventory("o1", "u1", null, 30);
        assertThat(result.isFullyReserved()).isFalse();
    }

    @Test
    void getLowStockAlerts_delegates() {
        var alerts = List.of(new InventoryAlert("a1", "p1", "WARNING", "low", 2, 5, Instant.now()));
        when(client.getLowStockItems(5)).thenReturn(alerts);
        assertThat(tool.getLowStockAlerts()).hasSize(1);
    }

    @Test
    void getAlternativeProducts_delegates() {
        when(client.findAlternatives("p1", "ski", "BEGINNER")).thenReturn(List.of("p2", "p3"));
        assertThat(tool.getAlternativeProducts("p1", "ski", "BEGINNER")).containsExactly("p2", "p3");
    }
}
