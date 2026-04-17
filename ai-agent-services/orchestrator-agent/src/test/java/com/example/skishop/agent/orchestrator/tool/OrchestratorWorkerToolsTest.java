package com.example.skishop.agent.orchestrator.tool;

import com.example.skishop.agent.common.dto.BulkPricingRequest;
import com.example.skishop.agent.common.dto.CartItemPricing;
import com.example.skishop.agent.common.dto.CouponCandidate;
import com.example.skishop.agent.common.dto.CouponOptimizationRequest;
import com.example.skishop.agent.common.dto.CouponOptimizationResult;
import com.example.skishop.agent.common.dto.CustomerIntentResult;
import com.example.skishop.agent.common.dto.EquipmentMatchRequest;
import com.example.skishop.agent.common.dto.EquipmentMatchResult;
import com.example.skishop.agent.common.dto.ExtractedConstraints;
import com.example.skishop.agent.common.dto.IntentCategory;
import com.example.skishop.agent.common.dto.InventoryStatus;
import com.example.skishop.agent.common.dto.PricingResult;
import com.example.skishop.agent.common.dto.ReservationRequest;
import com.example.skishop.agent.common.dto.ReservationResult;
import com.example.skishop.agent.common.dto.WeatherAgentResponse;
import com.example.skishop.agent.orchestrator.client.PaymentCartClient;
import com.example.skishop.agent.orchestrator.invoker.WorkerAgentInvoker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrchestratorWorkerToolsTest {

    private WorkerAgentInvoker invoker;
    private PaymentCartClient paymentCart;
    private ObjectMapper objectMapper;
    private OrchestratorWorkerTools tools;

    @BeforeEach
    void setUp() {
        invoker = mock(WorkerAgentInvoker.class);
        paymentCart = mock(PaymentCartClient.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        tools = new OrchestratorWorkerTools(invoker, paymentCart, objectMapper);
    }

    @Test
    void analyzeCustomerIntent_delegates_and_returns_json() {
        var constraints = new ExtractedConstraints("Naeba", null, null, null,
                "BEGINNER", 50000, false, true, List.of("スキー板"));
        var result = new CustomerIntentResult(
                "u1", "s1", new IntentCategory.Purchase("スキー板"),
                constraints, null, "summary", 0.9, Instant.now());
        when(invoker.invokeCustomerIntent(eq("u1"), eq("hello"), eq("s1"))).thenReturn(result);

        String json = tools.analyzeCustomerIntent("u1", "hello", "s1");
        assertThat(json).contains("Naeba");
        verify(invoker).invokeCustomerIntent("u1", "hello", "s1");
    }

    @Test
    void getWeatherAndSkiConditions_delegates() {
        var resp = new WeatherAgentResponse("Naeba", null, null, null, List.of(),
                "summary", "great", Instant.now());
        when(invoker.invokeWeather("Naeba", null, 7)).thenReturn(resp);
        String json = tools.getWeatherAndSkiConditions("Naeba", null, 7);
        assertThat(json).contains("Naeba");
    }

    @Test
    void matchEquipment_passes_correct_9arg_request() {
        var ematch = new EquipmentMatchResult("u1", List.of(), "summary", 45000.0, true, Instant.now());
        when(invoker.invokeEquipmentMatching(any(EquipmentMatchRequest.class))).thenReturn(ematch);

        tools.matchEquipment("u1", "BEGINNER", "スキー板,ウェア", 50000, "Naeba", 1);

        ArgumentCaptor<EquipmentMatchRequest> cap = ArgumentCaptor.forClass(EquipmentMatchRequest.class);
        verify(invoker).invokeEquipmentMatching(cap.capture());
        EquipmentMatchRequest req = cap.getValue();
        assertThat(req.userId()).isEqualTo("u1");
        assertThat(req.skillLevel()).isEqualTo("BEGINNER");
        assertThat(req.bodyMeasurements()).isNull();
        assertThat(req.desiredCategories()).containsExactly("スキー板", "ウェア");
        assertThat(req.budgetYen()).isEqualTo(50000);
        assertThat(req.destination()).isEqualTo("Naeba");
        assertThat(req.includeRental()).isFalse();
        assertThat(req.includePurchase()).isTrue();
        assertThat(req.quantity()).isEqualTo(1);
    }

    @Test
    void matchEquipment_quantity_zero_defaults_to_1() {
        when(invoker.invokeEquipmentMatching(any())).thenReturn(
                new EquipmentMatchResult("u1", List.of(), "", 0.0, true, Instant.now()));
        tools.matchEquipment("u1", "BEGINNER", "ウェア", null, null, 0);
        ArgumentCaptor<EquipmentMatchRequest> cap = ArgumentCaptor.forClass(EquipmentMatchRequest.class);
        verify(invoker).invokeEquipmentMatching(cap.capture());
        assertThat(cap.getValue().quantity()).isEqualTo(1);
    }

    @Test
    void checkInventoryAvailability_parses_ids_and_default_qty() {
        when(invoker.invokeInventoryCheck(anyList(), anyInt())).thenReturn(List.of());
        tools.checkInventoryAvailability("p1, p2 ,p3", 0);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        verify(invoker).invokeInventoryCheck(cap.capture(), eq(1));
        assertThat(cap.getValue()).containsExactly("p1", "p2", "p3");
    }

    @Test
    void reserveInventory_parses_quantities_and_creates_request() {
        var rr = new ReservationResult("res-1", "o1", true, List.of("p1"), List.of(), Instant.now());
        when(invoker.invokeInventoryReservation(any())).thenReturn(rr);

        tools.reserveInventory("o1", "u1", "p1:2,p2:1");

        ArgumentCaptor<ReservationRequest> cap = ArgumentCaptor.forClass(ReservationRequest.class);
        verify(invoker).invokeInventoryReservation(cap.capture());
        ReservationRequest req = cap.getValue();
        assertThat(req.orderId()).isEqualTo("o1");
        assertThat(req.userId()).isEqualTo("u1");
        assertThat(req.items()).hasSize(2);
        assertThat(req.reservationTtlMinutes()).isEqualTo(30);
    }

    @Test
    void calculateDynamicPrices_parses_and_invokes() {
        when(invoker.invokeDynamicPricing(any())).thenReturn(List.of());
        tools.calculateDynamicPrices("u1", "GOLD", "p1:1,p2:2", "Naeba");
        ArgumentCaptor<BulkPricingRequest> cap = ArgumentCaptor.forClass(BulkPricingRequest.class);
        verify(invoker).invokeDynamicPricing(cap.capture());
        BulkPricingRequest req = cap.getValue();
        assertThat(req.userId()).isEqualTo("u1");
        assertThat(req.customerTier()).isEqualTo("GOLD");
        assertThat(req.resortLocation()).isEqualTo("Naeba");
        assertThat(req.items()).hasSize(2);
    }

    @Test
    void optimizeCoupons_parses_cart_and_invokes() {
        var result = new CouponOptimizationResult("u1", "o1", List.<CouponCandidate>of(),
                0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(63000),
                BigDecimal.valueOf(60000), BigDecimal.valueOf(3000), "summary", Instant.now());
        when(invoker.invokeCouponOptimization(any())).thenReturn(result);

        String json = tools.optimizeCoupons("u1", "o1",
                "p1|スキー板A|スキー板|1|45000;p2|ウェアB|ウェア|1|18000",
                "GOLD", true, "WELCOME10");

        assertThat(json).contains("o1");
        ArgumentCaptor<CouponOptimizationRequest> cap = ArgumentCaptor.forClass(CouponOptimizationRequest.class);
        verify(invoker).invokeCouponOptimization(cap.capture());
        CouponOptimizationRequest req = cap.getValue();
        assertThat(req.userId()).isEqualTo("u1");
        assertThat(req.cartItems()).hasSize(2);
        assertThat(req.usePoints()).isTrue();
        assertThat(req.couponCode()).isEqualTo("WELCOME10");
    }

    @Test
    void buildCart_delegates_to_payment_cart_client() {
        when(paymentCart.buildCart(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(Map.of("orderId", "o1", "status", "COMPLETED"));

        String json = tools.buildCart("u1", "o1", "p1|板|1|45000",
                BigDecimal.valueOf(3000), BigDecimal.ZERO);
        assertThat(json).contains("o1").contains("COMPLETED");
        verify(paymentCart).buildCart("u1", "o1", "p1|板|1|45000",
                BigDecimal.valueOf(3000), BigDecimal.ZERO);
    }

    @Test
    void parseProductQuantities_handles_blank_default_qty_and_merging() {
        var map = OrchestratorWorkerTools.parseProductQuantities("p1:2, p2 ,p1:3,");
        assertThat(map).containsEntry("p1", 5).containsEntry("p2", 1);
    }

    @Test
    void parseProductQuantities_returns_empty_for_null_or_blank() {
        assertThat(OrchestratorWorkerTools.parseProductQuantities(null)).isEmpty();
        assertThat(OrchestratorWorkerTools.parseProductQuantities("")).isEmpty();
    }

    @Test
    void parseCartItemsEncoded_returns_empty_for_null() {
        assertThat(OrchestratorWorkerTools.parseCartItemsEncoded(null)).isEmpty();
        assertThat(OrchestratorWorkerTools.parseCartItemsEncoded("")).isEmpty();
    }

    @Test
    void parseCartItemsEncoded_throws_on_invalid_format() {
        assertThatThrownBy(() -> OrchestratorWorkerTools.parseCartItemsEncoded("p1|name|1|999"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseCartItemsEncoded_calculates_lineTotal() {
        List<CartItemPricing> items = OrchestratorWorkerTools.parseCartItemsEncoded(
                "p1|板|スキー|2|10000");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).lineTotal()).isEqualByComparingTo("20000");
    }

    @Test
    void toJson_throws_on_serialization_failure() {
        // Anonymous class with self-reference triggers Jackson failure
        var bad = new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() { throw new RuntimeException("boom"); }
        };
        assertThatThrownBy(() -> tools.toJson(bad))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invokeDynamicPricing_pricing_result_serialized() {
        var pr = new PricingResult("p1", "u1", BigDecimal.valueOf(40000),
                BigDecimal.valueOf(45000), 0.11, BigDecimal.valueOf(5000),
                null, "summary", Instant.now(), Instant.now().plusSeconds(60));
        when(invoker.invokeDynamicPricing(any())).thenReturn(List.of(pr));
        String json = tools.calculateDynamicPrices("u1", "GOLD", "p1:1", null);
        assertThat(json).contains("40000");
    }

    @Test
    void invokeInventoryCheck_returns_serialized_inventory_status() {
        var status = new InventoryStatus("p1", "板", 10, "AVAILABLE", true, null, List.of(), null);
        when(invoker.invokeInventoryCheck(anyList(), anyInt())).thenReturn(List.of(status));
        String json = tools.checkInventoryAvailability("p1", 1);
        assertThat(json).contains("AVAILABLE");
    }
}
