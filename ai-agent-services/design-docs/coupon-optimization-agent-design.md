# Coupon Optimization Agent 詳細設計書

## 1. 概要

### 1.1 目的

**Coupon Optimization Agent** は、顧客・カート内容・ポイント残高・適用条件を分析して、最大の割引効果をもたらすクーポン・プロモーションの組み合わせを選択する専門 Worker Agent である。Dynamic Pricing Agent が算出した動的価格に対して、さらにクーポン・ポイントを重畳適用し、顧客が支払う最終金額を最小化する。

### 1.2 アーキテクチャ上の位置づけ

シーケンス図のステップ9（Pricing & coupon strategy）および10（apply coupon）に対応する。

```
Orchestrator Agent
    │
    │ [ToolCallback] optimizeCoupons(userId, cartItems, dynamicPrices)
    ▼
Coupon Optimization Agent ← 本設計書対象
    │
    │ Evaluator-Optimizer パターン:
    │
    │  [Generator] getEligibleCoupons(userId, cartItems)
    │       ↓ 候補クーポンリスト（複数）
    │  [Evaluator] evaluateCouponCombinations(coupons, cartTotal)
    │       ↓ 各組み合わせのスコアリング
    │  [Optimizer] selectOptimalCombination(evaluated)
    │       ↓ 最適クーポンセット
    │  [Validator] validateAndApply(coupons, orderId)
    │       ↓ 適用済み最終金額
    │  CouponOptimizationResult
    ▼
Orchestrator へ返却 → payment-cart-service に適用
```

### 1.3 デプロイメントモード（ハイブリッド規約）

本 Agent は [hybrid-deployment-design.md](hybrid-deployment-design.md) の規約に従う。

- **モジュール種別**: **library jar**。`@SpringBootApplication` は持たない
- **デフォルト起動**: `agent-runtime-monolith`（ポート 8100）に同梱。Orchestrator と同 JVM のため Bean 直接呼び出し
- **分散起動**: `agent-runtime-standalone/coupon-optimization-standalone`（ポート 8105）として個別起動可能
- **共有 DTO**: `CouponOptimizationRequest`, `CouponOptimizationResult`, `CouponCandidate`, `CouponEvaluation`, `CartItemPricing` は `agent-common/dto/` に移動
- **Bean 名規約**: `couponOptimizationAgentChatClient`, `couponOptimizationCouponServiceRestClient`, `couponOptimizationPointServiceRestClient` など Agent 名プレフィックス必須。`@Qualifier` で注入
- **`SecurityConfig`**: `@ConditionalOnProperty(name="agents.deployment.mode", havingValue="distributed")` を付与
- **`Controller`**: `@ConditionalOnProperty(name="agents.web.enabled", havingValue="true", matchIfMissing=true)` を付与
- **`AutoConfiguration`**: `@AutoConfiguration` + `@Import({...})` で明示取り込み
- **`application.properties`**: 本モジュールでは同梱しない。§5 の内容は standalone 用テンプレート

---

## 2. Agentic Pattern の選択

**Evaluator-Optimizer パターン**を採用する。

複数のクーポン候補を生成し、各組み合わせを評価・スコアリングした上で最も効果の高い組み合わせを選択するため、Evaluator-Optimizer が最適。

```
[候補クーポン生成] → [各組み合わせを評価] → [最高スコアを選択] → [適用バリデーション]
                        ↑                              |
                        └──── 制約違反なら再評価 ────────┘
```

---

## 3. モジュール構成

```
ai-agent-services/
└── coupon-optimization-agent/
    ├── pom.xml
    ├── Dockerfile
    └── src/
        ├── main/java/com/example/skishop/agent/coupon/
        │   ├── CouponOptimizationAgentApplication.java
        │   ├── config/
        │   │   ├── CouponOptimizationAgentConfig.java
        │   │   └── SecurityConfig.java
        │   ├── tool/
        │   │   └── CouponOptimizationToolService.java
        │   ├── service/
        │   │   └── CouponOptimizationAgentService.java
        │   ├── client/
        │   │   ├── CouponServiceClient.java
        │   │   └── PointServiceClient.java
        │   ├── controller/
        │   │   └── CouponOptimizationController.java
        │   └── dto/
        │       ├── CouponOptimizationRequest.java
        │       ├── CouponOptimizationResult.java
        │       ├── CouponCandidate.java
        │       ├── CouponEvaluation.java
        │       └── CartItemPricing.java
        └── resources/
            └── application.properties
```

