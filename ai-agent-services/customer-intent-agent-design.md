# Customer Intent Agent 詳細設計書

## 1. 概要

### 1.1 目的

**Customer Intent Agent** は、顧客の自然言語リクエストを解析し、構造化された意図・制約・コンテキストを抽出する専門 Worker Agent である。Multi-Agent System において最初に呼び出され、後続エージェント（Equipment Matching, Dynamic Pricing, Coupon Optimization）が必要とする情報を整理・提供する役割を担う。

### 1.2 アーキテクチャ上の位置づけ

シーケンス図のステップ2〜3に対応する。

```
Customer / UI
    │
    │ "苗場スキー場に来週末行くので板とウェアを揃えたい。予算は10万円"
    ▼
Orchestrator Agent
    │
    │ [ToolCallback] analyzeCustomerIntent(request, userId)
    ▼
Customer Intent Agent ← 本設計書対象
    │
    │ Chain Workflow:
    │  1) 言語・カテゴリ分類
    │  2) 制約抽出（日程/場所/予算/人数/スキルレベル）
    │  3) ユーザー履歴との照合
    │  4) 構造化 CustomerIntentResult 出力
    ▼
Orchestrator へ CustomerIntentResult を返却
```

### 1.3 デプロイメントモード（ハイブリッド規約）

本 Agent は [hybrid-deployment-design.md](hybrid-deployment-design.md) の規約に従う。

- **モジュール種別**: **library jar**（`packaging=jar` + `spring-boot-maven-plugin` の `repackage` を skip）。`@SpringBootApplication` は持たない
- **デフォルト起動**: `agent-runtime-monolith`（ポート 8100）に同梱されて起動。Orchestrator と同 JVM のため `LocalWorkerAgentInvoker` から `CustomerIntentAgentService.analyze(...)` を直接呼び出しされる
- **分散起動**: `agent-runtime-standalone/customer-intent-standalone`（ポート 8101）として個別起動可能。分散時のみ `SecurityConfig`（`InternalApiKeyAuthenticationFilter`）が有効化される
- **共有 DTO**: `CustomerIntentRequest`, `CustomerIntentResult`, `IntentCategory`, `ExtractedConstraints`, `UserPurchaseHistory` は `agent-common/src/main/java/com/example/skishop/agent/common/dto/` に移動。本設計書下の `package com.example.skishop.agent.intent.dto` 記述は、共有 DTO のみ `com.example.skishop.agent.common.dto` と読み替える
- **Bean 名規約**: `ChatClient` / `RestClient` は Agent 名プレフィックス必須（`customerIntentAgentChatClient`, `customerIntentUserManagementRestClient` 等）。サービスクラスでは `@Qualifier` で注入
- **`SecurityConfig`**: クラスに `@ConditionalOnProperty(name = "agents.deployment.mode", havingValue = "distributed")` を付与
- **`Controller`**: クラスに `@ConditionalOnProperty(name = "agents.web.enabled", havingValue = "true", matchIfMissing = true)` を付与
- **`AutoConfiguration`**: `@AutoConfiguration` + `@Import({...})` で Bean を明示取り込み（`@ComponentScan` は使わない）。`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` に FQCN を記載
- **`application.properties`**: 本モジュールでは**同梱しない**。本設計書§5 の内容は standalone モジュール用テンプレートとして扱う

---

## 2. Agentic Pattern の選択

**Chain Workflow パターン**を採用する。

顧客の自然言語テキストから構造化データを抽出するには、複数の解析ステップを順序よく処理する必要があるため Chain が最適。

```
[自然言語入力]
    → Step1: IntentClassifier     (カテゴリ分類: PURCHASE/RENTAL/ADVICE/SUPPORT)
    → Step2: ConstraintExtractor  (日程/場所/予算/グループサイズ/スキルレベル)
    → Step3: HistoryEnricher      (過去購入・レンタル履歴と照合)
    → Step4: StructuredOutput     (CustomerIntentResult レコード出力)
```

---

## 3. モジュール構成

