# Inventory Monitoring Agent 詳細設計書

## 1. 概要

### 1.1 目的

**Inventory Monitoring Agent** は、在庫状況をリアルタイムで監視・予測し、在庫不足アラートの発行・在庫確保（予約ロック）・需要予測を行う専門 Worker Agent である。Equipment Matching Agent が候補製品を絞り込む際に在庫可用性を提供し、Orchestrator がカート構築時に在庫予約（Reserve）を実行する。

### 1.2 アーキテクチャ上の位置づけ

シーケンス図のステップ7〜8および11に対応する（retrieve candidates → inventory query、reserved inventory）。

```
Orchestrator Agent
    │
    ├─ [ToolCallback] checkInventoryAvailability(productIds)   ← ステップ7-8
    ├─ [ToolCallback] reserveInventory(productIds, orderId)    ← ステップ11
    └─ [ToolCallback] getLowStockAlerts()                      ← バックグラウンド監視
    ▼
Inventory Monitoring Agent ← 本設計書対象
    │
    │ Routing Workflow パターン:
    │  stockLevel >= threshold  → Route: AVAILABLE（即時返却）
    │  stockLevel < threshold   → Route: LOW_STOCK（アラート＋代替提案）
    │  stockLevel = 0           → Route: OUT_OF_STOCK（バックオーダー案内）
    ▼
inventory-management-service（既存） への RestClient 呼び出し
```

### 1.3 デプロイメントモード（ハイブリッド規約）

本 Agent は [hybrid-deployment-design.md](hybrid-deployment-design.md) の規約に従う。

- **モジュール種別**: **library jar**。`@SpringBootApplication` は持たない
- **デフォルト起動**: `agent-runtime-monolith`（ポート 8100）に同梱。Orchestrator / Equipment Matching と同 JVM のため Bean 直接呼び出し
- **分散起動**: `agent-runtime-standalone/inventory-monitoring-standalone`（ポート 8103）として個別起動可能
- **共有 DTO**: `InventoryCheckRequest`, `InventoryStatus`, `InventoryAlert`, `ReservationRequest`, `ReservationResult` は `agent-common/dto/` に移動
- **Bean 名規約**: `inventoryMonitoringAgentChatClient`, `inventoryMonitoringInventoryServiceRestClient` など Agent 名プレフィックス必須。`@Qualifier` で注入
- **`SecurityConfig`**: `@ConditionalOnProperty(name="agents.deployment.mode", havingValue="distributed")` を付与
- **`Controller`**: `@ConditionalOnProperty(name="agents.web.enabled", havingValue="true", matchIfMissing=true)` を付与
- **`AutoConfiguration`**: `@AutoConfiguration` + `@Import({...})` で明示取り込み
- **`application.properties`**: 本モジュールでは同梱しない。§5 の内容は standalone 用テンプレート

---

## 2. Agentic Pattern の選択

**Routing Workflow パターン**を採用する。

在庫状態に応じて処理を3つのルートに分岐させる。単純な CRUD ではなく、在庫状態に基づいた動的な意思決定とアクション選択が必要なため Routing が最適。

```
[在庫確認リクエスト]
    │
    ├─ stockLevel >= LOW_STOCK_THRESHOLD  → Route A: AVAILABLE
    │   └── 即時 InventoryStatus(available=true) を返却
    │
    ├─ 0 < stockLevel < LOW_STOCK_THRESHOLD  → Route B: LOW_STOCK
    │   └── アラート生成 + 代替製品を提案 + 需要予測で補充日程を返却
    │
    └─ stockLevel == 0  → Route C: OUT_OF_STOCK
        └── バックオーダー日程 + 類似製品案内を返却
```

---

## 3. モジュール構成

```
ai-agent-services/
└── inventory-monitoring-agent/
    ├── pom.xml
    ├── Dockerfile
    └── src/
        ├── main/java/com/example/skishop/agent/inventory/
        │   ├── InventoryMonitoringAgentApplication.java
        │   ├── config/
        │   │   ├── InventoryMonitoringAgentConfig.java
        │   │   └── SecurityConfig.java
        │   ├── tool/
        │   │   └── InventoryMonitoringToolService.java
        │   ├── service/
        │   │   └── InventoryMonitoringAgentService.java
        │   ├── client/
        │   │   └── InventoryManagementClient.java
        │   ├── controller/
        │   │   └── InventoryMonitoringController.java
        │   └── dto/
        │       ├── InventoryCheckRequest.java
        │       ├── InventoryStatus.java
        │       ├── InventoryAlert.java
        │       ├── ReservationRequest.java
        │       └── ReservationResult.java
        └── resources/
            └── application.properties
```

