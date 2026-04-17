# Weather Agent 詳細設計書

## 1. 概要

### 1.1 目的

本書は、スキーショップ EC プラットフォームの Multi-Agent System における **Weather Agent（天気・ロケーション エージェント）** の Spring AI を用いた詳細設計を記述する。

Weather Agent は、スキー場の気象情報・積雪状況・天気予報を取得・解析し、以下のエージェントおよびサービスに情報を提供する専門 Worker Agent である。

- **Equipment Matching Agent**: 天候・気温に応じた装備レコメンド
- **Dynamic Pricing Agent**: 悪天候/好天候によるダイナミックプライシング
- **Customer Support Agent**: 顧客への気象アドバイス提供
- **Orchestrator**: 全エージェントを調整するオーケストレーター

### 1.2 アーキテクチャ上の位置づけ

```
┌─────────────────────────────────────────────────────────┐
│              Multi-Agent System                         │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │         Orchestrator Agent (将来実装)             │  │
│  │   .tools(weatherAgentToolCallbackProvider)       │  │
│  └──────────────────┬───────────────────────────────┘  │
│                     │ ToolCallback 経由                 │
│  ┌──────────────────▼───────────────────────────────┐  │
│  │         Weather Agent (本設計書対象)              │  │
│  │                                                  │  │
│  │  [WeatherToolService]                            │  │
│  │   @Tool getCurrentWeather(location, unit)        │  │
│  │   @Tool getWeatherForecast(location, days)       │  │
│  │   @Tool getSkiConditions(resort)                 │  │
│  │   @Tool getWeatherAlerts(location)               │  │
│  │                                                  │  │
│  │  [WeatherAgentService]  ← ChatClient + @Tool    │  │
│  │   analyze(WeatherAgentRequest)                   │  │
│  │   → WeatherAgentResponse (structured output)    │  │
│  └──────────────────┬───────────────────────────────┘  │
│                     │                                   │
└─────────────────────┼───────────────────────────────────┘
                      │ RestClient
               ┌──────▼──────────────┐
               │  Open-Meteo API     │  (無料・APIキー不要)
               │  Azure Maps Weather │  (Azure AI Foundry 連携)
               └─────────────────────┘
```

### 1.3 デプロイメントモード（ハイブリッド規約）

本 Agent は [hybrid-deployment-design.md](hybrid-deployment-design.md) の規約に従う。

- **モジュール種別**: **library jar**。`@SpringBootApplication` は持たない
- **デフォルト起動**: `agent-runtime-monolith`（ポート 8100）に同梱。Orchestrator / Equipment Matching / Dynamic Pricing と同 JVM のため Bean 直接呼び出し
- **分散起動**: `agent-runtime-standalone/weather-standalone`（ポート 8100）として個別起動可能
- **共有 DTO**: `WeatherAgentRequest`, `WeatherAgentResponse`, `CurrentWeatherData`, `WeatherForecastData`, `SkiConditionsData`, `WeatherAlertData`, `SkiFeasibilityResult` は `agent-common/src/main/java/com/example/skishop/agent/common/dto/` に移動
- **Worker→Worker 抽象の提供側**: 本モジュールは `LocalWeatherInvoker implements WeatherInvoker`（クラス本体は本モジュールの `com.example.skishop.agent.weather.invoker` パッケージ、インターフェースは `agent-common/invoker`）を提供し、`@ConditionalOnProperty(name="agents.deployment.mode", havingValue="monolith", matchIfMissing=true)` + `@ConditionalOnBean(WeatherAgentService.class)` で登録する。Equipment Matching / Dynamic Pricing から同 JVM 内で使用される
- **Bean 名規約**: `weatherAgentChatClient`, `weatherOpenMeteoRestClient` など Agent 名プレフィックス必須。サービスクラスでは `@Qualifier` で注入
- **`SecurityConfig`**: `@ConditionalOnProperty(name="agents.deployment.mode", havingValue="distributed")` を付与
- **`Controller`**: `@ConditionalOnProperty(name="agents.web.enabled", havingValue="true", matchIfMissing=true)` を付与
- **`AutoConfiguration`**: `@AutoConfiguration` + `@Import({WeatherToolService.class, WeatherAgentService.class, OpenMeteoClient.class, WeatherAgentConfig.class, LocalWeatherInvoker.class})`
- **`application.properties`**: 本モジュールでは同梱しない。§5 の内容は standalone 用テンプレート

---

## 2. Agentic Pattern の選択

### 2.1 採用パターン: Orchestrator-Workers の Worker

