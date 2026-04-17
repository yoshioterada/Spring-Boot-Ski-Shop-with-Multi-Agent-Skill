# Equipment Matching Agent 詳細設計書

## 1. 概要

### 1.1 目的

**Equipment Matching Agent** は、顧客のスキルレベル・体型・気象コンディション・予算に基づき、最適なスキー用品をレコメンドする専門 Worker Agent である。Customer Intent Agent が抽出した制約と、Weather Agent が返す気象データを入力として受け取り、在庫の中から最適な製品セットをランキング形式で返す。

### 1.2 アーキテクチャ上の位置づけ

シーケンス図のステップ6〜8に対応する（Enrich: weather/location + skill model → retrieve candidates → ranked list）。

```
Orchestrator Agent
    │
    │ [ToolCallback] matchEquipment(intentResult, weatherResult)
    ▼
Equipment Matching Agent ← 本設計書対象
    │
    │ Parallelization パターン:
    │  ┌─ Branch A: 在庫候補検索（inventory-management-service）
    │  ├─ Branch B: AI推奨スコアリング（ai-support-service）
    │  └─ Branch C: 気象適合チェック（Weather Agent ToolCallback）
    │        ↓
    │  Merge: スコアを合算してランキング
    │        ↓
    │  StructuredOutput: EquipmentMatchResult
    ▼
Orchestrator へ EquipmentMatchResult を返却
```

### 1.3 デプロイメントモード（ハイブリッド規約）

本 Agent は [hybrid-deployment-design.md](hybrid-deployment-design.md) の規約に従う。

- **モジュール種別**: **library jar**。`@SpringBootApplication` は持たない
- **デフォルト起動**: `agent-runtime-monolith`（ポート 8100）に同梱。Orchestrator / Weather Agent と同 JVM のため Bean 直接呼び出し
- **分散起動**: `agent-runtime-standalone/equipment-matching-standalone`（ポート 8102）として個別起動可能
- **共有 DTO**: `EquipmentMatchRequest`, `EquipmentMatchResult`, `RankedProduct`, `ProductCandidate`, `BodyMeasurements` は `agent-common/dto/` に移動
- **Worker→Worker 抽象（使用側）**: 本 Agent は Weather Agent を呼び出す。`WeatherAgentClient` への直接依存を `WeatherInvoker`（`agent-common/invoker`）に置換。本モジュールに `RemoteWeatherInvoker implements WeatherInvoker`（`@ConditionalOnProperty(distributed)`）を配置し、分散時は REST、モノリス時は weather-agent モジュールの `LocalWeatherInvoker` が選択される
- **Bean 名規約**: `equipmentMatchingAgentChatClient`, `equipmentMatchingInventoryRestClient`, `equipmentMatchingWeatherRestClient` など Agent 名プレフィックス必須。`@Qualifier` で注入
- **`SecurityConfig`**: `@ConditionalOnProperty(name="agents.deployment.mode", havingValue="distributed")` を付与
- **`Controller`**: `@ConditionalOnProperty(name="agents.web.enabled", havingValue="true", matchIfMissing=true)` を付与
- **`AutoConfiguration`**: `@AutoConfiguration` + `@Import({...})` で明示取り込み。RemoteWeatherInvoker も `@Import` で含める
- **`application.properties`**: 本モジュールでは同梱しない。§5 の内容は standalone 用テンプレート

---

## 2. Agentic Pattern の選択

**Parallelization パターン**を採用する。

在庫検索・AI スコアリング・気象適合チェックの3処理を並列実行し、結果をマージする。これにより応答時間を最小化する。

```
[CustomerIntentResult + WeatherFeasibilityResult]
    │
    ├─ [並列] Branch A: searchInventoryCandidates()   → 在庫あり商品リスト
    ├─ [並列] Branch B: scoreBySkillAndBodyType()     → AI推奨スコア
    └─ [並列] Branch C: filterByWeatherConditions()  → 気象適合フィルタ
    │
    └─ [統合] mergeAndRank() → EquipmentMatchResult（ランキング済み推奨リスト）
```

---

## 3. モジュール構成

