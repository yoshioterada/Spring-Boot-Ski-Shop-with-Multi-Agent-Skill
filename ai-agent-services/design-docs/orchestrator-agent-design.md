# Orchestrator Agent 詳細設計書

## 1. 概要

### 1.1 目的

**Orchestrator Agent** は、アーキテクチャ図の「Planning & Orchestration」層を実装する中核エージェントである。顧客の購入最適化リクエストを受け取り、各 Worker Agent（Customer Intent, Weather, Equipment Matching, Inventory Monitoring, Dynamic Pricing, Coupon Optimization）を計画的に協調させて、最終的な推奨カート・価格・クーポンのセットを返す。

### 1.2 アーキテクチャ上の位置づけ

シーケンス図の全ステップ（1〜11）を制御する上位エージェント。

### 1.3 デプロイメントモード（ハイブリッド構成）

本 Agent は [hybrid-deployment-design.md](hybrid-deployment-design.md) に定義されたハイブリッド構成に従う。`agents.deployment.mode` プロパティで 2 つのモードを切替える:

| モード | 値 | Orchestrator → Worker 呼び出し | 認証 | ポート |
|------|---|----------------------------|------|-----|
| **モノリス（デフォルト）** | `monolith` | Bean 直接注入（`LocalWorkerAgentInvoker`） | 不要 | 8100（agent-runtime-monolith） |
| **分散** | `distributed` | REST + `X-Internal-Api-Key`（`RemoteWorkerAgentInvoker`） | 必須 | 8106（orchestrator-standalone） |

Orchestrator はどちらのモードでも `WorkerAgentInvoker` インターフェースを介して Worker を呼び出すため、Tool 定義やサービスロジックはモード選択を意識しない。

```
Customer / UI
    │
    │ POST /api/v1/orchestrator/recommend
    │ { "userId": "...", "message": "苗場に来週行くので板とウェアを揃えたい" }
    ▼
Orchestrator Agent ← 本設計書対象
    │
    │ Orchestrator-Workers パターン:
    │
    │ Step1-3: CustomerIntentAgent.analyze()           → CustomerIntentResult
    │ Step4-5: UserManagementService (JWT) 経由         → UserProfile
    │ Step6:   [並列] WeatherAgent + EquipmentMatchingAgent → WeatherResult + EquipmentMatchResult
    │ Step7-8: InventoryMonitoringAgent.check()        → InventoryStatus[]
    │ Step9:   [並列] DynamicPricingAgent + CouponOptimizationAgent → Prices + CouponResult
    │ Step10:  PaymentCartService.buildCart()          → QuoteSummary
    │ Step11:  InventoryMonitoringAgent.reserve()      → ReservationResult
    │
    └── OrchestratorResponse（推奨カート + 価格 + クーポン + 在庫確認）
```

---

## 2. Agentic Pattern の選択

**Orchestrator-Workers パターン**を採用する。

オーケストレーターは `ChatClient` に全 Worker Agent の `ToolCallback[]` を登録し、GPT が計画を立てて各 Worker を適切な順序・組み合わせで呼び出す。

```
Orchestrator ChatClient
    .toolCallbacks(customerIntentAgentToolCallbacks)
    .toolCallbacks(weatherAgentToolCallbacks)
    .toolCallbacks(equipmentAgentToolCallbacks)
    .toolCallbacks(inventoryAgentToolCallbacks)
    .toolCallbacks(pricingAgentToolCallbacks)
    .toolCallbacks(couponAgentToolCallbacks)
    .call()
    → GPT が計画を立て、必要な Worker ツールを連鎖的に呼び出す
```

---

## 3. モジュール構成

> **重要**: 本モジュールは **library jar**（`packaging=jar` + `spring-boot-maven-plugin` の `repackage` を skip）として作成し、実行可能 jar は `agent-runtime-monolith`（デフォルト）または `agent-runtime-standalone/orchestrator-standalone`（分散時）が生成する。モジュール規約の詳細は [hybrid-deployment-design.md §2](hybrid-deployment-design.md) を参照。

```
ai-agent-services/
├── orchestrator-agent/                       # library jar
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/example/skishop/agent/orchestrator/
│       │   ├── config/
│       │   │   ├── OrchestratorAgentAutoConfiguration.java   # @AutoConfiguration + @Import
│       │   │   ├── OrchestratorAgentConfig.java             # ChatClient/ToolCallback Bean（プレフィックス名）
│       │   │   └── OrchestratorSecurityConfig.java          # @ConditionalOnProperty(distributed)
│       │   ├── invoker/
│       │   │   ├── WorkerAgentInvoker.java                  # 抽象インターフェース
│       │   │   ├── LocalWorkerAgentInvoker.java             # @ConditionalOnProperty(monolith, matchIfMissing=true)
│       │   │   └── RemoteWorkerAgentInvoker.java            # @ConditionalOnProperty(distributed)
│       │   ├── service/
│       │   │   └── OrchestratorAgentService.java
│       │   ├── tool/
│       │   │   └── OrchestratorWorkerTools.java             # WorkerAgentInvoker 経由
│       │   ├── client/
│       │   │   ├── WorkerAgentRestClient.java               # @ConditionalOnProperty(distributed)
│       │   │   ├── UserManagementClient.java
│       │   │   └── PaymentCartClient.java
│       │   ├── controller/
│       │   │   └── OrchestratorController.java              # @ConditionalOnProperty(agents.web.enabled, matchIfMissing=true)
│       │   └── dto/
│       │       ├── OrchestratorRequest.java
│       │       ├── OrchestratorResponse.java
│       │       ├── QuoteSummary.java
│       │       └── WorkflowStepResult.java
│       └── resources/
│           └── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
└── agent-runtime-standalone/
    └── orchestrator-standalone/                  # 分散時のみ executable jar
        ├── pom.xml
        ├── Dockerfile
        └── src/main/
            ├── java/com/example/skishop/agent/orchestrator/standalone/
            │   └── OrchestratorStandaloneApplication.java
            └── resources/
                └── application.yml                       # agents.deployment.mode=distributed
```

**共有 DTO の移動**: `OrchestratorRequest` / `OrchestratorResponse` / `QuoteSummary` / `WorkflowStepResult` は Orchestrator 固有のため本モジュールに残す。Worker と共有される全 DTO（`CustomerIntentResult`, `WeatherAgentResponse` 等）は `agent-common` に移動済み（[hybrid-deployment-design.md §8](hybrid-deployment-design.md) 参照）。

---

## 4. クラス詳細設計

### 4.1 DTO 設計

```java
// dto/OrchestratorRequest.java
package com.example.skishop.agent.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrchestratorRequest(
        @NotBlank String userId,

        @NotBlank
        @Size(max = 2000)
        String message,              // 顧客の自然言語リクエスト

        String sessionId,            // 会話セッション（任意）
        String couponCode,           // 入力済みクーポンコード（任意）
        boolean usePoints            // ポイント使用希望（デフォルト: false）
) {}
```

```java
// dto/QuoteSummary.java
package com.example.skishop.agent.orchestrator.dto;

import java.math.BigDecimal;
import java.util.List;

public record QuoteSummary(
        String orderId,
        List<QuoteItem> items,
        BigDecimal subtotal,
        BigDecimal couponDiscount,
        BigDecimal pointDiscount,
        BigDecimal totalAmount,
        String reservationId,       // 在庫予約 ID（30分有効）
        java.time.Instant reservationExpiresAt
) {
    public record QuoteItem(
            String productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String matchReason
    ) {}
}
```

```java
// dto/OrchestratorResponse.java
package com.example.skishop.agent.orchestrator.dto;

import java.time.Instant;

public record OrchestratorResponse(
        String userId,
        String sessionId,
        QuoteSummary quote,
        String intentSummary,             // Customer Intent Agent の解析結果サマリー
        String weatherSummary,            // Weather Agent の気象サマリー
        String equipmentRecommendation,   // Equipment Matching Agent の推奨理由
        String couponSummary,             // Coupon Optimization Agent の適用クーポン説明
        String orchestrationSummary,      // Orchestrator が生成した全体サマリー（日本語）
        Instant generatedAt
) {}
```

