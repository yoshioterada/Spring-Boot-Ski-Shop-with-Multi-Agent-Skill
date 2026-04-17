package com.example.skishop.agent.orchestrator.invoker;

import com.example.skishop.agent.common.dto.BulkPricingRequest;
import com.example.skishop.agent.common.dto.CouponOptimizationRequest;
import com.example.skishop.agent.common.dto.EquipmentMatchRequest;
import com.example.skishop.agent.common.dto.ReservationRequest;
import com.example.skishop.agent.orchestrator.client.WorkerAgentRestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RemoteWorkerAgentInvokerTest {

    private WorkerAgentRestClient client;
    private RemoteWorkerAgentInvoker invoker;

    @BeforeEach
    void setUp() {
        client = mock(WorkerAgentRestClient.class);
        invoker = new RemoteWorkerAgentInvoker(client);
    }

    @Test
    void all_methods_delegate_to_rest_client() {
        invoker.invokeCustomerIntent("u1", "m", "s");
        verify(client).callCustomerIntent("u1", "m", "s");

        invoker.invokeWeather("Naeba", null, 7);
        verify(client).callWeather("Naeba", null, 7);

        var er = new EquipmentMatchRequest("u1", "BEGINNER", null, List.of("ウェア"),
                null, null, false, true, 1);
        invoker.invokeEquipmentMatching(er);
        verify(client).callEquipmentMatching(er);

        invoker.invokeInventoryCheck(List.of("p1"), 1);
        verify(client).callInventoryCheck(anyList(), eq(1));

        var rr = new ReservationRequest("o1", "u1",
                List.of(new ReservationRequest.ReservationItem("p1", 1)), 30);
        invoker.invokeInventoryReservation(rr);
        verify(client).callInventoryReserve(rr);

        var br = new BulkPricingRequest("u1", "GOLD",
                List.of(new BulkPricingRequest.BulkPricingItem("p1", 1)), null);
        invoker.invokeDynamicPricing(br);
        verify(client).callDynamicPricing(br);

        var cr = new CouponOptimizationRequest("u1", "o1", List.of(), "GOLD", false, null);
        invoker.invokeCouponOptimization(cr);
        verify(client).callCouponOptimization(cr);
    }
}
