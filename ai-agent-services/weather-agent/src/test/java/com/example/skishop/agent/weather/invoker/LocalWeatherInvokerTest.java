package com.example.skishop.agent.weather.invoker;

import com.example.skishop.agent.common.dto.SkiFeasibilityResult;
import com.example.skishop.agent.weather.tool.WeatherToolService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalWeatherInvokerTest {

    @Test
    void getFeasibility_delegates_to_tool_service() {
        WeatherToolService toolService = mock(WeatherToolService.class);
        var expected = new SkiFeasibilityResult("HIGH", 80, "COLD", "POWDER", "EXCELLENT");
        when(toolService.assessSkiFeasibility("Naeba", null)).thenReturn(expected);

        var invoker = new LocalWeatherInvoker(toolService);
        var actual = invoker.getFeasibility("Naeba");

        assertThat(actual).isSameAs(expected);
        verify(toolService).assessSkiFeasibility("Naeba", null);
    }

    @Test
    void getOverallCondition_default_method_returns_overall_from_feasibility() {
        WeatherToolService toolService = mock(WeatherToolService.class);
        when(toolService.assessSkiFeasibility("Naeba", null))
                .thenReturn(new SkiFeasibilityResult("MEDIUM", 50, "MODERATE", "GROOMED", "GOOD"));

        var invoker = new LocalWeatherInvoker(toolService);
        assertThat(invoker.getOverallCondition("Naeba")).isEqualTo("GOOD");
    }
}