---

## 4. クラス詳細設計

### 4.1 DTO 設計

```java
// dto/InventoryStatus.java
package com.example.skishop.agent.common.dto;

import java.util.List;

public record InventoryStatus(
        String productId,
        String productName,
        int stockQuantity,
        String availabilityStatus,   // "AVAILABLE" | "LOW_STOCK" | "OUT_OF_STOCK"
        boolean isReservable,
        String estimatedRestockDate, // ISO-8601 日付文字列（在庫切れ時）
        List<String> alternativeProductIds,
        InventoryAlert alert         // LOW_STOCK / OUT_OF_STOCK 時のみ
) {}
```

```java
// dto/InventoryAlert.java
package com.example.skishop.agent.common.dto;

import java.time.Instant;

public record InventoryAlert(
        String alertId,
        String productId,
        String severity,            // "INFO" | "WARNING" | "CRITICAL"
        String message,
        int currentStock,
        int threshold,
        Instant generatedAt
) {}
```

```java
// dto/InventoryCheckRequest.java
package com.example.skishop.agent.common.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InventoryCheckRequest(
        @NotEmpty List<String> productIds,
        int requiredQuantity          // 必要数（デフォルト: 1）
) {
    public InventoryCheckRequest {
        if (requiredQuantity <= 0) requiredQuantity = 1;
    }
}
```

```java
// dto/ReservationRequest.java
package com.example.skishop.agent.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReservationRequest(
        @NotBlank String orderId,
        @NotBlank String userId,
        @NotEmpty List<ReservationItem> items,
        int reservationTtlMinutes     // 予約ロックの有効期間（デフォルト: 30分）
) {
    public record ReservationItem(String productId, int quantity) {}
}
```

```java
// dto/ReservationResult.java
package com.example.skishop.agent.common.dto;

import java.time.Instant;
import java.util.List;

public record ReservationResult(
        String reservationId,
        String orderId,
        boolean isFullyReserved,
        List<String> reservedProductIds,
        List<String> failedProductIds,    // 在庫不足で予約失敗した製品
        Instant expiresAt
) {}
```

---

### 4.2 InventoryMonitoringToolService（@Tool 定義）

