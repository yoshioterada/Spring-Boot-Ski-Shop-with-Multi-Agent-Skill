package com.example.skishop.agent.orchestrator.invoker;

import com.example.skishop.agent.common.dto.BulkPricingRequest;
import com.example.skishop.agent.common.dto.CouponOptimizationRequest;
import com.example.skishop.agent.common.dto.CouponOptimizationResult;
import com.example.skishop.agent.common.dto.CustomerIntentRequest;
import com.example.skishop.agent.common.dto.CustomerIntentResult;
import com.example.skishop.agent.common.dto.EquipmentMatchRequest;
import com.example.skishop.agent.common.dto.EquipmentMatchResult;
import com.example.skishop.agent.common.dto.InventoryCheckRequest;
import com.example.skishop.agent.common.dto.InventoryStatus;
import com.example.skishop.agent.common.dto.PricingResult;
import com.example.skishop.agent.common.dto.ReservationRequest;
import com.example.skishop.agent.common.dto.ReservationResult;
import com.example.skishop.agent.common.dto.WeatherAgentRequest;
import com.example.skishop.agent.common.dto.WeatherAgentResponse;
import com.example.skishop.agent.coupon.service.CouponOptimizationAgentService;
import com.example.skishop.agent.equipment.service.EquipmentMatchingAgentService;
import com.example.skishop.agent.intent.service.CustomerIntentAgentService;
import com.example.skishop.agent.inventory.service.InventoryMonitoringAgentService;
import com.example.skishop.agent.pricing.service.DynamicPricingAgentService;
import com.example.skishop.agent.weather.service.WeatherAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * モノリスモード（デフォルト）：同一 JVM の Worker Agent Service Bean を直接呼び出す。
 */
@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "monolith", matchIfMissing = true)
public class LocalWorkerAgentInvoker implements WorkerAgentInvoker {

    private static final Logger log = LoggerFactory.getLogger(LocalWorkerAgentInvoker.class);

    private final CustomerIntentAgentService customerIntent;
    private final WeatherAgentService weather;
    private final EquipmentMatchingAgentService equipment;
    private final InventoryMonitoringAgentService inventory;
    private final DynamicPricingAgentService pricing;
    private final CouponOptimizationAgentService coupon;

    public LocalWorkerAgentInvoker(CustomerIntentAgentService customerIntent,
                                    WeatherAgentService weather,
                                    EquipmentMatchingAgentService equipment,
                                    InventoryMonitoringAgentService inventory,
                                    DynamicPricingAgentService pricing,
                                    CouponOptimizationAgentService coupon) {
        this.customerIntent = customerIntent;
        this.weather = weather;
        this.equipment = equipment;
        this.inventory = inventory;
        this.pricing = pricing;
        this.coupon = coupon;
    }

    @Override
    public CustomerIntentResult invokeCustomerIntent(String userId, String userMessage, String sessionId) {
        log.debug("Local invoke CustomerIntent: userId={}", userId);
        return customerIntent.analyze(new CustomerIntentRequest(userId, userMessage, sessionId, "ja"));
    }

    @Override
    public WeatherAgentResponse invokeWeather(String location, String resort, Integer forecastDays) {
        log.debug("Local invoke Weather: location={}", location);
        return weather.analyze(new WeatherAgentRequest(location, resort, "celsius", forecastDays));
    }

    @Override
    public EquipmentMatchResult invokeEquipmentMatching(EquipmentMatchRequest request) {
        log.debug("Local invoke EquipmentMatching: userId={}", request.userId());
        return equipment.match(request);
    }

    @Override
    public List<InventoryStatus> invokeInventoryCheck(List<String> productIds, int requiredQuantity) {
        log.debug("Local invoke InventoryCheck: ids={}", productIds.size());
        return inventory.checkAndRoute(new InventoryCheckRequest(productIds, requiredQuantity));
    }

    @Override
    public ReservationResult invokeInventoryReservation(ReservationRequest request) {
        log.debug("Local invoke InventoryReservation: orderId={}", request.orderId());
        return inventory.reserve(request);
    }

    @Override
    public List<PricingResult> invokeDynamicPricing(BulkPricingRequest request) {
        log.debug("Local invoke DynamicPricing: userId={}", request.userId());
        return pricing.calculateBulkPrices(request);
    }

    @Override
    public CouponOptimizationResult invokeCouponOptimization(CouponOptimizationRequest request) {
        log.debug("Local invoke CouponOptimization: orderId={}", request.orderId());
        return coupon.optimize(request);
    }
}
