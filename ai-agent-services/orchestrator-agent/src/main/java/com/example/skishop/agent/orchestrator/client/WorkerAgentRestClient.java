package com.example.skishop.agent.orchestrator.client;

import com.example.skishop.agent.common.dto.BulkPricingRequest;
import com.example.skishop.agent.common.dto.CouponOptimizationRequest;
import com.example.skishop.agent.common.dto.CouponOptimizationResult;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 分散モード時のみ Bean 登録される。全 Worker Agent への REST 呼び出しを集約する。
 */
@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "distributed")
public class WorkerAgentRestClient {

    private static final Logger log = LoggerFactory.getLogger(WorkerAgentRestClient.class);
    private static final ParameterizedTypeReference<List<InventoryStatus>> INVENTORY_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<PricingResult>> PRICING_LIST =
            new ParameterizedTypeReference<>() {};

    private final Map<String, RestClient> clients;

    public WorkerAgentRestClient(
            @Value("${services.customer-intent-agent.base-url:http://localhost:8101}") String intentUrl,
            @Value("${services.weather-agent.base-url:http://localhost:8100}") String weatherUrl,
            @Value("${services.equipment-matching-agent.base-url:http://localhost:8102}") String equipmentUrl,
            @Value("${services.inventory-monitoring-agent.base-url:http://localhost:8103}") String inventoryUrl,
            @Value("${services.dynamic-pricing-agent.base-url:http://localhost:8104}") String pricingUrl,
            @Value("${services.coupon-optimization-agent.base-url:http://localhost:8105}") String couponUrl,
            @Value("${services.internal-api-key:}") String apiKey) {
        this.clients = Map.of(
                "intent",    build(intentUrl, apiKey),
                "weather",   build(weatherUrl, apiKey),
                "equipment", build(equipmentUrl, apiKey),
                "inventory", build(inventoryUrl, apiKey),
                "pricing",   build(pricingUrl, apiKey),
                "coupon",    build(couponUrl, apiKey));
    }

    public CustomerIntentResult callCustomerIntent(String userId, String userMessage, String sessionId) {
        log.debug("REST CustomerIntent: userId={}", userId);
        var body = Map.of("userId", userId, "userMessage", userMessage,
                "sessionId", sessionId == null ? "" : sessionId, "locale", "ja");
        return clients.get("intent").post()
                .uri("/api/v1/agents/intent/analyze").body(body)
                .retrieve().body(CustomerIntentResult.class);
    }

    public WeatherAgentResponse callWeather(String location, String resort, Integer forecastDays) {
        log.debug("REST Weather: location={}", location);
        var body = new WeatherAgentRequest(location, resort, "celsius", forecastDays);
        return clients.get("weather").post()
                .uri("/api/v1/agents/weather/analyze").body(body)
                .retrieve().body(WeatherAgentResponse.class);
    }

    public EquipmentMatchResult callEquipmentMatching(EquipmentMatchRequest request) {
        log.debug("REST EquipmentMatching: userId={}", request.userId());
        return clients.get("equipment").post()
                .uri("/api/v1/agents/equipment/match").body(request)
                .retrieve().body(EquipmentMatchResult.class);
    }

    public List<InventoryStatus> callInventoryCheck(List<String> productIds, int requiredQuantity) {
        log.debug("REST InventoryCheck: ids={}", productIds.size());
        var body = new InventoryCheckRequest(productIds, requiredQuantity);
        return clients.get("inventory").post()
                .uri("/api/v1/agents/inventory/check").body(body)
                .retrieve().body(INVENTORY_LIST);
    }

    public ReservationResult callInventoryReserve(ReservationRequest request) {
        log.debug("REST InventoryReserve: orderId={}", request.orderId());
        return clients.get("inventory").post()
                .uri("/api/v1/agents/inventory/reserve").body(request)
                .retrieve().body(ReservationResult.class);
    }

    public List<PricingResult> callDynamicPricing(BulkPricingRequest request) {
        log.debug("REST DynamicPricing: userId={}", request.userId());
        return clients.get("pricing").post()
                .uri("/api/v1/agents/pricing/bulk").body(request)
                .retrieve().body(PRICING_LIST);
    }

    public CouponOptimizationResult callCouponOptimization(CouponOptimizationRequest request) {
        log.debug("REST CouponOptimization: orderId={}", request.orderId());
        return clients.get("coupon").post()
                .uri("/api/v1/agents/coupon/optimize").body(request)
                .retrieve().body(CouponOptimizationResult.class);
    }

    private static RestClient build(String baseUrl, String apiKey) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", apiKey == null ? "" : apiKey)
                .defaultHeader("X-Caller-Service", "orchestrator-agent")
                .build();
    }
}