```
ai-agent-services/
└── customer-intent-agent/
    ├── pom.xml
    ├── Dockerfile
    └── src/
        ├── main/java/com/example/skishop/agent/intent/
        │   ├── CustomerIntentAgentApplication.java
        │   ├── config/
        │   │   ├── CustomerIntentAgentConfig.java
        │   │   └── SecurityConfig.java
        │   ├── tool/
        │   │   └── CustomerIntentToolService.java   # @Tool 定義
        │   ├── service/
        │   │   └── CustomerIntentAgentService.java  # Chain Workflow 実装
        │   ├── client/
        │   │   └── UserProfileClient.java           # user-management-service 呼び出し
        │   ├── controller/
        │   │   └── CustomerIntentController.java
        │   └── dto/
        │       ├── CustomerIntentRequest.java
        │       ├── CustomerIntentResult.java
        │       ├── ExtractedConstraints.java
        │       ├── UserPurchaseHistory.java
        │       └── IntentCategory.java              # sealed interface
        └── resources/
            └── application.properties
```

---

## 4. クラス詳細設計

### 4.1 DTO 設計

```java
// dto/IntentCategory.java
package com.example.skishop.agent.common.dto;

/**
 * 顧客インテントの大分類。Sealed interface でパターンマッチングを安全に利用する。
 */
public sealed interface IntentCategory
        permits IntentCategory.Purchase, IntentCategory.Rental,
                IntentCategory.Advice, IntentCategory.Support {

    record Purchase(String productCategory) implements IntentCategory {}
    record Rental(String productCategory, int durationDays) implements IntentCategory {}
    record Advice(String topic) implements IntentCategory {}
    record Support(String issueType) implements IntentCategory {}
}
```

```java
// dto/CustomerIntentRequest.java
package com.example.skishop.agent.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerIntentRequest(
        @NotBlank String userId,

        @NotBlank
        @Size(max = 2000, message = "リクエストは2000文字以内で入力してください")
        String userMessage,

        String sessionId,      // 会話セッション管理用
        String locale          // デフォルト: "ja"
) {}
```

```java
// dto/ExtractedConstraints.java
package com.example.skishop.agent.common.dto;

import java.time.LocalDate;

public record ExtractedConstraints(
        String destination,          // 例: "苗場スキー場", "白馬"
        LocalDate tripStartDate,
        LocalDate tripEndDate,
        Integer groupSize,           // グループ人数（デフォルト: 1）
        String skillLevel,           // "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT"
        Integer budgetYen,           // 予算（円）
        boolean includesRental,      // レンタル希望か
        boolean includesPurchase,    // 購入希望か
        java.util.List<String> productCategories  // ["スキー板", "ウェア", "ブーツ"]
) {}
```

```java
// dto/CustomerIntentResult.java
package com.example.skishop.agent.common.dto;

import java.time.Instant;

public record CustomerIntentResult(
        String userId,
        String sessionId,
        IntentCategory primaryIntent,
        ExtractedConstraints constraints,
        UserPurchaseHistory purchaseHistory,
        String intentSummary,          // オーケストレーターへ渡す日本語サマリー
        double confidenceScore,        // 0.0〜1.0
        Instant analyzedAt
) {}
```

```java
// dto/UserPurchaseHistory.java
package com.example.skishop.agent.common.dto;

import java.util.List;

public record UserPurchaseHistory(
        String userId,
        List<String> previouslyPurchasedCategories,
        String lastKnownSkillLevel,
        int totalPurchaseCount,
        String customerTier           // "BRONZE" | "SILVER" | "GOLD" | "PLATINUM"
) {}
```

---

### 4.2 CustomerIntentToolService（@Tool 定義）

