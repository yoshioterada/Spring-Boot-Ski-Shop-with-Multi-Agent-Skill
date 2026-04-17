package com.example.skishop.agent.weather.controller;

import com.example.skishop.agent.common.dto.CurrentWeatherData;
import com.example.skishop.agent.common.dto.SkiConditionsData;
import com.example.skishop.agent.common.dto.SkiFeasibilityResult;
import com.example.skishop.agent.common.dto.WeatherAgentRequest;
import com.example.skishop.agent.common.dto.WeatherAgentResponse;
import com.example.skishop.agent.weather.service.WeatherAgentService;
import com.example.skishop.agent.weather.tool.WeatherToolService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents/weather")
@ConditionalOnProperty(name = "agents.web.enabled", havingValue = "true", matchIfMissing = true)
public class WeatherAgentController {

    private final WeatherAgentService weatherAgentService;
    private final WeatherToolService weatherToolService;

    public WeatherAgentController(WeatherAgentService weatherAgentService,
                                  WeatherToolService weatherToolService) {
        this.weatherAgentService = weatherAgentService;
        this.weatherToolService = weatherToolService;
    }

    @PostMapping("/analyze")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<WeatherAgentResponse> analyze(@Valid @RequestBody WeatherAgentRequest request) {
        return ResponseEntity.ok(weatherAgentService.analyze(request));
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT', 'USER')")
    public ResponseEntity<CurrentWeatherData> getCurrentWeather(
            @RequestParam String location,
            @RequestParam(defaultValue = "celsius") String unit) {
        return ResponseEntity.ok(weatherToolService.getCurrentWeather(location, unit));
    }

    @GetMapping("/ski-conditions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT', 'USER')")
    public ResponseEntity<SkiConditionsData> getSkiConditions(@RequestParam String resort) {
        return ResponseEntity.ok(weatherToolService.getSkiConditions(resort));
    }

    @GetMapping("/feasibility")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT', 'USER')")
    public ResponseEntity<SkiFeasibilityResult> getFeasibility(
            @RequestParam String location,
            @RequestParam(required = false) String date) {
        return ResponseEntity.ok(weatherToolService.assessSkiFeasibility(location, date));
    }
}