```java
// dto/WorkflowStepResult.java
package com.example.skishop.agent.orchestrator.dto;

public record WorkflowStepResult(
        String stepName,
        boolean isSuccess,
        String summary,
        Object data
) {}
```

---

### 4.2 OrchestratorAgentConfig（全 Worker の ToolCallback 集約）

```java
// config/OrchestratorAgentConfig.java
package com.example.skishop.agent.orchestrator.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrchestratorAgentConfig {

    /**
     * Orchestrator 専用 ChatClient。
     * 全 Worker Agent の ToolCallback[] を defaultTools として登録する。
     *
     * Worker Agent の ToolCallback[] は各 Agent のモジュールで Bean 定義されており、
     * Spring DI でここに自動注入される。
     *
     * 各 Worker Agent の ToolCallback[] Bean 名:
     *   - customerIntentAgentToolCallbacks  (customer-intent-agent モジュール)
     *   - weatherAgentToolCallbacks         (weather-agent モジュール)
     *   - equipmentAgentToolCallbacks       (equipment-matching-agent モジュール)
     *   - inventoryAgentToolCallbacks       (inventory-monitoring-agent モジュール)
     *   - pricingAgentToolCallbacks         (dynamic-pricing-agent モジュール)
     *   - couponAgentToolCallbacks          (coupon-optimization-agent モジュール)
     *
     * ※ ハイブリッド構成（hybrid-deployment-design.md §3）に従い、Orchestrator は
     *   {@code WorkerAgentInvoker} 経由で Worker を呼ぶ {@link OrchestratorWorkerTools}
     *   のみを Tool として登録する。モノリス時は in-JVM Bean 直接呼び出し、
     *   分散時は REST 呼び出しが {@code @ConditionalOnProperty} により自動選択される。
     *   Worker モジュール側の {@code *AgentToolCallbacks} Bean は、各 Worker を
     *   single-Agent モードで standalone 起動する場合や、将来 ChatClient で
     *   Worker 内部 Tool を直接呼ぶ場合のために維持する（現状 Orchestrator では使用しない）。
     */
    @Bean("orchestratorChatClient")
    public ChatClient orchestratorChatClient(
            ChatClient.Builder builder,
            ToolCallback[] orchestratorWorkerToolCallbacks) {
        return builder
                .defaultTools(orchestratorWorkerToolCallbacks)
                .build();
    }

    /**
     * Orchestrator が GPT に渡す唯一の Tool セット。
     * {@link OrchestratorWorkerTools} は内部で {@code WorkerAgentInvoker} を呼ぶため、
     * Orchestrator から見た呼び出しインターフェースはモード非依存となる。
     */
    @Bean("orchestratorWorkerToolCallbacks")
    public ToolCallback[] orchestratorWorkerToolCallbacks(
            OrchestratorWorkerTools orchestratorWorkerTools) {
        return org.springframework.ai.tool.ToolCallbacks.from(orchestratorWorkerTools);
    }
}
```

---

### 4.3 OrchestratorWorkerTools（Worker Agent への呼び出し @Tool 群）

オーケストレーターは `WorkerAgentInvoker` インターフェースを介して各 Worker を呼び出すため、**モノリス時は Bean 直接呼び出し（in-JVM、シリアライズなし）**、**分散時は REST 経由**となる。`@Tool` 定義側はモード選択を意識しない（[hybrid-deployment-design.md §3](hybrid-deployment-design.md) §3.1〜§3.4 参照）。

> **依存差分**: 旧設計では `WorkerAgentRestClient` を直接注入していたが、本改訂で `WorkerAgentInvoker` 注入に変更する。`WorkerAgentRestClient` は `RemoteWorkerAgentInvoker` の内部依存となり、`@ConditionalOnProperty(name="agents.deployment.mode", havingValue="distributed")` でのみ Bean 登録される（§4.5）。

