package com.example.skishop.agent.common.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Weather Agent への自然言語/構造化リクエスト。
 */
public record WeatherAgentRequest(
        @NotBlank(message = "location は必須です（例: Naeba, Japan）")
        String location,
        String resortName,
        String question,
        String unit,
        Integer forecastDays
) {
    public WeatherAgentRequest {
        if (unit == null) unit = "celsius";
        if (forecastDays == null) forecastDays = 7;
    }

    /** 簡易コンストラクタ: location + resort + unit + forecastDays。 */
    public WeatherAgentRequest(String location, String resortName, String unit, Integer forecastDays) {
        this(location, resortName, null, unit, forecastDays);
    }
}