```java
// tool/InventoryMonitoringToolService.java
package com.example.skishop.agent.inventory.tool;

import com.example.skishop.agent.inventory.client.InventoryManagementClient;
import com.example.skishop.agent.common.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryMonitoringToolService {

    private static final Logger log = LoggerFactory.getLogger(InventoryMonitoringToolService.class);
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final InventoryManagementClient inventoryClient;

    public InventoryMonitoringToolService(InventoryManagementClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    /**
     * ツール1: 複数商品の在庫状況を一括確認する。
     * Routing Workflow のルーティング判断に使用する。
     */
    @Tool(description = """
            指定した商品ID リストの在庫状況を確認する。
            各商品について AVAILABLE / LOW_STOCK / OUT_OF_STOCK を返す。
            Equipment Matching Agent が製品候補を絞り込む際に呼び出す。
            """)
    public List<InventoryStatus> checkInventoryAvailability(
            @ToolParam(description = "確認する商品 ID のリスト") List<String> productIds,
            @ToolParam(description = "必要な数量（デフォルト: 1）") int requiredQuantity) {
        log.info("Tool checkInventoryAvailability: productIds={}", productIds.size());
        return productIds.stream()
                .map(id -> enrichWithRouting(inventoryClient.getStock(id), requiredQuantity))
                .toList();
    }

    /**
     * ツール2: 在庫を一時予約ロックする（カート追加時）。
     * 二重購入を防ぐために TTL 付きで在庫をロックする。
     */
    @Tool(description = """
            注文 ID に紐づけて在庫を一時予約ロックする。
            reservationTtlMinutes 分間（デフォルト: 30分）ロックされ、
            期間内に決済完了しない場合は自動解除される。
            カート構築ステップで必ず呼び出すこと。
            """)
    public ReservationResult reserveInventory(
            @ToolParam(description = "注文 ID") String orderId,
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "予約アイテムリスト（商品ID と数量）") List<ReservationRequest.ReservationItem> items,
            @ToolParam(description = "予約有効期間（分）。デフォルト: 30") int ttlMinutes) {
        log.info("Tool reserveInventory: orderId={}, itemCount={}", orderId, items.size());
        var request = new ReservationRequest(orderId, userId, items, ttlMinutes);
        return inventoryClient.reserve(request);
    }

    /**
     * ツール3: 在庫不足アラート一覧を取得する（管理者向け監視）。
     * 閾値未満の全商品をリストアップする。
     */
    @Tool(description = """
            在庫が LOW_STOCK_THRESHOLD（5個）未満の全商品のアラートを取得する。
            管理者ダッシュボード、自動補充ワークフローで使用する。
            在庫切れが予測される商品の補充提案も含む。
            """)
    public List<InventoryAlert> getLowStockAlerts() {
        log.info("Tool getLowStockAlerts called");
        return inventoryClient.getLowStockItems(LOW_STOCK_THRESHOLD);
    }

    /**
     * ツール4: 代替製品候補を取得する（在庫切れ時のフォールバック）。
     * 同カテゴリ・同スキルレベルの在庫ありの代替商品を返す。
     */
    @Tool(description = """
            在庫切れ商品の代替製品候補を取得する。
            同じカテゴリ・スキルレベルで在庫ありの製品を最大5件返す。
            Equipment Matching Agent が代替推奨時に呼び出す。
            """)
    public List<String> getAlternativeProducts(
            @ToolParam(description = "在庫切れの商品 ID") String outOfStockProductId,
            @ToolParam(description = "商品カテゴリ、例: スキー板") String category,
            @ToolParam(description = "スキルレベル、例: BEGINNER") String skillLevel) {
        log.info("Tool getAlternativeProducts: productId={}, category={}", outOfStockProductId, category);
        return inventoryClient.findAlternatives(outOfStockProductId, category, skillLevel);
    }

    /**
     * Routing Workflow: 在庫状態に応じた情報を付与する。
     */
    private InventoryStatus enrichWithRouting(InventoryStatus rawStatus, int required) {
        String status;
        if (rawStatus.stockQuantity() == 0) {
            status = "OUT_OF_STOCK";
        } else if (rawStatus.stockQuantity() < required) {
            status = "OUT_OF_STOCK";
        } else if (rawStatus.stockQuantity() < LOW_STOCK_THRESHOLD) {
            status = "LOW_STOCK";
        } else {
            status = "AVAILABLE";
        }

        boolean isReservable = !"OUT_OF_STOCK".equals(status);
        InventoryAlert alert = "LOW_STOCK".equals(status)
                ? new InventoryAlert(
                        java.util.UUID.randomUUID().toString(),
                        rawStatus.productId(),
                        "WARNING",
                        "在庫が残り %d 点です".formatted(rawStatus.stockQuantity()),
                        rawStatus.stockQuantity(),
                        LOW_STOCK_THRESHOLD,
                        java.time.Instant.now())
                : null;

        return new InventoryStatus(
                rawStatus.productId(), rawStatus.productName(),
                rawStatus.stockQuantity(), status, isReservable,
                rawStatus.estimatedRestockDate(), rawStatus.alternativeProductIds(), alert);
    }
}
```

---

### 4.3 InventoryMonitoringAgentService