```java
// tool/OrchestratorWorkerTools.java
package com.example.skishop.agent.orchestrator.tool;

import com.example.skishop.agent.orchestrator.invoker.WorkerAgentInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
// tool/OrchestratorWorkerTools.java
package com.example.skishop.agent.orchestrator.tool;

import com.example.skishop.agent.common.dto.*;
import com.example.skishop.agent.orchestrator.client.PaymentCartClient;
import com.example.skishop.agent.orchestrator.invoker.WorkerAgentInvoker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 全 Worker Agent を @Tool としてラップするクラス。
 * Orchestrator の ChatClient に登録され、GPT が自律的に各 Worker を呼び出せるようにする。
 *
 * モード非依存:
 *   - モノリス時: {@link WorkerAgentInvoker} は {@code LocalWorkerAgentInvoker} → 各 Worker Bean を直接呼ぶ
 *   - 分散時:    {@link WorkerAgentInvoker} は {@code RemoteWorkerAgentInvoker} → {@code WorkerAgentRestClient} 経由
 *
 * @Tool は構造化文字列で返却する規約のため、各 Worker からの戻り値は Jackson で JSON 文字列化する。
 * payment-cart-service への呼び出しのみ Worker ではなく既存サービスのため {@link PaymentCartClient}（Worker Invoker と独立）を使用する。
 */
@Component
public class OrchestratorWorkerTools {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorWorkerTools.class);

    private final WorkerAgentInvoker invoker;
    private final PaymentCartClient paymentCart;
    private final ObjectMapper objectMapper;

    public OrchestratorWorkerTools(WorkerAgentInvoker invoker,
                                    PaymentCartClient paymentCart,
                                    ObjectMapper objectMapper) {
        this.invoker = invoker;
        this.paymentCart = paymentCart;
        this.objectMapper = objectMapper;
    }

    // ─── Customer Intent Agent ────────────────────────────────────

    @Tool(description = """
            顧客の自然言語リクエストを解析して、意図・制約（場所/日程/予算/スキルレベル/商品カテゴリ）を
            構造化データとして抽出する。
            ワークフローの最初に必ず呼び出すこと。
            返り値: CustomerIntentResult（JSON）
            """)
    public String analyzeCustomerIntent(
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "顧客の自然言語メッセージ") String userMessage,
            @ToolParam(description = "セッション ID（任意）") @Nullable String sessionId) {
        log.info("Tool analyzeCustomerIntent: userId={}", userId);
        return toJson(invoker.invokeCustomerIntent(userId, userMessage, sessionId));
    }

    // ─── Weather Agent ────────────────────────────────────────────

    @Tool(description = """
            指定したスキーリゾート・場所の気象情報・積雪状況・天気予報を取得する。
            スキー適性（HIGH/MEDIUM/LOW）と推奨ギアレベルも返す。
            destination が判明した時点で呼び出すこと。
            返り値: WeatherAgentResponse（JSON）
            """)
    public String getWeatherAndSkiConditions(
            @ToolParam(description = "スキーリゾートまたは場所名、例: Naeba, Niigata, Japan") String location,
            @ToolParam(description = "リゾート名（任意、location と異なる場合のみ指定）") @Nullable String resort,
            @ToolParam(description = "予報日数（デフォルト 7）") @Nullable Integer forecastDays) {
        log.info("Tool getWeatherAndSkiConditions: location={}", location);
        return toJson(invoker.invokeWeather(location, resort, forecastDays));
    }

    // ─── Equipment Matching Agent ─────────────────────────────────

    @Tool(description = """
            顧客のスキルレベル・体型・気象コンディション・予算に基づき、
            最適なスキー用品（スキー板・ブーツ・ウェア等）をランキング形式で推奨する。
            analyzeCustomerIntent と getWeatherAndSkiConditions の後に呼び出すこと。
            返り値: EquipmentMatchResult（JSON）
            """)
    public String matchEquipment(
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "スキルレベル: BEGINNER/INTERMEDIATE/ADVANCED/EXPERT") String skillLevel,
            @ToolParam(description = "希望商品カテゴリ（カンマ区切り）、例: スキー板,ウェア,ブーツ") String categories,
            @ToolParam(description = "予算（円）。未指定の場合は null") @Nullable Integer budgetYen,
            @ToolParam(description = "行き先リゾート名") @Nullable String destination,
            @ToolParam(description = "カテゴリ毎の推奨数量（デフォルト 1）") int quantity) {
        log.info("Tool matchEquipment: userId={}, skill={}, qty={}", userId, skillLevel, quantity);
        var request = new EquipmentMatchRequest(
                userId,
                skillLevel,
                /* bodyMeasurements */ null,
                Arrays.stream(categories.split(",")).map(String::trim).toList(),
                budgetYen,
                destination,
                /* includeRental   */ false,
                /* includePurchase */ true,
                quantity > 0 ? quantity : 1);
        return toJson(invoker.invokeEquipmentMatching(request));
    }

    // ─── Inventory Monitoring Agent ───────────────────────────────

    @Tool(description = """
            指定した商品 ID リストの在庫状況を確認する。
            AVAILABLE / LOW_STOCK / OUT_OF_STOCK を返す。
            Equipment Matching Agent の推奨結果を受けて、在庫確認のために呼び出すこと。
            返り値: List<InventoryStatus>（JSON）
            """)
    public String checkInventoryAvailability(
            @ToolParam(description = "確認する商品 ID のカンマ区切りリスト") String productIds,
            @ToolParam(description = "必要数量（デフォルト: 1）") int quantity) {
        log.info("Tool checkInventoryAvailability: productIds={}", productIds);
        var ids = Arrays.stream(productIds.split(",")).map(String::trim).toList();
        return toJson(invoker.invokeInventoryCheck(ids, quantity > 0 ? quantity : 1));
    }

    @Tool(description = """
            注文に対して在庫を一時予約ロックする（30分有効）。
            カート確定直前に呼び出すこと。
            返り値: ReservationResult（JSON）
            """)
    public String reserveInventory(
            @ToolParam(description = "注文 ID") String orderId,
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "商品ID:数量 のカンマ区切りリスト、例: prod1:2,prod2:1") String productQuantities) {
        log.info("Tool reserveInventory: orderId={}", orderId);
        var items = parseProductQuantities(productQuantities).entrySet().stream()
                .map(e -> new ReservationRequest.ReservationItem(e.getKey(), e.getValue()))
                .toList();
        var request = new ReservationRequest(orderId, userId, items, /* ttlMinutes */ 30);
        return toJson(invoker.invokeInventoryReservation(request));
    }

    // ─── Dynamic Pricing Agent ────────────────────────────────────

    @Tool(description = """
            カート内の全商品の動的価格を一括算出する。
            需要・天候・在庫・顧客ティアを考慮した最終価格を返す。
            在庫確認後、クーポン適用前に呼び出すこと。
            返り値: List<PricingResult>（JSON）
            """)
    public String calculateDynamicPrices(
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "顧客ティア: BRONZE/SILVER/GOLD/PLATINUM") String customerTier,
            @ToolParam(description = "商品ID:数量 のカンマ区切りリスト") String productQuantities,
            @ToolParam(description = "リゾート名（気象調整用）") @Nullable String resortLocation) {
        log.info("Tool calculateDynamicPrices: userId={}, tier={}", userId, customerTier);
        var items = parseProductQuantities(productQuantities).entrySet().stream()
                .map(e -> new BulkPricingRequest.BulkPricingItem(e.getKey(), e.getValue()))
                .toList();
        var request = new BulkPricingRequest(userId, customerTier, items, resortLocation);
        return toJson(invoker.invokeDynamicPricing(request));
    }

    // ─── Coupon Optimization Agent ────────────────────────────────

    @Tool(description = """
            カート内容に対して最適なクーポン・ポイント組み合わせを選択する。
            Dynamic Pricing 後の価格に対してクーポンを適用する。
            calculateDynamicPrices の後に必ず呼び出すこと。
            cartItems は calculateDynamicPrices の結果から構築する。
            各カートアイテムは「productId|productName|category|quantity|dynamicUnitPrice」形式で
            セミコロン区切りで連結すること。例:
              "prod1|スキー板A|スキー板|1|45000;prod2|ウェアB|ウェア|1|18000"
            返り値: CouponOptimizationResult（JSON）
            """)
    public String optimizeCoupons(
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "注文 ID") String orderId,
            @ToolParam(description = "カートアイテムの構造化文字列（productId|name|category|qty|unitPrice をセミコロン区切り）") String cartItemsEncoded,
            @ToolParam(description = "顧客ティア") String customerTier,
            @ToolParam(description = "ポイント使用希望: true/false") boolean usePoints,
            @ToolParam(description = "入力済みクーポンコード（任意）") @Nullable String couponCode) {
        log.info("Tool optimizeCoupons: userId={}, orderId={}", userId, orderId);
        var cartItems = parseCartItemsEncoded(cartItemsEncoded);
        var request = new CouponOptimizationRequest(userId, orderId, cartItems, customerTier, usePoints, couponCode);
        return toJson(invoker.invokeCouponOptimization(request));
    }

    // ─── Payment Cart Service（Step10: カート構築・確定）─────────

    @Tool(description = """
            最終決定したカートをショッピングカートサービスに構築し、注文 ID を確定する。
            Coupon Optimization 完了後、reserveInventory の前に呼び出すこと。
            返り値: BuildCartResult（orderId, subtotal, totalAmount, status を含む JSON）
            """)
    public String buildCart(
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "注文 ID（orchestrate 開始時に発行されたもの）") String orderId,
            @ToolParam(description = "カートアイテムの構造化文字列（productId|name|qty|unitPrice をセミコロン区切り）") String cartItemsEncoded,
            @ToolParam(description = "適用済みクーポン割引額") BigDecimal couponDiscount,
            @ToolParam(description = "適用済みポイント割引額") BigDecimal pointDiscount) {
        log.info("Tool buildCart: userId={}, orderId={}", userId, orderId);
        // payment-cart-service は Worker Agent ではなく既存サービスのため、専用 Client を使う
        return toJson(paymentCart.buildCart(userId, orderId, cartItemsEncoded, couponDiscount, pointDiscount));
    }

    // ─── 内部ヘルパー ─────────────────────────────────────────────

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize tool result to JSON: {}", e.getMessage());
            throw new IllegalStateException("Tool result serialization failed", e);
        }
    }

    /** "prod1:2,prod2:1" → Map{prod1=2, prod2=1} */
    private Map<String, Integer> parseProductQuantities(String encoded) {
        return Arrays.stream(encoded.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.split(":"))
                .collect(java.util.stream.Collectors.toMap(
                        a -> a[0].trim(),
                        a -> Integer.parseInt(a[1].trim()),
                        (v1, v2) -> v1 + v2));
    }

    /** "prod1|name|cat|1|45000;prod2|..." → List<CartItemPricing> */
    private List<CartItemPricing> parseCartItemsEncoded(String encoded) {
        return Arrays.stream(encoded.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    String[] p = s.split("\\|");
                    if (p.length < 5) {
                        throw new IllegalArgumentException(
                                "cartItemsEncoded must be productId|name|category|qty|unitPrice; got: " + s);
                    }
                    int qty = Integer.parseInt(p[3].trim());
                    BigDecimal unit = new BigDecimal(p[4].trim());
                    return new CartItemPricing(
                            p[0].trim(), p[1].trim(), p[2].trim(), qty, unit,
                            unit.multiply(BigDecimal.valueOf(qty)));
                })
                .toList();
    }
}
```

---

### 4.4 OrchestratorAgentService（Orchestrator-Workers 実装）