```java
// tool/CustomerIntentToolService.java
package com.example.skishop.agent.intent.tool;

import com.example.skishop.agent.intent.client.UserProfileClient;
import com.example.skishop.agent.common.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class CustomerIntentToolService {

    private static final Logger log = LoggerFactory.getLogger(CustomerIntentToolService.class);
    private final UserProfileClient userProfileClient;

    public CustomerIntentToolService(UserProfileClient userProfileClient) {
        this.userProfileClient = userProfileClient;
    }

    /**
     * ツール1: 顧客メッセージからカテゴリ分類を行う（LLM が呼び出す前処理ツール）。
     * GPT に「このツールでまずカテゴリを確定せよ」と指示するために定義。
     */
    @Tool(description = """
            顧客の自然言語メッセージを解析し、インテントカテゴリ（PURCHASE/RENTAL/ADVICE/SUPPORT）
            と主要な製品カテゴリを JSON で返す。
            最初に呼び出すべきツール。
            """)
    public String classifyIntent(
            @ToolParam(description = "顧客の自然言語メッセージ") String message) {
        log.info("Tool classifyIntent called, messageLength={}", message.length());
        // このツールは GPT 自身が判断する補助として存在。
        // 実際の分類ロジックは ChatClient が LLM として実行する。
        // returnDirect=false でモデルに結果を再提示させる。
        return message; // GPT に渡し直す
    }

    /**
     * ツール2: ユーザー購入・レンタル履歴を取得する。
     * user-management-service と sales-management-service から情報を取得。
     */
    @Tool(description = """
            指定ユーザーの過去の購入・レンタル履歴、スキルレベル、顧客ティアを取得する。
            製品推奨・価格調整の参考データとして使用する。
            userId が不明な場合はスキップしてよい。
            """)
    public UserPurchaseHistory getUserPurchaseHistory(
            @ToolParam(description = "ユーザー ID（UUID 形式）") String userId) {
        log.info("Tool getUserPurchaseHistory called: userId={}", userId);
        return userProfileClient.getPurchaseHistory(userId);
    }

    /**
     * ツール3: 制約抽出の結果を検証・補完する。
     * GPT が抽出した制約に欠損がある場合にデフォルト値を補完する。
     */
    @Tool(description = """
            抽出した制約情報（場所・日程・予算・スキルレベル）の欠損を補完する。
            スキルレベルが不明な場合は BEGINNER と仮定する。
            予算が未指定の場合は null を返す（ハードリミットなし）。
            場所が不明な場合は null を返す（後続エージェントが Weather Agent で補完）。
            """)
    public ExtractedConstraints validateAndFillConstraints(
            @ToolParam(description = "場所、例: 苗場スキー場") @Nullable String destination,
            @ToolParam(description = "旅行開始日 ISO-8601、例: 2026-12-27") @Nullable String startDate,
            @ToolParam(description = "旅行終了日 ISO-8601") @Nullable String endDate,
            @ToolParam(description = "グループ人数") @Nullable Integer groupSize,
            @ToolParam(description = "スキルレベル: BEGINNER/INTERMEDIATE/ADVANCED/EXPERT") @Nullable String skillLevel,
            @ToolParam(description = "予算（円）") @Nullable Integer budgetYen,
            @ToolParam(description = "購入希望商品カテゴリ（カンマ区切り）、例: スキー板,ウェア") @Nullable String categories) {

        log.info("Tool validateAndFillConstraints called: destination={}, skillLevel={}", destination, skillLevel);

        java.time.LocalDate parsedStart = parseDate(startDate);
        java.time.LocalDate parsedEnd = parseDate(endDate);
        String resolvedSkill = (skillLevel != null) ? skillLevel.toUpperCase() : "BEGINNER";
        int resolvedGroupSize = (groupSize != null && groupSize > 0) ? groupSize : 1;
        var categoryList = categories != null
                ? java.util.Arrays.asList(categories.split(","))
                : java.util.List.of();

        return new ExtractedConstraints(
                destination, parsedStart, parsedEnd, resolvedGroupSize,
                resolvedSkill, budgetYen, categoryList.isEmpty(), !categoryList.isEmpty(), categoryList);
    }

    private java.time.LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return java.time.LocalDate.parse(dateStr);
        } catch (Exception e) {
            log.warn("日付パース失敗: {}", dateStr);
            return null;
        }
    }
}
```

---

### 4.3 CustomerIntentAgentService（Chain Workflow 実装）

```java
// service/CustomerIntentAgentService.java
package com.example.skishop.agent.intent.service;

import com.example.skishop.agent.common.dto.*;
import com.example.skishop.agent.intent.tool.CustomerIntentToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CustomerIntentAgentService {

    private static final Logger log = LoggerFactory.getLogger(CustomerIntentAgentService.class);

    private static final String SYSTEM_PROMPT = """
            あなたはスキーショップの顧客インテント解析 AI エージェントです。
            提供されているツールを必要に応じて呼び出し、顧客のリクエストから以下を抽出してください。

            抽出すべき情報:
            1. インテントカテゴリ (PURCHASE / RENTAL / ADVICE / SUPPORT)
            2. 目的地・スキーリゾート名
            3. 旅行日程（開始日・終了日）
            4. グループ人数
            5. スキルレベル
            6. 予算（円）
            7. 希望商品カテゴリ（スキー板 / ブーツ / ウェア / ゴーグル / ヘルメット / グローブ 等）
            8. 購入・レンタルの別

            ツール呼び出し手順:
            1. まず getUserPurchaseHistory を呼び出してユーザー履歴を確認
            2. 次に validateAndFillConstraints で制約を整理
            3. 最後に日本語で 200 文字以内の intentSummary を生成

            情報が不足している場合はデフォルト値を適用し、confidenceScore を下げること。
            """;

    private final ChatClient chatClient;
    private final CustomerIntentToolService intentToolService;

    public CustomerIntentAgentService(
            @org.springframework.beans.factory.annotation.Qualifier("customerIntentAgentChatClient") ChatClient chatClient,
                                       CustomerIntentToolService intentToolService) {
        this.chatClient = chatClient;
        this.intentToolService = intentToolService;
    }

    /**
     * Chain Workflow: 自然言語 → 構造化 CustomerIntentResult
     */
    public CustomerIntentResult analyze(CustomerIntentRequest request) {
        log.info("CustomerIntentAgent analyze: userId={}", request.userId());

        // Step 1-3: ChatClient が @Tool を連鎖的に呼び出しながら分析
        CustomerIntentResult result = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("ユーザーID: %s\nメッセージ: %s".formatted(request.userId(), request.userMessage()))
                .tools(intentToolService)
                .call()
                .entity(CustomerIntentResult.class);

        log.info("CustomerIntentAgent result: intent={}, confidence={}",
                result.primaryIntent(), result.confidenceScore());
        return result;
    }
}
```

