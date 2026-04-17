package com.example.skishop.agent.weather.tool;

import com.example.skishop.agent.common.dto.CurrentWeatherData;
import com.example.skishop.agent.common.dto.SkiConditionsData;
import com.example.skishop.agent.common.dto.SkiFeasibilityResult;
import com.example.skishop.agent.weather.client.OpenMeteoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeatherToolServiceTest {

    private OpenMeteoClient client;
    private WeatherToolService tool;

    @BeforeEach
    void setUp() {
        client = mock(OpenMeteoClient.class);
        tool = new WeatherToolService(client);
    }

    @Test
    void getCurrentWeather_default_unit_when_null() {
        var data = new CurrentWeatherData(0, 0, 0, 0, "N", 10, "0", "晴れ", false, 0);
        when(client.getCurrentWeather("Naeba", "celsius")).thenReturn(data);

        var result = tool.getCurrentWeather("Naeba", null);

        assertThat(result).isSameAs(data);
        verify(client).getCurrentWeather("Naeba", "celsius");
    }

    @Test
    void getCurrentWeather_explicit_unit() {
        when(client.getCurrentWeather(anyString(), anyString()))
                .thenReturn(new CurrentWeatherData(0, 0, 0, 0, "N", 0, "0", "", false, 0));
        tool.getCurrentWeather("Naeba", "fahrenheit");
        verify(client).getCurrentWeather("Naeba", "fahrenheit");
    }

    @Test
    void getWeatherForecast_clamps_invalid_days_to_7() {
        tool.getWeatherForecast("Naeba", null);
        tool.getWeatherForecast("Naeba", 0);
        tool.getWeatherForecast("Naeba", 50);
        verify(client, times(3)).getWeatherForecast("Naeba", 7);
    }

    @Test
    void getWeatherForecast_keeps_valid_days() {
        tool.getWeatherForecast("Naeba", 10);
        verify(client).getWeatherForecast("Naeba", 10);
    }

    @Test
    void getWeatherAlerts_delegates() {
        when(client.getWeatherAlerts("Naeba")).thenReturn(List.of());
        var result = tool.getWeatherAlerts("Naeba");
        assertThat(result).isEmpty();
    }

    @Test
    void evaluateFeasibility_high_score_excellent() {
        var current = new CurrentWeatherData(-10, -15, 50, 20, "N", 10, "75", "雪", true, 5);
        var conditions = new SkiConditionsData(80, 15, 40, "POWDER", true, "EXCELLENT", "GROOMED", 10, "LOW");

        SkiFeasibilityResult r = tool.evaluateFeasibility(current, conditions);

        assertThat(r.feasibility()).isEqualTo("HIGH");
        assertThat(r.score()).isGreaterThanOrEqualTo(60);
        assertThat(r.recommendedGearLevel()).isEqualTo("COLD");
        assertThat(r.overallCondition()).isEqualTo("EXCELLENT");
    }

    @Test
    void evaluateFeasibility_medium_score_good() {
        var current = new CurrentWeatherData(-2, -5, 60, 40, "N", 3, "61", "雨", false, 0);
        var conditions = new SkiConditionsData(25, 0, 5, "WET", true, "FAIR", "GROOMED", 3, "LOW");

        SkiFeasibilityResult r = tool.evaluateFeasibility(current, conditions);

        assertThat(r.feasibility()).isIn("MEDIUM", "HIGH");
        assertThat(r.recommendedGearLevel()).isEqualTo("MODERATE");
    }

    @Test
    void evaluateFeasibility_low_score_poor() {
        var current = new CurrentWeatherData(10, 10, 80, 80, "S", 1, "80", "雨", false, 0);
        var conditions = new SkiConditionsData(5, 0, 0, "ICY", false, "POOR", "UNGROOMED", 1, "LOW");

        SkiFeasibilityResult r = tool.evaluateFeasibility(current, conditions);

        assertThat(r.feasibility()).isEqualTo("LOW");
        assertThat(r.score()).isLessThan(35);
        assertThat(r.overallCondition()).isEqualTo("POOR");
        assertThat(r.recommendedGearLevel()).isEqualTo("MODERATE");
    }

    @Test
    void evaluateFeasibility_extreme_cold_gear() {
        var current = new CurrentWeatherData(-15, -20, 40, 20, "N", 8, "75", "雪", true, 5);
        var conditions = new SkiConditionsData(60, 12, 30, "POWDER", true, "GOOD", "GROOMED", 8, "MODERATE");

        SkiFeasibilityResult r = tool.evaluateFeasibility(current, conditions);

        assertThat(r.recommendedGearLevel()).isEqualTo("EXTREME_COLD");
    }
}