```java
// service/OrchestratorAgentService.java
package com.example.skishop.agent.orchestrator.service;

import com.example.skishop.agent.orchestrator.client.UserManagementClient;
import com.example.skishop.agent.orchestrator.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrchestratorAgentService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorAgentService.class);

    /**
     * Orchestrator のシステムプロンプト。
     * GPT に全 Worker Agent の呼び出し順序・条件・最終目標を指示する。
     */
    private static final String ORCHESTRATOR_SYSTEM_PROMPT = """
            あなたはスキーショップの購入最適化オーケストレーター AI エージェントです。
            顧客の購入リクエストに対して、以下の Worker Agent を計画的に協調させて
            最適な推奨カートを構築してください。

            ワークフロー手順（必ずこの順序で実行すること）:

            Phase 1 - 意図解析:
              1. analyzeCustomerIntent を呼び出して顧客の意図・制約を抽出する

            Phase 2 - コンテキスト収集（並列実行推奨）:
              2. 行き先が判明したら getWeatherAndSkiConditions で気象情報を取得する
              3. 顧客のスキルレベル・希望カテゴリが判明したら matchEquipment で製品候補を取得する

            Phase 3 - 在庫確認:
              4. checkInventoryAvailability で推奨製品の在庫を確認する
              5. 在庫切れ製品は候補から除外し、代替品があれば採用する

            Phase 4 - 価格・クーポン決定:
              6. calculateDynamicPrices で動的価格を一括算出する
              7. optimizeCoupons で最適クーポンを選択する（cartItems は Phase4-6 の結果から構築）

            Phase 5 - カート構築・在庫予約:
              8. buildCart で payment-cart-service にカートを構築する
              9. reserveInventory で確定した製品の在庫を予約する（30分 TTL）

            最終出力（必須）:
            - 推奨カートの全製品・価格・割引をまとめた orchestrationSummary（日本語・300文字以内）
            - 各 Phase の結果サマリー

            エラー・在庫切れ・制約違反があっても、可能な限り代替案を提示して完了させること。
            予算を超える場合は最も優先度の高い製品から順に選択すること。
            """;

    private final ChatClient orchestratorChatClient;
    private final UserManagementClient userManagementClient;

    public OrchestratorAgentService(
            @org.springframework.beans.factory.annotation.Qualifier("orchestratorChatClient") ChatClient orchestratorChatClient,
            UserManagementClient userManagementClient) {
        this.orchestratorChatClient = orchestratorChatClient;
        this.userManagementClient = userManagementClient;
    }

    /**
     * メインエントリーポイント。
     * シーケンス図の全ステップを Orchestrator-Workers パターンで実行する。
     */
    public OrchestratorResponse orchestrate(OrchestratorRequest request, String jwtToken) {
        String orderId = UUID.randomUUID().toString();
        log.info("Orchestrator start: userId={}, orderId={}", request.userId(), orderId);

        // ステップ4-5: ユーザープロフィール・購入履歴を先に取得（JWT 付き）
        var userProfile = userManagementClient.getUserProfile(request.userId(), jwtToken);
        log.info("Orchestrator: userProfile fetched, tier={}", userProfile.customerTier());

        // Orchestrator-Workers: GPT が Worker Tools を自律的に呼び出すメインループ
        OrchestratorResponse response = orchestratorChatClient.prompt()
                .system(ORCHESTRATOR_SYSTEM_PROMPT)
                .user(buildUserPrompt(request, orderId, userProfile))
                .call()
                .entity(OrchestratorResponse.class);

        log.info("Orchestrator completed: orderId={}, totalAmount={}",
                orderId, response.quote().totalAmount());
        return response;
    }

    private String buildUserPrompt(OrchestratorRequest request, String orderId,
                                    UserManagementClient.UserProfile profile) {
        return """
                ユーザーID: %s
                注文ID: %s
                顧客ティア: %s
                過去購入カテゴリ: %s
                リクエスト: %s
                クーポンコード: %s
                ポイント使用希望: %s
                
                上記のワークフローを全 Phase 実行して、最適な購入プランを作成してください。
                """.formatted(
                request.userId(),
                orderId,
                profile.customerTier(),
                String.join("・", profile.purchasedCategories()),
                request.message(),
                request.couponCode() != null ? request.couponCode() : "なし",
                request.usePoints() ? "はい" : "いいえ");
    }
}
```

---

### 4.5 WorkerAgentRestClient（分散モード時のみ有効）

> **重要**: 本クラスは **分散モード（`agents.deployment.mode=distributed`）でのみ Bean 登録**される。モノリス時は `LocalWorkerAgentInvoker` が Worker Bean を直接呼び出すため、本クラスは生成されない。