```
ai-agent-services/
└── equipment-matching-agent/
    ├── pom.xml
    ├── Dockerfile
    └── src/
        ├── main/java/com/example/skishop/agent/equipment/
        │   ├── config/
        │   │   ├── EquipmentMatchingAgentAutoConfiguration.java   ← @AutoConfiguration
        │   │   ├── EquipmentMatchingAgentConfig.java
        │   │   └── EquipmentMatchingAgentSecurityConfig.java      ← @ConditionalOnProperty(distributed)
        │   ├── tool/
        │   │   └── EquipmentMatchingToolService.java
        │   ├── service/
        │   │   └── EquipmentMatchingAgentService.java
        │   ├── client/
        │   │   └── InventoryClient.java
        │   ├── invoker/
        │   │   └── RemoteWeatherInvoker.java                      ← implements agent-common WeatherInvoker (@ConditionalOnProperty(distributed))
        │   └── controller/
        │       └── EquipmentMatchingController.java
        └── resources/
            ├── META-INF/spring/
            │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
            └── application.properties                              ← standalone テンプレート
```

> **DTO について**: 全 DTO（EquipmentMatchRequest / EquipmentMatchResult / ProductCandidate / RankedProduct / BodyMeasurements）は `agent-common` の `com.example.skishop.agent.common.dto` パッケージに集約されるため、本モジュールには含まない。

---

## 4. クラス詳細設計

### 4.1 DTO 設計

```java
// dto/BodyMeasurements.java
package com.example.skishop.agent.common.dto;

public record BodyMeasurements(
        Integer heightCm,
        Integer weightKg,
        Integer footSizeCm,     // ブーツサイズ
        String stance           // "REGULAR" | "GOOFY"（スノーボード用）
) {}
```

```java
// dto/EquipmentMatchRequest.java
package com.example.skishop.agent.common.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record EquipmentMatchRequest(
        @NotBlank String userId,
        String skillLevel,                   // "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT"
        BodyMeasurements bodyMeasurements,   // null 可（Orchestrator 経由ではプロフィール参照）
        List<String> desiredCategories,      // ["スキー板", "ウェア", "ブーツ"]
        Integer budgetYen,                   // null 可
        String destination,                  // null 可（気象データ取得先）
        boolean includeRental,
        boolean includePurchase,
        Integer quantity                     // 各カテゴリの推奨数量（null の場合は1として扱う）
) {
    public EquipmentMatchRequest {
        if (quantity == null || quantity <= 0) quantity = 1;
        if (desiredCategories == null) desiredCategories = List.of();
    }
}
```

```java
// dto/ProductCandidate.java
package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;

public record ProductCandidate(
        String productId,
        String productName,
        String category,
        String brand,
        BigDecimal basePrice,
        boolean isAvailable,
        int stockQuantity,
        String skillLevelSuitability,        // "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "ALL"
        String weatherSuitability,           // "POWDER" | "ALL_CONDITIONS" | "GROOMED"
        java.util.Map<String, String> attributes  // {"length": "165cm", "flex": "medium"}
) {}
```

```java
// dto/RankedProduct.java
package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;

public record RankedProduct(
        int rank,
        ProductCandidate product,
        double matchScore,           // 0.0〜100.0
        String matchReason,          // 日本語での推奨理由
        BigDecimal estimatedPrice,   // Dynamic Pricing 後の予想価格
        boolean isWeatherOptimal     // 現在の気象に最適か
) {}
```

```java
// dto/EquipmentMatchResult.java
package com.example.skishop.agent.common.dto;

import java.time.Instant;
import java.util.List;

public record EquipmentMatchResult(
        String userId,
        List<RankedProduct> recommendations,
        String aiRecommendationSummary,   // GPT による日本語推奨サマリー
        double totalEstimatedBudget,
        boolean withinBudget,
        Instant generatedAt
) {}
```

---

### 4.2 EquipmentMatchingToolService（@Tool 定義）

