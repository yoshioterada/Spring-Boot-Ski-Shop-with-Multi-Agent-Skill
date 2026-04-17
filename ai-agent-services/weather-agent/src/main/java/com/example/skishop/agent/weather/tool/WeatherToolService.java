package com.example.skishop.agent.weather.tool;

import com.example.skishop.agent.common.dto.CurrentWeatherData;
import com.example.skishop.agent.common.dto.SkiConditionsData;
import com.example.skishop.agent.common.dto.SkiFeasibilityResult;
import com.example.skishop.agent.common.dto.WeatherAlertData;
import com.example.skishop.agent.common.dto.WeatherForecastData;
import com.example.skishop.agent.weather.client.OpenMeteoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Weather Agent の Tool 定義クラス。
 * ChatClient#tools() に渡すことで、GPT が各 @Tool を自律的に呼び出せる。
 */
@Service
public class WeatherToolService {

    private static final Logger log = LoggerFactory.getLogger(WeatherToolService.class);

    private final OpenMeteoClient openMeteoClient;

    public WeatherToolService(OpenMeteoClient openMeteoClient) {
        this.openMeteoClient = openMeteoClient;
    }

    @Tool(description = """
            指定した場所の現在の気象データを取得する。
            気温（摂氏）、体感温度、湿度、風速、視界、現在の降雪状況を返す。
            スキー場名または都市名・地域名を location に指定すること。
            例: "Naeba, Japan", "Hakuba, Nagano, Japan", "Niseko, Hokkaido, Japan"
            """)
    @Cacheable(value = "currentWeather", key = "#location + ':' + (#unit ?: 'celsius')")
    public CurrentWeatherData getCurrentWeather(
            @ToolParam(description = "場所の名称。スキー場名または都市名、例: Naeba, Japan") String location,
            @ToolParam(description = "温度単位。celsius または fahrenheit", required = false) @Nullable String unit) {
        log.info("Tool getCurrentWeather: location={}, unit={}", location, unit);
        String resolvedUnit = unit != null ? unit : "celsius";
        return openMeteoClient.getCurrentWeather(location, resolvedUnit);
    }

    @Tool(description = """
            指定した場所の天気予報を取得する。
            日別の最高・最低気温、降水量、降雪量、積雪深、風速の予報を返す。
            スキー旅行の計画立案や装備の推奨に使用する。
            """)
    @Cacheable(value = "weatherForecast", key = "#location + ':' + (#days ?: 7)")
    public WeatherForecastData getWeatherForecast(
            @ToolParam(description = "場所の名称") String location,
            @ToolParam(description = "予報日数。1〜16の整数。デフォルトは7", required = false) @Nullable Integer days) {
        log.info("Tool getWeatherForecast: location={}, days={}", location, days);
        int resolvedDays = (days != null && days >= 1 && days <= 16) ? days : 7;
        return openMeteoClient.getWeatherForecast(location, resolvedDays);
    }

    @Tool(description = """
            スキーリゾートのコンディション情報を取得する。
            積雪深、直近24〜72時間の新雪量、雪質、リフト稼働見込み、アバランチリスクレベルを返す。
            """)
    @Cacheable(value = "skiConditions", key = "#resortLocation")
    public SkiConditionsData getSkiConditions(
            @ToolParam(description = "スキーリゾート名、例: Naeba Ski Resort, Niigata, Japan") String resortLocation) {
        log.info("Tool getSkiConditions: resort={}", resortLocation);
        return openMeteoClient.getSkiConditions(resortLocation);
    }

    @Tool(description = """
            指定した場所の気象警報・注意報を取得する。
            吹雪・強風・凍結雨などを返す。警報がない場合は空のリスト。
            """)
    public List<WeatherAlertData> getWeatherAlerts(
            @ToolParam(description = "場所の名称") String location) {
        log.info("Tool getWeatherAlerts: location={}", location);
        return openMeteoClient.getWeatherAlerts(location);
    }

    @Tool(description = """
            指定した場所の気象データを総合的に評価し、スキーの適性（HIGH/MEDIUM/LOW）と
            推奨ギアレベルを返す。Equipment Matching と Dynamic Pricing が利用する。
            """)
    public SkiFeasibilityResult assessSkiFeasibility(
            @ToolParam(description = "場所の名称") String location,
            @ToolParam(description = "日付（ISO-8601）。省略時は今日", required = false) @Nullable String date) {
        log.info("Tool assessSkiFeasibility: location={}, date={}", location, date);
        CurrentWeatherData current = openMeteoClient.getCurrentWeather(location, "celsius");
        SkiConditionsData conditions = openMeteoClient.getSkiConditions(location);
        return evaluateFeasibility(current, conditions);
    }

    SkiFeasibilityResult evaluateFeasibility(CurrentWeatherData current, SkiConditionsData conditions) {
        int score = 0;
        if (conditions.snowDepthCm() >= 50) score += 30;
        else if (conditions.snowDepthCm() >= 20) score += 15;

        if (current.visibilityKm() >= 5) score += 20;
        else if (current.visibilityKm() >= 2) score += 10;

        if (current.windSpeedKph() <= 30) score += 20;
        else if (current.windSpeedKph() <= 50) score += 10;

        if (current.temperatureCelsius() >= -20 && current.temperatureCelsius() <= 0) score += 20;
        else if (current.temperatureCelsius() > 0 && current.temperatureCelsius() <= 5) score += 10;

        if (conditions.freshSnowLast24hCm() >= 10) score += 10;

        String feasibility = score >= 60 ? "HIGH" : score >= 35 ? "MEDIUM" : "LOW";
        String gearLevel = current.temperatureCelsius() < -10 ? "EXTREME_COLD"
                : current.temperatureCelsius() < -5 ? "COLD" : "MODERATE";
        String overall = score >= 60 ? "EXCELLENT" : score >= 35 ? "GOOD" : "POOR";

        return new SkiFeasibilityResult(feasibility, score, gearLevel, conditions.overallCondition(), overall);
    }
}