```java
// client/WorkerAgentRestClient.java
package com.example.skishop.agent.orchestrator.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 全 Worker Agent の REST API を呼び出すクライアント（分散モード専用）。
 * RemoteWorkerAgentInvoker から使用される。
 */
@Component
@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "distributed")
public class WorkerAgentRestClient {

    private static final Logger log = LoggerFactory.getLogger(WorkerAgentRestClient.class);

    private final Map<String, RestClient> agentClients;

    public WorkerAgentRestClient(
            @Value("${services.customer-intent-agent.base-url}") String intentUrl,
            @Value("${services.weather-agent.base-url}") String weatherUrl,
            @Value("${services.equipment-matching-agent.base-url}") String equipmentUrl,
            @Value("${services.inventory-monitoring-agent.base-url}") String inventoryUrl,
            @Value("${services.dynamic-pricing-agent.base-url}") String pricingUrl,
            @Value("${services.coupon-optimization-agent.base-url}") String couponUrl,
            @Value("${services.payment-cart.base-url}") String paymentCartUrl,
            @Value("${services.internal-api-key}") String apiKey) {

        this.agentClients = Map.of(
                "intent",    buildClient(intentUrl, apiKey),
                "weather",   buildClient(weatherUrl, apiKey),
                "equipment", buildClient(equipmentUrl, apiKey),
                "inventory", buildClient(inventoryUrl, apiKey),
                "pricing",   buildClient(pricingUrl, apiKey),
                "coupon",    buildClient(couponUrl, apiKey));
        this.paymentCartClient = buildClient(paymentCartUrl, apiKey);
    }

    public String callCustomerIntentAgent(String userId, String message, String sessionId) {
        log.debug("Calling CustomerIntentAgent: userId={}", userId);
        var body = Map.of("userId", userId, "userMessage", message,
                "sessionId", sessionId != null ? sessionId : "");
        return agentClients.get("intent").post()
                .uri("/api/v1/agents/intent/analyze")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    public String callWeatherAgent(String location, String question) {
        log.debug("Calling WeatherAgent: location={}", location);
        var body = Map.of("location", location,
                "question", question != null ? question : "");
        return agentClients.get("weather").post()
                .uri("/api/v1/agents/weather/analyze")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    public String callEquipmentMatchingAgent(String userId, String skillLevel,
                                              String categories, Integer budgetYen,
                                              String destination, int quantity) {
        log.debug("Calling EquipmentMatchingAgent: userId={}, qty={}", userId, quantity);
        var body = new java.util.HashMap<String, Object>();
        body.put("userId", userId);
        body.put("skillLevel", skillLevel);
        body.put("desiredCategories", java.util.Arrays.asList(categories.split(",")));
        if (budgetYen != null) body.put("budgetYen", budgetYen);
        if (destination != null) body.put("destination", destination);
        body.put("includePurchase", true);
        body.put("includeRental", false);
        body.put("quantity", quantity);
        // bodyMeasurements は Orchestrator からは送信しない（Worker 側で user-management から取得）

        return agentClients.get("equipment").post()
                .uri("/api/v1/agents/equipment/match")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    public String callInventoryCheck(String productIds, int quantity) {
        log.debug("Calling InventoryMonitoringAgent: productIds={}", productIds);
        var body = Map.of(
                "productIds", java.util.Arrays.asList(productIds.split(",")),
                "requiredQuantity", quantity);
        return agentClients.get("inventory").post()
                .uri("/api/v1/agents/inventory/check")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    public String callInventoryReserve(String orderId, String userId, String productQuantities) {
        log.debug("Calling InventoryMonitoringAgent reserve: orderId={}", orderId);
        var items = java.util.Arrays.stream(productQuantities.split(","))
                .map(pq -> {
                    var parts = pq.split(":");
                    return Map.of("productId", parts[0],
                            "quantity", parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
                })
                .toList();
        var body = Map.of("orderId", orderId, "userId", userId,
                "items", items, "reservationTtlMinutes", 30);
        return agentClients.get("inventory").post()
                .uri("/api/v1/agents/inventory/reserve")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    public String callDynamicPricingAgent(String userId, String customerTier,
                                           String productQuantities, String resortLocation) {
        log.debug("Calling DynamicPricingAgent: userId={}", userId);
        var items = java.util.Arrays.stream(productQuantities.split(","))
                .map(pq -> {
                    var parts = pq.split(":");
                    return Map.of("productId", parts[0],
                            "quantity", parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
                })
                .toList();
        var body = new java.util.HashMap<String, Object>();
        body.put("userId", userId);
        body.put("customerTier", customerTier);
        body.put("items", items);
        if (resortLocation != null) body.put("resortLocation", resortLocation);
        return agentClients.get("pricing").post()
                .uri("/api/v1/agents/pricing/calculate/bulk")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    public String callCouponOptimizationAgent(String userId, String orderId,
                                               String cartItemsEncoded, String customerTier,
                                               boolean usePoints, String couponCode) {
        log.debug("Calling CouponOptimizationAgent: userId={}, orderId={}", userId, orderId);
        // "productId|name|category|qty|unitPrice;..." を List<CartItemPricing> にパース
        var cartItems = parseCartItems(cartItemsEncoded);
        var body = new java.util.HashMap<String, Object>();
        body.put("userId", userId);
        body.put("orderId", orderId);
        body.put("customerTier", customerTier);
        body.put("usePoints", usePoints);
        if (couponCode != null && !couponCode.isBlank()) body.put("couponCode", couponCode);
        body.put("cartItems", cartItems);
        return agentClients.get("coupon").post()
                .uri("/api/v1/agents/coupon/optimize")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    /**
     * payment-cart-service にカートを構築して最終誋求を確定する。
     * Step10 （buildCart Tool）の実体。
     */
    public String callPaymentCartBuild(String userId, String orderId,
                                        String cartItemsEncoded,
                                        java.math.BigDecimal couponDiscount,
                                        java.math.BigDecimal pointDiscount) {
        log.debug("Calling PaymentCartService buildCart: orderId={}", orderId);
        var items = parseCartItems(cartItemsEncoded);
        var body = new java.util.HashMap<String, Object>();
        body.put("userId", userId);
        body.put("orderId", orderId);
        body.put("items", items);
        body.put("couponDiscount", couponDiscount != null ? couponDiscount : java.math.BigDecimal.ZERO);
        body.put("pointDiscount", pointDiscount != null ? pointDiscount : java.math.BigDecimal.ZERO);
        return paymentCartClient.post()
                .uri("/api/v1/cart/build")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    /**
     * "productId|name|category|qty|unitPrice;..." 形式を Map リストに変換。
     */
    private java.util.List<java.util.Map<String, Object>> parseCartItems(String encoded) {
        if (encoded == null || encoded.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(encoded.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    var parts = s.split("\\|");
                    if (parts.length < 5) {
                        throw new IllegalArgumentException(
                                "cartItems の形式不正: " + s + " (期待: productId|name|category|qty|unitPrice)");
                    }
                    var item = new java.util.HashMap<String, Object>();
                    item.put("productId", parts[0]);
                    item.put("productName", parts[1]);
                    item.put("category", parts[2]);
                    int qty = Integer.parseInt(parts[3]);
                    java.math.BigDecimal unitPrice = new java.math.BigDecimal(parts[4]);
                    item.put("quantity", qty);
                    item.put("dynamicUnitPrice", unitPrice);
                    item.put("lineTotal", unitPrice.multiply(java.math.BigDecimal.valueOf(qty)));
                    return item;
                })
                .toList();
    }

    private RestClient buildClient(String baseUrl, String apiKey) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", apiKey)
                .defaultHeader("X-Caller-Service", "orchestrator-agent")
                .build();
    }

    /** payment-cart-service 用の RestClient（agentClients とは別途保持）。 */
    private final RestClient paymentCartClient;
}
```

> **設計改訂注記（モード分離の明確化）**: 上記 §4.5 の `WorkerAgentRestClient` は `@ConditionalOnProperty(distributed)` でのみ Bean 化されるため、**モノリス時は消滅する**。一方、payment-cart-service への呼び出し（`callPaymentCartBuild` / `loadDynamicPricedCart`）は **モノリス・分散の両方で必要**な既存サービス呼び出しであり、`@Tool buildCart` から参照される。実装フェーズではこの責務を **`PaymentCartClient`（§4.5.1、モード非依存）** に切り出し、`WorkerAgentRestClient` 側からは payment-cart 関連メソッドを削除すること。

### 4.5.1 PaymentCartClient（モード非依存・常時有効）

```java
// client/PaymentCartClient.java
package com.example.skishop.agent.orchestrator.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * payment-cart-service への呼び出し（モード非依存）。
 * Worker Agent 経由ではなく既存ドメインサービスのため、{@code agents.deployment.mode} に関わらず常時有効。
 * {@code X-Internal-Api-Key} は分散モード時のみ意味を持つが、既存サービス側は本キーを常時要求するため
 * モノリス時も同 header を付与する。
 */
@Component
public class PaymentCartClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentCartClient.class);

    private final RestClient restClient;

    public PaymentCartClient(@Value("${external-services.payment-cart}") String baseUrl,
                              @Value("${internal.api-key}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", apiKey)
                .defaultHeader("X-Caller-Service", "orchestrator-agent")
                .build();
    }

    /**
     * payment-cart-service にカートを構築・確定する。
     * @return BuildCartResult を JSON 文字列で返す（@Tool 規約）。
     */
    @SuppressWarnings("unchecked")
    public String buildCart(String userId, String orderId, String cartItemsEncoded,
                             BigDecimal couponDiscount, BigDecimal pointDiscount) {
        log.info("PaymentCart buildCart: userId={}, orderId={}", userId, orderId);
        var body = Map.of(
                "userId", userId,
                "orderId", orderId,
                "items", parseCartItems(cartItemsEncoded),
                "couponDiscount", couponDiscount,
                "pointDiscount", pointDiscount);
        Map<String, Object> result = restClient.post()
                .uri("/api/v1/cart/build")
                .body(body)
                .retrieve()
                .body(Map.class);
        return result == null ? "{}" : result.toString();
    }

    private List<Map<String, Object>> parseCartItems(String encoded) {
        return Arrays.stream(encoded.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    String[] p = s.split("\\|");
                    if (p.length < 4) {
                        throw new IllegalArgumentException("cartItemsEncoded format invalid: " + s);
                    }
                    Map<String, Object> item = new HashMap<>();
                    item.put("productId", p[0].trim());
                    item.put("productName", p[1].trim());
                    int qty = Integer.parseInt(p[2].trim());
                    BigDecimal unitPrice = new BigDecimal(p[3].trim());
                    item.put("quantity", qty);
                    item.put("dynamicUnitPrice", unitPrice);
                    item.put("lineTotal", unitPrice.multiply(BigDecimal.valueOf(qty)));
                    return item;
                })
                .toList();
    }
}
```

---

### 4.6 OrchestratorController

