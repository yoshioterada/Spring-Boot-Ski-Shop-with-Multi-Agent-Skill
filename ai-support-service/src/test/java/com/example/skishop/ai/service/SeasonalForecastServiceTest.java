package com.example.skishop.ai.service;

import com.example.skishop.ai.dto.SeasonalForecastRequest;
import com.example.skishop.ai.dto.SeasonalForecastRequest.Horizon;
import com.example.skishop.ai.dto.SeasonalForecastResponse;
import com.example.skishop.ai.dto.SeasonalForecastResponse.CategoryForecast;
import com.example.skishop.ai.forecast.SeasonalForecaster;
import com.example.skishop.ai.forecast.SeasonalForecaster.ForecastResult;
import com.example.skishop.ai.tool.AnalyticsToolFunctions;
import com.example.skishop.ai.dto.ToolResults.WeatherForecastResult;
import com.example.skishop.ai.dto.ToolResults.WeatherForecastResult.WeekForecast;
import com.example.skishop.ai.util.PromptSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SeasonalForecastService テスト (P4: D-F2-04 assumptions, D-F2-05 weather failopen).
 */
@ExtendWith(MockitoExtension.class)
class SeasonalForecastServiceTest {

    @Mock private SeasonalForecaster forecaster;
    @Mock private ChatClient.Builder chatClientBuilder;
    @Mock private ChatClient chatClient;
    @Mock private AnalyticsToolFunctions toolFunctions;
    @Mock private LlmAuditService auditService;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callResponseSpec;

    private PromptSanitizer sanitizer;
    private SeasonalForecastService service;

    @BeforeEach
    void setUp() {
        sanitizer = new PromptSanitizer();
        when(chatClientBuilder.build()).thenReturn(chatClient);
        service = new SeasonalForecastService(forecaster, chatClientBuilder, toolFunctions, auditService, sanitizer);
    }

    private ForecastResult sampleStep1() {
        return new ForecastResult(
                "2026年 来シーズン",
                List.of(new CategoryForecast("cat-boots", "スキーブーツ", 1240, 980, 1480, 0.07, List.of())),
                new java.util.ArrayList<>(List.of("過去 2 シーズンの月次販売を基に需要を推定"))
        );
    }

    // ── D-F2-04: assumptions 配列が必ず 1 件以上 ──

    @Test
    void generateForecast_alwaysHasAssumptions() {
        when(forecaster.forecast(any(), any())).thenReturn(sampleStep1());
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("ナラティブテスト");

        var request = new SeasonalForecastRequest(Horizon.NEXT_SEASON, null, false);
        SeasonalForecastResponse response = service.generateForecast(request);

        assertThat(response.assumptions()).isNotEmpty();
    }

    // ── D-F2-05: weather 失敗時フェイルオープン ──

    @Test
    void generateForecast_weatherFailure_continuesWithAssumption() {
        when(forecaster.forecast(any(), any())).thenReturn(sampleStep1());
        when(toolFunctions.getWeatherForecast(anyString(), anyInt())).thenThrow(new RuntimeException("weather down"));
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("気象なし予測");

        var request = new SeasonalForecastRequest(Horizon.NEXT_SEASON, null, true);
        SeasonalForecastResponse response = service.generateForecast(request);

        assertThat(response.assumptions())
                .anyMatch(a -> a.contains("気象データなし"));
        assertThat(response.narrative()).isNotNull();
    }

    // ── D-F2-05: weather 成功時に assumptions に気象引用 ──

    @Test
    void generateForecast_weatherSuccess_addsWeatherAssumption() {
        when(forecaster.forecast(any(), any())).thenReturn(sampleStep1());
        when(toolFunctions.getWeatherForecast(anyString(), anyInt()))
                .thenReturn(new WeatherForecastResult("kanto", 12,
                        List.of(new WeekForecast("2026-10-01", 8.5, 0.0, "暖冬傾向 60%"))));
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("暖冬を考慮した予測");

        var request = new SeasonalForecastRequest(Horizon.NEXT_SEASON, null, true);
        SeasonalForecastResponse response = service.generateForecast(request);

        assertThat(response.assumptions())
                .anyMatch(a -> a.contains("気象長期予報"));
    }

    // ── D-F2-03: Step 1 の信頼区間が Step 2 で変更されない ──

    @Test
    void generateForecast_preservesStep1ConfidenceInterval() {
        ForecastResult step1 = sampleStep1();
        when(forecaster.forecast(any(), any())).thenReturn(step1);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("test narrative");

        var request = new SeasonalForecastRequest(Horizon.NEXT_SEASON, null, false);
        SeasonalForecastResponse response = service.generateForecast(request);

        // Step 1 の数値がそのまま response に渡っている
        assertThat(response.categories()).isEqualTo(step1.categories());
        assertThat(response.categories().getFirst().confidenceLow()).isEqualTo(980);
        assertThat(response.categories().getFirst().confidenceHigh()).isEqualTo(1480);
    }

    // ── LLM フォールバック ──

    @Test
    void fallbackNarrative_returnsDefaultMessage() {
        String fallback = service.fallbackNarrative(sampleStep1(), null, List.of(), new RuntimeException("CB open"));
        assertThat(fallback).contains("AI ナラティブを生成できません");
    }

    // ── レスポンス構造検証 ──

    @Test
    void generateForecast_responseHasAllFields() {
        when(forecaster.forecast(any(), any())).thenReturn(sampleStep1());
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("complete narrative");

        var request = new SeasonalForecastRequest(Horizon.NEXT_MONTH, List.of("cat-boots"), false);
        SeasonalForecastResponse response = service.generateForecast(request);

        assertThat(response.horizonLabel()).isNotBlank();
        assertThat(response.generatedAt()).isNotNull();
        assertThat(response.categories()).isNotEmpty();
        assertThat(response.narrative()).isNotBlank();
        assertThat(response.assumptions()).isNotEmpty();
    }

    // ── 監査ログ呼出確認 ──

    @Test
    void generateForecast_callsAuditService() {
        when(forecaster.forecast(any(), any())).thenReturn(sampleStep1());
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("audited narrative");

        var request = new SeasonalForecastRequest(Horizon.NEXT_MONTH, null, false);
        service.generateForecast(request);

        verify(auditService).record(eq("F2"), anyString(), eq("seasonal-forecast"),
                anyInt(), anyInt(), anyLong(), eq(true), isNull());
    }
}
