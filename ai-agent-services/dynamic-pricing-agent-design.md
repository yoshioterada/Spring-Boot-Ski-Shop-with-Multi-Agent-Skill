# Dynamic Pricing Agent 詳細設計書

## 1. 概要

### 1.1 目的

**Dynamic Pricing Agent** は、需要・天候・在庫残量・顧客ティア・季節などの複数因子を考慮して最適価格を算出する専門 Worker Agent である。Orchestrator がカート構築前（シーケンス図ステップ9）に呼び出し、各製品の最終提示価格を決定する。

### 1.2 アーキテクチャ上の位置づけ

シーケンス図のステップ9に対応する（Pricing & coupon strategy → Orchestrator）。

```
Orchestrator Agent
    │
    │ [ToolCallback] calculateDynamicPrice(productId, customerId, context)
    ▼
Dynamic Pricing Agent ← 本設計書対象
    │
    │ Chain Workflow パターン（5段階価格決定チェーン）:
    │
    │  Step1: getBasePrice(productId)
    │      ↓
    │  Step2: applyDemandAdjustment(basePrice, demandLevel)
    │      ↓
    │  Step3: applyWeatherAdjustment(price, skiCondition)
    │      ↓
    │  Step4: applyInventoryAdjustment(price, stockLevel)
    │      ↓
    │  Step5: applyCustomerTierDiscount(price, customerTier)
    │      ↓
    │  PricingResult（最終価格 + 価格内訳）
    ▼
Orchestrator へ PricingResult を返却
```

### 1.3 デプロイメントモード（ハイブリッド規約）

本 Agent は [hybrid-deployment-design.md](hybrid-deployment-design.md) の規約に従う。

- **モジュール種別**: **library jar**。`@SpringBootApplication` は持たない
- **デフォルト起動**: `agent-runtime-monolith`（ポート 8100）に同梱。Orchestrator / Weather / Coupon Optimization と同 JVM のため Bean 直接呼び出し
- **分散起動**: `agent-runtime-standalone/dynamic-pricing-standalone`（ポート 8104）として個別起動可能
- **共有 DTO**: `BulkPricingRequest`, `PricingRequest`, `PricingResult`, `PriceBreakdown` は `agent-common/dto/` に移動
- **Worker→Worker 抽象（使用側）**: 本 Agent は Weather Agent を呼び出す。`WeatherAgentClient` への直接依存を `WeatherInvoker`（`agent-common/invoker`）に置換。本モジュールに `RemoteWeatherInvoker implements WeatherInvoker`（`@ConditionalOnProperty(distributed)`）を配置
- **Bean 名規約**: `dynamicPricingAgentChatClient`, `dynamicPricingInventoryRestClient`, `dynamicPricingSalesRestClient`, `dynamicPricingWeatherRestClient` など Agent 名プレフィックス必須。`@Qualifier` で注入
- **`SecurityConfig`**: `@ConditionalOnProperty(name="agents.deployment.mode", havingValue="distributed")` を付与
- **`Controller`**: `@ConditionalOnProperty(name="agents.web.enabled", havingValue="true", matchIfMissing=true)` を付与
- **`AutoConfiguration`**: `@AutoConfiguration` + `@Import({...})` で明示取り込み。RemoteWeatherInvoker も `@Import` で含める
- **`application.properties`**: 本モジュールでは同梱しない。§5 の内容は standalone 用テンプレート

---

## 2. Agentic Pattern の選択

**Chain Workflow パターン**を採用する。

価格決定は複数の調整因子を順序通りに適用する必要があるため Chain が最適。各ステップの出力が次ステップの入力になる線形処理。

```
[BasePrice]
    → × DemandMultiplier (需要: 0.9〜1.5)
    → × WeatherMultiplier (好天候: 1.2、悪天候: 0.8)
    → × InventoryMultiplier (在庫僅少: 1.1、在庫豊富: 0.95)
    → × CustomerTierDiscount (PLATINUM: 0.85、GOLD: 0.90、SILVER: 0.95、BRONZE: 1.0)
    → FinalPrice (小数点以下四捨五入)
```

---

## 3. モジュール構成