```java
// controller/OrchestratorController.java
package com.example.skishop.agent.orchestrator.controller;

import com.example.skishop.agent.orchestrator.dto.OrchestratorRequest;
import com.example.skishop.agent.orchestrator.dto.OrchestratorResponse;
import com.example.skishop.agent.orchestrator.service.OrchestratorAgentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orchestrator")
@ConditionalOnProperty(name = "agents.web.enabled", havingValue = "true", matchIfMissing = true)
public class OrchestratorController {

    private final OrchestratorAgentService service;

    public OrchestratorController(OrchestratorAgentService service) {
        this.service = service;
    }

    /**
     * 購入最適化ワークフローのメインエントリーポイント。
     * E-Commerce Platform およびカスタマーチャットから呼び出される。
     */
    @PostMapping("/recommend")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<OrchestratorResponse> recommend(
            @Valid @RequestBody OrchestratorRequest request,
            HttpServletRequest httpRequest) {
        // JWT トークンを下流 Worker Agent への内部呼び出し認証に使用
        String jwtToken = extractBearerToken(httpRequest);
        return ResponseEntity.ok(service.orchestrate(request, jwtToken));
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return "";
    }
}
```

---

### 4.7 UserManagementClient（ユーザープロフィール取得）

```java
// client/UserManagementClient.java
package com.example.skishop.agent.orchestrator.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * user-management-service / sales-management-service からユーザープロフィールを取得する。
 * シーケンス図 Step4-5（fetch user profile/history with JWT → return context payload）に対応。
 *
 * 注意: このクライアントは Orchestrator が直接呼び出し（@Tool ではない）、
 *      JWT トークン（顧客本人のもの）を Authorization ヘッダーで転送する。
 */
@Component
public class UserManagementClient {

    private static final Logger log = LoggerFactory.getLogger(UserManagementClient.class);

    private final RestClient restClient;

    public UserManagementClient(@Value("${services.user-management.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public UserProfile getUserProfile(String userId, String jwtToken) {
        log.debug("Fetching user profile: userId={}", userId);
        return restClient.get()
                .uri("/api/v1/users/{id}/profile", userId)
                .header("Authorization", "Bearer " + jwtToken)
                .retrieve()
                .body(UserProfile.class);
    }

    /**
     * Orchestrator が利用するユーザープロフィールの最小集合。
     * customerTier は coupon-optimization / dynamic-pricing にも使われる。
     */
    public record UserProfile(
            String userId,
            String displayName,
            String customerTier,                // "BRONZE" | "SILVER" | "GOLD" | "PLATINUM"
            List<String> purchasedCategories,   // 過去購入カテゴリ
            String preferredSkillLevel,         // 直近の自己申告スキル
            Integer pointBalance                // 利用可能ポイント残高
    ) {}
}
```

---

### 4.8 WorkerAgentInvoker（モード抽象化）

Orchestrator が Worker を呼び出す際の抽象インターフェース。実装は `LocalWorkerAgentInvoker`（モノリス・デフォルト）と `RemoteWorkerAgentInvoker`（分散）の 2 つで、`agents.deployment.mode` で自動選択される。詳細仕様は [hybrid-deployment-design.md §3](hybrid-deployment-design.md)。

```java
// invoker/WorkerAgentInvoker.java
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

```java
// invoker/LocalWorkerAgentInvoker.java（モノリス・デフォルト）
@Component
@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "monolith", matchIfMissing = true)
public class LocalWorkerAgentInvoker implements WorkerAgentInvoker {
    private final CustomerIntentAgentService customerIntent;
    private final WeatherAgentService weather;
    private final EquipmentMatchingAgentService equipmentMatching;
    private final InventoryMonitoringAgentService inventoryMonitoring;
    private final DynamicPricingAgentService dynamicPricing;
    private final CouponOptimizationAgentService couponOptimization;
    // 全コンストラクタ引数は @Autowired 経由で注入される（同 ApplicationContext 内）

    @Override
    public CustomerIntentResult invokeCustomerIntent(String userId, String userMessage, String sessionId) {
        return customerIntent.analyze(new CustomerIntentRequest(userId, userMessage, sessionId, "ja"));
    }
    // 他のメソッドも各 *AgentService の同等メソッドへ直接委譲
}
```

```java
// invoker/RemoteWorkerAgentInvoker.java（分散時）
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

---

## 5. application.properties

> **ハイブリッド規約**: 本ファイルは `agent-runtime-monolith`（モノリス・デフォルト）と `agent-runtime-standalone/orchestrator-standalone`（分散時）の両方で参照される共通設定例である。実際は各 runtime jar の `src/main/resources/application.yml` に統合し、library jar には同梱しない（[apply-existing-services.md §6](apply-existing-services.md)、[hybrid-deployment-design.md §6](hybrid-deployment-design.md) を参照）。`agents.deployment.mode` を環境変数 `AGENTS_DEPLOYMENT_MODE` で切り替え、Bean 構成（`LocalWorkerAgentInvoker` / `RemoteWorkerAgentInvoker`、`WorkerAgentRestClient` の有無）が `@ConditionalOnProperty` で自動選択される。

```properties
spring.application.name=orchestrator-agent
server.port=8106

# ─── デプロイモード（hybrid-deployment-design.md §1.3）─────────
# monolith: agent-runtime-monolith に同梱、Worker は in-JVM Bean 直接呼び出し
# distributed: orchestrator-standalone として単独起動、Worker は REST 呼び出し
agents.deployment.mode=${AGENTS_DEPLOYMENT_MODE:monolith}
agents.web.enabled=${AGENTS_WEB_ENABLED:true}

# Azure OpenAI（オーケストレーター用：より高性能なモデルを使用）
spring.ai.azure.openai.api-key=${AZURE_OPENAI_API_KEY:}
spring.ai.azure.openai.endpoint=${AZURE_OPENAI_ENDPOINT:}
spring.ai.azure.openai.chat.options.deployment-name=${AZURE_OPENAI_DEPLOYMENT:gpt-4o}
spring.ai.azure.openai.chat.options.temperature=0.3
spring.ai.azure.openai.chat.options.max-completion-tokens=4096

# ─── 全 Worker Agent の URL（分散モードのみ参照される）─────────
services.customer-intent-agent.base-url=${CUSTOMER_INTENT_AGENT_URL:http://localhost:8101}
services.weather-agent.base-url=${WEATHER_AGENT_URL:http://localhost:8100}
services.equipment-matching-agent.base-url=${EQUIPMENT_MATCHING_AGENT_URL:http://localhost:8102}
services.inventory-monitoring-agent.base-url=${INVENTORY_MONITORING_AGENT_URL:http://localhost:8103}
services.dynamic-pricing-agent.base-url=${DYNAMIC_PRICING_AGENT_URL:http://localhost:8104}
services.coupon-optimization-agent.base-url=${COUPON_OPTIMIZATION_AGENT_URL:http://localhost:8105}

# ─── 既存ドメインサービスの URL ────────────────────────────────
services.user-management.base-url=${USER_MANAGEMENT_SERVICE_URL:http://localhost:8081}
services.payment-cart.base-url=${PAYMENT_CART_SERVICE_URL:http://localhost:8084}

# 内部 API キー（Worker Agent 間認証）
services.internal-api-key=${INTERNAL_API_KEY:}

# JWT
jwt.secret=${JWT_SECRET:}

# Actuator
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.show-details=always

# Spring AI リトライ（Worker Agent 障害時の自動リトライ）
spring.ai.retry.max-attempts=3
spring.ai.retry.backoff.initial-interval=2000
spring.ai.retry.backoff.multiplier=2
```

---

## 6. エンドツーエンド シーケンス（完全版）