---

### 4.4 CustomerIntentAgentConfig

```java
// config/CustomerIntentAgentConfig.java
package com.example.skishop.agent.intent.config;

import com.example.skishop.agent.intent.tool.CustomerIntentToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomerIntentAgentConfig {

    @Bean("customerIntentAgentChatClient")
    public ChatClient customerIntentAgentChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * オーケストレーターが顧客インテント解析を呼び出すための ToolCallback[]。
     * Orchestrator の ChatClient に .toolCallbacks(customerIntentAgentToolCallbacks) で登録する。
     */
    @Bean("customerIntentAgentToolCallbacks")
    public ToolCallback[] customerIntentAgentToolCallbacks(CustomerIntentToolService toolService) {
        return ToolCallbacks.from(toolService);
    }
}
```

---

### 4.5 UserProfileClient（サービス間呼び出し）

```java
// client/UserProfileClient.java
package com.example.skishop.agent.intent.client;

import com.example.skishop.agent.common.dto.UserPurchaseHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class UserProfileClient {

    private static final Logger log = LoggerFactory.getLogger(UserProfileClient.class);
    private final RestClient restClient;

    public UserProfileClient(
            @Value("${services.user-management.base-url}") String userManagementBaseUrl,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(userManagementBaseUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }

    public UserPurchaseHistory getPurchaseHistory(String userId) {
        try {
            return restClient.get()
                    .uri("/api/v1/internal/users/{userId}/purchase-history", userId)
                    .retrieve()
                    .body(UserPurchaseHistory.class);
        } catch (RestClientException e) {
            log.warn("ユーザー履歴取得失敗 userId={}: {}", userId, e.getMessage());
            // フォールバック: 購入履歴なしの新規顧客として扱う
            return new UserPurchaseHistory(userId, List.of(), "BEGINNER", 0, "BRONZE");
        }
    }
}
```

---

### 4.6 CustomerIntentController

```java
// controller/CustomerIntentController.java
package com.example.skishop.agent.intent.controller;

import com.example.skishop.agent.common.dto.CustomerIntentRequest;
import com.example.skishop.agent.common.dto.CustomerIntentResult;
import com.example.skishop.agent.intent.service.CustomerIntentAgentService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agents/intent")
@ConditionalOnProperty(name = "agents.web.enabled", havingValue = "true", matchIfMissing = true)
public class CustomerIntentController {

    private final CustomerIntentAgentService service;

    public CustomerIntentController(CustomerIntentAgentService service) {
        this.service = service;
    }

    /**
     * 顧客インテント解析エンドポイント。
     * オーケストレーターまたはフロントエンドから呼び出す。
     */
    @PostMapping("/analyze")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<CustomerIntentResult> analyze(
            @Valid @RequestBody CustomerIntentRequest request) {
        return ResponseEntity.ok(service.analyze(request));
    }
}
```

---

### 4.7 CustomerIntentAgentSecurityConfig

> **ハイブリッド規約**: 本 `SecurityConfig` は **分散モードのみ有効化**される。モノリス時は `agent-runtime-monolith` 側の単一 SecurityConfig が処理する。

```java
// config/CustomerIntentAgentSecurityConfig.java
package com.example.skishop.agent.intent.config;

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
public class CustomerIntentAgentSecurityConfig {

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

### 4.8 CustomerIntentAgentAutoConfiguration

```java
// config/CustomerIntentAgentAutoConfiguration.java
package com.example.skishop.agent.intent.config;