```
ai-agent-services/
└── dynamic-pricing-agent/
    ├── pom.xml
    ├── Dockerfile
    └── src/
        ├── main/java/com/example/skishop/agent/pricing/
        │   ├── config/
        │   │   ├── DynamicPricingAgentAutoConfiguration.java   ← @AutoConfiguration
        │   │   ├── DynamicPricingAgentConfig.java
        │   │   └── DynamicPricingAgentSecurityConfig.java      ← @ConditionalOnProperty(distributed)
        │   ├── tool/
        │   │   └── DynamicPricingToolService.java
        │   ├── service/
        │   │   └── DynamicPricingAgentService.java
        │   ├── client/
        │   │   ├── ProductCatalogClient.java
        │   │   └── SalesManagementClient.java
        │   ├── invoker/
        │   │   └── RemoteWeatherInvoker.java                   ← implements agent-common WeatherInvoker (@ConditionalOnProperty(distributed))
        │   └── controller/
        │       └── DynamicPricingController.java
        └── resources/
            ├── META-INF/spring/
            │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
            └── application.properties                            ← standalone テンプレート
```

> **DTO について**: 全 DTO（PricingRequest / PricingResult / PriceBreakdown / BulkPricingRequest）は `agent-common` の `com.example.skishop.agent.common.dto` パッケージに集約されるため、本モジュールには含まない。
```

---

## 4. クラス詳細設計

### 4.1 DTO 設計

```java
// dto/PriceBreakdown.java
package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;

public record PriceBreakdown(
        BigDecimal basePrice,
        double demandMultiplier,
        String demandLevel,            // "VERY_HIGH" | "HIGH" | "NORMAL" | "LOW"
        double weatherMultiplier,
        String weatherCondition,       // "EXCELLENT" | "GOOD" | "POOR"
        double inventoryMultiplier,
        String inventoryStatus,        // "SCARCE" | "NORMAL" | "ABUNDANT"
        double customerTierDiscount,
        String customerTier,
        BigDecimal finalPrice
) {}
```

```java
// dto/PricingRequest.java
package com.example.skishop.agent.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PricingRequest(
        @NotBlank String productId,
        @NotBlank String userId,
        String customerTier,       // 省略時は BRONZE として扱う
        String resortLocation,     // 気象調整用（省略可）
        @Positive int quantity
) {
    public PricingRequest {
        if (customerTier == null) customerTier = "BRONZE";
        if (quantity <= 0) quantity = 1;
    }
}
```

```java
// dto/PricingResult.java
package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PricingResult(
        String productId,
        String userId,
        BigDecimal finalPrice,
        BigDecimal originalBasePrice,
        double totalDiscountRate,       // 0.0〜1.0（割引率）
        BigDecimal savingsAmount,
        PriceBreakdown breakdown,
        String priceJustification,      // GPT が生成した価格根拠の説明（日本語）
        Instant calculatedAt,
        Instant validUntil              // 価格の有効期限（15分）
) {}
```

```java
// dto/BulkPricingRequest.java
package com.example.skishop.agent.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkPricingRequest(
        @NotBlank String userId,
        String customerTier,
        @NotEmpty List<BulkPricingItem> items,
        String resortLocation
) {
    public record BulkPricingItem(
            @NotBlank String productId,
            int quantity
    ) {}
}
```

---

### 4.2 DynamicPricingToolService（@Tool 定義・Chain Workflow）

```java
// tool/DynamicPricingToolService.java
package com.example.skishop.agent.pricing.tool;

