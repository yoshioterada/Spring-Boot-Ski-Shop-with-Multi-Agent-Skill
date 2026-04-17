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

import java.util.List;

/**
 * Orchestrator が Worker を呼び出すためのモード非依存インターフェース。
 * モノリス時 = LocalWorkerAgentInvoker、分散時 = RemoteWorkerAgentInvoker。
 */
public interface WorkerAgentInvoker {

    CustomerIntentResult invokeCustomerIntent(String userId, String userMessage, String sessionId);

    WeatherAgentResponse invokeWeather(String location, String resort, Integer forecastDays);

    EquipmentMatchResult invokeEquipmentMatching(EquipmentMatchRequest request);

    List<InventoryStatus> invokeInventoryCheck(List<String> productIds, int requiredQuantity);

    ReservationResult invokeInventoryReservation(ReservationRequest request);

    List<PricingResult> invokeDynamicPricing(BulkPricingRequest request);

    CouponOptimizationResult invokeCouponOptimization(CouponOptimizationRequest request);
}