```java
// service/InventoryMonitoringAgentService.java
package com.example.skishop.agent.inventory.service;

import com.example.skishop.agent.common.dto.*;
import com.example.skishop.agent.inventory.tool.InventoryMonitoringToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryMonitoringAgentService {

    private static final Logger log = LoggerFactory.getLogger(InventoryMonitoringAgentService.class);

    private static final String SYSTEM_PROMPT = """
            あなたは在庫管理 AI エージェントです。
            在庫ツールを使って以下を実行してください。

            1. 指定された商品の在庫状況を確認する（checkInventoryAvailability）
            2. LOW_STOCK の商品があれば代替品を提案する（getAlternativeProducts）
            3. OUT_OF_STOCK の商品は除外し、代替品があれば代わりに案内する
            4. 在庫ありの商品については予約可能かどうかを明示する

            在庫不足でも代替品で対応できる場合は顧客に選択肢を提示すること。
            """;

    private final ChatClient chatClient;
    private final InventoryMonitoringToolService toolService;

    public InventoryMonitoringAgentService(
            @org.springframework.beans.factory.annotation.Qualifier("inventoryAgentChatClient") ChatClient chatClient,
                                            InventoryMonitoringToolService toolService) {
        this.chatClient = chatClient;
        this.toolService = toolService;
    }

    public List<InventoryStatus> checkAndRoute(InventoryCheckRequest request) {
        log.info("InventoryMonitoringAgent checkAndRoute: {} products", request.productIds().size());
        return toolService.checkInventoryAvailability(request.productIds(), request.requiredQuantity());
    }

    public ReservationResult reserve(ReservationRequest request) {
        log.info("InventoryMonitoringAgent reserve: orderId={}", request.orderId());
        return toolService.reserveInventory(
                request.orderId(), request.userId(), request.items(), request.reservationTtlMinutes());
    }

    /**
     * 在庫不足時の AI サポート: ChatClient を使って代替製品を提案する。
     */
    public String suggestAlternatives(List<InventoryStatus> unavailableItems) {
        log.info("InventoryMonitoringAgent suggestAlternatives: {} items", unavailableItems.size());
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("以下の製品が在庫不足です。代替品を提案してください: " + unavailableItems)
                .tools(toolService)
                .call()
                .content();
    }
}
```

---

### 4.4 InventoryMonitoringAgentConfig

```java
// config/InventoryMonitoringAgentConfig.java
package com.example.skishop.agent.inventory.config;

import com.example.skishop.agent.inventory.tool.InventoryMonitoringToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InventoryMonitoringAgentConfig {

    @Bean("inventoryAgentChatClient")
    public ChatClient inventoryAgentChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * オーケストレーターが在庫監視 Agent の全ツールを一括登録するための ToolCallback[]。
     */
    @Bean("inventoryAgentToolCallbacks")
    public ToolCallback[] inventoryAgentToolCallbacks(InventoryMonitoringToolService toolService) {
        return ToolCallbacks.from(toolService);
    }
}
```

---

### 4.5 InventoryMonitoringController

```java
// controller/InventoryMonitoringController.java
package com.example.skishop.agent.inventory.controller;

import com.example.skishop.agent.common.dto.*;
import com.example.skishop.agent.inventory.service.InventoryMonitoringAgentService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents/inventory")
@ConditionalOnProperty(name = "agents.web.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryMonitoringController {

    private final InventoryMonitoringAgentService service;

    public InventoryMonitoringController(InventoryMonitoringAgentService service) {
        this.service = service;
    }

    @PostMapping("/check")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<List<InventoryStatus>> check(
            @Valid @RequestBody InventoryCheckRequest request) {
        return ResponseEntity.ok(service.checkAndRoute(request));
    }

    @PostMapping("/reserve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<ReservationResult> reserve(
            @Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.ok(service.reserve(request));
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<InventoryAlert>> getAlerts() {
        return ResponseEntity.ok(service.getToolService().getLowStockAlerts());
    }
}
```

---

### 4.6 InventoryMonitoringAgentSecurityConfig

> **ハイブリッド規約**: 本 `SecurityConfig` は **分散モードのみ有効化**される。モノリス時は `agent-runtime-monolith` 側の単一 SecurityConfig が処理する。

```java
// config/InventoryMonitoringAgentSecurityConfig.java
package com.example.skishop.agent.inventory.config;

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
public class InventoryMonitoringAgentSecurityConfig {

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

### 4.7 InventoryMonitoringAgentAutoConfiguration

```java
// config/InventoryMonitoringAgentAutoConfiguration.java
package com.example.skishop.agent.inventory.config;

import com.example.skishop.agent.inventory.controller.InventoryMonitoringController;
import com.example.skishop.agent.inventory.service.InventoryMonitoringAgentService;
import com.example.skishop.agent.inventory.tool.InventoryMonitoringToolService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    InventoryMonitoringToolService.class,
    InventoryMonitoringAgentService.class,
    InventoryMonitoringAgentConfig.class,
    InventoryMonitoringController.class,
    InventoryMonitoringAgentSecurityConfig.class
})
public class InventoryMonitoringAgentAutoConfiguration {
}
```

登録: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` に `com.example.skishop.agent.inventory.config.InventoryMonitoringAgentAutoConfiguration` を記載。