Spring AI Examples の [Agentic Patterns](https://github.com/spring-projects/spring-ai-examples/tree/main/agentic-patterns) に定義された **Orchestrator-Workers パターン**における **Worker** として設計する。

| パターン | 用途 | 選択理由 |
|---------|------|---------|
| Chain Workflow | 順序処理 | ✗ 天気は単独で完結 |
| Parallelization | 並列処理 | △ 内部では複数 API 並列呼び出し |
| Routing Workflow | 分類・ルーティング | ✗ |
| **Orchestrator-Workers** | 専門 Worker の組み合わせ | ✅ 他エージェントから呼び出される Worker |
| Evaluator-Optimizer | 評価・改善ループ | ✗ |

### 2.2 外部呼び出し設計の方針

オーケストレーターが Weather Agent を呼び出す方法は **2つ** を提供する。

**方法 A: ToolCallback として提供（推奨）**

オーケストレーターが `ChatClient` 上で `.tools(weatherAgentToolCallbackProvider)` と指定するだけで、GPT モデルが自律的に Weather Agent の各 Tool を呼び出せる。

```java
// オーケストレーター側（将来実装）のイメージ
chatClient.prompt()
    .user("新潟県の苗場スキー場の今週の天気と装備推奨を教えて")
    .tools(weatherAgentToolCallbackProvider)   // Weather Agent を Tool として登録
    .tools(equipmentAgentToolCallbackProvider) // Equipment Agent を Tool として登録
    .call()
    .content();
```

**方法 B: REST API として提供**

他サービス・他言語からも呼び出せるよう、REST エンドポイントも公開する。

```
POST /api/v1/agents/weather/analyze
GET  /api/v1/agents/weather/current?location=Naeba,Japan
GET  /api/v1/agents/weather/forecast?location=Naeba,Japan&days=7
GET  /api/v1/agents/weather/ski-conditions?resort=Naeba
```

---

## 3. モジュール構成

### 3.1 Maven モジュール

```
ai-agent-services/
└── weather-agent/
    ├── pom.xml
    ├── Dockerfile
    └── src/
        ├── main/
        │   ├── java/com/example/skishop/agent/weather/
        │   │   ├── WeatherAgentApplication.java
        │   │   ├── config/
        │   │   │   ├── WeatherAgentConfig.java          # ChatClient・Bean 定義
        │   │   │   ├── WeatherApiConfig.java            # 外部 API クライアント
        │   │   │   └── SecurityConfig.java              # JWT 認証設定
        │   │   ├── tool/
        │   │   │   └── WeatherToolService.java          # @Tool アノテーション付きツール群
        │   │   ├── service/
        │   │   │   └── WeatherAgentService.java         # ChatClient を使うエージェントロジック
        │   │   ├── client/
        │   │   │   ├── OpenMeteoClient.java             # Open-Meteo API クライアント
        │   │   │   └── AzureMapsWeatherClient.java      # Azure Maps 気象 API クライアント
        │   │   ├── controller/
        │   │   │   └── WeatherAgentController.java      # REST エンドポイント（方法B）
        │   │   └── dto/
        │   │       ├── WeatherAgentRequest.java
        │   │       ├── WeatherAgentResponse.java
        │   │       ├── CurrentWeatherData.java
        │   │       ├── WeatherForecastData.java
        │   │       ├── SkiConditionsData.java
        │   │       └── WeatherAlertData.java
        └── resources/
            └── application.properties
```

### 3.2 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.example.skishop</groupId>
        <artifactId>skishop-parent</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>weather-agent</artifactId>
    <name>Weather Agent Service</name>

    <dependencies>
        <!-- 共通ライブラリ（JWT フィルター等） -->
        <dependency>
            <groupId>com.example.skishop</groupId>
            <artifactId>common-lib</artifactId>
        </dependency>

        <!-- Spring Boot Web / Security / Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Spring AI - Azure OpenAI（既存サービスと同じモデルを使用） -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-azure-openai</artifactId>
        </dependency>

        <!-- Redis キャッシュ（天気データのキャッシュに使用） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-cache</artifactId>
        </dependency>

        <!-- Actuator / Prometheus -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>

        <!-- OpenAPI ドキュメント -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>

        <!-- テスト -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <!--
      library jar 規約（hybrid-deployment-design.md §2.2 参照）:
      - packaging=jar のまま、spring-boot-maven-plugin の <plugin> 宣言は行わない
      - 親 POM の <pluginManagement> で plugin が管理されているため、宣言しなければ repackage は走らない
      - 実行可能 jar は agent-runtime-monolith または agent-runtime-standalone/weather-standalone が生成する
    -->
</project>
```

---

## 4. クラス詳細設計

### 4.1 DTO 設計

```java
// dto/WeatherAgentRequest.java
package com.example.skishop.agent.common.dto;

import jakarta.validation.constraints.NotBlank;

public record WeatherAgentRequest(
        @NotBlank(message = "location は必須です（例: Naeba, Japan）")
        String location,

        String resortName,           // スキーリゾート名（任意）

        String question,             // 自然言語での質問（例: 「今週末スキーに行けますか？」）

        String unit,                 // "celsius" | "fahrenheit"（デフォルト: celsius）

        Integer forecastDays         // 予報日数（デフォルト: 7）
) {
    public WeatherAgentRequest {
        if (unit == null) unit = "celsius";
        if (forecastDays == null) forecastDays = 7;
    }
}
```

```java
// dto/WeatherAgentResponse.java
package com.example.skishop.agent.common.dto;

import java.time.Instant;

public record WeatherAgentResponse(
        String location,
        CurrentWeatherData currentWeather,
        WeatherForecastData forecast,
        SkiConditionsData skiConditions,        // スキーに特化した情報
        java.util.List<WeatherAlertData> alerts,
        String aiSummary,                       // GPT による日本語サマリー
        String skiFeasibilityAssessment,        // スキー可否の判断（HIGH/MEDIUM/LOW）
        Instant generatedAt
) {}
```

```java
// dto/CurrentWeatherData.java
package com.example.skishop.agent.common.dto;

public record CurrentWeatherData(
        double temperatureCelsius,
        double feelsLikeCelsius,
        double humidity,
        double windSpeedKph,
        String windDirection,
        double visibilityKm,
        String weatherCode,      // WMO 天気コード
        String weatherDescription,
        boolean isSnowing,
        double snowfallMm
) {}
```

```java
// dto/WeatherForecastData.java
package com.example.skishop.agent.common.dto;

import java.time.LocalDate;
import java.util.List;

public record WeatherForecastData(
        List<DailyForecast> dailyForecasts
) {
    public record DailyForecast(
            LocalDate date,
            double maxTempCelsius,
            double minTempCelsius,
            double precipitationMm,
            double snowfallCm,
            double snowDepthCm,
            double windSpeedMaxKph,
            String weatherDescription,
            int weatherCode,
            double uvIndex
    ) {}
}
```

```java
// dto/SkiConditionsData.java
package com.example.skishop.agent.common.dto;

public record SkiConditionsData(
        double snowDepthCm,
        double freshSnowLast24hCm,
        double freshSnowLast72hCm,
        String snowQuality,          // "POWDER" | "PACKED_POWDER" | "WET" | "ICY" | "SPRING"
        boolean isLiftsLikelyOpen,
        String overallCondition,     // "EXCELLENT" | "GOOD" | "FAIR" | "POOR"
        String grooomingStatus,
        double visibilityKm,
        String avalancheRisk         // "LOW" | "MODERATE" | "CONSIDERABLE" | "HIGH" | "EXTREME"
) {}
```

```java
// dto/WeatherAlertData.java
package com.example.skishop.agent.common.dto;

import java.time.Instant;

public record WeatherAlertData(
        String alertType,    // "BLIZZARD" | "HIGH_WIND" | "FREEZING_RAIN" | "AVALANCHE"
        String severity,     // "ADVISORY" | "WATCH" | "WARNING" | "EMERGENCY"
        String headline,
        String description,
        Instant effectiveFrom,
        Instant effectiveUntil
) {}
```

---

### 4.2 WeatherToolService（@Tool 定義）

オーケストレーターから `ToolCallback` として利用される中核クラス。
`@Tool` アノテーションにより、GPT モデルが自律的に呼び出すツールを定義する。

```java
// tool/WeatherToolService.java
package com.example.skishop.agent.weather.tool;

import com.example.skishop.agent.weather.client.OpenMeteoClient;
import com.example.skishop.agent.common.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Weather Agent の Tool 定義クラス。
 * このクラスのインスタンスを ChatClient#tools() に渡すことで、
 * オーケストレーターが GPT 経由で各ツールを呼び出せるようになる。
 */
@Service
public class WeatherToolService {

    private static final Logger log = LoggerFactory.getLogger(WeatherToolService.class);

    private final OpenMeteoClient openMeteoClient;

    public WeatherToolService(OpenMeteoClient openMeteoClient) {
        this.openMeteoClient = openMeteoClient;
    }

    /**
     * ツール1: 現在の気象データを取得する。
     * スキー場周辺の気温・降雪・風速をリアルタイムで返す。
     */
    @Tool(description = """
            指定した場所の現在の気象データを取得する。
            気温（摂氏）、体感温度、湿度、風速、視界、現在の降雪状況を返す。
            スキー場名または都市名・地域名を location に指定すること。
            例: "Naeba, Japan", "Hakuba, Nagano, Japan", "Niseko, Hokkaido, Japan"
            """)
    @Cacheable(value = "currentWeather", key = "#location + ':' + #unit")
    public CurrentWeatherData getCurrentWeather(
            @ToolParam(description = "場所の名称。スキー場名または都市名、例: Naeba, Japan") String location,
            @ToolParam(description = "温度単位。celsius または fahrenheit", required = false) @Nullable String unit) {
        log.info("Tool getCurrentWeather called: location={}, unit={}", location, unit);
        String resolvedUnit = unit != null ? unit : "celsius";
        return openMeteoClient.getCurrentWeather(location, resolvedUnit);
    }

    /**
     * ツール2: 天気予報を取得する。
     * 指定日数分の日別予報（降雪量・積雪深・最高最低気温）を返す。
     */
    @Tool(description = """
            指定した場所の天気予報を取得する。
            日別の最高・最低気温、降水量、降雪量、積雪深、風速の予報を返す。
            スキー旅行の計画立案や装備の推奨に使用する。
            """)
    @Cacheable(value = "weatherForecast", key = "#location + ':' + #days")
    public WeatherForecastData getWeatherForecast(
            @ToolParam(description = "場所の名称。スキー場名または都市名、例: Hakuba, Nagano, Japan") String location,
            @ToolParam(description = "予報日数。1〜16の整数。デフォルトは7", required = false) @Nullable Integer days) {
        log.info("Tool getWeatherForecast called: location={}, days={}", location, days);
        int resolvedDays = (days != null && days >= 1 && days <= 16) ? days : 7;
        return openMeteoClient.getWeatherForecast(location, resolvedDays);
    }

    /**
     * ツール3: スキー場の雪質・コンディションを取得する。
     * 積雪深、新雪量、雪質、リフト稼働見込み、アバランチリスクを返す。
     */
    @Tool(description = """
            スキーリゾートのコンディション情報を取得する。
            積雪深、直近24〜72時間の新雪量、雪質（パウダー/圧雪/湿雪/アイスバーン）、
            リフト稼働見込み、アバランチリスクレベルを返す。
            スキー装備の推奨やリゾート選定に使用する。
            """)
    @Cacheable(value = "skiConditions", key = "#resortLocation")
    public SkiConditionsData getSkiConditions(
            @ToolParam(description = "スキーリゾートの場所名、例: Naeba Ski Resort, Niigata, Japan") String resortLocation) {
        log.info("Tool getSkiConditions called: resortLocation={}", resortLocation);
        return openMeteoClient.getSkiConditions(resortLocation);
    }

    /**
     * ツール4: 気象警報・注意報を取得する。
     * 吹雪・強風・アバランチ等の警報を返す。
     */
    @Tool(description = """
            指定した場所の気象警報・注意報を取得する。
            吹雪警報、強風注意報、アバランチ警報などを返す。
            顧客への安全情報提供や旅行取り消しアドバイスに使用する。
            警報がない場合は空のリストを返す。
            """)
    public java.util.List<WeatherAlertData> getWeatherAlerts(
            @ToolParam(description = "場所の名称、例: Hakuba, Nagano, Japan") String location) {
        log.info("Tool getWeatherAlerts called: location={}", location);
        return openMeteoClient.getWeatherAlerts(location);
    }

    /**
     * ツール5: スキー適性評価を実施する。
     * 気象データを総合的に評価し、スキーの可否と推奨装備カテゴリを返す。
     */
    @Tool(description = """
            指定した場所の気象データを総合的に評価し、スキーの適性（HIGH/MEDIUM/LOW）と
            推奨するギアカテゴリ（防寒具レベル等）を返す。
            Equipment Matching Agent と連携して最適な装備を推奨する際に使用する。
            """)
    public SkiFeasibilityResult assessSkiFeasibility(
            @ToolParam(description = "場所の名称、例: Niseko, Hokkaido, Japan") String location,
            @ToolParam(description = "日付（ISO-8601形式、例: 2026-12-25）。省略時は今日", required = false) @Nullable String date) {
        log.info("Tool assessSkiFeasibility called: location={}, date={}", location, date);
        CurrentWeatherData current = openMeteoClient.getCurrentWeather(location, "celsius");
        SkiConditionsData conditions = openMeteoClient.getSkiConditions(location);
        return evaluateFeasibility(current, conditions);
    }

    private SkiFeasibilityResult evaluateFeasibility(CurrentWeatherData current, SkiConditionsData conditions) {
        // 気温・積雪・視界・風速に基づいてスコアリング
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

        // overallCondition は Pricing Agent が使用する EXCELLENT/GOOD/POOR スケールにマッピング
        String overall = score >= 60 ? "EXCELLENT" : score >= 35 ? "GOOD" : "POOR";
        return new SkiFeasibilityResult(feasibility, score, gearLevel, conditions.overallCondition(), overall);
    }
}
```

> **重要 — `SkiFeasibilityResult` の位置**: 本型は Equipment Matching / Dynamic Pricing / Orchestrator からも参照されるため `agent-common/dto/SkiFeasibilityResult.java` にトップレベル record として配置する。定義は以下の通り:
>
> ```java
> // agent-common/src/main/java/com/example/skishop/agent/common/dto/SkiFeasibilityResult.java
> package com.example.skishop.agent.common.dto;
>
> public record SkiFeasibilityResult(
>         String feasibility,             // HIGH / MEDIUM / LOW
>         int score,                      // 0〜100
>         String recommendedGearLevel,    // EXTREME_COLD / COLD / MODERATE
>         String snowCondition,           // POWDER / GROOMED / ICY / SLUSH 等
>         String overallCondition         // EXCELLENT / GOOD / POOR（Dynamic Pricing が価格調整に使用）
> ) {}
> ```

---

### 4.3 WeatherAgentService（エージェントロジック）

`ChatClient` を使い、自然言語の質問に対して `WeatherToolService` の Tools を活用しながら回答を生成する。

```java
// service/WeatherAgentService.java
package com.example.skishop.agent.weather.service;

import com.example.skishop.agent.common.dto.*;
import com.example.skishop.agent.weather.tool.WeatherToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;

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
            @org.springframework.beans.factory.annotation.Qualifier("weatherAgentChatClient") ChatClient chatClient,
            WeatherToolService weatherToolService) {
        this.chatClient = chatClient;
        this.weatherToolService = weatherToolService;
    }

    /**
     * 自然言語の質問に対して気象分析を行い、構造化レスポンスを返す。
     * オーケストレーターからは REST API 経由（方法B）で呼び出される。
     */
    public WeatherAgentResponse analyze(WeatherAgentRequest request) {
        log.info("Weather Agent analyze: location={}, question={}", request.location(), request.question());

        // ChatClient に WeatherToolService の @Tool メソッドを登録して呼び出す
        // Framework-Controlled Tool Execution により、GPT が自律的に必要なツールを選択・実行する
        String userPrompt = buildUserPrompt(request);

        String aiSummary = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .tools(weatherToolService)   // @Tool アノテーション付きメソッドを全て登録
                .call()
                .content();

        // 構造化データは個別に取得してレスポンスに組み込む
        CurrentWeatherData current = weatherToolService.getCurrentWeather(request.location(), request.unit());
        WeatherForecastData forecast = weatherToolService.getWeatherForecast(request.location(), request.forecastDays());
        SkiConditionsData skiConditions = weatherToolService.getSkiConditions(request.location());
        var alerts = weatherToolService.getWeatherAlerts(request.location());
        var feasibility = weatherToolService.assessSkiFeasibility(request.location(), null);

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

    /**
     * オーケストレーター向け: ToolCallback として提供するためのシンプルな文字列回答。
     * オーケストレーターが ChatClient#tools() で WeatherToolService を直接登録する場合、
     * このメソッドは不要。ただし REST 経由の統合テストに有用。
     */
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
```

---

### 4.4 WeatherAgentConfig（Spring Bean 設定）

```java
// config/WeatherAgentConfig.java
package com.example.skishop.agent.weather.config;

import com.example.skishop.agent.weather.tool.WeatherToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WeatherAgentConfig {

    /**
     * WeatherAgent 専用の ChatClient Bean。
     * デフォルトシステムプロンプトを設定しない（Service 側で制御するため）。
     * モノリス時の Bean 衰突回避のため Agent 名プレフィックスを付与する。
     */
    @Bean("weatherAgentChatClient")
    public ChatClient weatherAgentChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }

    /**
     * オーケストレーターが Weather Agent の全 Tool を一括登録するための ToolCallback[] Bean。
     */
    @Bean("weatherAgentToolCallbacks")
    public ToolCallback[] weatherAgentToolCallbacks(WeatherToolService weatherToolService) {
        return ToolCallbacks.from(weatherToolService);
    }
}
```

---

### 4.5 OpenMeteoClient（外部 API クライアント）

Open-Meteo API は無料・APIキー不要で、スキーに必要な積雪・降雪データを提供する。

```java
// client/OpenMeteoClient.java
package com.example.skishop.agent.weather.client;