---

## 4. クラス詳細設計

### 4.1 DTO 設計

```java
// dto/CouponCandidate.java
package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CouponCandidate(
        String couponId,
        String couponCode,
        String couponType,         // "PERCENTAGE" | "FIXED_AMOUNT" | "FREE_SHIPPING" | "BUNDLE"
        BigDecimal discountRate,   // PERCENTAGE の場合（0.1 = 10%）
        BigDecimal discountAmount, // FIXED_AMOUNT の場合（円）
        BigDecimal minimumOrder,   // 最低注文金額
        String applicableCategory, // 適用可能カテゴリ（null = 全商品）
        LocalDate expiresAt,
        boolean isStackable,       // 他のクーポンと重複適用可能か
        int usageLimit             // 残利用可能回数
) {}
```

```java
// dto/CouponEvaluation.java
package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;
import java.util.List;

public record CouponEvaluation(
        List<CouponCandidate> appliedCoupons,
        BigDecimal totalDiscountAmount,
        BigDecimal finalCartTotal,
        double optimizationScore,    // 0.0〜100.0（割引率 + 顧客満足度加重スコア）
        boolean meetsConstraints,
        String evaluationReason
) {}
```

```java
// dto/CartItemPricing.java
package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;

public record CartItemPricing(
        String productId,
        String productName,
        String category,
        int quantity,
        BigDecimal dynamicUnitPrice,   // Dynamic Pricing Agent が算出した単価
        BigDecimal lineTotal
) {}
```

```java
// dto/CouponOptimizationRequest.java
package com.example.skishop.agent.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CouponOptimizationRequest(
        @NotBlank String userId,
        @NotBlank String orderId,
        @NotEmpty List<CartItemPricing> cartItems,
        String customerTier,
        boolean usePoints,         // ポイント併用可否
        String couponCode          // フロントエンドで入力されたクーポンコード（任意）
) {}
```

```java
// dto/CouponOptimizationResult.java
package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CouponOptimizationResult(
        String userId,
        String orderId,
        List<CouponCandidate> appliedCoupons,
        int appliedPoints,
        BigDecimal pointsDiscount,
        BigDecimal couponDiscountTotal,
        BigDecimal cartTotalBeforeDiscount,
        BigDecimal cartTotalAfterDiscount,
        BigDecimal totalSavings,
        String optimizationSummary,   // GPT が生成した日本語サマリー
        Instant calculatedAt
) {}
```

---

### 4.2 CouponOptimizationToolService（@Tool 定義・Evaluator-Optimizer）

