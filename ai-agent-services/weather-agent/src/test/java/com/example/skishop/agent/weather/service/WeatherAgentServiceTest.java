package com.example.skishop.agent.weather.service;

import com.example.skishop.agent.common.dto.CurrentWeatherData;
import com.example.skishop.agent.common.dto.SkiConditionsData;
import com.example.skishop.agent.common.dto.SkiFeasibilityResult;
import com.example.skishop.agent.common.dto.WeatherAgentRequest;
import com.example.skishop.agent.common.dto.WeatherAgentResponse;
import com.example.skishop.agent.common.dto.WeatherForecastData;
import com.example.skishop.agent.weather.tool.WeatherToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeatherAgentServiceTest {

    private ChatClient chatClient;
    private WeatherToolService toolService;
    private WeatherAgentService service;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        toolService = mock(WeatherToolService.class);
        service = new WeatherAgentService(chatClient, toolService);

        when(chatClient.prompt().system(anyString()).user(anyString()).tools(any(Object.class)).call().content())
                .thenReturn("AIサマリー");

        when(toolService.getCurrentWeather(anyString(), any()))
                .thenReturn(new CurrentWeatherData(-3, -6, 70, 15, "N", 8, "75", "雪", true, 5));
        when(toolService.getWeatherForecast(anyString(), any()))
                .thenReturn(new WeatherForecastData(List.of()));
        when(toolService.getSkiConditions(anyString()))
                .thenReturn(new SkiConditionsData(60, 5, 20, "POWDER", true, "GOOD", "GROOMED", 8, "LOW"));
        when(toolService.getWeatherAlerts(anyString())).thenReturn(List.of());
        when(toolService.assessSkiFeasibility(anyString(), any()))
                .thenReturn(new SkiFeasibilityResult("HIGH", 70, "COLD", "EXCELLENT", "EXCELLENT"));
    }

    @Test
    void analyze_with_explicit_question_uses_question_branch() {
        var req = new WeatherAgentRequest("Naeba", "Naeba Resort",
                "明日のスキー条件は？", "celsius", 7);
        var resp = service.analyze(req);
        assertThat(resp).isNotNull();
        assertThat(resp.location()).isEqualTo("Naeba");
        assertThat(resp.aiSummary()).isEqualTo("AIサマリー");
        assertThat(resp.skiFeasibilityAssessment()).isEqualTo("HIGH");
    }

    @Test
    void analyze_without_question_uses_default_prompt_branch() {
        var req = new WeatherAgentRequest("Hakuba", "Hakuba Resort",
                null, "celsius", 5);
        var resp = service.analyze(req);
        assertThat(resp.aiSummary()).isEqualTo("AIサマリー");
    }

    @Test
    void analyze_blank_question_falls_through_to_default_prompt() {
        var req = new WeatherAgentRequest("Niseko", "Niseko Resort",
                "   ", "celsius", 3);
        var resp = service.analyze(req);
        assertThat(resp.aiSummary()).isEqualTo("AIサマリー");
    }

    @Test
    void quickAnalyze_returns_chat_content() {
        String s = service.quickAnalyze("Hakuba");
        assertThat(s).isEqualTo("AIサマリー");
    }
}