import com.example.skishop.agent.common.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Open-Meteo API クライアント。
 * https://open-meteo.com/ - 無料・APIキー不要の気象データ API。
 *
 * 2ステップ処理:
 * 1. Geocoding API で地名 → 緯度・経度に変換
 * 2. Forecast API で気象データを取得
 */
@Component
public class OpenMeteoClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

    private final RestClient geocodingClient;
    private final RestClient forecastClient;

    public OpenMeteoClient() {
        this.geocodingClient = RestClient.builder()
                .baseUrl("https://geocoding-api.open-meteo.com/v1")
                .build();
        this.forecastClient = RestClient.builder()
                .baseUrl("https://api.open-meteo.com/v1")
                .build();
    }

    public CurrentWeatherData getCurrentWeather(String location, String unit) {
        GeoLocation geo = geocode(location);
        log.debug("Fetching current weather for {} ({},{})", location, geo.latitude(), geo.longitude());

        // Open-Meteo current_weather + hourly snow data を取得
        @SuppressWarnings("unchecked")
        Map<String, Object> response = forecastClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/forecast")
                        .queryParam("latitude", geo.latitude())
                        .queryParam("longitude", geo.longitude())
                        .queryParam("current", "temperature_2m,apparent_temperature,relative_humidity_2m," +
                                "wind_speed_10m,wind_direction_10m,visibility,weather_code,snowfall")
                        .queryParam("temperature_unit", "celsius".equals(unit) ? "celsius" : "fahrenheit")
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
                        .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum," +
                                "snowfall_sum,snow_depth_max,wind_speed_10m_max,weather_code,uv_index_max")
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
                        .queryParam("daily", "snowfall_sum,snow_depth_max,visibility_10m_max,wind_speed_10m_max")
                        .queryParam("hourly", "snow_depth,snowfall,visibility,wind_speed_10m")
                        .queryParam("forecast_days", 3)
                        .queryParam("timezone", "Asia/Tokyo")
                        .build())
                .retrieve()
                .body(Map.class);

        return parseSkiConditions(response);
    }

    public List<WeatherAlertData> getWeatherAlerts(String location) {
        // Open-Meteo は警報 API を提供しないため、気象データから閾値ベースで判定
        GeoLocation geo = geocode(location);
        CurrentWeatherData current = getCurrentWeather(location, "celsius");
        return generateAlertsFromData(current);
    }

    private GeoLocation geocode(String locationName) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = geocodingClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("name", locationName)
                        .queryParam("count", 1)
                        .queryParam("language", "ja")
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .body(Map.class);

        @SuppressWarnings("unchecked")
        var results = (List<Map<String, Object>>) response.get("results");
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("場所が見つかりません: " + locationName);
        }
        var first = results.get(0);
        return new GeoLocation(
                ((Number) first.get("latitude")).doubleValue(),
                ((Number) first.get("longitude")).doubleValue(),
                (String) first.get("name"),
                (String) first.get("country"));
    }

    // --- パース処理（実装省略: Map から DTO にマッピング） ---
    private CurrentWeatherData parseCurrentWeather(Map<String, Object> response) { /* 実装 */ return null; }
    private WeatherForecastData parseForecast(Map<String, Object> response) { /* 実装 */ return null; }
    private SkiConditionsData parseSkiConditions(Map<String, Object> response) { /* 実装 */ return null; }
    private List<WeatherAlertData> generateAlertsFromData(CurrentWeatherData current) { /* 実装 */ return List.of(); }

    private record GeoLocation(double latitude, double longitude, String name, String country) {}
}
```

---

### 4.6 WeatherAgentController（REST API 方法B）

```java
// controller/WeatherAgentController.java
package com.example.skishop.agent.weather.controller;

