# Multi-Agent System ハイブリッド・デプロイメント設計書

## 1. 概要

### 1.1 目的

本書は、Multi-Agent System を **モジュラモノリス（デフォルト）** で動作させつつ、必要に応じて **個別の独立 Spring Boot サービスに分離可能** なハイブリッド構成を定義する。

すべての Agent 詳細設計書（[weather-agent-design.md](weather-agent-design.md), [customer-intent-agent-design.md](customer-intent-agent-design.md), [equipment-matching-agent-design.md](equipment-matching-agent-design.md), [inventory-monitoring-agent-design.md](inventory-monitoring-agent-design.md), [dynamic-pricing-agent-design.md](dynamic-pricing-agent-design.md), [coupon-optimization-agent-design.md](coupon-optimization-agent-design.md), [orchestrator-agent-design.md](orchestrator-agent-design.md)）は、本書に定義された規約に従う。

### 1.2 採用方針

- **デフォルト（推奨）**: 全 7 Agent を 1 つの Spring Boot アプリ（`agent-runtime-monolith`）に同梱して 1 プロセスでデプロイ
- **オプション**: 高負荷・障害分離が必要になった Agent のみ独立 Spring Boot アプリ（`*-standalone`）として個別デプロイ
- **コードベース**: 1 リポジトリ・1 Maven マルチモジュール構成。モジュール境界は維持し、将来の分離を阻害しない

### 1.3 デプロイモード

`agents.deployment.mode` プロパティで切替可能（デフォルト: `monolith`）。

| モード | 値 | プロセス数 | Worker 呼び出し方式 | 認証 |
|-------|---|---------|-------------------|------|
| **モノリス** | `monolith`（デフォルト） | 1 | Bean 直接注入（in-JVM） | 不要 |
| **分散** | `distributed` | 7（または部分分離） | REST + `X-Internal-Api-Key` | 必須 |

---

## 2. Maven モジュール構成

### 2.1 全体構造

```
ai-agent-services/
├── pom.xml                              ← 親 POM（packaging=pom）
├── agent-common/                        ← 全 Agent 共有 DTO・抽象
│   ├── pom.xml                          ← packaging=jar
│   └── src/main/java/com/example/skishop/agent/common/
│       ├── dto/                         ← 共有 DTO
│       │   ├── CustomerIntentRequest.java
│       │   ├── CustomerIntentResult.java
│       │   ├── WeatherAgentRequest.java
│       │   ├── WeatherAgentResponse.java
│       │   ├── SkiConditionsData.java
│       │   ├── SkiFeasibilityResult.java
│       │   ├── EquipmentMatchRequest.java
│       │   ├── EquipmentMatchResult.java
│       │   ├── InventoryCheckRequest.java
│       │   ├── InventoryStatus.java
│       │   ├── ReservationRequest.java
│       │   ├── ReservationResult.java
│       │   ├── BulkPricingRequest.java
│       │   ├── PricingResult.java
│       │   ├── CouponOptimizationRequest.java
│       │   └── CouponOptimizationResult.java
│       ├── invoker/
│       │   └── WeatherInvoker.java      ← Worker→Worker 共有抽象
│       └── config/
│           └── AgentDeploymentProperties.java
│
├── weather-agent/                       ← packaging=jar (Spring Boot library)
├── customer-intent-agent/
├── equipment-matching-agent/
├── inventory-monitoring-agent/
├── dynamic-pricing-agent/
├── coupon-optimization-agent/
├── orchestrator-agent/                  ← WorkerAgentInvoker / Local / Remote 実装を内包
│
├── agent-runtime-monolith/              ← packaging=jar（実行可能 Spring Boot アプリ）
│   ├── pom.xml                          ← 全 Agent モジュールに依存
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/example/skishop/agent/runtime/
│       │   ├── AgentRuntimeApplication.java   ← @SpringBootApplication
│       │   └── config/
│       │       └── MonolithSecurityConfig.java
│       └── resources/
│           └── application.yml
│
└── agent-runtime-standalone/            ← オプション：個別起動アプリ
    ├── weather-standalone/
    ├── customer-intent-standalone/
    ├── equipment-matching-standalone/
    ├── inventory-monitoring-standalone/
    ├── dynamic-pricing-standalone/
    ├── coupon-optimization-standalone/
    └── orchestrator-standalone/
```