```java
// tool/CouponOptimizationToolService.java
package com.example.skishop.agent.coupon.tool;

import com.example.skishop.agent.coupon.client.CouponServiceClient;
import com.example.skishop.agent.coupon.client.PointServiceClient;
import com.example.skishop.agent.common.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CouponOptimizationToolService {

    private static final Logger log = LoggerFactory.getLogger(CouponOptimizationToolService.class);

    private final CouponServiceClient couponClient;
    private final PointServiceClient pointClient;

    public CouponOptimizationToolService(CouponServiceClient couponClient,
                                          PointServiceClient pointClient) {
        this.couponClient = couponClient;
        this.pointClient = pointClient;
    }

    /**
     * ツール1 [Generator]: ユーザーが利用可能なクーポン候補を取得する。
     * coupon-service から有効なクーポン一覧を取得し、カート条件で絞り込む。
     */
    @Tool(description = """
            指定ユーザーのカート内容に対して利用可能なクーポン候補を全て取得する。
            最低注文金額・有効期限・適用カテゴリを考慮して絞り込む。
            Evaluator-Optimizer の Generator ステップ。
            """)
    public List<CouponCandidate> getEligibleCoupons(
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "カートの合計金額（円）") BigDecimal cartTotal,
            @ToolParam(description = "カート内の商品カテゴリ（カンマ区切り、例: スキー板,ウェア）") String categories,
            @ToolParam(description = "フロントエンドで入力されたクーポンコード（任意）") @Nullable String inputCouponCode) {
        log.info("Tool getEligibleCoupons: userId={}, cartTotal={}", userId, cartTotal);

        List<CouponCandidate> allCoupons = couponClient.getUserCoupons(userId);

        // 入力されたクーポンコードがある場合は追加で検索
        if (inputCouponCode != null && !inputCouponCode.isBlank()) {
            couponClient.findByCouponCode(inputCouponCode).ifPresent(allCoupons::add);
        }

        var categoryList = List.of(categories.split(","));
        return allCoupons.stream()
                .filter(c -> !c.expiresAt().isBefore(LocalDate.now()))
                .filter(c -> cartTotal.compareTo(c.minimumOrder()) >= 0)
                .filter(c -> c.applicableCategory() == null
                        || categoryList.contains(c.applicableCategory()))
                .filter(c -> c.usageLimit() > 0)
                .toList();
    }

    /**
     * ツール2 [Evaluator]: クーポンの組み合わせを評価してスコアリングする。
     * stackable なクーポンの組み合わせを生成し、各組み合わせの割引額を計算する。
     */
    @Tool(description = """
            クーポン候補リストの各組み合わせを評価してスコアリングする。
            stackable=true のクーポンは複数適用可能。
            各組み合わせの割引額・最終金額・最適スコアを計算して返す。
            Evaluator-Optimizer の Evaluator ステップ。
            """)
    public List<CouponEvaluation> evaluateCouponCombinations(
            @ToolParam(description = "利用可能なクーポン候補リスト") List<CouponCandidate> candidates,
            @ToolParam(description = "カートの合計金額（円）") BigDecimal cartTotal) {
        log.info("Tool evaluateCouponCombinations: candidates={}, cartTotal={}", candidates.size(), cartTotal);

        List<CouponEvaluation> evaluations = new ArrayList<>();

        // 単品適用の評価
        for (CouponCandidate coupon : candidates) {
            BigDecimal discount = calculateDiscount(coupon, cartTotal);
            BigDecimal finalTotal = cartTotal.subtract(discount).max(BigDecimal.ZERO);
            double score = discount.divide(cartTotal, 4, RoundingMode.HALF_UP).doubleValue() * 100;

            evaluations.add(new CouponEvaluation(
                    List.of(coupon), discount, finalTotal, score, true,
                    "単品適用: %s → %,d円割引".formatted(coupon.couponCode(), discount.intValue())));
        }

        // Stackable クーポンの2件組み合わせ評価
        var stackable = candidates.stream().filter(CouponCandidate::isStackable).toList();
        for (int i = 0; i < stackable.size(); i++) {
            for (int j = i + 1; j < stackable.size(); j++) {
                var combo = List.of(stackable.get(i), stackable.get(j));
                BigDecimal totalDiscount = combo.stream()
                        .map(c -> calculateDiscount(c, cartTotal))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal finalTotal = cartTotal.subtract(totalDiscount).max(BigDecimal.ZERO);
                double score = totalDiscount.divide(cartTotal, 4, RoundingMode.HALF_UP).doubleValue() * 100;

                evaluations.add(new CouponEvaluation(
                        combo, totalDiscount, finalTotal, score, true,
                        "2枚重複適用 → %,d円割引".formatted(totalDiscount.intValue())));
            }
        }

        return evaluations;
    }

    /**
     * ツール3 [Optimizer]: 最高スコアのクーポン組み合わせを選択する。
     * Evaluator の結果から最も割引効果の高いものを返す。
     */
    @Tool(description = """
            評価済みクーポン組み合わせの中から最高スコアのものを選択する。
            Evaluator-Optimizer の Optimizer ステップ（最後に呼び出す）。
            """)
    public CouponEvaluation selectOptimalCombination(
            @ToolParam(description = "評価済みクーポン組み合わせリスト") List<CouponEvaluation> evaluations) {
        log.info("Tool selectOptimalCombination: evaluations={}", evaluations.size());
        return evaluations.stream()
                .filter(CouponEvaluation::meetsConstraints)
                .max(Comparator.comparingDouble(CouponEvaluation::optimizationScore))
                .orElseThrow(() -> new IllegalStateException("適用可能なクーポンが見つかりません"));
    }

    /**
     * ツール4: ポイント残高を確認して割引額を計算する。
     * ポイント1pt = 1円として計算する。
     */
    @Tool(description = """
            ユーザーのポイント残高を取得し、今回カートに使用できるポイント数と割引額を返す。
            ポイント1pt = 1円。最大利用率はカート金額の50%まで。
            usePoints=true の場合に呼び出すこと。
            """)
    public PointUsageResult calculatePointsUsage(
            @ToolParam(description = "ユーザー ID") String userId,
            @ToolParam(description = "カートの合計金額（円）") BigDecimal cartTotal) {
        log.info("Tool calculatePointsUsage: userId={}, cartTotal={}", userId, cartTotal);
        int pointBalance = pointClient.getPointBalance(userId);
        int maxUsablePoints = cartTotal.divide(BigDecimal.TWO, 0, RoundingMode.DOWN).intValue();
        int usablePoints = Math.min(pointBalance, maxUsablePoints);
        return new PointUsageResult(pointBalance, usablePoints,
                BigDecimal.valueOf(usablePoints));
    }

    public record PointUsageResult(int balance, int usable, BigDecimal discountAmount) {}

    private BigDecimal calculateDiscount(CouponCandidate coupon, BigDecimal cartTotal) {
        return switch (coupon.couponType()) {
            case "PERCENTAGE" -> cartTotal.multiply(coupon.discountRate()).setScale(0, RoundingMode.HALF_UP);
            case "FIXED_AMOUNT" -> coupon.discountAmount().min(cartTotal);
            default -> BigDecimal.ZERO;
        };
    }
}
```