```
顧客                Orchestrator        Worker Agents        ドメインサービス
 │                      │                    │                     │
 │── POST /recommend ──→ │                    │                     │
 │   "苗場に来週末行く   │                    │                     │
 │    板とウェアを揃え   │                    │                     │
 │    たい"              │                    │                     │
 │                       │                    │                     │
 │                       │─ getUserProfile ──────────────────────→  │
 │                       │← UserProfile(tier=GOLD) ───────────────  │
 │                       │                    │                     │
 │  ┌── GPT が計画立案 ─→ │                    │                     │
 │  │   & Tools を選択  │                    │                     │
 │  │                   │                    │                     │
 │  │  Phase1            │                    │                     │
 │  │  ─ analyzeCustomerIntent ──────────→   │                     │
 │  │  ← CustomerIntentResult ──────────←   │                     │
 │  │                   │                    │                     │
 │  │  Phase2（並列）    │                    │                     │
 │  │  ─ getWeatherAndSkiConditions ──────→  │                     │
 │  │  ─ matchEquipment ──────────────────→  │                     │
 │  │  ← WeatherAgentResponse ───────────←  │                     │
 │  │  ← EquipmentMatchResult ───────────←  │                     │
 │  │                   │                    │                     │
 │  │  Phase3            │                    │                     │
 │  │  ─ checkInventoryAvailability ──────→  │                     │
 │  │  ← InventoryStatus[] ──────────────←  │                     │
 │  │                   │                    │                     │
 │  │  Phase4（並列）    │                    │                     │
 │  │  ─ calculateDynamicPrices ──────────→  │                     │
 │  │  ─ optimizeCoupons ─────────────────→  │                     │
 │  │  ← List<PricingResult> ────────────←  │                     │
 │  │  ← CouponOptimizationResult ───────←  │                     │
 │  │                   │                    │                     │
 │  │  Phase5            │                    │                     │
 │  │  ─ reserveInventory ────────────────→  │                     │
 │  │  ← ReservationResult ──────────────←  │                     │
 │  │                   │                    │                     │
 │  └── 全結果集約     │                    │                     │
 │                       │                    │                     │
 │← OrchestratorResponse ─│                   │                     │
 │  推奨カート + 価格     │                    │                     │
 │  + クーポン適用済み    │                    │                     │
```

---

## 7. テスト設計

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrchestratorAgentServiceIntegrationTest {

    @Test
    @DisplayName("orchestrate: 全 Worker Agent を正しい順序で呼び出す")
    void should_callAllWorkerAgentsInCorrectOrder() {
        // WireMock で全 Worker Agent のエンドポイントをモック
        // 1. CustomerIntentAgent が最初に呼ばれることを検証
        // 2. WeatherAgent と EquipmentMatchingAgent が並列で呼ばれることを検証
        // 3. InventoryMonitoringAgent が Equipment の後に呼ばれることを検証
        // 4. OrchestratorResponse に全 Agent の結果が含まれることを検証
    }

    @Test
    @DisplayName("orchestrate: 在庫切れ製品は推奨から除外される")
    void should_excludeOutOfStockProducts_from_recommendations() {
        // InventoryAgent が OUT_OF_STOCK を返す場合、その製品がカートに含まれないことを検証
    }

    @Test
    @DisplayName("orchestrate: 予算超過時は優先度の高い製品から選択される")
    void should_selectHighPriorityProducts_when_exceedingBudget() {
        // 予算100,000円に対して合計120,000円の推奨が来た場合の動作を検証
    }
}
```

```java
@ExtendWith(MockitoExtension.class)
class WorkerAgentRestClientTest {

    @Test
    @DisplayName("callInventoryCheck: productIds を正しく List に変換してリクエストする")
    void should_parseProductIdsCorrectly() {
        // "prod1,prod2,prod3" → ["prod1", "prod2", "prod3"] に変換されることを検証
    }

    @Test
    @DisplayName("callDynamicPricingAgent: productQuantities を正しく Item リストに変換する")
    void should_parseProductQuantitiesCorrectly() {
        // "prod1:2,prod2:1" → [{productId:prod1, quantity:2}, {productId:prod2, quantity:1}] を検証
    }
}
```

---

## 8. 障害対応・回復力設計

| 障害シナリオ | 対応方針 |
|------------|---------|
| Worker Agent 障害（タイムアウト） | Spring AI Retry（最大3回）+ フォールバック応答 |
| 在庫切れ製品の推奨 | 代替製品を InventoryAgent が提案。代替不可の場合は除外 |
| 天気情報取得失敗 | 気象調整なし（weather multiplier=1.0）で処理続行 |
| クーポン最適化失敗 | クーポンなしで最終価格を提示 |
| 全 Phase 失敗 | エラーレスポンス（503）+ Kafka に障害イベント送信 |

---

## 9. セキュリティ考慮事項

| リスク | 対策 |
|--------|------|
| A01 Access Control | `/recommend` は認証ユーザー全員。内部 Worker 呼び出しは `X-Internal-Api-Key` ヘッダーで認証 |
| A03 Injection | GPT への入力（ユーザーメッセージ）は 2000 文字制限 + System/User Prompt 分離 |
| Prompt Injection | Worker Agent への転送データに含まれるユーザー入力を JSON エスケープして渡す |
| A09 Logging | ユーザーメッセージ全文はログ出力禁止。userId・orderId のみをログ出力 |

---

## 10. 実装フェーズ計画

| フェーズ | 作業内容 | 優先度 |
|---------|---------|--------|
| Phase 1 | CustomerIntentAgent + WeatherAgent + 基本 Orchestrator（2 Worker 連携）| 高 |
| Phase 2 | EquipmentMatchingAgent + InventoryMonitoringAgent の組み込み | 高 |
| Phase 3 | DynamicPricingAgent + CouponOptimizationAgent の組み込み | 中 |
| Phase 4 | 並列実行最適化（CompletableFuture） | 中 |
| Phase 5 | E2E 統合テスト + Kafka イベント発行 | 中 |

---

## 11. 整合性検証マトリクス（Worker Agent との契約）

このセクションは Orchestrator が呼び出す各 Worker Agent との REST 契約と DTO 整合性をマトリクスで示す。すべての Worker Agent はこの契約に厳密に従って実装すること。

### 11.1 Tool ↔ Worker Endpoint 対応表

> **モード別ルーティング**: モノリス時は `LocalWorkerAgentInvoker` が直接 Bean メソッドを呼び出し、HTTP エンドポイントは経由しない。下表のエンドポイントは **分散モードでのみ実体として呼び出される**（モノリス時は管理用 REST として同一 JVM 内に存在）。

| Tool 名 (LLM) | HTTP Method | Worker Endpoint（分散） | モノリス時の呼び出し先 Bean メソッド | Request DTO | Response DTO | 呼び出し Phase |
|---|---|---|---|---|---|---|
| `analyzeCustomerIntent` | POST | `customer-intent-agent:8101/api/v1/agents/intent/analyze` | `CustomerIntentAgentService.analyze` | `CustomerIntentRequest` | `CustomerIntentResult` | Phase1 |
| `getWeatherAndSkiConditions` | POST | `weather-agent:8100/api/v1/agents/weather/analyze` | `WeatherAgentService.analyze` | `WeatherAgentRequest` | `WeatherAgentResponse` | Phase2 |
| `matchEquipment` | POST | `equipment-matching-agent:8102/api/v1/agents/equipment/match` | `EquipmentMatchingAgentService.match` | `EquipmentMatchRequest` | `EquipmentMatchResult` | Phase2 |
| `checkInventoryAvailability` | POST | `inventory-monitoring-agent:8103/api/v1/agents/inventory/check` | `InventoryMonitoringAgentService.check` | `InventoryCheckRequest` | `List<InventoryStatus>` | Phase3 |
| `calculateDynamicPrices` | POST | `dynamic-pricing-agent:8104/api/v1/agents/pricing/calculate/bulk` | `DynamicPricingAgentService.calculateBulk` | `BulkPricingRequest` | `List<PricingResult>` | Phase4 |
| `optimizeCoupons` | POST | `coupon-optimization-agent:8105/api/v1/agents/coupon/optimize` | `CouponOptimizationAgentService.optimize` | `CouponOptimizationRequest` | `CouponOptimizationResult` | Phase4 |
| `buildCart` | POST | `payment-cart-service:8084/api/v1/cart/build` | （HTTP 経由・モード非依存） | `BuildCartRequest` | `BuildCartResult` | Phase5 |
| `reserveInventory` | POST | `inventory-monitoring-agent:8103/api/v1/agents/inventory/reserve` | `InventoryMonitoringAgentService.reserve` | `ReservationRequest` | `ReservationResult` | Phase5 |

### 11.2 Tool パラメータ ↔ DTO フィールドマッピング

各 Tool の `@ToolParam` で受け取った値を、`WorkerAgentRestClient` が下記のとおり Request DTO に変換する。Worker Agent はこの DTO 構造で受信できなければならない。

#### `matchEquipment` → `EquipmentMatchRequest`
| Tool param | DTO field | 変換内容 |
|---|---|---|
| `userId` | `userId` | そのまま |
| `skillLevel` | `skillLevel` | そのまま |
| `categories` (CSV) | `desiredCategories` (List<String>) | `split(",")` |
| `budgetYen` | `budgetYen` | null 可 |
| `destination` | `destination` | null 可 |
| `quantity` | `quantity` | int → Integer |
| (送信しない) | `bodyMeasurements` | null。Worker 内で user-management から取得 |
| (固定値) | `includePurchase=true, includeRental=false` | デフォルト |

#### `checkInventoryAvailability` → `InventoryCheckRequest`
| Tool param | DTO field |
|---|---|
| `productIds` (CSV) | `productIds` (List<String>): `split(",")` |
| `quantity` | `requiredQuantity` |

#### `reserveInventory` → `ReservationRequest`
| Tool param | DTO field |
|---|---|
| `orderId` | `orderId` |
| `userId` | `userId` |
| `productQuantities` (CSV `prodId:qty`) | `items` (List<ReservationItem>): パース |
| (固定値) | `reservationTtlMinutes=30` |

#### `calculateDynamicPrices` → `BulkPricingRequest`
| Tool param | DTO field |
|---|---|
| `userId` | `userId` |
| `customerTier` | `customerTier` |
| `productQuantities` (CSV `prodId:qty`) | `items` (List<BulkPricingItem>): パース |
| `resortLocation` | `resortLocation` (null 可) |

#### `optimizeCoupons` → `CouponOptimizationRequest`
| Tool param | DTO field |
|---|---|
| `userId` | `userId` |
| `orderId` | `orderId` |
| `cartItemsEncoded` (`prodId\|name\|cat\|qty\|unitPrice` を `;` 区切り) | `cartItems` (List<CartItemPricing>): `parseCartItems()` で展開 |
| `customerTier` | `customerTier` |
| `usePoints` | `usePoints` |
| `couponCode` | `couponCode` (null 可) |

### 11.3 認証・認可フロー（共通）

```
Customer ──[JWT Bearer Token]──→ Orchestrator (POST /api/v1/orchestrator/recommend)
                                    │
                                    ├── UserManagement Service ──[JWT 転送]──→ プロフィール取得
                                    │
                                    └── Worker Agents ──[X-Internal-Api-Key + X-Caller-Service]──→
                                                                各 Worker
