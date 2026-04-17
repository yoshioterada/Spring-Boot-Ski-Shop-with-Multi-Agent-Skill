package com.example.skishop.agent.weather.service;

import com.example.skishop.agent.common.dto.CurrentWeatherData;
import com.example.skishop.agent.common.dto.SkiConditionsData;
import com.example.skishop.agent.common.dto.SkiFeasibilityResult;
import com.example.skishop.agent.common.dto.WeatherAgentRequest;
import com.example.skishop.agent.common.dto.WeatherAgentResponse;
import com.example.skishop.agent.common.dto.WeatherAlertData;
import com.example.skishop.agent.common.dto.WeatherForecastData;
import com.example.skishop.agent.weather.tool.WeatherToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Weather Agent のメインサービス。
 * ChatClient + WeatherToolService の @Tool で気象 API を呼び出し、構造化レスポンスを返す。
 */
@Service
public class WeatherAgentService {

    private static final Logger log = LoggerFactory.getLogger(WeatherAgentService.class);

    private static final String SYSTEM_PROMPT = """
            あなたはスキーショップ専門の気象アナリストAIエージェントです。
            提供されている気象ツールを使用して、スキー客に対して正確で実用的な情報を提供してください。

            回答の際は以下を必ず含めること:
            1. 現在の気象状況（気温・積雪・視界）
            2. 今後の天気予報（7日間）
            3. スキーコンディション（雪質・積雪深）
            4. 気象警報がある場合は必ず明示
            5. スキーの可否と推奨装備のカテゴリ
            6. 安全に関する注意事項

            常に日本語で回答すること。数値には単位を明示すること。
            """;

    private final ChatClient chatClient;
    private final WeatherToolService weatherToolService;

    public WeatherAgentService(
            @Qualifier("weatherAgentChatClient") ChatClient chatClient,
            WeatherToolService weatherToolService) {
        this.chatClient = chatClient;
        this.weatherToolService = weatherToolService;
    }

    public WeatherAgentResponse analyze(WeatherAgentRequest request) {
        log.info("Weather Agent analyze: location={}, question={}", request.location(), request.question());

        String userPrompt = buildUserPrompt(request);

        String aiSummary = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .tools(weatherToolService)
                .call()
                .content();

        CurrentWeatherData current = weatherToolService.getCurrentWeather(request.location(), request.unit());
        WeatherForecastData forecast = weatherToolService.getWeatherForecast(request.location(), request.forecastDays());
        SkiConditionsData skiConditions = weatherToolService.getSkiConditions(request.location());
        List<WeatherAlertData> alerts = weatherToolService.getWeatherAlerts(request.location());
        SkiFeasibilityResult feasibility = weatherToolService.assessSkiFeasibility(request.location(), null);

        return new WeatherAgentResponse(
                request.location(),
                current,
                forecast,
                skiConditions,
                alerts,
                aiSummary,
                feasibility.feasibility(),
                Instant.now());
    }

    public String quickAnalyze(String location) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("「" + location + "」の現在のスキーコンディションを200字以内で要約して")
                .tools(weatherToolService)
                .call()
                .content();
    }

    private String buildUserPrompt(WeatherAgentRequest request) {
        if (request.question() != null && !request.question().isBlank()) {
            return "場所: %s\n質問: %s".formatted(request.location(), request.question());
        }
        return "「%s」のスキーコンディションと天気予報を詳しく教えてください。".formatted(request.location());
    }
}