---

### 4.3 CouponOptimizationAgentService

```java
// service/CouponOptimizationAgentService.java
package com.example.skishop.agent.coupon.service;

import com.example.skishop.agent.common.dto.*;
import com.example.skishop.agent.coupon.tool.CouponOptimizationToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class CouponOptimizationAgentService {

    private static final Logger log = LoggerFactory.getLogger(CouponOptimizationAgentService.class);

    private static final String SYSTEM_PROMPT = """
            あなたはスキーショップのクーポン最適化 AI エージェントです。
            以下の Evaluator-Optimizer パターンで最適なクーポン戦略を決定してください。

            ステップ1 [Generator]: getEligibleCoupons でクーポン候補を取得する
            ステップ2 [Evaluator]: evaluateCouponCombinations で全組み合わせを評価する
            ステップ3 [Optimizer]: selectOptimalCombination で最適な組み合わせを選択する
            ステップ4 [Option]: usePoints=true の場合、calculatePointsUsage も実行する

            最終的に顧客が得られる割引総額と最終支払い金額を日本語で説明する
            optimizationSummary を 200 文字以内で生成すること。
            
            クーポンが1枚も利用できない場合はポイントのみの割引を提案すること。
            """;

    private final ChatClient chatClient;
    private final CouponOptimizationToolService toolService;

    public CouponOptimizationAgentService(
            @org.springframework.beans.factory.annotation.Qualifier("couponAgentChatClient") ChatClient chatClient,
                                           CouponOptimizationToolService toolService) {
        this.chatClient = chatClient;
        this.toolService = toolService;
    }

    public CouponOptimizationResult optimize(CouponOptimizationRequest request) {
        log.info("CouponOptimizationAgent optimize: userId={}, orderId={}",
                request.userId(), request.orderId());

        BigDecimal cartTotal = request.cartItems().stream()
                .map(CartItemPricing::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String categories = request.cartItems().stream()
                .map(CartItemPricing::category)
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        CouponOptimizationResult result = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("""
                        ユーザーID: %s
                        注文ID: %s
                        カート合計: %,d円
                        商品カテゴリ: %s
                        顧客ティア: %s
                        ポイント使用: %s
                        クーポンコード（入力済み）: %s
                        最適なクーポン戦略を実行してください。
                        """.formatted(
                        request.userId(), request.orderId(),
                        cartTotal.intValue(), categories,
                        request.customerTier(), request.usePoints() ? "はい" : "いいえ",
                        request.couponCode() != null ? request.couponCode() : "なし"))
                .tools(toolService)
                .call()
                .entity(CouponOptimizationResult.class);

        log.info("CouponOptimizationAgent result: totalSavings={}", result.totalSavings());
        return result;
    }
}
```