---

## 5. application.properties

> **ハイブリッド規約**: 本ファイルは **分散モード用テンプレート**である。モノリスモードでは `agent-runtime-monolith/src/main/resources/application.yml` に統合される。

```properties
spring.application.name=inventory-monitoring-agent
server.port=8103

agents.deployment.mode=distributed
agents.web.enabled=true

spring.ai.azure.openai.api-key=${AZURE_OPENAI_API_KEY:}
spring.ai.azure.openai.endpoint=${AZURE_OPENAI_ENDPOINT:}
spring.ai.azure.openai.chat.options.deployment-name=${AZURE_OPENAI_DEPLOYMENT:gpt-4o}
spring.ai.azure.openai.chat.options.temperature=0.0
spring.ai.azure.openai.chat.options.max-completion-tokens=1024

services.inventory-management.base-url=${INVENTORY_MANAGEMENT_SERVICE_URL:http://localhost:8082}
services.internal-api-key=${INTERNAL_API_KEY:}

# 在庫監視しきい値
inventory.low-stock-threshold=5
inventory.reservation-ttl-minutes=30

jwt.secret=${JWT_SECRET:}
management.endpoints.web.exposure.include=health,info,prometheus
```

---

## 6. テスト設計

```java
@ExtendWith(MockitoExtension.class)
class InventoryMonitoringToolServiceTest {

    @Test
    @DisplayName("checkInventoryAvailability: 在庫0件で OUT_OF_STOCK になる")
    void should_returnOutOfStock_when_stockIsZero() {
        // stockQuantity=0 → availabilityStatus="OUT_OF_STOCK", isReservable=false
    }

    @Test
    @DisplayName("checkInventoryAvailability: 在庫3件・しきい値5件で LOW_STOCK かつアラートあり")
    void should_returnLowStockWithAlert_when_stockBelowThreshold() {
        // stockQuantity=3, threshold=5 → status="LOW_STOCK", alert.severity="WARNING"
    }

    @Test
    @DisplayName("reserveInventory: 在庫不足時は failedProductIds にリストされる")
    void should_includeFailedProductIds_when_insufficientStock() {
        // 予約リクエストの数量が在庫を超える場合
    }
}
```

---

## 7. セキュリティ考慮事項

| リスク | 対策 |
|--------|------|
| A01 Access Control | 予約 API は `AGENT` ロール必須。在庫アラートは `ADMIN` / `MANAGER` のみ |
| A04 Insecure Design | 予約ロックは TTL（30分）付き。期限切れの予約は自動解除される |
| 競合状態 | inventory-management-service 側で楽観的ロックを使用（バージョン管理）|

### 7.1 Orchestrator からの認証

**モノリスモード**: Orchestrator は同一 JVM 内で Bean を直接呼び出すため、ネットワーク認証は不要。

**分散モード**: Orchestrator は `X-Internal-Api-Key` を付与して `/api/v1/agents/inventory/check` および `/reserve` を呼び出す。§4.6 の `InventoryMonitoringAgentSecurityConfig`（`@ConditionalOnProperty(distributed)` 付き）に `InternalApiKeyAuthenticationFilter`（common-lib）を登録し `ROLE_AGENT` を付与する。詳細は [orchestrator-agent-design.md §11.3](orchestrator-agent-design.md) と [hybrid-deployment-design.md §7](hybrid-deployment-design.md) を参照。

### 7.2 Orchestrator から送信されるリクエスト例

**在庫確認**（`POST /check`）:
```json
{ "productIds": ["ski-001", "wear-014"], "requiredQuantity": 1 }
```

**在庫予約**（`POST /reserve`）:
```json
{
  "orderId": "ord-xxx", "userId": "u001",
  "items": [ { "productId": "ski-001", "quantity": 1 },
             { "productId": "wear-014", "quantity": 1 } ],
  "reservationTtlMinutes": 30
}
```

---

## 8. 参照リソース

- [Agentic Patterns - Routing Workflow](https://github.com/spring-projects/spring-ai-examples/tree/main/agentic-patterns/routing)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
