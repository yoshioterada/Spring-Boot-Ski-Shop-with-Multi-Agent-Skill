package com.example.skishop.agent.equipment.invoker;

import com.example.skishop.agent.common.dto.SkiFeasibilityResult;
import com.example.skishop.agent.common.invoker.WeatherInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Worker → Worker (REST). 分散モードでのみ Bean 登録。
 * モノリス時は weather-agent の LocalWeatherInvoker が選択される。
 */
@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "distributed")
public class RemoteWeatherInvoker implements WeatherInvoker {

    private static final Logger log = LoggerFactory.getLogger(RemoteWeatherInvoker.class);
    private final RestClient restClient;

    public RemoteWeatherInvoker(
            @Value("${services.weather-agent.base-url:http://localhost:8100}") String baseUrl,
            @Value("${services.internal-api-key:}") String apiKey) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", apiKey == null ? "" : apiKey)
                .defaultHeader("X-Caller-Service", "equipment-matching-agent")
                .build());
    }

    RemoteWeatherInvoker(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public SkiFeasibilityResult getFeasibility(String location) {
        try {
            return restClient.get()
                    .uri("/api/v1/agents/weather/feasibility?location={loc}", location)
                    .retrieve()
                    .body(SkiFeasibilityResult.class);
        } catch (RestClientException e) {
            log.warn("Weather Agent 呼び出し失敗 location={}: {}", location, e.getMessage());
            return new SkiFeasibilityResult("MEDIUM", 50, "MODERATE", "ALL_CONDITIONS", "GOOD");
        }
    }
}