---

### 4.4 CouponOptimizationAgentConfig

```java
// config/CouponOptimizationAgentConfig.java
package com.example.skishop.agent.coupon.config;

import com.example.skishop.agent.coupon.tool.CouponOptimizationToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CouponOptimizationAgentConfig {

    @Bean("couponAgentChatClient")
    public ChatClient couponAgentChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * オーケストレーターがクーポン最適化エージェントを利用するための ToolCallback[]。
     */
    @Bean("couponAgentToolCallbacks")
    public ToolCallback[] couponAgentToolCallbacks(CouponOptimizationToolService toolService) {
        return ToolCallbacks.from(toolService);
    }
}
```

---

### 4.5 CouponOptimizationController

```java
// controller/CouponOptimizationController.java
package com.example.skishop.agent.coupon.controller;

import com.example.skishop.agent.common.dto.CouponOptimizationRequest;
import com.example.skishop.agent.common.dto.CouponOptimizationResult;
import com.example.skishop.agent.coupon.service.CouponOptimizationAgentService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agents/coupon")
@ConditionalOnProperty(name = "agents.web.enabled", havingValue = "true", matchIfMissing = true)
public class CouponOptimizationController {

    private final CouponOptimizationAgentService service;

    public CouponOptimizationController(CouponOptimizationAgentService service) {
        this.service = service;
    }

    @PostMapping("/optimize")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<CouponOptimizationResult> optimize(
            @Valid @RequestBody CouponOptimizationRequest request) {
        return ResponseEntity.ok(service.optimize(request));
    }
}
```

---

### 4.6 CouponOptimizationAgentSecurityConfig

> **ハイブリッド規約**: 本 `SecurityConfig` は **分散モードのみ有効化**される。モノリス時は `agent-runtime-monolith` 側の単一 SecurityConfig が処理する。

```java
// config/CouponOptimizationAgentSecurityConfig.java
package com.example.skishop.agent.coupon.config;

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
public class CouponOptimizationAgentSecurityConfig {

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

### 4.7 CouponOptimizationAgentAutoConfiguration

```java
// config/CouponOptimizationAgentAutoConfiguration.java
package com.example.skishop.agent.coupon.config;

import com.example.skishop.agent.coupon.controller.CouponOptimizationController;
import com.example.skishop.agent.coupon.service.CouponOptimizationAgentService;
import com.example.skishop.agent.coupon.tool.CouponOptimizationToolService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    CouponOptimizationToolService.class,
    CouponOptimizationAgentService.class,
    CouponOptimizationAgentConfig.class,
    CouponOptimizationController.class,
    CouponOptimizationAgentSecurityConfig.class
})
public class CouponOptimizationAgentAutoConfiguration {
}
```

登録: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` に `com.example.skishop.agent.coupon.config.CouponOptimizationAgentAutoConfiguration` を記載。

---

## 5. application.properties

> **ハイブリッド規約**: 本ファイルは **分散モード用テンプレート**である。モノリスモードでは `agent-runtime-monolith/src/main/resources/application.yml` に統合される。

```properties
spring.application.name=coupon-optimization-agent
server.port=8105

agents.deployment.mode=distributed
agents.web.enabled=true

spring.ai.azure.openai.api-key=${AZURE_OPENAI_API_KEY:}
spring.ai.azure.openai.endpoint=${AZURE_OPENAI_ENDPOINT:}
spring.ai.azure.openai.chat.options.deployment-name=${AZURE_OPENAI_DEPLOYMENT:gpt-4o}
spring.ai.azure.openai.chat.options.temperature=0.0
spring.ai.azure.openai.chat.options.max-completion-tokens=1024

services.coupon.base-url=${COUPON_SERVICE_URL:http://localhost:8088}
services.point.base-url=${POINT_SERVICE_URL:http://localhost:8085}
services.internal-api-key=${INTERNAL_API_KEY:}

# ポイント最大利用率（カート金額に対する割合）
coupon.points.max-usage-rate=0.5

jwt.secret=${JWT_SECRET:}
management.endpoints.web.exposure.include=health,info,prometheus
```