```java
// tool/EquipmentMatchingToolService.java
package com.example.skishop.agent.equipment.tool;

import com.example.skishop.agent.common.invoker.WeatherInvoker;
import com.example.skishop.agent.equipment.client.InventoryClient;
import com.example.skishop.agent.common.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EquipmentMatchingToolService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentMatchingToolService.class);
    private final InventoryClient inventoryClient;
    private final WeatherInvoker weatherInvoker;

    public EquipmentMatchingToolService(InventoryClient inventoryClient,
                                         WeatherInvoker weatherInvoker) {
        this.inventoryClient = inventoryClient;
        this.weatherInvoker = weatherInvoker;
    }

    /**
     * ツール1: スキルレベルと商品カテゴリで在庫候補を検索する。
     * 在庫管理サービスから在庫ありの商品を取得する。
     */
    @Tool(description = """
            スキルレベルと商品カテゴリを指定して、在庫ありの商品候補を最大20件取得する。
            skillLevel: BEGINNER / INTERMEDIATE / ADVANCED / EXPERT
            category: スキー板 / ブーツ / ウェア / ゴーグル / ヘルメット / グローブ
            """)
    public List<ProductCandidate> searchInventoryCandidates(
            @ToolParam(description = "商品カテゴリ、例: スキー板") String category,
            @ToolParam(description = "スキルレベル: BEGINNER/INTERMEDIATE/ADVANCED/EXPERT") String skillLevel,
            @ToolParam(description = "最大予算（円）。未指定の場合は null") @Nullable Integer maxBudgetYen) {
        log.info("Tool searchInventoryCandidates: category={}, skill={}", category, skillLevel);
        return inventoryClient.searchBySkillAndCategory(category, skillLevel, maxBudgetYen);
    }

    /**
     * ツール2: 体型・サイズ情報で候補を絞り込む。
     * 身長・体重・足のサイズに合わない製品を除外する。
     */
    @Tool(description = """
            身長・体重・靴のサイズに基づいて製品リストをフィルタリングする。
            スキー板の適正身長、ブーツサイズの適合チェックを行う。
            返り値は適合する商品のみを含むリスト。
            """)
    public List<ProductCandidate> filterByBodyMeasurements(
            @ToolParam(description = "商品候補リスト（JSON 配列）") List<ProductCandidate> candidates,
            @ToolParam(description = "身長（cm）") @Nullable Integer heightCm,
            @ToolParam(description = "体重（kg）") @Nullable Integer weightKg,
            @ToolParam(description = "ブーツ・靴サイズ（cm、例: 26.5）") @Nullable Double footSizeCm) {
        log.info("Tool filterByBodyMeasurements: candidates={}, height={}", candidates.size(), heightCm);
        return candidates.stream()
                .filter(p -> isSizeCompatible(p, heightCm, weightKg, footSizeCm))
                .toList();
    }

    /**
     * ツール3: 現在の気象コンディションで製品の適合性を評価する。
     * Weather Agent の /feasibility エンドポイントを呼び出して雪質・気温を確認する。
     */
    @Tool(description = """
            指定リゾートの現在の気象コンディション（雪質・気温）に基づいて、
            各製品の気象適合スコアを付与する。
            POWDER 時は柔らかいフレックスのボード/板を優先。
            強風・アイスバーン時は安定性重視の製品を優先。
            """)
    public List<ProductCandidate> scoreByWeatherConditions(
            @ToolParam(description = "商品候補リスト") List<ProductCandidate> candidates,
            @ToolParam(description = "スキーリゾートの場所、例: Naeba, Niigata, Japan") String resortLocation) {
        log.info("Tool scoreByWeatherConditions: candidates={}, resort={}", candidates.size(), resortLocation);
        var feasibility = weatherInvoker.getFeasibility(resortLocation);
        // 雪質に応じてフィルタリング（POWDER の場合は POWDER 対応品を優先）
        return candidates.stream()
                .sorted((a, b) -> weatherScore(b, feasibility.snowCondition())
                        - weatherScore(a, feasibility.snowCondition()))
                .toList();
    }

    /**
     * ツール4: スキルレベルと気象スコアを総合して最終ランキングを生成する。
     */
    @Tool(description = """
            スキルレベル適合度・気象適合度・在庫状況・価格を総合スコアリングして、
            上位10件をランキング形式で返す。
            最後に呼び出すツール。
            """)
    public List<RankedProduct> rankProducts(
            @ToolParam(description = "フィルタリング済み商品候補リスト") List<ProductCandidate> candidates,
            @ToolParam(description = "スキルレベル: BEGINNER/INTERMEDIATE/ADVANCED/EXPERT") String skillLevel,
            @ToolParam(description = "最大予算（円）。未指定の場合は null") @Nullable Integer maxBudgetYen) {
        log.info("Tool rankProducts: candidates={}, skill={}", candidates.size(), skillLevel);

        return candidates.stream()
                .filter(p -> maxBudgetYen == null || p.basePrice().intValue() <= maxBudgetYen)
                .map(p -> scoreProduct(p, skillLevel))
                .sorted((a, b) -> Double.compare(b.matchScore(), a.matchScore()))
                .limit(10)
                .toList();
    }

    private boolean isSizeCompatible(ProductCandidate product, Integer height, Integer weight, Double footSize) {
        // スキー板は身長の 0.85〜1.0 倍が適正
        if ("スキー板".equals(product.category()) && height != null) {
            var attrs = product.attributes();
            if (attrs.containsKey("length")) {
                try {
                    int len = Integer.parseInt(attrs.get("length").replace("cm", "").trim());
                    int minLen = (int) (height * 0.85);
                    int maxLen = (int) (height * 1.0);
                    return len >= minLen && len <= maxLen;
                } catch (NumberFormatException ignored) { /* 属性形式不正はスキップ */ }
            }
        }
        return true; // 不明な場合は通過させる
    }

    private int weatherScore(ProductCandidate product, String snowCondition) {
        if ("POWDER".equals(snowCondition) && "POWDER".equals(product.weatherSuitability())) return 20;
        if ("ALL_CONDITIONS".equals(product.weatherSuitability())) return 10;
        return 0;
    }

    private RankedProduct scoreProduct(ProductCandidate product, String skillLevel) {
        double score = 50.0;
        if (skillLevel.equals(product.skillLevelSuitability())) score += 30.0;
        if ("ALL".equals(product.skillLevelSuitability())) score += 15.0;
        if (product.isAvailable()) score += 10.0;
        String reason = "スキルレベル「%s」に適しており、在庫があります。".formatted(skillLevel);
        return new RankedProduct(0, product, score, reason, product.basePrice(), false);
    }
}
```

