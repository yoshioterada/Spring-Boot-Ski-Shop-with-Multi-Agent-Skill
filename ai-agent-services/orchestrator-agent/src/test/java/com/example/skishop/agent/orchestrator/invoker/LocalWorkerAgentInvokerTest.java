package com.example.skishop.agent.orchestrator.invoker;

import com.example.skishop.agent.common.dto.BulkPricingRequest;
import com.example.skishop.agent.common.dto.CouponOptimizationRequest;
import com.example.skishop.agent.common.dto.CouponOptimizationResult;
import com.example.skishop.agent.common.dto.CustomerIntentResult;
import com.example.skishop.agent.common.dto.EquipmentMatchRequest;
import com.example.skishop.agent.common.dto.EquipmentMatchResult;
import com.example.skishop.agent.common.dto.InventoryCheckRequest;
import com.example.skishop.agent.common.dto.InventoryStatus;
import com.example.skishop.agent.common.dto.ReservationRequest;
import com.example.skishop.agent.common.dto.ReservationResult;
import com.example.skishop.agent.coupon.service.CouponOptimizationAgentService;
import com.example.skishop.agent.equipment.service.EquipmentMatchingAgentService;
import com.example.skishop.agent.intent.service.CustomerIntentAgentService;
import com.example.skishop.agent.inventory.service.InventoryMonitoringAgentService;
import com.example.skishop.agent.pricing.service.DynamicPricingAgentService;
import com.example.skishop.agent.weather.service.WeatherAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalWorkerAgentInvokerTest {

    private CustomerIntentAgentService intent;
    private WeatherAgentService weather;
    private EquipmentMatchingAgentService equipment;
    private InventoryMonitoringAgentService inventory;
    private DynamicPricingAgentService pricing;
    private CouponOptimizationAgentService coupon;
    private LocalWorkerAgentInvoker invoker;

    @BeforeEach
    void setUp() {
        intent = mock(CustomerIntentAgentService.class);
        weather = mock(WeatherAgentService.class);
        equipment = mock(EquipmentMatchingAgentService.class);
        inventory = mock(InventoryMonitoringAgentService.class);
        pricing = mock(DynamicPricingAgentService.class);
        coupon = mock(CouponOptimizationAgentService.class);
        invoker = new LocalWorkerAgentInvoker(intent, weather, equipment, inventory, pricing, coupon);
    }

    @Test
    void invokeCustomerIntent_delegates_to_service() {
        when(intent.analyze(any())).thenReturn(mock(CustomerIntentResult.class));
        invoker.invokeCustomerIntent("u1", "msg", "s1");
        verify(intent).analyze(any());
    }

    @Test
    void invokeWeather_delegates() {
        invoker.invokeWeather("Naeba", null, 7);
        verify(weather).analyze(any());
    }

    @Test
    void invokeEquipmentMatching_delegates() {
        var req = new EquipmentMatchRequest("u1", "BEGINNER", null,
                List.of("ウェア"), 50000, "Naeba", false, true, 1);
        when(equipment.match(req)).thenReturn(
                new EquipmentMatchResult("u1", List.of(), "", 0.0, true, Instant.now()));
        invoker.invokeEquipmentMatching(req);
        verify(equipment).match(req);
    }

    @Test
    void invokeInventoryCheck_delegates() {
        when(inventory.checkAndRoute(any(InventoryCheckRequest.class))).thenReturn(List.<InventoryStatus>of());
        invoker.invokeInventoryCheck(List.of("p1"), 1);
        verify(inventory).checkAndRoute(any());
    }

    @Test
    void invokeInventoryReservation_delegates() {
        var req = new ReservationRequest("o1", "u1",
                List.of(new ReservationRequest.ReservationItem("p1", 1)), 30);
        when(inventory.reserve(req)).thenReturn(
                new ReservationResult("r1", "o1", true, List.of("p1"), List.of(), Instant.now()));
        invoker.invokeInventoryReservation(req);
        verify(inventory).reserve(req);
    }

    @Test
    void invokeDynamicPricing_delegates() {
        var req = new BulkPricingRequest("u1", "GOLD",
                List.of(new BulkPricingRequest.BulkPricingItem("p1", 1)), null);
        invoker.invokeDynamicPricing(req);
        verify(pricing).calculateBulkPrices(req);
    }

    @Test
    void invokeCouponOptimization_delegates() {
        var req = new CouponOptimizationRequest("u1", "o1", List.of(), "GOLD", true, null);
        when(coupon.optimize(req)).thenReturn(mock(CouponOptimizationResult.class));
        invoker.invokeCouponOptimization(req);
        verify(coupon).optimize(req);
    }

    @Test
    void empty_constructor_succeeds_when_dependencies_provided() {
        assertThat(invoker).isNotNull();
    }
}