### 2.1.1 agent-common モジュールの内容

`agent-common` は **DTO・抽象インターフェース・共通プロパティのみ**を持つ純 library jar。`@SpringBootApplication` も `@Configuration` も持たない（プロパティ Bean の登録は §2.1.2 を参照）。

```java
// agent-common/src/main/java/com/example/skishop/agent/common/config/AgentDeploymentProperties.java
package com.example.skishop.agent.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Multi-Agent System の動作モードを表現するプロパティ。
 * すべての Agent モジュール / Runtime から参照される。
 *
 * 例:
 *   agents.deployment.mode=monolith   # or "distributed"
 *   agents.web.enabled=true
 *
 * @param mode "monolith"（デフォルト） / "distributed"
 * @param web  Controller 公開 ON/OFF
 */
@ConfigurationProperties(prefix = "agents")
public record AgentDeploymentProperties(
        Deployment deployment,
        Web web
) {
    public AgentDeploymentProperties {
        if (deployment == null) deployment = new Deployment("monolith");
        if (web == null)        web = new Web(true);
    }

    public record Deployment(String mode) {
        public Deployment {
            if (mode == null || mode.isBlank()) mode = "monolith";
        }
        public boolean isMonolith()    { return "monolith".equalsIgnoreCase(mode); }
        public boolean isDistributed() { return "distributed".equalsIgnoreCase(mode); }
    }

    public record Web(Boolean enabled) {
        public Web { if (enabled == null) enabled = Boolean.TRUE; }
    }
}
```

### 2.1.2 agent-common AutoConfiguration

`AgentDeploymentProperties` を Bean 登録するため、`agent-common` に最小の AutoConfiguration を追加する。

```java
// agent-common/src/main/java/com/example/skishop/agent/common/config/AgentCommonAutoConfiguration.java
package com.example.skishop.agent.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties(AgentDeploymentProperties.class)
public class AgentCommonAutoConfiguration {
}
```

`agent-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.example.skishop.agent.common.config.AgentCommonAutoConfiguration
```

### 2.2 Agent モジュールの packaging 規約

各 Agent モジュール（`weather-agent` 等）は **Spring Boot library** として作る。

- `packaging=jar`
- `spring-boot-maven-plugin` の `repackage` ゴールは **execution 単位で `<skip>true</skip>`** にする（plugin レベルの `<configuration>` では効かない）。または親 POM の `<pluginManagement>` で plugin を管理し、library モジュールでは `<plugin>` を宣言しない
- `@SpringBootApplication` クラスは **持たない**
- `@Configuration` / `@Service` / `@Component` / `@RestController` / `@Tool` 定義 のみ
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` で `<Name>AgentAutoConfiguration` を登録（standalone 単独利用に備える）

```xml
<!-- 各 Agent モジュールの pom.xml 例 -->
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <executions>
                <execution>
                    <id>repackage</id>
                    <goals><goal>repackage</goal></goals>
                    <configuration><skip>true</skip></configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 2.3 agent-runtime-monolith の構成

全 Agent モジュールを依存に持ち、`@SpringBootApplication` 1 つで起動する。

```xml
<!-- agent-runtime-monolith/pom.xml -->
<dependencies>
    <dependency><groupId>com.example.skishop</groupId><artifactId>agent-common</artifactId></dependency>
    <dependency><groupId>com.example.skishop</groupId><artifactId>weather-agent</artifactId></dependency>
    <dependency><groupId>com.example.skishop</groupId><artifactId>customer-intent-agent</artifactId></dependency>
    <dependency><groupId>com.example.skishop</groupId><artifactId>equipment-matching-agent</artifactId></dependency>
    <dependency><groupId>com.example.skishop</groupId><artifactId>inventory-monitoring-agent</artifactId></dependency>
    <dependency><groupId>com.example.skishop</groupId><artifactId>dynamic-pricing-agent</artifactId></dependency>
    <dependency><groupId>com.example.skishop</groupId><artifactId>coupon-optimization-agent</artifactId></dependency>
    <dependency><groupId>com.example.skishop</groupId><artifactId>orchestrator-agent</artifactId></dependency>
    <dependency><groupId>com.example.skishop</groupId><artifactId>common-lib</artifactId></dependency>
</dependencies>
```

