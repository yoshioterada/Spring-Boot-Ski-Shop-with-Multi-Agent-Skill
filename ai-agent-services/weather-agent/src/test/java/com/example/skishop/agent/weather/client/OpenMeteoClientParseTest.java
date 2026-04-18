package com.example.skishop.agent.weather.client;

import com.example.skishop.agent.common.dto.CurrentWeatherData;
import com.example.skishop.agent.common.dto.SkiConditionsData;
import com.example.skishop.agent.common.dto.WeatherAlertData;
import com.example.skishop.agent.common.dto.WeatherForecastData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenMeteoClientParseTest {

    private final OpenMeteoClient client = new OpenMeteoClient();

    @Test
    void parseCurrentWeather_handles_full_payload() {
        Map<String, Object> response = Map.of("current", Map.of(
                "temperature_2m", -5.5,
                "apparent_temperature", -10.0,
                "relative_humidity_2m", 70,
                "wind_speed_10m", 25.0,
                "wind_direction_10m", 90,
                "visibility", 8000,
                "weather_code", 73,
                "snowfall", 3.0
        ));

        CurrentWeatherData d = client.parseCurrentWeather(response);

        assertThat(d.temperatureCelsius()).isEqualTo(-5.5);
        assertThat(d.feelsLikeCelsius()).isEqualTo(-10.0);
        assertThat(d.humidity()).isEqualTo(70.0);
        assertThat(d.windSpeedKph()).isEqualTo(25.0);
        assertThat(d.windDirection()).isEqualTo("E");
        assertThat(d.visibilityKm()).isEqualTo(8.0);
        assertThat(d.weatherCode()).isEqualTo("73");
        assertThat(d.weatherDescription()).isEqualTo("雪");
        assertThat(d.isSnowing()).isTrue();
        assertThat(d.snowfallMm()).isEqualTo(3.0);
    }

    @Test
    void parseCurrentWeather_returns_empty_when_response_is_null_or_missing_current() {
        assertThat(client.parseCurrentWeather(null).weatherDescription()).isEqualTo("データなし");
        assertThat(client.parseCurrentWeather(Map.of()).weatherDescription()).isEqualTo("データなし");
    }

    @Test
    void parseForecast_returns_daily_list() {
        Map<String, Object> response = Map.of("daily", Map.of(
                "time", List.of("2026-01-10", "2026-01-11"),
                "temperature_2m_max", List.of(2.0, 1.0),
                "temperature_2m_min", List.of(-5.0, -8.0),
                "precipitation_sum", List.of(1.0, 0.5),
                "snowfall_sum", List.of(10.0, 5.0),
                "wind_speed_10m_max", List.of(20.0, 15.0),
                "weather_code", List.of(75, 71),
                "uv_index_max", List.of(2.0, 1.5)
        ));

        WeatherForecastData d = client.parseForecast(response);

        assertThat(d.dailyForecasts()).hasSize(2);
        assertThat(d.dailyForecasts().get(0).maxTempCelsius()).isEqualTo(2.0);
        assertThat(d.dailyForecasts().get(0).snowfallCm()).isEqualTo(10.0);
        assertThat(d.dailyForecasts().get(1).weatherCode()).isEqualTo(71);
    }

    @Test
    void parseForecast_handles_null_or_missing_daily() {
        assertThat(client.parseForecast(null).dailyForecasts()).isEmpty();
        assertThat(client.parseForecast(Map.of()).dailyForecasts()).isEmpty();
    }

    @Test
    void parseSkiConditions_calculates_metrics_from_hourly() {
        // 24h ぶんの降雪 (snowfall) と最終 snow_depth (m)
        var snowfall24 = java.util.Collections.nCopies(24, (Number) 0.5); // 0.5cm × 24 = 12cm
        var depth = List.of((Number) 0.8); // 0.8m = 80cm
        Map<String, Object> response = Map.of("hourly", Map.of(
                "snow_depth", depth,
                "snowfall", snowfall24,
                "visibility", List.of((Number) 5000),
                "wind_speed_10m", List.of((Number) 10)
        ));

        SkiConditionsData d = client.parseSkiConditions(response);

        assertThat(d.snowDepthCm()).isEqualTo(80.0);
        assertThat(d.freshSnowLast24hCm()).isEqualTo(12.0);
        assertThat(d.visibilityKm()).isEqualTo(5.0);
        assertThat(d.snowQuality()).isEqualTo("PACKED_POWDER"); // 5 ≤ 12 < 15
        assertThat(d.isLiftsLikelyOpen()).isTrue();
    }

    @Test
    void parseSkiConditions_returns_empty_for_null() {
        var d = client.parseSkiConditions(null);
        assertThat(d.snowDepthCm()).isEqualTo(0.0);
        assertThat(d.overallCondition()).isEqualTo("POOR");
    }

    @Test
    void generateAlertsFromData_high_wind_warning() {
        var data = new CurrentWeatherData(0, 0, 0, 80, "N", 10, "0", "", false, 0);
        List<WeatherAlertData> alerts = client.generateAlertsFromData(data);
        assertThat(alerts).extracting(WeatherAlertData::alertType).contains("HIGH_WIND");
    }

    @Test
    void generateAlertsFromData_blizzard_warning() {
        // wind >= 40 + snowfall >= 10 → BLIZZARD（wind < 60 のため HIGH_WIND は出ない）
        var data = new CurrentWeatherData(-5, -10, 80, 50, "N", 1, "75", "雪", true, 15);
        List<WeatherAlertData> alerts = client.generateAlertsFromData(data);
        assertThat(alerts).extracting(WeatherAlertData::alertType).contains("BLIZZARD");
    }

    @Test
    void generateAlertsFromData_blizzard_and_high_wind_when_wind_over_60() {
        var data = new CurrentWeatherData(-5, -10, 80, 70, "N", 1, "75", "雪", true, 15);
        List<WeatherAlertData> alerts = client.generateAlertsFromData(data);
        assertThat(alerts).extracting(WeatherAlertData::alertType).contains("BLIZZARD", "HIGH_WIND");
    }

    @Test
    void generateAlertsFromData_freezing_rain_advisory() {
        var data = new CurrentWeatherData(2, 1, 80, 10, "N", 5, "61", "雨", false, 5);
        List<WeatherAlertData> alerts = client.generateAlertsFromData(data);
        assertThat(alerts).extracting(WeatherAlertData::alertType).contains("FREEZING_RAIN");
    }

    @Test
    void generateAlertsFromData_no_alerts_for_calm_weather() {
        var data = new CurrentWeatherData(-5, -8, 60, 10, "N", 10, "1", "晴れ", false, 0);
        assertThat(client.generateAlertsFromData(data)).isEmpty();
    }

    @Test
    void describeWeatherCode_known_codes() {
        assertThat(OpenMeteoClient.describeWeatherCode(0)).isEqualTo("快晴");
        assertThat(OpenMeteoClient.describeWeatherCode(75)).isEqualTo("雪");
        assertThat(OpenMeteoClient.describeWeatherCode(95)).isEqualTo("雷雨");
        assertThat(OpenMeteoClient.describeWeatherCode(999)).startsWith("不明");
    }

    // ----- parseSkiConditions: snow quality / overall / avalanche / liftsLikelyOpen 分岐網羅 -----

    @Test
    void parseSkiConditions_powder_excellent_high_avalanche() {
        // freshSnow24 = 20cm (POWDER), depth=100cm + fresh24>=5 (EXCELLENT), fresh72=60cm (HIGH avalanche)
        var fresh24Series = java.util.Collections.nCopies(24, (Number) 1.0); // 24cm in last 24h
        var fresh72Padding = java.util.Collections.nCopies(48, (Number) 0.75); // 36cm in earlier 48h → 60cm total 72h
        var snowfallSeries = new java.util.ArrayList<Number>();
        snowfallSeries.addAll(fresh72Padding);
        snowfallSeries.addAll(fresh24Series);
        Map<String, Object> response = Map.of("hourly", Map.of(
                "snow_depth", List.of((Number) 1.0), // 100cm
                "snowfall", snowfallSeries,
                "visibility", List.of((Number) 10000),
                "wind_speed_10m", List.of((Number) 10)
        ));

        SkiConditionsData d = client.parseSkiConditions(response);

        assertThat(d.snowDepthCm()).isEqualTo(100.0);
        assertThat(d.snowQuality()).isEqualTo("POWDER");
        assertThat(d.overallCondition()).isEqualTo("EXCELLENT");
        assertThat(d.avalancheRisk()).isEqualTo("HIGH");
        assertThat(d.isLiftsLikelyOpen()).isTrue();
    }

    @Test
    void parseSkiConditions_good_with_considerable_avalanche() {
        // depth = 50cm (GOOD), fresh72 = 35cm (CONSIDERABLE)
        var snowfallSeries = java.util.Collections.nCopies(72, (Number) 0.5); // 36cm
        Map<String, Object> response = Map.of("hourly", Map.of(
                "snow_depth", List.of((Number) 0.5),
                "snowfall", snowfallSeries,
                "visibility", List.of((Number) 5000)
        ));
        SkiConditionsData d = client.parseSkiConditions(response);
        assertThat(d.overallCondition()).isEqualTo("GOOD");
        assertThat(d.avalancheRisk()).isEqualTo("CONSIDERABLE");
    }

    @Test
    void parseSkiConditions_fair_with_moderate_avalanche() {
        // depth = 25cm (FAIR), fresh72 = 18cm (MODERATE)
        var snowfallSeries = java.util.Collections.nCopies(72, (Number) 0.25); // 18cm
        Map<String, Object> response = Map.of("hourly", Map.of(
                "snow_depth", List.of((Number) 0.25),
                "snowfall", snowfallSeries,
                "visibility", List.of((Number) 3000)
        ));
        SkiConditionsData d = client.parseSkiConditions(response);
        assertThat(d.overallCondition()).isEqualTo("FAIR");
        assertThat(d.avalancheRisk()).isEqualTo("MODERATE");
    }

    @Test
    void parseSkiConditions_poor_low_visibility_lifts_closed() {
        // depth=10cm POOR, visibility 0.5km → lifts closed
        Map<String, Object> response = Map.of("hourly", Map.of(
                "snow_depth", List.of((Number) 0.10),
                "snowfall", List.of(),
                "visibility", List.of((Number) 500)
        ));
        SkiConditionsData d = client.parseSkiConditions(response);
        assertThat(d.snowDepthCm()).isEqualTo(10.0);
        assertThat(d.overallCondition()).isEqualTo("POOR");
        assertThat(d.avalancheRisk()).isEqualTo("LOW");
        assertThat(d.isLiftsLikelyOpen()).isFalse();
        assertThat(d.snowQuality()).isEqualTo("ICY"); // fresh72=0
    }

    @Test
    void parseSkiConditions_wet_quality_when_some_old_snow() {
        // fresh24=2cm (<5), fresh72=5cm (<10), so fall through to WET
        var snowfallSeries = new java.util.ArrayList<Number>();
        snowfallSeries.addAll(java.util.Collections.nCopies(48, (Number) 0.0625)); // 3cm in earlier 48h
        snowfallSeries.addAll(java.util.Collections.nCopies(24, (Number) 0.083)); // ~2cm in last 24h
        Map<String, Object> response = Map.of("hourly", Map.of(
                "snow_depth", List.of((Number) 0.30),
                "snowfall", snowfallSeries,
                "visibility", List.of((Number) 6000)
        ));
        SkiConditionsData d = client.parseSkiConditions(response);
        assertThat(d.snowQuality()).isEqualTo("WET");
    }

    @Test
    void parseSkiConditions_packed_powder_via_fresh72() {
        // fresh24 < 5, fresh72 >= 10 → PACKED_POWDER
        var snowfallSeries = new java.util.ArrayList<Number>();
        snowfallSeries.addAll(java.util.Collections.nCopies(48, (Number) 0.25)); // 12cm in earlier 48h
        snowfallSeries.addAll(java.util.Collections.nCopies(24, (Number) 0.0)); // 0cm last 24h
        Map<String, Object> response = Map.of("hourly", Map.of(
                "snow_depth", List.of((Number) 0.50),
                "snowfall", snowfallSeries
        ));
        SkiConditionsData d = client.parseSkiConditions(response);
        assertThat(d.snowQuality()).isEqualTo("PACKED_POWDER");
    }

    @Test
    void parseSkiConditions_falls_back_to_daily_when_hourly_depth_zero() {
        // hourly.snow_depth ends at 0 → fallback to daily.snowfall_sum sum
        Map<String, Object> response = Map.of(
                "hourly", Map.of("snow_depth", List.of((Number) 0.0), "snowfall", List.of()),
                "daily", Map.of("snowfall_sum", List.of((Number) 30.0, (Number) 20.0))
        );
        SkiConditionsData d = client.parseSkiConditions(response);
        assertThat(d.snowDepthCm()).isEqualTo(50.0); // from daily fallback
    }

    @Test
    void parseSkiConditions_no_hourly_only_daily() {
        Map<String, Object> response = Map.of(
                "daily", Map.of("snowfall_sum", List.of((Number) 25.0))
        );
        SkiConditionsData d = client.parseSkiConditions(response);
        assertThat(d.snowDepthCm()).isEqualTo(25.0);
    }

    // ----- parseCurrentWeather: 雨/曇り/不明 weather code -----

    @Test
    void parseCurrentWeather_rain_code_non_snowing() {
        Map<String, Object> response = Map.of("current", Map.of(
                "temperature_2m", 10.0,
                "wind_speed_10m", 5.0,
                "wind_direction_10m", 180,
                "visibility", 5000,
                "weather_code", 65,
                "snowfall", 0.0
        ));
        CurrentWeatherData d = client.parseCurrentWeather(response);
        assertThat(d.weatherDescription()).isEqualTo("雨");
        assertThat(d.isSnowing()).isFalse();
        assertThat(d.windDirection()).isEqualTo("S");
    }

    @Test
    void describeWeatherCode_all_named_branches() {
        assertThat(OpenMeteoClient.describeWeatherCode(1)).isEqualTo("晴れ時々曇り");
        assertThat(OpenMeteoClient.describeWeatherCode(45)).isEqualTo("霧");
        assertThat(OpenMeteoClient.describeWeatherCode(51)).isEqualTo("霧雨");
        assertThat(OpenMeteoClient.describeWeatherCode(65)).isEqualTo("雨");
        assertThat(OpenMeteoClient.describeWeatherCode(77)).isEqualTo("雪あられ");
        assertThat(OpenMeteoClient.describeWeatherCode(80)).isEqualTo("にわか雨");
        assertThat(OpenMeteoClient.describeWeatherCode(85)).isEqualTo("にわか雪");
        assertThat(OpenMeteoClient.describeWeatherCode(96)).isEqualTo("雷雨を伴う雹");
    }

    // ----- parseForecast: 部分欠損のリスト -----

    @Test
    void parseForecast_handles_partial_lists() {
        // dates 3件、temperature_2m_max 1件のみ → safe(...) で 0.0 返却
        Map<String, Object> response = Map.of("daily", Map.of(
                "time", List.of("2026-01-10", "2026-01-11", "2026-01-12"),
                "temperature_2m_max", List.of(2.0),
                "weather_code", List.of()
        ));
        WeatherForecastData d = client.parseForecast(response);
        assertThat(d.dailyForecasts()).hasSize(3);
        assertThat(d.dailyForecasts().get(0).maxTempCelsius()).isEqualTo(2.0);
        assertThat(d.dailyForecasts().get(1).maxTempCelsius()).isEqualTo(0.0); // safe fallback
        assertThat(d.dailyForecasts().get(2).weatherCode()).isZero(); // codes 空 → 0
    }
}