import com.example.skishop.agent.intent.client.UserProfileClient;
import com.example.skishop.agent.intent.controller.CustomerIntentController;
import com.example.skishop.agent.intent.service.CustomerIntentAgentService;
import com.example.skishop.agent.intent.tool.CustomerIntentToolService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    CustomerIntentToolService.class,
    CustomerIntentAgentService.class,
    UserProfileClient.class,
    CustomerIntentAgentConfig.class,
    CustomerIntentController.class,
    CustomerIntentAgentSecurityConfig.class
})
public class CustomerIntentAgentAutoConfiguration {
}
```

登録: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` に `com.example.skishop.agent.intent.config.CustomerIntentAgentAutoConfiguration` を記載。

---

## 5. application.properties

> **ハイブリッド規約**: 本ファイルは **分散モード（`customer-intent-standalone`）用テンプレート**である。モノリスモードでは `agent-runtime-monolith/src/main/resources/application.yml` に統合される。

```properties
spring.application.name=customer-intent-agent
server.port=8101

# ハイブリッドモード
agents.deployment.mode=distributed
agents.web.enabled=true

# Azure OpenAI
spring.ai.azure.openai.api-key=${AZURE_OPENAI_API_KEY:}
spring.ai.azure.openai.endpoint=${AZURE_OPENAI_ENDPOINT:}
spring.ai.azure.openai.chat.options.deployment-name=${AZURE_OPENAI_DEPLOYMENT:gpt-4o}
spring.ai.azure.openai.chat.options.temperature=0.1
spring.ai.azure.openai.chat.options.max-completion-tokens=1024

# サービス間通信
services.user-management.base-url=${USER_MANAGEMENT_SERVICE_URL:http://localhost:8081}
services.internal-api-key=${INTERNAL_API_KEY:}

# JWT
jwt.secret=${JWT_SECRET:}

# Actuator
management.endpoints.web.exposure.include=health,info,prometheus
```

---

## 6. テスト設計

```java
@ExtendWith(MockitoExtension.class)
class CustomerIntentAgentServiceTest {

    @Test
    @DisplayName("analyze: 購入意図・予算・スキルレベルが正しく抽出される")
    void should_extractPurchaseIntentWithConstraints() {
        // ChatClient のモック + ToolService のモックを用いた単体テスト
        // 期待値: IntentCategory.Purchase, budgetYen=100000, skillLevel="BEGINNER"
    }

    @Test
    @DisplayName("analyze: ユーザー履歴取得失敗時はフォールバック値を使用する")
    void should_useFallbackHistory_when_userProfileClientFails() {
        // UserProfileClient が RestClientException を投げる場合のテスト
    }
}
```

---

## 7. セキュリティ考慮事項

| リスク | 対策 |
|--------|------|
| A03 Injection | `userMessage` の最大文字数制限（2000文字）、HTML エスケープを適用 |
| Prompt Injection | System Prompt と User Prompt を明確に分離。ユーザー入力を直接 System Prompt に埋め込まない |
| A01 Access Control | `AGENT` ロールのみ呼び出し可能（エンドユーザー直接呼び出し不可） |
| A09 Logging | `userMessage` をそのままログ出力しない（個人情報の可能性）。長さのみログ出力 |

### 7.1 Orchestrator からの認証

**モノリスモード**: Orchestrator は同一 JVM 内で本 Agent の Bean を直接呼び出すため、ネットワーク認証は不要。`SecurityConfig` は `@ConditionalOnProperty(distributed)` で Bean 化されない。

**分散モード**: Orchestrator は `X-Internal-Api-Key` と `X-Caller-Service: orchestrator-agent` を付与して REST 呼び出しする。本 Agent の `CustomerIntentAgentSecurityConfig`（§4.7、`@ConditionalOnProperty(distributed)` 付き）に `InternalApiKeyAuthenticationFilter`（common-lib）を登録し、`ROLE_AGENT` を付与する。詳細は [orchestrator-agent-design.md §11.3](orchestrator-agent-design.md) と [hybrid-deployment-design.md §7](hybrid-deployment-design.md) を参照。

---

## 8. 参照リソース

- [Spring AI ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI Structured Output](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
- [Agentic Patterns - Chain Workflow](https://github.com/spring-projects/spring-ai-examples/tree/main/agentic-patterns/chain-workflow)