```java
// agent-runtime-monolith/src/main/java/com/example/skishop/agent/runtime/AgentRuntimeApplication.java
package com.example.skishop.agent.runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.example.skishop.agent.common",
        "com.example.skishop.agent.weather",
        "com.example.skishop.agent.intent",
        "com.example.skishop.agent.equipment",
        "com.example.skishop.agent.inventory",
        "com.example.skishop.agent.pricing",
        "com.example.skishop.agent.coupon",
        "com.example.skishop.agent.orchestrator",
        "com.example.skishop.agent.runtime",
        "com.example.skishop.common"            // common-lib（JwtAuthenticationFilter 等）
})
public class AgentRuntimeApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentRuntimeApplication.class, args);
    }
}
```

### 2.4 Standalone モジュールの構成（オプション・分離時のみ）

各 standalone モジュールは `<Name>StandaloneApplication` メインクラスを持ち、`scanBasePackages` は当該 Agent + `agent-common` + `common-lib` のみに限定する。`application.yml` で `agents.deployment.mode=distributed` と当該 Agent のポート（8100〜8106）を設定する。

```java
// weather-standalone/src/main/java/com/example/skishop/agent/weather/standalone/WeatherStandaloneApplication.java
@SpringBootApplication(scanBasePackages = {
        "com.example.skishop.agent.common",
        "com.example.skishop.agent.weather",
        "com.example.skishop.common"
})
public class WeatherStandaloneApplication {
    public static void main(String[] args) {
        SpringApplication.run(WeatherStandaloneApplication.class, args);
    }
}
```

---

## 3. Orchestrator の Worker 呼び出し抽象化

### 3.1 WorkerAgentInvoker インターフェース

Orchestrator は **Worker を直接 Bean として呼び出すか REST 経由で呼び出すかを意識しない**よう、`WorkerAgentInvoker` インターフェースを介して呼び出す。

```java
// orchestrator-agent/src/main/java/com/example/skishop/agent/orchestrator/invoker/WorkerAgentInvoker.java
package com.example.skishop.agent.orchestrator.invoker;

import com.example.skishop.agent.common.dto.*;
import java.util.List;

public interface WorkerAgentInvoker {

    CustomerIntentResult invokeCustomerIntent(String userId, String userMessage, String sessionId);

    WeatherAgentResponse invokeWeather(String location, String resort, Integer forecastDays);

    EquipmentMatchResult invokeEquipmentMatching(EquipmentMatchRequest request);

    List<InventoryStatus> invokeInventoryCheck(List<String> productIds, int requiredQuantity);

    ReservationResult invokeInventoryReservation(ReservationRequest request);

    List<PricingResult> invokeDynamicPricing(BulkPricingRequest request);

    CouponOptimizationResult invokeCouponOptimization(CouponOptimizationRequest request);
}
```

### 3.2 LocalWorkerAgentInvoker（モノリス時のデフォルト）

各 Worker の `*AgentService` Bean を直接注入して呼び出す。**REST/JSON シリアライズ・認証・タイムアウトのオーバーヘッドが一切ない**。

```java
@Component
@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "monolith", matchIfMissing = true)
public class LocalWorkerAgentInvoker implements WorkerAgentInvoker {

    private final CustomerIntentAgentService customerIntent;
    private final WeatherAgentService weather;
    private final EquipmentMatchingAgentService equipmentMatching;
    private final InventoryMonitoringAgentService inventoryMonitoring;
    private final DynamicPricingAgentService dynamicPricing;
    private final CouponOptimizationAgentService couponOptimization;

    // コンストラクタ省略

    @Override
    public CustomerIntentResult invokeCustomerIntent(String userId, String userMessage, String sessionId) {
        return customerIntent.analyze(new CustomerIntentRequest(userId, userMessage, sessionId, "ja"));
    }

    @Override
    public WeatherAgentResponse invokeWeather(String location, String resort, Integer forecastDays) {
        return weather.analyze(new WeatherAgentRequest(location, resort, "celsius", forecastDays));
    }

    // 他の invoke* メソッドも同様に Bean メソッド呼び出しを直接ラップ
}
```