---

### 4.3 EquipmentMatchingAgentService（Parallelization 実装）

```java
// service/EquipmentMatchingAgentService.java
package com.example.skishop.agent.equipment.service;

import com.example.skishop.agent.common.dto.*;
import com.example.skishop.agent.equipment.tool.EquipmentMatchingToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class EquipmentMatchingAgentService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentMatchingAgentService.class);

    private static final String SYSTEM_PROMPT = """
            あなたはスキー用品の専門アドバイザー AI エージェントです。
            提供されているツールを使い、以下の手順で最適な製品を推奨してください。

            推奨手順:
            1. 各商品カテゴリ（スキー板・ブーツ・ウェア等）ごとに searchInventoryCandidates を呼び出す
            2. filterByBodyMeasurements で体型に合わない製品を除外する
            3. scoreByWeatherConditions で気象適合性を評価する
            4. rankProducts で最終ランキングを生成する
            5. 推奨理由を日本語で 300 文字以内にまとめた aiRecommendationSummary を生成する

            予算が指定されている場合は、合計金額が予算内に収まるよう考慮すること。
            初心者には操作しやすい製品を、上級者には高性能製品を優先すること。
            """;

    private final ChatClient chatClient;
    private final EquipmentMatchingToolService toolService;

    public EquipmentMatchingAgentService(
            @org.springframework.beans.factory.annotation.Qualifier("equipmentAgentChatClient") ChatClient chatClient,
                                          EquipmentMatchingToolService toolService) {
        this.chatClient = chatClient;
        this.toolService = toolService;
    }

    /**
     * Parallelization: 各カテゴリを並列で候補検索し、ChatClient でマージ・ランキング。
     */
    public EquipmentMatchResult match(EquipmentMatchRequest request) {
        log.info("EquipmentMatchingAgent match: userId={}, categories={}",
                request.userId(), request.desiredCategories());

        // 複数カテゴリを並列で候補検索（Parallelization）
        List<CompletableFuture<List<ProductCandidate>>> futures = request.desiredCategories().stream()
                .map(category -> CompletableFuture.supplyAsync(() ->
                        toolService.searchInventoryCandidates(category, request.skillLevel(), request.budgetYen())))
                .toList();

        // 並列結果をマージ
        List<ProductCandidate> allCandidates = new ArrayList<>();
        futures.forEach(f -> allCandidates.addAll(f.join()));

        log.info("EquipmentMatchingAgent: {} candidates found across {} categories",
                allCandidates.size(), request.desiredCategories().size());

        // ChatClient に Tools を渡してスコアリング・ランキング（GPT が自律的に絞り込み）
        EquipmentMatchResult result = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(buildUserPrompt(request, allCandidates))
                .tools(toolService)
                .call()
                .entity(EquipmentMatchResult.class);

        return result;
    }

    private String buildUserPrompt(EquipmentMatchRequest request, List<ProductCandidate> candidates) {
        return """
                ユーザーID: %s
                スキルレベル: %s
                希望カテゴリ: %s
                予算: %s円
                行き先: %s
                在庫候補数: %d件
                最適な製品セットを推奨してください。
                """.formatted(
                request.userId(),
                request.skillLevel(),
                String.join("・", request.desiredCategories()),
                request.budgetYen() != null ? request.budgetYen() : "未指定",
                request.destination(),
                candidates.size());
    }
}
```