import com.example.skishop.agent.common.dto.WeatherAgentRequest;
import com.example.skishop.agent.common.dto.WeatherAgentResponse;
import com.example.skishop.agent.common.dto.CurrentWeatherData;
import com.example.skishop.agent.common.dto.SkiConditionsData;
import com.example.skishop.agent.weather.service.WeatherAgentService;
import com.example.skishop.agent.weather.tool.WeatherToolService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    /**
     * フル分析: 自然言語の質問に対してAIが気象ツールを活用して回答する。
     * 他エージェント・フロントエンドから利用。
     */
    @PostMapping("/analyze")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<WeatherAgentResponse> analyze(
            @Valid @RequestBody WeatherAgentRequest request) {
        return ResponseEntity.ok(weatherAgentService.analyze(request));
    }

    /**
     * 現在の気象データ取得（ツール直接呼び出し）。
     * オーケストレーターや他 Agent が軽量呼び出しで使用。
     */
    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT', 'USER')")
    public ResponseEntity<CurrentWeatherData> getCurrentWeather(
            @RequestParam String location,
            @RequestParam(defaultValue = "celsius") String unit) {
        return ResponseEntity.ok(weatherToolService.getCurrentWeather(location, unit));
    }

    /**
     * スキーコンディション取得。
     * 在庫管理・ダイナミックプライシング Agent からも利用される。
     */
    @GetMapping("/ski-conditions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT', 'USER')")
    public ResponseEntity<SkiConditionsData> getSkiConditions(
            @RequestParam String resort) {
        return ResponseEntity.ok(weatherToolService.getSkiConditions(resort));
    }

    /**
     * スキー適性評価（スコア付き）。
     * Equipment Matching Agent が装備推奨時に呼び出す。
     */
    @GetMapping("/feasibility")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT', 'USER')")
    public ResponseEntity<SkiFeasibilityResult> getFeasibility(
            @RequestParam String location,
            @RequestParam(required = false) String date) {
        return ResponseEntity.ok(weatherToolService.assessSkiFeasibility(location, date));
    }
}
```

---

### 4.7 SecurityConfig

> **ハイブリッド規約**: 本 `SecurityConfig` は **分散モードのみ有効化**される。モノリス時は `agent-runtime-monolith` 側の単一 SecurityConfig が全 Agent のリクエストを一括処理する。

```java
// config/WeatherAgentSecurityConfig.java
package com.example.skishop.agent.weather.config;