### 3.3 RemoteWorkerAgentInvoker（分散時）

`agents.deployment.mode=distributed` のときに活性化される。既存の `WorkerAgentRestClient` をラップする。

```java
@Component
@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "distributed")
public class RemoteWorkerAgentInvoker implements WorkerAgentInvoker {

    private final WorkerAgentRestClient client;

    public RemoteWorkerAgentInvoker(WorkerAgentRestClient client) {
        this.client = client;
    }

    @Override
    public CustomerIntentResult invokeCustomerIntent(String userId, String userMessage, String sessionId) {
        return client.callCustomerIntentAgent(userId, userMessage, sessionId);
    }
    // 他のメソッドも client.call*() に委譲
}
```

`WorkerAgentRestClient` 自体も `@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "distributed")` で有効化する。

### 3.4 OrchestratorWorkerTools のリファクタ

`@Tool` メソッドは `WorkerAgentInvoker` を介して呼ぶ。これにより、モノリス/分散の切替を Tool 定義側で意識しない。

```java
@Component
public class OrchestratorWorkerTools {

    private final WorkerAgentInvoker invoker;   // ← Local or Remote が注入される

    public OrchestratorWorkerTools(WorkerAgentInvoker invoker) {
        this.invoker = invoker;
    }

    @Tool(description = "顧客の自然言語リクエストを解析し、構造化されたインテント・制約を抽出する")
    public CustomerIntentResult analyzeCustomerIntent(
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "顧客メッセージ") String userMessage,
            @ToolParam(description = "セッション ID") @Nullable String sessionId) {
        return invoker.invokeCustomerIntent(userId, userMessage, sessionId);
    }
    // 他の Tool も同様
}
```

### 3.5 Worker→Worker 呼び出しの抽象化（WeatherInvoker）

`equipment-matching-agent` と `dynamic-pricing-agent` は `weather-agent` を呼び出す。これも同様に抽象化する。

`agent-common` に共有インターフェースを定義:

```java
// agent-common/src/main/java/com/example/skishop/agent/common/invoker/WeatherInvoker.java
package com.example.skishop.agent.common.invoker;

import com.example.skishop.agent.common.dto.SkiFeasibilityResult;

/**
 * Worker→Worker 呼び出し抽象（Equipment Matching / Dynamic Pricing → Weather）。
 * weather-agent モジュールが {@code LocalWeatherInvoker}（モノリス用）を提供し、
 * 各利用側 Agent モジュールが {@code RemoteWeatherInvoker}（分散用、@ConditionalOnProperty(distributed)）を提供する。
 *
 * 注: Orchestrator は本インターフェースではなく {@code WorkerAgentInvoker} を介して
 * Weather Agent を呼ぶため、analyze/getSkiConditions/getCurrent 等の幅広い API は
 * 本抽象には含めない（YAGNI 原則）。新規ユースケースで必要になった時点で追加する。
 */
public interface WeatherInvoker {

    /** スキー適性評価（spec score・装備推奨レベル・気象総合スコア）を返す。Equipment Matching が使用。 */
    SkiFeasibilityResult getFeasibility(String location);

    /**
     * 気象総合判定（EXCELLENT/GOOD/POOR）。Dynamic Pricing が価格係数決定に使用。
     * デフォルト実装は {@link #getFeasibility(String)} の {@code overallCondition} を返す。
     */
    default String getOverallCondition(String location) {
        return getFeasibility(location).overallCondition();
    }
}
```

`weather-agent` モジュールに `LocalWeatherInvoker` を配置（モノリス用）:

```java
@Component
@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "monolith", matchIfMissing = true)
@ConditionalOnBean(WeatherAgentService.class)
public class LocalWeatherInvoker implements WeatherInvoker {
    private final WeatherAgentService service;
    // 委譲
}
```

`equipment-matching-agent` / `dynamic-pricing-agent` 各モジュールに `RemoteWeatherInvoker` を配置（分散用）:

```java
@Component
@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "distributed")
public class RemoteWeatherInvoker implements WeatherInvoker {
    private final WeatherAgentClient client;  // 既存の RestClient ラッパー
    // 委譲
}
```