---

### 4.4 EquipmentMatchingAgentConfig

```java
// config/EquipmentMatchingAgentConfig.java
package com.example.skishop.agent.equipment.config;

import com.example.skishop.agent.equipment.tool.EquipmentMatchingToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EquipmentMatchingAgentConfig {

    @Bean("equipmentAgentChatClient")
    public ChatClient equipmentAgentChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * オーケストレーターから Equipment Matching Agent を呼び出すための ToolCallback[]。
     */
    @Bean("equipmentAgentToolCallbacks")
    public ToolCallback[] equipmentAgentToolCallbacks(EquipmentMatchingToolService toolService) {
        return ToolCallbacks.from(toolService);
    }
}
```

---

### 4.5 EquipmentMatchingController

```java
// controller/EquipmentMatchingController.java
package com.example.skishop.agent.equipment.controller;

import com.example.skishop.agent.common.dto.EquipmentMatchRequest;
import com.example.skishop.agent.common.dto.EquipmentMatchResult;
import com.example.skishop.agent.equipment.service.EquipmentMatchingAgentService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agents/equipment")
@ConditionalOnProperty(name = "agents.web.enabled", havingValue = "true", matchIfMissing = true)
public class EquipmentMatchingController {

    private final EquipmentMatchingAgentService service;

    public EquipmentMatchingController(EquipmentMatchingAgentService service) {
        this.service = service;
    }

    @PostMapping("/match")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<EquipmentMatchResult> match(
            @Valid @RequestBody EquipmentMatchRequest request) {
        return ResponseEntity.ok(service.match(request));
    }
}
```

---

### 4.6 EquipmentMatchingAgentSecurityConfig

> **ハイブリッド規約**: 本 `SecurityConfig` は **分散モードのみ有効化**される。モノリス時は `agent-runtime-monolith` 側の単一 SecurityConfig が処理する。

```java
// config/EquipmentMatchingAgentSecurityConfig.java
package com.example.skishop.agent.equipment.config;

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
public class EquipmentMatchingAgentSecurityConfig {

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

分散モードでは Weather Agent を REST で呼ぶ。モノリス時は weather-agent モジュールの `LocalWeatherInvoker` が選択されるため Bean 衰突は起こらない。

```java
// invoker/RemoteWeatherInvoker.java
package com.example.skishop.agent.equipment.invoker;

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
                .defaultHeader("X-Caller-Service", "equipment-matching-agent")
                .build();
    }

    @Override
    public SkiFeasibilityResult getFeasibility(String location) {
        return restClient.get()
                .uri("/api/v1/agents/weather/feasibility?location={loc}", location)
                .retrieve()
                .body(SkiFeasibilityResult.class);
    }
}
```

### 4.8 EquipmentMatchingAgentAutoConfiguration

```java
// config/EquipmentMatchingAgentAutoConfiguration.java
package com.example.skishop.agent.equipment.config;

import com.example.skishop.agent.equipment.client.InventoryClient;
import com.example.skishop.agent.equipment.controller.EquipmentMatchingController;
import com.example.skishop.agent.equipment.invoker.RemoteWeatherInvoker;
import com.example.skishop.agent.equipment.service.EquipmentMatchingAgentService;
import com.example.skishop.agent.equipment.tool.EquipmentMatchingToolService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    EquipmentMatchingToolService.class,
    EquipmentMatchingAgentService.class,
    InventoryClient.class,
    RemoteWeatherInvoker.class,                    // 分散時のみ有効（@ConditionalOnProperty）
    EquipmentMatchingAgentConfig.class,
    EquipmentMatchingController.class,
    EquipmentMatchingAgentSecurityConfig.class
})
public class EquipmentMatchingAgentAutoConfiguration {
}
```

登録: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` に `com.example.skishop.agent.equipment.config.EquipmentMatchingAgentAutoConfiguration` を記載。

