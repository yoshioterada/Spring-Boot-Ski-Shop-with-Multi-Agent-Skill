package com.example.skishop.agent.weather.config;

import com.example.skishop.agent.weather.client.OpenMeteoClient;
import com.example.skishop.agent.weather.controller.WeatherAgentController;
import com.example.skishop.agent.weather.invoker.LocalWeatherInvoker;
import com.example.skishop.agent.weather.service.WeatherAgentService;
import com.example.skishop.agent.weather.tool.WeatherToolService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;

/**
 * Weather Agent モジュールの AutoConfiguration。
 * standalone / monolith いずれからも自動取り込み可能。
 */
@AutoConfiguration
@EnableCaching
@Import({
        WeatherToolService.class,
        WeatherAgentService.class,
        OpenMeteoClient.class,
        WeatherAgentConfig.class,
        WeatherAgentController.class,
        WeatherAgentSecurityConfig.class,
        LocalWeatherInvoker.class
})
public class WeatherAgentAutoConfiguration {
}