`*ToolService` は `WeatherAgentClient` への直接依存を `WeatherInvoker` に置換する。

---

## 4. 各 Worker Agent の構造規約

各 Agent モジュールは以下の構造に従う。

```
weather-agent/
├── pom.xml
└── src/main/
    ├── java/com/example/skishop/agent/weather/
    │   ├── tool/
    │   │   └── WeatherToolService.java
    │   ├── service/
    │   │   └── WeatherAgentService.java
    │   ├── client/
    │   │   └── OpenMeteoClient.java
    │   ├── controller/
    │   │   └── WeatherAgentController.java     ← @ConditionalOnProperty(agents.web.enabled)
    │   ├── config/
    │   │   ├── WeatherAgentAutoConfiguration.java  ← @AutoConfiguration
    │   │   ├── WeatherAgentConfig.java             ← ChatClient/ToolCallback Bean（プレフィックス命名）
    │   │   └── WeatherAgentSecurityConfig.java     ← @ConditionalOnProperty(distributed)
    │   └── invoker/
    │       └── LocalWeatherInvoker.java        ← weather-agent のみ実装（他 Agent から共有利用）
    └── resources/
        └── META-INF/spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 4.1 Controller の条件付き有効化

```java
@RestController
@RequestMapping("/api/v1/agents/weather")
@ConditionalOnProperty(name = "agents.web.enabled", havingValue = "true", matchIfMissing = true)
public class WeatherAgentController {
    // ...
}
```

モノリス時はデフォルトで有効（管理用に各 Worker REST も同居公開）。分散時の standalone モジュールでも有効。Tool 呼び出しのみに限定したい場合は `agents.web.enabled=false` で無効化可能。

### 4.2 SecurityConfig の条件付き有効化

各 Worker の `SecurityConfig` は **分散モードのみ** 有効化。

```java
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "distributed")
public class WeatherAgentSecurityConfig {
    // InternalApiKeyAuthenticationFilter を登録
}
```

モノリス時は `agent-runtime-monolith` 側の単一 `MonolithSecurityConfig` が全リクエストを処理:
- `/api/v1/orchestrator/recommend` → JWT 認証
- `/api/v1/agents/**` → `permitAll()` または `hasRole('ADMIN')`（Worker REST は管理用）
- `/actuator/health`, `/actuator/info` → permitAll

### 4.3 ChatClient / RestClient Bean 命名規約（重要）

モノリス時は 7 Agent の `@Configuration` が同居するため、**Bean 名衝突を避けるため Agent 名プレフィックスを必須**とする。

```java
@Configuration
public class WeatherAgentConfig {

    @Bean("weatherAgentChatClient")
    public ChatClient weatherAgentChatClient(ChatClient.Builder builder, WeatherToolService tools) {
        return builder
                .defaultSystem("You are a weather assistant...")
                .defaultToolCallbacks(ToolCallbacks.from(tools))
                .build();
    }

    @Bean("weatherAgentToolCallbacks")
    public ToolCallback[] weatherAgentToolCallbacks(WeatherToolService tools) {
        return ToolCallbacks.from(tools);
    }

    @Bean("weatherOpenMeteoRestClient")
    public RestClient weatherOpenMeteoRestClient() {
        return RestClient.builder().baseUrl("https://api.open-meteo.com").build();
    }
}
```

`*AgentService` クラスでは `@Qualifier` で対応 ChatClient を注入:

```java
@Service
public class WeatherAgentService {
    public WeatherAgentService(@Qualifier("weatherAgentChatClient") ChatClient chatClient,
                               WeatherToolService tools) { ... }
}
```

### 4.4 AutoConfiguration の作成方法

`@AutoConfiguration` には `@ComponentScan` を **付けない**（オートコンフィグはユーザーパッケージを汚さないのが Spring Boot 推奨）。`@Import` で明示的に Bean を取り込む。

```java
// weather-agent/src/main/java/com/example/skishop/agent/weather/config/WeatherAgentAutoConfiguration.java
@AutoConfiguration
@Import({
    WeatherToolService.class,
    WeatherAgentService.class,
    OpenMeteoClient.class,
    WeatherAgentConfig.class,           // ChatClient/ToolCallback Bean 定義
    LocalWeatherInvoker.class           // モノリス時のみ有効化される
})
public class WeatherAgentAutoConfiguration {
}
```

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` に FQCN を 1 行で記載。

**併用時の注意**: モノリス側 `@SpringBootApplication(scanBasePackages = "com.example.skishop.agent")` で全 Agent をスキャンする方式と AutoConfiguration を併用する場合、Bean 重複登録を避けるため Bean 定義に `@ConditionalOnMissingBean` を付与する。

### 4.5 application.properties の扱い

各 Worker モジュールは `application.properties` を **持たない**。プロパティのデフォルトは `@ConfigurationProperties` クラスで定義し、最終値は `agent-runtime-monolith` または `*-standalone` の `application.yml` で与える。

例外: 開発時の自己完結テスト用に `src/test/resources/application-test.properties` を置くのは可。

各 Agent 詳細設計書 §5 に記載されている `application.properties` 内容は、**standalone モジュール用テンプレート** として扱う。

---

## 5. ポート割り当て

| モード | ポート構成 |
|-------|----------|
| **モノリス（デフォルト）** | `agent-runtime-monolith`: **8100** のみ。Orchestrator の `/api/v1/orchestrator/recommend` も 8100 |
| **分散** | weather=8100, customer-intent=8101, equipment=8102, inventory=8103, pricing=8104, coupon=8105, orchestrator=8106 |

---

## 6. デプロイメント構成

### 6.1 docker-compose（モノリス・デフォルト）

```yaml
services:
  agent-runtime:
    build: ./ai-agent-services/agent-runtime-monolith
    ports:
      - "8100:8100"
    environment:
      AZURE_OPENAI_API_KEY: ${AZURE_OPENAI_API_KEY}
      AZURE_OPENAI_ENDPOINT: ${AZURE_OPENAI_ENDPOINT}
      AZURE_OPENAI_DEPLOYMENT: gpt-4o
      JWT_SECRET: ${JWT_SECRET}
      INTERNAL_API_KEY: ${INTERNAL_API_KEY}
      USER_MANAGEMENT_SERVICE_URL: http://user-management-service:8081
      INVENTORY_MANAGEMENT_SERVICE_URL: http://inventory-management-service:8082
      SALES_MANAGEMENT_SERVICE_URL: http://sales-management-service:8083
      PAYMENT_CART_SERVICE_URL: http://payment-cart-service:8084
      POINT_SERVICE_URL: http://point-service:8085
      COUPON_SERVICE_URL: http://coupon-service:8088
      # AGENTS_DEPLOYMENT_MODE: monolith  ← デフォルトなので省略可
    depends_on:
      - user-management-service
      - inventory-management-service
      - payment-cart-service
      - coupon-service
      - point-service
```

### 6.2 docker-compose.distributed.yml（オプション・分散）

```yaml
services:
  weather-agent:
    build: ./ai-agent-services/agent-runtime-standalone/weather-standalone
    ports: ["8100:8100"]
    environment:
      AGENTS_DEPLOYMENT_MODE: distributed
      INTERNAL_API_KEY: ${INTERNAL_API_KEY}
      AZURE_OPENAI_API_KEY: ${AZURE_OPENAI_API_KEY}
      AZURE_OPENAI_ENDPOINT: ${AZURE_OPENAI_ENDPOINT}

  customer-intent-agent:
    build: ./ai-agent-services/agent-runtime-standalone/customer-intent-standalone
    ports: ["8101:8101"]
    environment:
      AGENTS_DEPLOYMENT_MODE: distributed
      INTERNAL_API_KEY: ${INTERNAL_API_KEY}
      USER_MANAGEMENT_SERVICE_URL: http://user-management-service:8081

  # ... equipment-matching:8102, inventory-monitoring:8103,
  #     dynamic-pricing:8104, coupon-optimization:8105 を同様に定義

  orchestrator-agent:
    build: ./ai-agent-services/agent-runtime-standalone/orchestrator-standalone
    ports: ["8106:8106"]
    environment:
      AGENTS_DEPLOYMENT_MODE: distributed
      INTERNAL_API_KEY: ${INTERNAL_API_KEY}
      WEATHER_AGENT_URL: http://weather-agent:8100
      CUSTOMER_INTENT_AGENT_URL: http://customer-intent-agent:8101
      EQUIPMENT_MATCHING_AGENT_URL: http://equipment-matching-agent:8102
      INVENTORY_MONITORING_AGENT_URL: http://inventory-monitoring-agent:8103
      DYNAMIC_PRICING_AGENT_URL: http://dynamic-pricing-agent:8104
      COUPON_OPTIMIZATION_AGENT_URL: http://coupon-optimization-agent:8105
      USER_MANAGEMENT_SERVICE_URL: http://user-management-service:8081
      PAYMENT_CART_SERVICE_URL: http://payment-cart-service:8084
    depends_on:
      - weather-agent
      - customer-intent-agent
      - equipment-matching-agent
      - inventory-monitoring-agent
      - dynamic-pricing-agent
      - coupon-optimization-agent
```

### 6.3 部分分離（推奨される段階的移行）

```yaml
# 例: Equipment Matching と Dynamic Pricing だけ分離
services:
  agent-runtime:                    # 残り 5 Agent + Orchestrator
    image: agent-runtime-monolith
    ports: ["8100:8100"]
    environment:
      AGENTS_DEPLOYMENT_MODE: distributed   # Orchestrator は分離 Agent を REST 呼び出し
      EQUIPMENT_MATCHING_AGENT_URL: http://equipment-matching:8102
      DYNAMIC_PRICING_AGENT_URL: http://dynamic-pricing:8104
      # 他の Worker URL は self（http://localhost:8100）に向ける

  equipment-matching:
    image: equipment-matching-standalone
    ports: ["8102:8102"]

  dynamic-pricing:
    image: dynamic-pricing-standalone
    ports: ["8104:8104"]
```

> **TODO（実装フェーズ）**: 部分分離時の Worker 単位の Local/Remote 切替は、`agents.workers.<name>.mode` プロパティで個別指定可能にする拡張が必要。現状は全 Worker が一律に `agents.deployment.mode` に従う。

---

## 7. 認証ポリシー

| モード | 通信経路 | 認証方式 |
|-------|---------|---------|
| **モノリス** | Orchestrator → Worker | なし（同一 JVM） |
| **モノリス** | Customer → Orchestrator `/recommend` | JWT |
| **モノリス** | Multi-Agent → 既存サービス | JWT 転送 or `X-Internal-Api-Key` |
| **分散** | Orchestrator → Worker | `X-Internal-Api-Key` + `X-Caller-Service` |
| **分散** | Customer → Orchestrator `/recommend` | JWT |
| **分散** | Multi-Agent → 既存サービス | JWT 転送 or `X-Internal-Api-Key` |

`InternalApiKeyAuthenticationFilter`（[apply-existing-services.md §1.1](apply-existing-services.md) 参照）は **分散モードのみ** Worker SecurityConfig で有効化される（`@ConditionalOnProperty(name="agents.deployment.mode", havingValue="distributed")`）。**既存ドメインサービス側**（user-management 等）は **モード非依存で常に必須**。

---

## 8. 共有 DTO の `agent-common` への移動

以下の DTO は **2 箇所以上で参照される**ため、`agent-common/src/main/java/com/example/skishop/agent/common/dto/` に移動する。

| DTO | 元の所在 | 参照する Agent |
|-----|---------|--------------|
| `CustomerIntentRequest`, `CustomerIntentResult`, `IntentCategory`, `ExtractedConstraints`, `UserPurchaseHistory` | customer-intent-agent | Orchestrator |
| `WeatherAgentRequest`, `WeatherAgentResponse`, `CurrentWeatherData`, `WeatherForecastData`, `SkiConditionsData`, `WeatherAlertData`, `SkiFeasibilityResult` | weather-agent | Orchestrator, Equipment Matching, Dynamic Pricing |
| `EquipmentMatchRequest`, `EquipmentMatchResult`, `RankedProduct`, `ProductCandidate`, `BodyMeasurements` | equipment-matching-agent | Orchestrator |
| `InventoryCheckRequest`, `InventoryStatus`, `InventoryAlert`, `ReservationRequest`, `ReservationResult` | inventory-monitoring-agent | Orchestrator, Equipment Matching |
| `BulkPricingRequest`, `PricingRequest`, `PricingResult`, `PriceBreakdown` | dynamic-pricing-agent | Orchestrator, Coupon Optimization |
| `CouponOptimizationRequest`, `CouponOptimizationResult`, `CouponCandidate`, `CouponEvaluation`, `CartItemPricing` | coupon-optimization-agent | Orchestrator |

> **重要**: パッケージは `com.example.skishop.agent.common.dto` に統一する。各 Agent 設計書の `package com.example.skishop.agent.<name>.dto` 記述は、共有 DTO のみ `com.example.skishop.agent.common.dto` に置き換える。

各 Agent 内部のみで使う DTO（例: `OpenMeteoClient` の生レスポンスマッピング用）は元のパッケージに残す。

---

## 9. テスト戦略

| モード | テスト方法 |
|-------|----------|
| 単体テスト | 各 Agent モジュール内で `@ExtendWith(MockitoExtension.class)` |
| モノリス統合テスト | `agent-runtime-monolith` 配下で `@SpringBootTest`。全 Agent Bean が同 ApplicationContext に同居 |
| 分散統合テスト | TestContainers で 7 サービスを起動。Orchestrator は `RemoteWorkerAgentInvoker` を使用 |
| 切替動作テスト | `@SpringBootTest(properties = "agents.deployment.mode=distributed")` で `RemoteWorkerAgentInvoker` が選択されることを検証 |
| Bean 衝突回帰テスト | モノリス時に全 Agent の `ChatClient` Bean が `@Qualifier` で正しく解決されることを `ApplicationContext.getBean(name)` で検証 |

---

## 10. 利点・留意点

### 10.1 利点

- **デフォルトで運用コスト最小**（コンテナ 1 個、Azure OpenAI クォータ 1 つ、Prometheus scrape 1 つ）
- **Worker 呼び出しレイテンシゼロ**（Bean 直接呼び出し）
- **トランザクション境界が単純**
- **コードベースは将来分離可能な構造を維持**（モジュール境界・DTO 共通化済み）
- **段階的分離が容易**（負荷プロファイルが見えてから判断可能）

### 10.2 留意点

- モノリスでは 1 Agent のクラッシュが全体を巻き込むため、Worker 内で **適切な例外ハンドリング** を徹底
- Azure OpenAI のレート制限が 1 アプリで集中するため、Spring AI Retry + circuit breaker をモノリス全体で調整
- 部分分離時、`WorkerAgentInvoker` の Local/Remote 混在に対応する拡張が必要（§6.3 TODO 参照）
- Bean 名衝突対策（§4.3）を全 Agent 設計書に徹底適用する

---

## 11. 各 Agent 設計書への適用ガイド

各 Agent 詳細設計書の以下のセクションは、本書の規約で上書きされる。

| 設計書のセクション | 本書での扱い |
|---------------|-----------|
| §3 モジュール構成 | §2.2 / §4 で上書き（Spring Boot library 化 + AutoConfiguration） |
| §4.x DTO（共有 DTO の package） | §8 で `com.example.skishop.agent.common.dto` に変更 |
| §4.x *AgentConfig（Bean 定義） | §4.3 で Bean 名にプレフィックス必須 |
| §4.x SecurityConfig | §4.2 で `@ConditionalOnProperty(distributed)` 付与 |
| §4.x Controller | §4.1 で `@ConditionalOnProperty(agents.web.enabled)` 付与 |
| §5 application.properties | §4.5 で standalone 用テンプレートとして扱う |
| Orchestrator §4.3 OrchestratorWorkerTools | §3.4 で `WorkerAgentInvoker` 経由にリファクタ |
| Orchestrator §4.5 WorkerAgentRestClient | §3.3 で `@ConditionalOnProperty(distributed)` 付与 |
| Equipment Matching / Dynamic Pricing の WeatherAgentClient 直接依存 | §3.5 で `WeatherInvoker` 経由に置換 |

---

## 12. 参照リソース

- [orchestrator-agent-design.md](orchestrator-agent-design.md)
- [apply-existing-services.md](apply-existing-services.md)
- [modify-spec-plan.md](modify-spec-plan.md)
- [Spring Boot AutoConfiguration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration)
- [Spring Modulith](https://spring.io/projects/spring-modulith)