import com.example.skishop.common.security.InternalApiKeyAuthenticationFilter;
import com.example.skishop.common.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "distributed")
public class WeatherAgentSecurityConfig {

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            @Value("${jwt.secret}") String jwtSecret) {
        return new JwtAuthenticationFilter(jwtSecret);
    }

    @Bean
    public InternalApiKeyAuthenticationFilter internalApiKeyAuthenticationFilter(
            @Value("${services.internal-api-key:}") String apiKey) {
        return new InternalApiKeyAuthenticationFilter(apiKey);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtFilter,
                                            InternalApiKeyAuthenticationFilter internalFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(internalFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

### 4.8 WeatherAgentAutoConfiguration

library jar を standalone / monolith どちらからも取り込めるよう `@AutoConfiguration` を提供する。`@ComponentScan` は使わず `@Import` で Bean を明示取り込み。

```java
// config/WeatherAgentAutoConfiguration.java
package com.example.skishop.agent.weather.config;

import com.example.skishop.agent.weather.client.OpenMeteoClient;
import com.example.skishop.agent.weather.controller.WeatherAgentController;
import com.example.skishop.agent.weather.invoker.LocalWeatherInvoker;
import com.example.skishop.agent.weather.service.WeatherAgentService;
import com.example.skishop.agent.weather.tool.WeatherToolService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    WeatherToolService.class,
    WeatherAgentService.class,
    OpenMeteoClient.class,
    WeatherAgentConfig.class,
    WeatherAgentController.class,
    WeatherAgentSecurityConfig.class,
    LocalWeatherInvoker.class           // モノリス時の Worker→Worker 抽象 (@ConditionalOnProperty で評価)
})
public class WeatherAgentAutoConfiguration {
}
```

登録ファイル: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
com.example.skishop.agent.weather.config.WeatherAgentAutoConfiguration
```

### 4.9 LocalWeatherInvoker（Worker→Worker モノリス用）

`agent-common.invoker.WeatherInvoker` の **モノリス時実装**。Equipment Matching / Dynamic Pricing からは `WeatherInvoker` 型で注入される（実装の選択は `@ConditionalOnProperty` で行われる）。

```java
// invoker/LocalWeatherInvoker.java
package com.example.skishop.agent.weather.invoker;

import com.example.skishop.agent.common.dto.SkiFeasibilityResult;
import com.example.skishop.agent.common.invoker.WeatherInvoker;
import com.example.skishop.agent.weather.service.WeatherAgentService;
import com.example.skishop.agent.weather.tool.WeatherToolService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "monolith", matchIfMissing = true)
@ConditionalOnBean(WeatherAgentService.class)
public class LocalWeatherInvoker implements WeatherInvoker {

    private final WeatherToolService toolService;

    public LocalWeatherInvoker(WeatherToolService toolService) {
        this.toolService = toolService;
    }

    @Override
    public SkiFeasibilityResult getFeasibility(String location) {
        return toolService.assessSkiFeasibility(location, null);
    }
    // getOverallCondition は WeatherInvoker の default 実装を利用
}
```

---

## 5. application.properties

> **ハイブリッド規約**: 本ファイルは **分散モード（`weather-standalone`）用テンプレート**である。モノリスモードでは `agent-runtime-monolith/src/main/resources/application.yml`（[apply-existing-services.md §6](apply-existing-services.md)）に統合される。library jar に同梱してはならない。

```properties
# ===================================================
# Weather Agent Service 設定（standalone 用テンプレート）
# ===================================================
spring.application.name=weather-agent
server.port=8100

# ハイブリッドモード: standalone 起動時は distributed、
agents.deployment.mode=distributed
agents.web.enabled=true

# --- Azure OpenAI (ai-support-service と共用設定) ---
spring.ai.azure.openai.api-key=${AZURE_OPENAI_API_KEY:}
spring.ai.azure.openai.endpoint=${AZURE_OPENAI_ENDPOINT:}
spring.ai.azure.openai.chat.options.deployment-name=${AZURE_OPENAI_DEPLOYMENT:gpt-4o}
spring.ai.azure.openai.chat.options.temperature=0.3
spring.ai.azure.openai.chat.options.max-completion-tokens=2048

# --- Spring AI リトライ設定 ---
spring.ai.retry.max-attempts=3
spring.ai.retry.backoff.initial-interval=1000
spring.ai.retry.backoff.multiplier=2

# --- Tool 呼び出しエラーハンドリング ---
# false: ツールエラーをモデルにフィードバック（デフォルト）
spring.ai.tools.throw-exception-on-error=false

# --- Redis キャッシュ（天気データを5分間キャッシュ） ---
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.cache.type=redis
spring.cache.redis.time-to-live=300000

# --- JWT 認証（common-lib と共通） ---
jwt.secret=${JWT_SECRET:}

# --- Actuator ---
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.show-details=always

# --- Spring AI ロギング（デバッグ時） ---
logging.level.org.springframework.ai=INFO
# logging.level.org.springframework.ai=DEBUG  # ツール呼び出しログを有効化する場合
```

---

## 6. シーケンス図

### 6.1 オーケストレーターからの ToolCallback 経由呼び出し（方法A）

```
Orchestrator Agent
    │
    │  chatClient.prompt()
    │      .user("苗場の今週末のスキーコンディションと必要な装備は？")
    │      .toolCallbacks(weatherAgentToolCallbacks)     ← WeatherToolService の全 @Tool
    │      .toolCallbacks(equipmentAgentToolCallbacks)   ← Equipment Agent の @Tool
    │      .call()
    │
    ▼
Azure OpenAI (GPT-4o)
    │
    │  [Tool Call Request] getSkiConditions("Naeba, Niigata, Japan")
    │
    ▼
WeatherToolService#getSkiConditions()
    │
    ▼
OpenMeteoClient → Open-Meteo API
    │
    ▼
GPT-4o が結果を統合して回答生成
```

### 6.2 REST API 経由呼び出し（方法B）

```
Client (Frontend / 他サービス)
    │
    │  POST /api/v1/agents/weather/analyze
    │  { "location": "Hakuba, Nagano, Japan",
    │    "question": "今週末スキーに行けますか？" }
    │
    ▼
WeatherAgentController
    │
    ▼
WeatherAgentService#analyze()
    │
    ├─ chatClient.tools(weatherToolService).call() ──→ GPT-4o
    │       │                                              │
    │       │ [Tool Call] getCurrentWeather()              │
    │       │ [Tool Call] getWeatherForecast()             │
    │       │ [Tool Call] getSkiConditions()               │
    │       │ [Tool Call] getWeatherAlerts()               │
    │       │ [Tool Call] assessSkiFeasibility()           │
    │       ▼                                              │
    │   OpenMeteoClient → Open-Meteo API              AI Summary
    │
    └─ WeatherAgentResponse（構造化 + aiSummary）
```

---

## 7. キャッシュ戦略

| キャッシュ名 | TTL | キー | 説明 |
|------------|-----|------|------|
| `currentWeather` | 5分 | `{location}:{unit}` | 現在の気象データ |
| `weatherForecast` | 30分 | `{location}:{days}` | 予報データ（頻繁な更新不要） |
| `skiConditions` | 10分 | `{resortLocation}` | スキーコンディション |

Tool 呼び出しが頻発する場合（特にオーケストレーターが複数エージェントを並列起動する場合）に、外部 API のレートリミットと遅延を軽減する。

---

## 8. テスト設計

### 8.1 単体テスト（WeatherToolService）

```java
// WeatherToolServiceTest.java（抜粋）
@ExtendWith(MockitoExtension.class)
class WeatherToolServiceTest {

    @Mock OpenMeteoClient openMeteoClient;
    @InjectMocks WeatherToolService weatherToolService;

    @Test
    @DisplayName("assessSkiFeasibility: 積雪50cm以上・視界5km以上でHIGH判定")
    void should_returnHighFeasibility_when_excellentConditions() {
        var mockCurrent = new CurrentWeatherData(-8.0, -12.0, 70.0, 20.0,
                "N", 6.0, "71", "大雪", true, 15.0);
        var mockConditions = new SkiConditionsData(80.0, 20.0, 5.0,
                "POWDER", true, "EXCELLENT", "GROOMED", 6.0, "LOW");

        when(openMeteoClient.getCurrentWeather("Naeba, Japan", "celsius")).thenReturn(mockCurrent);
        when(openMeteoClient.getSkiConditions("Naeba, Japan")).thenReturn(mockConditions);

        var result = weatherToolService.assessSkiFeasibility("Naeba, Japan", null);

        assertThat(result.feasibility()).isEqualTo("HIGH");
        assertThat(result.recommendedGearLevel()).isEqualTo("COLD");
    }
}
```

### 8.2 統合テスト（WeatherAgentController）

```java
@WebMvcTest(WeatherAgentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=test-secret-key-for-testing-only-minimum-256-bits-required-here")
class WeatherAgentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean WeatherAgentService weatherAgentService;
    @MockitoBean WeatherToolService weatherToolService;

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /ski-conditions でスキーコンディションを取得できる")
    void should_returnSkiConditions_when_validResort() throws Exception {
        var mockConditions = new SkiConditionsData(75.0, 15.0, 3.0, "POWDER",
                true, "EXCELLENT", "GROOMED", 5.0, "LOW");
        when(weatherToolService.getSkiConditions("Naeba")).thenReturn(mockConditions);

        mockMvc.perform(get("/api/v1/agents/weather/ski-conditions")
                        .param("resort", "Naeba"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snowDepthCm").value(75.0))
                .andExpect(jsonPath("$.snowQuality").value("POWDER"));
    }
}
```

---

## 9. 他エージェントとの連携設計

### 9.1 Equipment Matching Agent との連携

> **注**: 以下は \u65e7\u8a2d\u8a08\u306e\u30a4\u30e1\u30fc\u30b8\u3068\u3057\u3066\u6b8b\u3059\u3002\u5b9f\u88c5\u3067\u306f [equipment-matching-agent-design.md \u00a74.7](equipment-matching-agent-design.md) \u306e `RemoteWeatherInvoker`\uff08\u5206\u6563\u6642\uff09\u307e\u305f\u306f\u540c JVM \u5185\u306e `LocalWeatherInvoker` Bean\uff08\u30e2\u30ce\u30ea\u30b9\u6642\uff09\u3092\u4ecb\u3057\u3066\u547c\u3076\u3002

```java
// \u53c2\u8003\uff08\u65e7\u30a4\u30e1\u30fc\u30b8\uff09: Equipment Agent \u3067\u306e\u4f7f\u7528\u4f8b
@Tool(description = "指定場所のスキー適性評価を Weather Agent から取得する")
public SkiFeasibilityResult getWeatherFeasibility(String location) {
    return restClient.get()
            .uri("/api/v1/agents/weather/feasibility?location=" + location)
            .retrieve()
            .body(SkiFeasibilityResult.class);
}
```

### 9.2 オーケストレーターでの統合（将来実装イメージ）

```java
// orchestrator（将来実装）での使用例
@Service
public class SkiAdvisorOrchestrator {

    private final ChatClient chatClient;
    private final ToolCallback[] weatherAgentToolCallbacks;     // Weather Agent の Tools
    private final ToolCallback[] equipmentAgentToolCallbacks;   // Equipment Agent の Tools

    public String advise(String userRequest) {
        return chatClient.prompt()
                .system("あなたはスキーアドバイザーです。天気と装備の両エージェントを活用して最適な提案をしてください。")
                .user(userRequest)
                .toolCallbacks(weatherAgentToolCallbacks)
                .toolCallbacks(equipmentAgentToolCallbacks)
                .call()
                .content();
    }
}
```

---

## 10. セキュリティ考慮事項（OWASP Top 10）

| リスク | 対策 |
|--------|------|
| A01 Broken Access Control | `@PreAuthorize` で全エンドポイントにロール検証を適用 |
| A02 Cryptographic Failures | API キーを環境変数で管理し、ハードコード禁止 |
| A03 Injection | `@ToolParam` の `location` 引数はジオコーディング API に渡す前にサニタイズ（英数字・カンマ・スペースのみ許可） |
| A05 Security Misconfiguration | ステートレス JWT 認証、CSRF 無効化（API 専用サービスのため） |
| A09 Logging Failures | SLF4J で構造化ログを記録。`location` 等の入力値のみログ出力し、API キー等の秘密情報は禁止 |

---

## 11. 実装フェーズ計画

| フェーズ | 作業内容 | 優先度 |
|---------|---------|--------|
| Phase 1 | `OpenMeteoClient` + `WeatherToolService` の `@Tool` 4本実装 | 高 |
| Phase 2 | `WeatherAgentService`（ChatClient 統合）実装 | 高 |
| Phase 3 | `WeatherAgentController`（REST API）実装 | 高 |
| Phase 4 | Redis キャッシュ統合 | 中 |
| Phase 5 | `WeatherAgentConfig`の `ToolCallback[]` Bean 公開 | 中（オーケストレーター実装前提） |
| Phase 6 | `AzureMapsWeatherClient`（警報 API）統合 | 低 |

---

## 12. 参照リソース

- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI Agentic Patterns - Orchestrator-Workers](https://github.com/spring-projects/spring-ai-examples/tree/main/agentic-patterns/orchestrator-workers)
- [Open-Meteo API ドキュメント](https://open-meteo.com/en/docs)
- [Open-Meteo Geocoding API](https://open-meteo.com/en/docs/geocoding-api)
- [WMO Weather Interpretation Codes](https://open-meteo.com/en/docs#weathervariables)