import com.example.skishop.agent.pricing.client.ProductCatalogClient;
import com.example.skishop.agent.pricing.client.SalesManagementClient;
import com.example.skishop.agent.common.invoker.WeatherInvoker;
import com.example.skishop.agent.common.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class DynamicPricingToolService {

    private static final Logger log = LoggerFactory.getLogger(DynamicPricingToolService.class);

    // 価格調整係数の設定値
    private static final double DEMAND_VERY_HIGH = 1.50;
    private static final double DEMAND_HIGH      = 1.20;
    private static final double DEMAND_NORMAL    = 1.00;
    private static final double DEMAND_LOW       = 0.90;

    private static final double WEATHER_EXCELLENT = 1.15;
    private static final double WEATHER_GOOD      = 1.05;
    private static final double WEATHER_POOR      = 0.80;

    private static final double INVENTORY_SCARCE   = 1.10;
    private static final double INVENTORY_NORMAL   = 1.00;
    private static final double INVENTORY_ABUNDANT = 0.95;

    private final ProductCatalogClient catalogClient;
    private final SalesManagementClient salesClient;
    private final WeatherInvoker weatherClient;

    public DynamicPricingToolService(ProductCatalogClient catalogClient,
                                      SalesManagementClient salesClient,
                                      WeatherInvoker weatherClient) {
        this.catalogClient = catalogClient;
        this.salesClient = salesClient;
        this.weatherClient = weatherClient;
    }

    /**
     * ツール1 (Chain Step1): 商品の標準価格を取得する。
     * product-catalog または inventory-management-service から取得。
     */
    @Tool(description = """
            商品の標準（ベース）価格を取得する。
            動的価格算出チェーンの最初のステップ。
            productId を指定して呼び出すこと。
            """)
    public BigDecimal getBasePrice(
            @ToolParam(description = "商品 ID") String productId) {
        log.info("Tool getBasePrice: productId={}", productId);
        return catalogClient.getBasePrice(productId);
    }

    /**
     * ツール2 (Chain Step2): 需要レベルに応じた調整係数を適用する。
     * 直近7日間の販売データから需要を算出する。
     */
    @Tool(description = """
            商品の直近7日間の販売数から需要レベルを判定し、価格に調整係数を適用する。
            VERY_HIGH（1.5倍）/ HIGH（1.2倍）/ NORMAL（1.0倍）/ LOW（0.9倍）
            getBasePrice の後に呼び出すこと。
            """)
    public BigDecimal applyDemandAdjustment(
            @ToolParam(description = "ベース価格") BigDecimal basePrice,
            @ToolParam(description = "商品 ID") String productId) {
        log.info("Tool applyDemandAdjustment: productId={}", productId);
        int salesLast7Days = salesClient.getSalesCount(productId, 7);
        double multiplier = determineDemandMultiplier(salesLast7Days);
        return basePrice.multiply(BigDecimal.valueOf(multiplier)).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * ツール3 (Chain Step3): 気象コンディションに応じた調整係数を適用する。
     * スキー好適日（EXCELLENT）は需要増で価格アップ、悪天候（POOR）は割引。
     */
    @Tool(description = """
            スキーリゾートの気象コンディションに基づいて価格を調整する。
            EXCELLENT（1.15倍）/ GOOD（1.05倍）/ POOR（0.80倍）
            resortLocation が null の場合はスキップして price をそのまま返す。
            """)
    public BigDecimal applyWeatherAdjustment(
            @ToolParam(description = "需要調整後の価格") BigDecimal price,
            @ToolParam(description = "スキーリゾートの場所（省略可）") @Nullable String resortLocation) {
        log.info("Tool applyWeatherAdjustment: resort={}", resortLocation);
        if (resortLocation == null || resortLocation.isBlank()) return price;

        String condition = weatherClient.getOverallCondition(resortLocation);
        double multiplier = switch (condition) {
            case "EXCELLENT" -> WEATHER_EXCELLENT;
            case "GOOD"      -> WEATHER_GOOD;
            case "POOR"      -> WEATHER_POOR;
            default          -> 1.00;
        };
        return price.multiply(BigDecimal.valueOf(multiplier)).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * ツール4 (Chain Step4): 在庫残量に応じた調整係数を適用する。
     * 在庫僅少は希少価値で価格アップ、在庫豊富は処分割引。
     */
    @Tool(description = """
            在庫残量に基づいて価格を調整する。
            SCARCE（残5個未満・1.1倍）/ NORMAL（1.0倍）/ ABUNDANT（残50個超・0.95倍）
            applyWeatherAdjustment の後に呼び出すこと。
            """)
    public BigDecimal applyInventoryAdjustment(
            @ToolParam(description = "気象調整後の価格") BigDecimal price,
            @ToolParam(description = "商品 ID") String productId) {
        log.info("Tool applyInventoryAdjustment: productId={}", productId);
        int stockCount = catalogClient.getStockCount(productId);
        double multiplier = stockCount < 5 ? INVENTORY_SCARCE
                : stockCount > 50 ? INVENTORY_ABUNDANT
                : INVENTORY_NORMAL;
        return price.multiply(BigDecimal.valueOf(multiplier)).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * ツール5 (Chain Step5): 顧客ティア割引を適用して最終価格を算出する。
     * PLATINUM: 15%割引 / GOLD: 10%割引 / SILVER: 5%割引 / BRONZE: 割引なし
     */
    @Tool(description = """
            顧客ティアに応じた会員割引を適用して最終価格を算出する。
            PLATINUM: 15%割引 / GOLD: 10%割引 / SILVER: 5%割引 / BRONZE: 割引なし
            Chain の最後のステップ。この結果が顧客への提示価格となる。
            """)
    public BigDecimal applyCustomerTierDiscount(
            @ToolParam(description = "在庫調整後の価格") BigDecimal price,
            @ToolParam(description = "顧客ティア: BRONZE/SILVER/GOLD/PLATINUM") String customerTier) {
        log.info("Tool applyCustomerTierDiscount: tier={}", customerTier);
        double discountRate = switch (customerTier.toUpperCase()) {
            case "PLATINUM" -> 0.85;
            case "GOLD"     -> 0.90;
            case "SILVER"   -> 0.95;
            default         -> 1.00; // BRONZE
        };
        return price.multiply(BigDecimal.valueOf(discountRate)).setScale(0, RoundingMode.HALF_UP);
    }

    private double determineDemandMultiplier(int salesLast7Days) {
        if (salesLast7Days >= 50) return DEMAND_VERY_HIGH;
        if (salesLast7Days >= 20) return DEMAND_HIGH;
        if (salesLast7Days >= 5)  return DEMAND_NORMAL;
        return DEMAND_LOW;
    }
}
```

---

### 4.3 DynamicPricingAgentService

```java
// service/DynamicPricingAgentService.java
package com.example.skishop.agent.pricing.service;

import com.example.skishop.agent.common.dto.*;
import com.example.skishop.agent.pricing.tool.DynamicPricingToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class DynamicPricingAgentService {

    private static final Logger log = LoggerFactory.getLogger(DynamicPricingAgentService.class);

    private static final String SYSTEM_PROMPT = """
            あなたはスキーショップの動的価格決定 AI エージェントです。
            以下の5段階チェーンを必ず順番通りに実行して最終価格を算出してください。

            Step1: getBasePrice(productId) → basePrice を取得
            Step2: applyDemandAdjustment(basePrice, productId) → 需要調整後価格
            Step3: applyWeatherAdjustment(price, resortLocation) → 気象調整後価格（リゾートなければスキップ）
            Step4: applyInventoryAdjustment(price, productId) → 在庫調整後価格
            Step5: applyCustomerTierDiscount(price, customerTier) → 最終価格

            各ステップの結果と適用した係数を記録し、
            最後に価格の根拠を日本語で 150 文字以内で説明する priceJustification を生成してください。
            """;

    private final ChatClient chatClient;
    private final DynamicPricingToolService toolService;

    public DynamicPricingAgentService(
            @org.springframework.beans.factory.annotation.Qualifier("pricingAgentChatClient") ChatClient chatClient,
                                       DynamicPricingToolService toolService) {
        this.chatClient = chatClient;
        this.toolService = toolService;
    }

    public PricingResult calculatePrice(PricingRequest request) {
        log.info("DynamicPricingAgent calculatePrice: productId={}, userId={}",
                request.productId(), request.userId());

        PricingResult result = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("""
                        商品ID: %s
                        ユーザーID: %s
                        顧客ティア: %s
                        リゾート: %s
                        数量: %d
                        上記の商品の最終価格を Chain Workflow で算出してください。
                        """.formatted(
                        request.productId(), request.userId(),
                        request.customerTier(), request.resortLocation(), request.quantity()))
                .tools(toolService)
                .call()
                .entity(PricingResult.class);

        log.info("DynamicPricingAgent result: productId={}, finalPrice={}",
                request.productId(), result.finalPrice());
        return result;
    }

    /**
     * 複数商品の価格を一括算出（カート全体の価格決定）。
     * 各商品について同じ Chain を実行する。
     */
    public List<PricingResult> calculateBulkPrices(BulkPricingRequest request) {
        log.info("DynamicPricingAgent calculateBulkPrices: userId={}, itemCount={}",
                request.userId(), request.items().size());

        return request.items().stream()
                .map(item -> calculatePrice(new PricingRequest(
                        item.productId(), request.userId(),
                        request.customerTier(), request.resortLocation(), item.quantity())))
                .toList();
    }
}
```

---

### 4.4 DynamicPricingAgentConfig

```java
// config/DynamicPricingAgentConfig.java
package com.example.skishop.agent.pricing.config;

import com.example.skishop.agent.pricing.tool.DynamicPricingToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DynamicPricingAgentConfig {

    @Bean("pricingAgentChatClient")
    public ChatClient pricingAgentChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * オーケストレーターが動的価格エージェントの全ツールを登録するための ToolCallback[]。
     */
    @Bean("pricingAgentToolCallbacks")
    public ToolCallback[] pricingAgentToolCallbacks(DynamicPricingToolService toolService) {
        return ToolCallbacks.from(toolService);
    }
}
```

---

### 4.5 DynamicPricingController

```java
// controller/DynamicPricingController.java
package com.example.skishop.agent.pricing.controller;

import com.example.skishop.agent.common.dto.*;
import com.example.skishop.agent.pricing.service.DynamicPricingAgentService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents/pricing")
@ConditionalOnProperty(name = "agents.web.enabled", havingValue = "true", matchIfMissing = true)
public class DynamicPricingController {

    private final DynamicPricingAgentService service;

    public DynamicPricingController(DynamicPricingAgentService service) {
        this.service = service;
    }

    @PostMapping("/calculate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<PricingResult> calculate(
            @Valid @RequestBody PricingRequest request) {
        return ResponseEntity.ok(service.calculatePrice(request));
    }

    @PostMapping("/calculate/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<List<PricingResult>> calculateBulk(
            @Valid @RequestBody BulkPricingRequest request) {
        return ResponseEntity.ok(service.calculateBulkPrices(request));
    }
}
```

---

### 4.6 DynamicPricingAgentSecurityConfig

> **ハイブリッド規約**: 本 `SecurityConfig` は **分散モードのみ有効化**される。モノリス時は `agent-runtime-monolith` 側の単一 SecurityConfig が処理する。

```java
// config/DynamicPricingAgentSecurityConfig.java
package com.example.skishop.agent.pricing.config;

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
public class DynamicPricingAgentSecurityConfig {

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(@Value("${jwt.secret}") String jwtSecret) {
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
                        .anyRequest().authenticated()
                )
                .addFilterBefore(internalFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

### 4.7 RemoteWeatherInvoker　—　Worker→Worker (REST)

```java
// invoker/RemoteWeatherInvoker.java
package com.example.skishop.agent.pricing.invoker;

import com.example.skishop.agent.common.dto.SkiFeasibilityResult;
import com.example.skishop.agent.common.invoker.WeatherInvoker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "distributed")
public class RemoteWeatherInvoker implements WeatherInvoker {

    private final RestClient restClient;

    public RemoteWeatherInvoker(
            @Value("${services.weather-agent.base-url}") String baseUrl,
            @Value("${services.internal-api-key:}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", apiKey)
                .defaultHeader("X-Caller-Service", "dynamic-pricing-agent")
                .build();
    }

    @Override
    public SkiFeasibilityResult getFeasibility(String location) {
        return restClient.get()
                .uri("/api/v1/agents/weather/feasibility?location={loc}", location)
                .retrieve()
                .body(SkiFeasibilityResult.class);
    }

    @Override
    public String getOverallCondition(String location) {
        return getFeasibility(location).overallCondition();
    }
}
```

### 4.8 DynamicPricingAgentAutoConfiguration

```java
// config/DynamicPricingAgentAutoConfiguration.java
package com.example.skishop.agent.pricing.config;

import com.example.skishop.agent.pricing.client.ProductCatalogClient;
import com.example.skishop.agent.pricing.client.SalesManagementClient;
import com.example.skishop.agent.pricing.controller.DynamicPricingController;
import com.example.skishop.agent.pricing.invoker.RemoteWeatherInvoker;
import com.example.skishop.agent.pricing.service.DynamicPricingAgentService;
import com.example.skishop.agent.pricing.tool.DynamicPricingToolService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    DynamicPricingToolService.class,
    DynamicPricingAgentService.class,
    ProductCatalogClient.class,
    SalesManagementClient.class,
    RemoteWeatherInvoker.class,                  // 分散時のみ有効（@ConditionalOnProperty）
    DynamicPricingAgentConfig.class,
    DynamicPricingController.class,
    DynamicPricingAgentSecurityConfig.class
})
public class DynamicPricingAgentAutoConfiguration {
}
```

登録: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` に `com.example.skishop.agent.pricing.config.DynamicPricingAgentAutoConfiguration` を記載。

---

## 5. application.properties

> **ハイブリッド規約**: 本ファイルは **分散モード用テンプレート**である。モノリスモードでは `agent-runtime-monolith/src/main/resources/application.yml` に統合される。

```properties
spring.application.name=dynamic-pricing-agent
server.port=8104

agents.deployment.mode=distributed
agents.web.enabled=true

spring.ai.azure.openai.api-key=${AZURE_OPENAI_API_KEY:}
spring.ai.azure.openai.endpoint=${AZURE_OPENAI_ENDPOINT:}
spring.ai.azure.openai.chat.options.deployment-name=${AZURE_OPENAI_DEPLOYMENT:gpt-4o}
spring.ai.azure.openai.chat.options.temperature=0.0
spring.ai.azure.openai.chat.options.max-completion-tokens=1024

services.inventory-management.base-url=${INVENTORY_MANAGEMENT_SERVICE_URL:http://localhost:8082}
services.sales-management.base-url=${SALES_MANAGEMENT_SERVICE_URL:http://localhost:8083}
services.weather-agent.base-url=${WEATHER_AGENT_URL:http://localhost:8100}
services.internal-api-key=${INTERNAL_API_KEY:}

# 価格有効期限（分）
pricing.validity-minutes=15

jwt.secret=${JWT_SECRET:}
management.endpoints.web.exposure.include=health,info,prometheus
```

---

## 6. テスト設計

```java
@ExtendWith(MockitoExtension.class)
class DynamicPricingToolServiceTest {

    @Test
    @DisplayName("applyCustomerTierDiscount: PLATINUM で 15% 割引になる")
    void should_apply15PercentDiscount_for_platinumTier() {
        BigDecimal price = new BigDecimal("100000");
        BigDecimal result = toolService.applyCustomerTierDiscount(price, "PLATINUM");
        assertThat(result).isEqualByComparingTo(new BigDecimal("85000"));
    }

    @Test
    @DisplayName("applyDemandAdjustment: 7日間販売数50件以上で 1.5 倍になる")
    void should_applyVeryHighDemandMultiplier_when_salesExceed50() {
        when(salesClient.getSalesCount("p1", 7)).thenReturn(55);
        BigDecimal result = toolService.applyDemandAdjustment(new BigDecimal("10000"), "p1");
        assertThat(result).isEqualByComparingTo(new BigDecimal("15000"));
    }
}
```

---

## 7. セキュリティ考慮事項

| リスク | 対策 |
|--------|------|
| A01 Access Control | 価格算出 API は `AGENT` ロール必須（エンドユーザーが直接価格操作不可）|
| A04 Insecure Design | 価格係数は設定値（application.properties）で管理。コードへのハードコードを最小化 |
| A06 Vulnerable Components | 価格の有効期限を15分に設定。古い価格でのカート操作を防止 |

### 7.1 Orchestrator および Worker→Worker の認証

**モノリスモード**: Orchestrator/Weather Agent と同一 JVM 内で動作し、`WeatherInvoker` は weather-agent 側の `LocalWeatherInvoker` Bean を直接使う。ネットワーク認証は不要。

**分散モード**: Orchestrator は `X-Internal-Api-Key` を付与して `/api/v1/agents/pricing/calculate/bulk` を呼び出す。§4.6 の `DynamicPricingAgentSecurityConfig`（`@ConditionalOnProperty(distributed)`）に `InternalApiKeyAuthenticationFilter`（common-lib）を登録し `ROLE_AGENT` を付与する。Worker→Worker は §4.7 の `RemoteWeatherInvoker` が `X-Internal-Api-Key` + `X-Caller-Service: dynamic-pricing-agent` を付与して weather-agent の REST エンドポイントを呼ぶ。詳細は [hybrid-deployment-design.md §3, §7](hybrid-deployment-design.md) を参照。

### 7.2 Orchestrator から送信されるリクエスト例と BulkPricingRequest の対応

```json
{
  "userId": "u001",
  "customerTier": "GOLD",
  "items": [ { "productId": "ski-001", "quantity": 1 },
             { "productId": "wear-014", "quantity": 1 } ],
  "resortLocation": "Naeba"
}
```

レスポンスは `List<PricingResult>` で、Orchestrator は各要素の `productId` と `finalPrice` を抽出して次の Tool（`optimizeCoupons`）の `cartItemsEncoded` に使用する。

---

## 8. 参照リソース

- [Agentic Patterns - Chain Workflow](https://github.com/spring-projects/spring-ai-examples/tree/main/agentic-patterns/chain-workflow)
- [Spring AI Structured Output](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