---

## 5. application.properties

> **ハイブリッド規約**: 本ファイルは **分散モード用テンプレート**である。モノリスモードでは `agent-runtime-monolith/src/main/resources/application.yml` に統合される。

```properties
spring.application.name=equipment-matching-agent
server.port=8102

agents.deployment.mode=distributed
agents.web.enabled=true

spring.ai.azure.openai.api-key=${AZURE_OPENAI_API_KEY:}
spring.ai.azure.openai.endpoint=${AZURE_OPENAI_ENDPOINT:}
spring.ai.azure.openai.chat.options.deployment-name=${AZURE_OPENAI_DEPLOYMENT:gpt-4o}
spring.ai.azure.openai.chat.options.temperature=0.2
spring.ai.azure.openai.chat.options.max-completion-tokens=2048

# サービス間通信
services.inventory.base-url=${INVENTORY_SERVICE_URL:http://localhost:8082}
services.weather-agent.base-url=${WEATHER_AGENT_URL:http://localhost:8100}
services.internal-api-key=${INTERNAL_API_KEY:}

jwt.secret=${JWT_SECRET:}

management.endpoints.web.exposure.include=health,info,prometheus
```

---

## 6. テスト設計

```java
@ExtendWith(MockitoExtension.class)
class EquipmentMatchingToolServiceTest {

    @Test
    @DisplayName("filterByBodyMeasurements: 身長165cmにスキー板140-165cmが適合する")
    void should_filterCompatibleSkis_by_height() {
        // ProductCandidate with attributes {"length": "155cm"} → 適合
        // ProductCandidate with attributes {"length": "175cm"} → 除外
    }

    @Test
    @DisplayName("rankProducts: 予算超過製品はランキングから除外される")
    void should_excludeProductsExceedingBudget() {
        // budgetYen=50000 の場合、basePrice=60000 の製品は除外される
    }
}
```

---

## 7. セキュリティ考慮事項

| リスク | 対策 |
|--------|------|
| A01 Access Control | `AGENT` ロールのみ呼び出し可能 |
| A03 Injection | `category` / `skillLevel` パラメータはホワイトリスト検証 |
| N+1 問題 | 在庫検索は CompletableFuture で並列化。カテゴリ数上限を10に制限 |

### 7.1 Orchestrator および Worker→Worker の認証

**モノリスモード**: Orchestrator/Weather Agent と同一 JVM 内で動作し、`WeatherInvoker` は weather-agent 側の `LocalWeatherInvoker` Bean を直接使う。ネットワーク認証は不要。

**分散モード**: Orchestrator は `X-Internal-Api-Key` を付与して `/api/v1/agents/equipment/match` を呼び出す。§4.6 の `EquipmentMatchingAgentSecurityConfig`（`@ConditionalOnProperty(distributed)`）に `InternalApiKeyAuthenticationFilter`（common-lib）を登録し `ROLE_AGENT` を付与する。Worker→Worker は §4.7 の `RemoteWeatherInvoker` が `X-Internal-Api-Key` + `X-Caller-Service: equipment-matching-agent` を付与して weather-agent の REST エンドポイントを呼ぶ。詳細は [hybrid-deployment-design.md §3, §7](hybrid-deployment-design.md) を参照。

### 7.2 リクエストコントラクト（Orchestrator との整合性）

Orchestrator はこの Worker に対して以下の JSON を送信する（[orchestrator-agent-design.md §11.2](orchestrator-agent-design.md) 参照）:

```json
{
  "userId": "u001",
  "skillLevel": "INTERMEDIATE",
  "desiredCategories": ["スキー板", "ウェア"],
  "budgetYen": 100000,
  "destination": "Naeba",
  "includePurchase": true,
  "includeRental": false,
  "quantity": 1
  // bodyMeasurements は送信されないため null となる。
  // 必要に応じて user-management-service から取得すること。
}
```

---

## 8. 参照リソース

- [Agentic Patterns - Parallelization](https://github.com/spring-projects/spring-ai-examples/tree/main/agentic-patterns/parallelization)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