---

## 6. テスト設計

```java
@ExtendWith(MockitoExtension.class)
class CouponOptimizationToolServiceTest {

    @Test
    @DisplayName("evaluateCouponCombinations: stackable なクーポン2枚の重複適用スコアが単品より高い")
    void should_scoreHigher_forStackableCombination() {
        // 10%クーポン + 5%クーポン(stackable) の組み合わせスコアが 10%単品より高い
    }

    @Test
    @DisplayName("calculatePointsUsage: 利用可能ポイントはカート金額の50%が上限")
    void should_capPointsAt50Percent_of_cartTotal() {
        // cartTotal=20000円, pointBalance=20000pt → usable=10000pt
        when(pointClient.getPointBalance("u1")).thenReturn(20000);
        var result = toolService.calculatePointsUsage("u1", new BigDecimal("20000"));
        assertThat(result.usable()).isEqualTo(10000);
    }

    @Test
    @DisplayName("getEligibleCoupons: 有効期限切れのクーポンは除外される")
    void should_excludeExpiredCoupons() {
        // expiresAt が昨日のクーポンは返却リストから除外される
    }
}
```

---

## 7. セキュリティ考慮事項

| リスク | 対策 |
|--------|------|
| A01 Access Control | クーポン最適化は `AGENT` ロール必須。エンドユーザーは間接的にのみアクセス可能 |
| A04 Insecure Design | クーポン重複適用の上限を2枚に制限。ポイント利用はカート金額の50%上限 |
| A07 Auth Failures | フロントエンド入力のクーポンコードはサーバー側で有効性を再検証 |

### 7.1 Orchestrator からの認証

**モノリスモード**: Orchestrator は同一 JVM 内で Bean を直接呼び出すため、ネットワーク認証は不要。

**分散モード**: Orchestrator は `X-Internal-Api-Key` を付与して `/api/v1/agents/coupon/optimize` を呼び出す。§4.6 の `CouponOptimizationAgentSecurityConfig`（`@ConditionalOnProperty(distributed)` 付き）に `InternalApiKeyAuthenticationFilter`（common-lib）を登録し `ROLE_AGENT` を付与する。詳細は [orchestrator-agent-design.md §11.3](orchestrator-agent-design.md) と [hybrid-deployment-design.md §7](hybrid-deployment-design.md) を参照。

### 7.2 Orchestrator から送信される CouponOptimizationRequest の構造

Orchestrator の `WorkerAgentRestClient.callCouponOptimizationAgent()` は、LLM が生成した `cartItemsEncoded`（`productId|name|category|qty|unitPrice` を `;` 区切り）をパースして以下の構造で送信する:

```json
{
  "userId": "u001",
  "orderId": "ord-xxx",
  "customerTier": "GOLD",
  "usePoints": false,
  "couponCode": null,
  "cartItems": [
    { "productId": "ski-001", "productName": "Salomon QST 92",
      "category": "スキー板", "quantity": 1,
      "dynamicUnitPrice": 62000, "lineTotal": 62000 },
    { "productId": "wear-014", "productName": "Patagonia Powder",
      "category": "ウェア", "quantity": 1,
      "dynamicUnitPrice": 36100, "lineTotal": 36100 }
  ]
}
```

`dynamicUnitPrice` は **必ず Dynamic Pricing Agent の `finalPrice` を採用**すること。`basePrice` を渡してはならない。これにより、動的価格算出後の金額に対してクーポンが正しく適用される。

---

## 8. 参照リソース

- [Agentic Patterns - Evaluator-Optimizer](https://github.com/spring-projects/spring-ai-examples/tree/main/agentic-patterns/evaluator-optimizer)
- [Spring AI Structured Output](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
