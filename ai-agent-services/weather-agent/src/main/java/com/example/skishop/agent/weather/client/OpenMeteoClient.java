package com.example.skishop.agent.weather.client;

import com.example.skishop.agent.common.dto.CurrentWeatherData;
import com.example.skishop.agent.common.dto.SkiConditionsData;
import com.example.skishop.agent.common.dto.WeatherAlertData;
import com.example.skishop.agent.common.dto.WeatherForecastData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Open-Meteo API クライアント（無料・APIキー不要）。
 * <ul>
 *   <li>Geocoding API: 場所名 → 緯度経度</li>
 *   <li>Forecast API: 現在天気・予報・スキーコンディション</li>
 *   <li>警報 API は提供されないため、{@link #generateAlertsFromData} で気象データから閾値生成</li>
 * </ul>
 */
@Component
public class OpenMeteoClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

    private final RestClient geocodingClient;
    private final RestClient forecastClient;

    public OpenMeteoClient() {
        this(RestClient.builder().baseUrl("https://geocoding-api.open-meteo.com/v1").build(),
             RestClient.builder().baseUrl("https://api.open-meteo.com/v1").build());
    }

    /** テスト用コンストラクタ。 */
    OpenMeteoClient(RestClient geocodingClient, RestClient forecastClient) {
        this.geocodingClient = geocodingClient;
        this.forecastClient = forecastClient;
    }

    public CurrentWeatherData getCurrentWeather(String location, String unit) {
        GeoLocation geo = geocode(location);
        log.debug("Fetching current weather for {} ({},{})", location, geo.latitude(), geo.longitude());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = forecastClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/forecast")
                        .queryParam("latitude", geo.latitude())
                        .queryParam("longitude", geo.longitude())
                        .queryParam("current", "temperature_2m,apparent_temperature,relative_humidity_2m,"
                                + "wind_speed_10m,wind_direction_10m,visibility,weather_code,snowfall")
                        .queryParam("temperature_unit", "celsius".equals(unit) ? "celsius" : "fahrenheit")
                        .queryParam("timezone", "Asia/Tokyo")
                        .build())
                .retrieve()
                .body(Map.class);

        return parseCurrentWeather(response);
    }

    public WeatherForecastData getWeatherForecast(String location, int days) {
        GeoLocation geo = geocode(location);
        log.debug("Fetching {}-day forecast for {}", days, location);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = forecastClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/forecast")
                        .queryParam("latitude", geo.latitude())
                        .queryParam("longitude", geo.longitude())
                        .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum,"
                                + "snowfall_sum,wind_speed_10m_max,weather_code,uv_index_max")
                        .queryParam("forecast_days", days)
                        .queryParam("timezone", "Asia/Tokyo")
                        .build())
                .retrieve()
                .body(Map.class);

        return parseForecast(response);
    }

    public SkiConditionsData getSkiConditions(String resortLocation) {
        GeoLocation geo = geocode(resortLocation);
        log.debug("Fetching ski conditions for {}", resortLocation);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = forecastClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/forecast")
                        .queryParam("latitude", geo.latitude())
                        .queryParam("longitude", geo.longitude())
                        .queryParam("daily", "snowfall_sum,wind_speed_10m_max")
                        .queryParam("hourly", "snow_depth,snowfall,visibility,wind_speed_10m")
                        .queryParam("forecast_days", 3)
                        .queryParam("past_days", 3)
                        .queryParam("timezone", "Asia/Tokyo")
                        .build())
                .retrieve()
                .body(Map.class);

        return parseSkiConditions(response);
    }

    public List<WeatherAlertData> getWeatherAlerts(String location) {
        CurrentWeatherData current = getCurrentWeather(location, "celsius");
        return generateAlertsFromData(current);
    }

    /**
     * 主要な国内スキーリゾート/地域のフォールバック座標。
     * Open-Meteo の Geocoding API は日本の小規模リゾート名を解決できないことが多いため、
     * 既知のリゾートはオフライン辞書で確実にヒットさせる。
     */
    private static final Map<String, GeoLocation> KNOWN_RESORTS = Map.ofEntries(
            // 新潟
            Map.entry("苗場", new GeoLocation(36.7878, 138.7917, "Naeba Ski Resort", "Japan")),
            Map.entry("naeba", new GeoLocation(36.7878, 138.7917, "Naeba Ski Resort", "Japan")),
            Map.entry("かぐら", new GeoLocation(36.8333, 138.8167, "Kagura Ski Resort", "Japan")),
            Map.entry("kagura", new GeoLocation(36.8333, 138.8167, "Kagura Ski Resort", "Japan")),
            Map.entry("湯沢", new GeoLocation(36.9342, 138.8147, "Yuzawa", "Japan")),
            Map.entry("越後湯沢", new GeoLocation(36.9342, 138.8147, "Echigo-Yuzawa", "Japan")),
            Map.entry("ガーラ湯沢", new GeoLocation(36.9486, 138.8133, "Gala Yuzawa", "Japan")),
            Map.entry("妙高", new GeoLocation(36.9069, 138.1247, "Myoko Ski Area", "Japan")),
            Map.entry("赤倉", new GeoLocation(36.8997, 138.1953, "Akakura Ski Resort", "Japan")),
            // 長野
            Map.entry("白馬", new GeoLocation(36.6981, 137.8623, "Hakuba", "Japan")),
            Map.entry("hakuba", new GeoLocation(36.6981, 137.8623, "Hakuba", "Japan")),
            Map.entry("八方尾根", new GeoLocation(36.6986, 137.8244, "Hakuba Happo-One", "Japan")),
            Map.entry("志賀高原", new GeoLocation(36.7261, 138.5044, "Shiga Kogen", "Japan")),
            Map.entry("野沢温泉", new GeoLocation(36.9233, 138.4400, "Nozawa Onsen", "Japan")),
            // 北海道
            Map.entry("ニセコ", new GeoLocation(42.8047, 140.6878, "Niseko", "Japan")),
            Map.entry("niseko", new GeoLocation(42.8047, 140.6878, "Niseko", "Japan")),
            Map.entry("ルスツ", new GeoLocation(42.7383, 140.8867, "Rusutsu Resort", "Japan")),
            Map.entry("富良野", new GeoLocation(43.3422, 142.3811, "Furano", "Japan")),
            Map.entry("トマム", new GeoLocation(43.0775, 142.6356, "Tomamu", "Japan")),
            Map.entry("キロロ", new GeoLocation(43.0903, 140.8589, "Kiroro Resort", "Japan")),
            Map.entry("札幌", new GeoLocation(43.0642, 141.3469, "Sapporo", "Japan")),
            // その他主要都市
            Map.entry("東京", new GeoLocation(35.6762, 139.6503, "Tokyo", "Japan")),
            Map.entry("tokyo", new GeoLocation(35.6762, 139.6503, "Tokyo", "Japan"))
    );

    /** デフォルトのフォールバック座標（東京）。 */
    private static final GeoLocation FALLBACK_LOCATION =
            new GeoLocation(35.6762, 139.6503, "Tokyo (fallback)", "Japan");

    private GeoLocation geocode(String locationName) {
        if (locationName == null || locationName.isBlank()) {
            log.warn("Geocode: empty location, using fallback (Tokyo)");
            return FALLBACK_LOCATION;
        }
        // 1) 既知リゾート辞書で部分一致を試行
        String normalized = locationName.toLowerCase().trim();
        for (Map.Entry<String, GeoLocation> entry : KNOWN_RESORTS.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (normalized.contains(key) || locationName.contains(entry.getKey())) {
                log.info("Geocode: matched known resort '{}' for input '{}'", entry.getValue().name(), locationName);
                return entry.getValue();
            }
        }
        // 2) Open-Meteo Geocoding API を試行
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = geocodingClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("name", locationName)
                            .queryParam("count", 5)
                            .queryParam("language", "ja")
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                if (results != null && !results.isEmpty()) {
                    // 日本の結果を優先
                    Map<String, Object> first = results.stream()
                            .filter(r -> "JP".equals(r.get("country_code")))
                            .findFirst()
                            .orElse(results.get(0));
                    return new GeoLocation(
                            ((Number) first.get("latitude")).doubleValue(),
                            ((Number) first.get("longitude")).doubleValue(),
                            (String) first.get("name"),
                            (String) first.getOrDefault("country", ""));
                }
            }
        } catch (Exception ex) {
            log.warn("Geocode API call failed for '{}': {}", locationName, ex.getMessage());
        }
        // 3) フォールバック（東京）
        log.warn("Geocode: location '{}' not found, using fallback (Tokyo)", locationName);
        return FALLBACK_LOCATION;
    }

    // ---------- パース処理 ----------

    @SuppressWarnings("unchecked")
    CurrentWeatherData parseCurrentWeather(Map<String, Object> response) {
        if (response == null) {
            return emptyCurrentWeather();
        }
        Map<String, Object> current = (Map<String, Object>) response.get("current");
        if (current == null) return emptyCurrentWeather();
        double temp = asDouble(current.get("temperature_2m"));
        double feels = asDouble(current.getOrDefault("apparent_temperature", temp));
        double humidity = asDouble(current.getOrDefault("relative_humidity_2m", 0.0));
        double wind = asDouble(current.getOrDefault("wind_speed_10m", 0.0));
        double windDir = asDouble(current.getOrDefault("wind_direction_10m", 0.0));
        double visibilityMeters = asDouble(current.getOrDefault("visibility", 10000.0));
        int weatherCode = (int) asDouble(current.getOrDefault("weather_code", 0));
        double snowfall = asDouble(current.getOrDefault("snowfall", 0.0));

        return new CurrentWeatherData(
                temp,
                feels,
                humidity,
                wind,
                degreesToCardinal(windDir),
                visibilityMeters / 1000.0,
                String.valueOf(weatherCode),
                describeWeatherCode(weatherCode),
                snowfall > 0,
                snowfall);
    }

    @SuppressWarnings("unchecked")
    WeatherForecastData parseForecast(Map<String, Object> response) {
        if (response == null) return new WeatherForecastData(List.of());
        Map<String, Object> daily = (Map<String, Object>) response.get("daily");
        if (daily == null) return new WeatherForecastData(List.of());

        List<String> dates = (List<String>) daily.getOrDefault("time", List.of());
        List<Number> tMax = (List<Number>) daily.getOrDefault("temperature_2m_max", List.of());
        List<Number> tMin = (List<Number>) daily.getOrDefault("temperature_2m_min", List.of());
        List<Number> precip = (List<Number>) daily.getOrDefault("precipitation_sum", List.of());
        List<Number> snowfall = (List<Number>) daily.getOrDefault("snowfall_sum", List.of());
        List<Number> wind = (List<Number>) daily.getOrDefault("wind_speed_10m_max", List.of());
        List<Number> codes = (List<Number>) daily.getOrDefault("weather_code", List.of());
        List<Number> uv = (List<Number>) daily.getOrDefault("uv_index_max", List.of());

        List<WeatherForecastData.DailyForecast> list = new ArrayList<>(dates.size());
        for (int i = 0; i < dates.size(); i++) {
            int code = i < codes.size() ? codes.get(i).intValue() : 0;
            list.add(new WeatherForecastData.DailyForecast(
                    LocalDate.parse(dates.get(i)),
                    safe(tMax, i),
                    safe(tMin, i),
                    safe(precip, i),
                    safe(snowfall, i),
                    0.0,
                    safe(wind, i),
                    describeWeatherCode(code),
                    code,
                    safe(uv, i)));
        }
        return new WeatherForecastData(list);
    }

    @SuppressWarnings("unchecked")
    SkiConditionsData parseSkiConditions(Map<String, Object> response) {
        if (response == null) return emptySkiConditions();

        Map<String, Object> hourly = (Map<String, Object>) response.get("hourly");
        Map<String, Object> daily = (Map<String, Object>) response.get("daily");

        double snowDepth = 0.0;
        double freshSnow24 = 0.0;
        double freshSnow72 = 0.0;
        double visibilityKm = 10.0;

        if (hourly != null) {
            List<Number> depthSeries = (List<Number>) hourly.getOrDefault("snow_depth", List.of());
            if (!depthSeries.isEmpty()) {
                snowDepth = depthSeries.get(depthSeries.size() - 1).doubleValue() * 100.0; // m -> cm
            }
            List<Number> snowfallSeries = (List<Number>) hourly.getOrDefault("snowfall", List.of());
            int n = snowfallSeries.size();
            for (int i = Math.max(0, n - 24); i < n; i++) freshSnow24 += snowfallSeries.get(i).doubleValue();
            for (int i = Math.max(0, n - 72); i < n; i++) freshSnow72 += snowfallSeries.get(i).doubleValue();

            List<Number> visSeries = (List<Number>) hourly.getOrDefault("visibility", List.of());
            if (!visSeries.isEmpty()) {
                visibilityKm = visSeries.get(visSeries.size() - 1).doubleValue() / 1000.0;
            }
        }

        if (daily != null && snowDepth == 0.0) {
            List<Number> snowfallSum = (List<Number>) daily.getOrDefault("snowfall_sum", List.of());
            for (Number n : snowfallSum) snowDepth += n.doubleValue();
        }

        String snowQuality = classifySnowQuality(freshSnow24, freshSnow72);
        boolean liftsLikelyOpen = snowDepth >= 30.0 && visibilityKm >= 1.0;
        String overall = snowDepth >= 80 && freshSnow24 >= 5 ? "EXCELLENT"
                : snowDepth >= 40 ? "GOOD"
                : snowDepth >= 20 ? "FAIR" : "POOR";
        String avalancheRisk = freshSnow72 >= 50 ? "HIGH"
                : freshSnow72 >= 30 ? "CONSIDERABLE"
                : freshSnow72 >= 15 ? "MODERATE" : "LOW";

        return new SkiConditionsData(
                snowDepth,
                freshSnow24,
                freshSnow72,
                snowQuality,
                liftsLikelyOpen,
                overall,
                liftsLikelyOpen ? "GROOMED" : "UNGROOMED",
                visibilityKm,
                avalancheRisk);
    }

    List<WeatherAlertData> generateAlertsFromData(CurrentWeatherData current) {
        List<WeatherAlertData> alerts = new ArrayList<>();
        Instant now = Instant.now();
        Instant later = now.plus(6, ChronoUnit.HOURS);

        if (current.windSpeedKph() >= 60.0) {
            alerts.add(new WeatherAlertData(
                    "HIGH_WIND", "WARNING",
                    "強風警報",
                    "風速が " + current.windSpeedKph() + " km/h を超えています。リフト停止の可能性があります。",
                    now, later));
        }
        if (current.snowfallMm() >= 10.0 && current.windSpeedKph() >= 40.0) {
            alerts.add(new WeatherAlertData(
                    "BLIZZARD", "WARNING",
                    "吹雪警報",
                    "視界不良の恐れがあります。スキー場の利用に注意してください。",
                    now, later));
        }
        if (current.temperatureCelsius() >= 0.0 && current.snowfallMm() > 0) {
            alerts.add(new WeatherAlertData(
                    "FREEZING_RAIN", "ADVISORY",
                    "凍結雨注意報",
                    "気温が0度以上で降雪があります。アイスバーン化の可能性があります。",
                    now, later));
        }
        return alerts;
    }

    // ---------- ユーティリティ ----------

    private static double asDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(v.toString());
    }

    private static double safe(List<Number> list, int i) {
        return i < list.size() && list.get(i) != null ? list.get(i).doubleValue() : 0.0;
    }

    private static String degreesToCardinal(double deg) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int idx = (int) Math.round(((deg % 360) / 45.0)) % 8;
        return dirs[(idx + 8) % 8];
    }

    static String describeWeatherCode(int code) {
        return switch (code) {
            case 0 -> "快晴";
            case 1, 2, 3 -> "晴れ時々曇り";
            case 45, 48 -> "霧";
            case 51, 53, 55 -> "霧雨";
            case 61, 63, 65 -> "雨";
            case 71, 73, 75 -> "雪";
            case 77 -> "雪あられ";
            case 80, 81, 82 -> "にわか雨";
            case 85, 86 -> "にわか雪";
            case 95 -> "雷雨";
            case 96, 99 -> "雷雨を伴う雹";
            default -> "不明 (コード " + code + ")";
        };
    }

    private static String classifySnowQuality(double fresh24, double fresh72) {
        if (fresh24 >= 15) return "POWDER";
        if (fresh24 >= 5) return "PACKED_POWDER";
        if (fresh72 >= 10) return "PACKED_POWDER";
        if (fresh72 == 0) return "ICY";
        return "WET";
    }

    private static CurrentWeatherData emptyCurrentWeather() {
        return new CurrentWeatherData(0, 0, 0, 0, "N", 10.0, "0", "データなし", false, 0);
    }

    private static SkiConditionsData emptySkiConditions() {
        return new SkiConditionsData(0, 0, 0, "UNKNOWN", false, "POOR", "UNGROOMED", 0, "LOW");
    }

    record GeoLocation(double latitude, double longitude, String name, String country) {}
}
