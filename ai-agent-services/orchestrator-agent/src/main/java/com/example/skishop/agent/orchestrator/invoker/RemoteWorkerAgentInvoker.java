package com.example.skishop.agent.orchestrator.invoker;

import com.example.skishop.agent.common.dto.BulkPricingRequest;
import com.example.skishop.agent.common.dto.CouponOptimizationRequest;
import com.example.skishop.agent.common.dto.CouponOptimizationResult;
import com.example.skishop.agent.common.dto.CustomerIntentResult;
import com.example.skishop.agent.common.dto.EquipmentMatchRequest;
import com.example.skishop.agent.common.dto.EquipmentMatchResult;
import com.example.skishop.agent.common.dto.InventoryStatus;
import com.example.skishop.agent.common.dto.PricingResult;
import com.example.skishop.agent.common.dto.ReservationRequest;
import com.example.skishop.agent.common.dto.ReservationResult;
import com.example.skishop.agent.common.dto.WeatherAgentResponse;
import com.example.skishop.agent.orchestrator.client.WorkerAgentRestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * 分散モード：Worker は別 JVM のため REST 経由で呼び出す。
 */
@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "distributed")
public class RemoteWorkerAgentInvoker implements WorkerAgentInvoker {

    private final WorkerAgentRestClient client;

    public RemoteWorkerAgentInvoker(WorkerAgentRestClient client) {
        this.client = client;
    }

    @Override
    public CustomerIntentResult invokeCustomerIntent(String userId, String userMessage, String sessionId) {
        return client.callCustomerIntent(userId, userMessage, sessionId);
    }

    @Override
    public WeatherAgentResponse invokeWeather(String location, String resort, Integer forecastDays) {
        return client.callWeather(location, resort, forecastDays);
    }

    @Override
    public EquipmentMatchResult invokeEquipmentMatching(EquipmentMatchRequest request) {
        return client.callEquipmentMatching(request);
    }

    @Override
    public List<InventoryStatus> invokeInventoryCheck(List<String> productIds, int requiredQuantity) {
        return client.callInventoryCheck(productIds, requiredQuantity);
    }

    @Override
    public ReservationResult invokeInventoryReservation(ReservationRequest request) {
        return client.callInventoryReserve(request);
    }

    @Override
    public List<PricingResult> invokeDynamicPricing(BulkPricingRequest request) {
        return client.callDynamicPricing(request);
    }

    @Override
    public CouponOptimizationResult invokeCouponOptimization(CouponOptimizationRequest request) {
        return client.callCouponOptimization(request);
    }
}
