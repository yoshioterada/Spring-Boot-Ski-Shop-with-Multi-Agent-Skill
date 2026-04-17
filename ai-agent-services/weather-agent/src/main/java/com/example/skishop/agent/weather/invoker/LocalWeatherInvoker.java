package com.example.skishop.agent.weather.invoker;

import com.example.skishop.agent.common.dto.SkiFeasibilityResult;
import com.example.skishop.agent.common.invoker.WeatherInvoker;
import com.example.skishop.agent.weather.service.WeatherAgentService;
import com.example.skishop.agent.weather.tool.WeatherToolService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Worker→Worker 抽象 {@link WeatherInvoker} の <strong>モノリス時実装</strong>。
 * Equipment Matching / Dynamic Pricing から WeatherInvoker 型で注入され、
 * 同 JVM 内の WeatherToolService を直接呼び出す（REST 経由を回避）。
 */
@Component
@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "monolith", matchIfMissing = true)
@ConditionalOnBean(WeatherAgentService.class)
public class LocalWeatherInvoker implements WeatherInvoker {

    private final WeatherToolService toolService;

    public LocalWeatherInvoker(WeatherToolService toolService) {
        this.toolService = toolService;
    }

    @Override
    public SkiFeasibilityResult getFeasibility(String location) {
        return toolService.assessSkiFeasibility(location, null);
    }
}