```

- **顧客 → Orchestrator**: JWT (USER ロール必須)
- **Orchestrator → UserManagement**: 顧客の JWT を Authorization ヘッダーで転送（顧客本人の権限で取得）
- **Orchestrator → Worker Agents**: `X-Internal-Api-Key`（環境変数 `INTERNAL_API_KEY` で配布）
- **各 Worker は `InternalApiKeyFilter` を実装**して、共有秘密鍵を検証し、リクエストに `ROLE_AGENT` を付与する

各 Worker Agent の `SecurityConfig` には次のフィルターを追加すること（共通実装は `common-lib` に配置推奨）:

```java
// common-lib/security/InternalApiKeyAuthenticationFilter.java
public class InternalApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private final String expectedApiKey;
    public InternalApiKeyAuthenticationFilter(String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String headerKey = req.getHeader("X-Internal-Api-Key");
        if (headerKey != null && headerKey.equals(expectedApiKey)) {
            String caller = req.getHeader("X-Caller-Service");
            var auth = new UsernamePasswordAuthenticationToken(
                    caller != null ? caller : "internal", null,
                    List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(req, res);
    }
}
```

各 Worker Agent の `SecurityConfig` 例:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            @Value("${services.internal-api-key}") String internalApiKey) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new InternalApiKeyAuthenticationFilter(internalApiKey),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
```

### 11.4 サービス間ポート一覧

| サービス | ポート | 種別 |
|---------|--------|------|
| weather-agent | 8090 | Worker Agent |
| customer-intent-agent | 8091 | Worker Agent |
| equipment-matching-agent | 8092 | Worker Agent |
| inventory-monitoring-agent | 8093 | Worker Agent |
| dynamic-pricing-agent | 8094 | Worker Agent |
| coupon-optimization-agent | 8095 | Worker Agent |
| **orchestrator-agent** | **8096** | **Orchestrator** |
| user-management-service | 8082 | ドメイン |
| inventory-management-service | 8084 | ドメイン |
| payment-cart-service | 8085 | ドメイン |
| sales-management-service | 8087 | ドメイン |

### 11.5 データフロー サンプル（実トレース例）

「苗場に来週末行くので板とウェアを揃えたい。予算は10万円」というリクエスト時の Tool 連鎖：

```
1. analyzeCustomerIntent(userId="u001", userMessage="苗場...", sessionId=null)
   → { primaryIntent: Purchase("スキー板,ウェア"),
       constraints: { destination:"Naeba", tripStartDate:"2026-04-25",
                      groupSize:1, skillLevel:"INTERMEDIATE", budgetYen:100000,
                      productCategories:["スキー板","ウェア"] }, ... }

2. getWeatherAndSkiConditions(location="Naeba, Niigata, Japan", question=null)
   → { current: { snowDepthCm:180, temperature:-5 },
       skiSuitability: "HIGH", recommendedGearLevel: "INTERMEDIATE" }

3. matchEquipment(userId="u001", skillLevel="INTERMEDIATE",
                  categories="スキー板,ウェア", budgetYen=100000,
                  destination="Naeba", quantity=1)
   → { recommendations: [
        { rank:1, product:{ productId:"ski-001", productName:"Salomon QST 92",
          category:"スキー板", basePrice:65000 }, matchScore:92.5 },
        { rank:2, product:{ productId:"wear-014", productName:"Patagonia Powder",
          category:"ウェア", basePrice:38000 }, matchScore:88.0 } ] }

4. checkInventoryAvailability(productIds="ski-001,wear-014", quantity=1)
   → [ { productId:"ski-001", availabilityStatus:"AVAILABLE", isReservable:true },
       { productId:"wear-014", availabilityStatus:"AVAILABLE", isReservable:true } ]

5. calculateDynamicPrices(userId="u001", customerTier="GOLD",
                           productQuantities="ski-001:1,wear-014:1",
                           resortLocation="Naeba")
   → [ { productId:"ski-001", finalPrice:62000, breakdown:{...} },
       { productId:"wear-014", finalPrice:36100, breakdown:{...} } ]

6. optimizeCoupons(userId="u001", orderId="ord-xxx",
                   cartItemsEncoded="ski-001|Salomon QST 92|スキー板|1|62000;wear-014|Patagonia Powder|ウェア|1|36100",
                   customerTier="GOLD", usePoints=false, couponCode=null)
   → { appliedCoupons:[{couponCode:"GOLD10"}], couponDiscountTotal:9810,
       cartTotalAfterDiscount:88290 }

7. buildCart(userId="u001", orderId="ord-xxx",
             cartItemsEncoded="ski-001|...|1|62000;wear-014|...|1|36100",
             couponDiscount=9810, pointDiscount=0)
   → { orderId:"ord-xxx", subtotal:98100, totalAmount:88290, status:"BUILT" }

8. reserveInventory(orderId="ord-xxx", userId="u001",
                    productQuantities="ski-001:1,wear-014:1")
   → { reservationId:"res-xxx", isFullyReserved:true,
       expiresAt:"2026-04-17T15:30:00Z" }

→ GPT が全結果を集約して OrchestratorResponse を組み立て、顧客に返却
```

---

## 12. 参照リソース

- [Agentic Patterns - Orchestrator-Workers](https://github.com/spring-projects/spring-ai-examples/tree/main/agentic-patterns/orchestrator-workers)
- [Spring AI ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI Structured Output](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
