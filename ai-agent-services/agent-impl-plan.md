# Multi-Agent System 実装計画書（agent-impl-plan）

> **目的**: ハイブリッド構成（モノリス・デフォルト / 分散・オプション）の Multi-Agent System を、詳細設計書を基に**漏れなく・順序通りに・検証可能な形で**実装するための工程計画。
>
> **対象設計書**:
> - [hybrid-deployment-design.md](hybrid-deployment-design.md) — 規約の最終源
> - [apply-existing-services.md](apply-existing-services.md) — 親 POM・既存サービス改修
> - [weather-agent-design.md](weather-agent-design.md)
> - [customer-intent-agent-design.md](customer-intent-agent-design.md)
> - [equipment-matching-agent-design.md](equipment-matching-agent-design.md)
> - [inventory-monitoring-agent-design.md](inventory-monitoring-agent-design.md)
> - [dynamic-pricing-agent-design.md](dynamic-pricing-agent-design.md)
> - [coupon-optimization-agent-design.md](coupon-optimization-agent-design.md)
> - [orchestrator-agent-design.md](orchestrator-agent-design.md)
>
> **凡例**: ☑ = 実装タスク / ✅ = 完了確認チェック / ⚠ = 既知の落とし穴

---

## 0. プラン作成中に発見した問題（事前報告）

設計書を実装目線で精査した結果、**実装着手前に確認・解決すべき問題**を以下に整理する。実装計画は本セクションの解決を前提として進める。

| # | 区分 | 内容 | 影響範囲 | 取り扱い |
|---|------|------|---------|---------|
| P1 | コード整合 | `EquipmentMatchRequest` は **9 引数 record**（`userId, skillLevel, bodyMeasurements, desiredCategories, budgetYen, destination, includeRental, includePurchase, quantity`）。Orchestrator §4.3 の `matchEquipment` 呼び出しがこの順序通りであることを必ずレビュー時に再確認 | orchestrator-agent | 修正済（本書 Phase 5 で再検証） |
| P2 | API 規約 | **payment-cart-service へのカート構築 API（`POST /api/v1/cart/build`）は既存サービス側に新設が必要**。詳細設計 §4.5.1 の `PaymentCartClient.buildCart` が依存している | payment-cart-service / orchestrator-agent | 既存サービス改修工程（Phase 7）で扱う |
| P3 | API 規約 | **inventory-management-service の `POST /api/v1/inventory/reservations` API**（`InventoryMonitoringToolService.reserveInventory` から呼ばれる）も既存サービス側に新設が必要 | inventory-management-service / inventory-monitoring-agent | 既存サービス改修工程（Phase 7）で扱う |
| P4 | API 規約 | **user-management-service の `GET /api/v1/users/{id}/profile` 拡張**（`customerTier`, `purchasedCategories`, `pointBalance` を含む `UserProfile`）が必要。`CustomerIntentToolService` と `OrchestratorAgentService` が両者使用 | user-management-service | Phase 7 で確認・拡張 |
| P5 | API 規約 | **point-service の残高取得 API**（`GET /api/v1/points/{userId}/balance`）が `coupon-optimization-agent` から呼ばれる前提。既存にない場合は新設 | point-service / coupon-optimization-agent | Phase 7 で確認 |
| P6 | API 規約 | **sales-management-service の販売実績取得 API**（`GET /api/v1/sales/products/{id}/demand`）が `dynamic-pricing-agent` から呼ばれる前提 | sales-management-service / dynamic-pricing-agent | Phase 7 で確認 |
| P7 | API 規約 | **coupon-service の利用可能クーポン一覧 API**（`GET /api/v1/coupons/available?userId=...`）が `coupon-optimization-agent` から呼ばれる前提 | coupon-service / coupon-optimization-agent | Phase 7 で確認 |
| P8 | 設計上の選択 | `agent-runtime-monolith` の `MonolithSecurityConfig` は **すべての `/api/v1/agents/**` および `/api/v1/orchestrator/**` を JWT 認証で保護する** 単一 `SecurityFilterChain`。各 Worker `*SecurityConfig` は `@ConditionalOnProperty(distributed)` のため起動時無効 → モノリス時は Bean 衝突なし | agent-runtime-monolith | Phase 6 で実装 |
| P9 | 設計上の選択 | `OrchestratorWorkerTools` は `WorkerAgentInvoker` 注入だが、`Jackson ObjectMapper` も注入する（@Tool は構造化文字列返却のため）。`ObjectMapper` は Spring Boot 標準で auto-configured のため別途定義不要 | orchestrator-agent | Phase 5 で実装 |
| P10 | 環境差 | `LocalWeatherInvoker` は `@ConditionalOnBean(WeatherAgentService.class)` を持つ。standalone モジュール（weather 以外）が `WeatherInvoker` を必要とするとき、当該 standalone 側は `RemoteWeatherInvoker` のみが活性化されることを `@ConditionalOnProperty(distributed)` で担保 | equipment-matching/dynamic-pricing standalone | Phase 8 で確認 |
| P11 | テスト基盤 | `Azure OpenAI` への実呼び出しはユニットテスト範囲外。`ChatClient` は `@MockBean` で差し替え、`@Tool` メソッドの呼び出しは `MockMvc` + テスト用 ToolCallback で検証 | 全 Agent | Phase 4・5 のテストで明文化 |
| P12 | API Gateway | フロントエンドは `/api/v1/orchestrator/recommend` のみを api-gateway 経由で叩く。Worker `/api/v1/agents/**` は **公開しない**（モノリス時も内部呼び出しのみ）。ただし `agents.web.enabled=true` のときは Controller は活性化される（管理用）。api-gateway-service のルーティングで `/api/v1/agents/**` を遮断する | api-gateway-service | Phase 7 で実装 |
| P13 | プロパティ命名衝突 | 既存サービスが `JWT_SECRET` を使用済み。Multi-Agent も同名を共有することで OK（同一発行元のトークンを検証するため）。これを Phase 1 の `INTERNAL_API_KEY` 配布手順とセットでドキュメント化 | 全サービス | Phase 0 で運用ドキュメント化 |
| P14 | データ移行 | 本実装ではデータベーススキーマ変更は **行わない**（Multi-Agent はステートレス、既存テーブル参照のみ）。万一 `coupon_usage_history` 等の永続化が必要になった場合は別途設計 | — | スコープ外 |

---

## 1. 全体ロードマップ

```text
┌─────────────────────────────────────────────────────────────────┐
│ Phase 0: 準備                                                   │
│   - INTERNAL_API_KEY 発行・配布手順書化                         │
│   - 親 POM (ai-agent-services/pom.xml) と pluginManagement      │
│   - common-lib/InternalApiKeyAuthenticationFilter 追加          │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ Phase 1: agent-common モジュール                                │
│   - 全 共有 DTO（17 件）                                        │
│   - WeatherInvoker インターフェース                             │
│   - AgentDeploymentProperties / AgentCommonAutoConfiguration    │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ Phase 2: weather-agent（最初の Worker・WeatherInvoker 提供側） │
│   - DTO/Tool/Service/Controller/SecurityConfig                  │
│   - LocalWeatherInvoker（@ConditionalOnBean）                   │
│   - AutoConfiguration                                            │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ Phase 3: 他 Worker（並列実装可能）                              │
│   3a. customer-intent-agent                                     │
│   3b. inventory-monitoring-agent                                │
│   3c. coupon-optimization-agent                                 │
│   3d. equipment-matching-agent（RemoteWeatherInvoker 含む）    │
│   3e. dynamic-pricing-agent  （RemoteWeatherInvoker 含む）    │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ Phase 4: 各 Worker のユニットテスト                             │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ Phase 5: orchestrator-agent                                     │
│   - WorkerAgentInvoker / Local / Remote                         │
│   - OrchestratorWorkerTools                                     │
│   - OrchestratorAgentService                                    │
│   - WorkerAgentRestClient / PaymentCartClient                   │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ Phase 6: agent-runtime-monolith（実行可能 jar）                 │
│   - AgentRuntimeApplication / MonolithSecurityConfig            │
│   - application.yml / Dockerfile                                │
│   - 全 Worker Bean が同 ApplicationContext で起動することを検証 │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ Phase 7: 既存サービス側改修                                     │
│   - common-lib 連携（全 8 サービス）                            │
│   - payment-cart: POST /api/v1/cart/build 新設                  │
│   - inventory: POST /api/v1/inventory/reservations 新設         │
│   - user-management: profile API 拡張                           │
│   - point/sales/coupon: 必要 API 確認                           │
│   - api-gateway: ルーティング追加 / Worker パス遮断             │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ Phase 8: 統合テスト・モノリス起動検証                           │
│   - docker-compose up でフルスタック起動                        │
│   - /api/v1/orchestrator/recommend で E2E 動作確認              │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ Phase 9 (オプション): standalone モジュール作成と分散検証       │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ Phase 10: 監視・運用整備                                        │
│   - Prometheus / Grafana / runbook 更新                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## Phase 0 — 準備

### 0.1 タスク

- ☑ ルート `pom.xml` の `<modules>` に `<module>ai-agent-services</module>` を追加
- ☑ `ai-agent-services/pom.xml`（親 POM）を新規作成（`packaging=pom`、サブモジュール列挙）
- ☑ 親 POM に `spring-boot-maven-plugin` の `<pluginManagement>` を追加（`agent-runtime-monolith` のみで `<plugin>` を有効化）
- ☑ `INTERNAL_API_KEY`（32 文字以上）を発行し、`.env`・docker-compose・Kubernetes Secret 用テンプレートに反映
- ☑ `docs/runbook.md` に「INTERNAL_API_KEY ローテーション手順」を追記

### 0.2 完了確認

```bash
# 親 POM が解決可能か
mvn -f ai-agent-services/pom.xml -N validate

# ルート POM 全体ビルドの構造確認（まだ各モジュールは空でよい）
mvn -pl ai-agent-services -am help:effective-pom | head -50
```

✅ `<modules>` に 8 サブモジュール（`agent-common`, `weather-agent`, `customer-intent-agent`, `equipment-matching-agent`, `inventory-monitoring-agent`, `dynamic-pricing-agent`, `coupon-optimization-agent`, `orchestrator-agent`）と `agent-runtime-monolith` が列挙されている
✅ `INTERNAL_API_KEY` が 32 文字以上で生成されている
✅ runbook にローテーション手順が記載されている

⚠ 落とし穴: 親 POM の `<dependencyManagement>` は **継承する**（ルート POM の `spring-ai-bom` を再宣言しない）

---

## Phase 1 — agent-common モジュール

### 1.1 タスク

- ☑ `agent-common/pom.xml` 作成（`packaging=jar`、依存は `jakarta.validation-api`、`spring-context`、`spring-boot-autoconfigure` のみ）
- ☑ `com.example.skishop.agent.common.dto` パッケージに **17 件の record** を作成

  | DTO | 出典設計書 |
  |---|---|
  | `CustomerIntentRequest`, `CustomerIntentResult`, `IntentCategory` (sealed), `ExtractedConstraints`, `UserPurchaseHistory` | customer-intent-agent §4.1 |
  | `WeatherAgentRequest`, `WeatherAgentResponse`, `CurrentWeatherData`, `WeatherForecastData`, `SkiConditionsData`, `WeatherAlertData`, `SkiFeasibilityResult` | weather-agent §4.1 |
  | `EquipmentMatchRequest`, `EquipmentMatchResult`, `RankedProduct`, `ProductCandidate`, `BodyMeasurements` | equipment-matching-agent §4.1 |
  | `InventoryCheckRequest`, `InventoryStatus`, `InventoryAlert`, `ReservationRequest` (内 `ReservationItem`), `ReservationResult` | inventory-monitoring-agent §4.1 |
  | `BulkPricingRequest` (内 `BulkPricingItem`), `PricingRequest`, `PricingResult`, `PriceBreakdown` | dynamic-pricing-agent §4.1 |
  | `CouponOptimizationRequest`, `CouponOptimizationResult`, `CouponCandidate`, `CouponEvaluation`, `CartItemPricing` | coupon-optimization-agent §4.1 |

- ☑ `com.example.skishop.agent.common.invoker.WeatherInvoker` インターフェースを作成（`getFeasibility` + default `getOverallCondition`）— [hybrid-deployment-design.md §3.5](hybrid-deployment-design.md)
- ☑ `com.example.skishop.agent.common.config.AgentDeploymentProperties` を作成（[hybrid-deployment-design.md §2.1.1](hybrid-deployment-design.md)）
- ☑ `com.example.skishop.agent.common.config.AgentCommonAutoConfiguration` を作成 + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 登録

### 1.2 完了確認

```bash
mvn -pl ai-agent-services/agent-common -am clean install
```

✅ コンパイル成功（17 DTO + 1 interface + 2 config = 20 クラス）
✅ JUnit でレコードのバリデーション制約を最小確認（`CustomerIntentRequest` の `@NotBlank`、`SkiFeasibilityResult.score` の範囲外不正値テスト等）
✅ `mvn dependency:tree` で `spring-boot-starter`・`spring-web`・`spring-ai-*` が含まれていない（純 library であることの確認）

⚠ 落とし穴: `agent-common` は **`@ComponentScan` を一切使わない**。Bean 化が必要なものは `AutoConfiguration` 経由で登録すること

⚠ 落とし穴: 各 record の **フィールド順序は設計書 §4.1 の宣言順に厳密に合わせる**（OrchestratorWorkerTools のコンストラクタ呼び出しが順序依存のため）。特に `EquipmentMatchRequest` は 9 引数、順序：`userId, skillLevel, bodyMeasurements, desiredCategories, budgetYen, destination, includeRental, includePurchase, quantity`

---

## Phase 2 — weather-agent

### 2.1 タスク

- ☑ `weather-agent/pom.xml` 作成（`packaging=jar`、`spring-boot-maven-plugin` は宣言しない）
- ☑ 依存: `agent-common`, `common-lib`, `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `spring-ai-azure-openai-spring-boot-starter`, `spring-boot-starter-cache`
- ☑ `client/OpenMeteoClient.java`（外部 Open-Meteo API 呼び出し、`@Cacheable` 付与）
- ☑ `tool/WeatherToolService.java`（4 つの `@Tool` メソッド + `assessSkiFeasibility` メソッド、`SkiFeasibilityResult` 構築含む）
- ☑ `service/WeatherAgentService.java`（`@Qualifier("weatherAgentChatClient") ChatClient` 注入）
- ☑ `config/WeatherAgentConfig.java`（`weatherAgentChatClient` Bean、`weatherAgentToolCallbacks` Bean）
- ☑ `controller/WeatherController.java`（`@ConditionalOnProperty("agents.web.enabled", matchIfMissing=true)`）
- ☑ `config/WeatherAgentSecurityConfig.java`（`@ConditionalOnProperty("agents.deployment.mode", "distributed")`）
- ☑ `invoker/LocalWeatherInvoker.java`（`@ConditionalOnProperty(monolith, matchIfMissing=true)` + `@ConditionalOnBean(WeatherAgentService.class)`）
- ☑ `config/WeatherAgentAutoConfiguration.java`（`@AutoConfiguration` + `@Import` で 6 クラス取り込み）
- ☑ `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### 2.2 完了確認

```bash
mvn -pl ai-agent-services/weather-agent -am clean install
```

✅ ビルド成功（`spring-boot:repackage` が **走らない** ことを確認: `target/` に `*.jar.original` がないこと）
✅ ユニットテスト
  - `WeatherToolServiceTest`: 4 つの `@Tool` メソッドそれぞれの正常系・異常系（Mock `OpenMeteoClient`）
  - `WeatherAgentServiceTest`: `@MockBean ChatClient` で `analyze()` の Chain 呼び出し検証
  - `LocalWeatherInvokerTest`: `getFeasibility()` の委譲、`getOverallCondition()` の default 実装
✅ `WeatherToolService.assessSkiFeasibility(...)` が **`overallCondition` 入りの `SkiFeasibilityResult` を返す**（Phase 5 の Pricing 連携に必須）

⚠ 落とし穴: `WeatherAgentSecurityConfig` のクラス名は他 Worker と命名統一（`<Name>AgentSecurityConfig`）。モノリス時は `@ConditionalOnProperty` で無効化されることを `@SpringBootTest` で確認

⚠ 落とし穴: `LocalWeatherInvoker` の `@ConditionalOnBean(WeatherAgentService.class)` を忘れると、standalone（weather 以外）で誤って Local が選ばれる事故が起きる

---

## Phase 3 — 他 Worker Agent（5 モジュール）

各サブフェーズは **並列実装可能**（DTO は agent-common に集約済みのため依存しない）。

### 3.1 customer-intent-agent

- ☑ `pom.xml`（依存: agent-common, common-lib, spring-ai-azure-openai-starter, spring-boot-starter-web, security, validation）
- ☑ `client/UserProfileClient.java`（user-management-service 呼び出し、`X-Internal-Api-Key` ヘッダ付与）
- ☑ `tool/CustomerIntentToolService.java`（`@Tool` 群）
- ☑ `service/CustomerIntentAgentService.java`（`@Qualifier("customerIntentAgentChatClient")`）
- ☑ `config/CustomerIntentAgentConfig.java`（ChatClient + ToolCallbacks Bean）
- ☑ `controller/CustomerIntentController.java`
- ☑ `config/CustomerIntentAgentSecurityConfig.java`
- ☑ `config/CustomerIntentAgentAutoConfiguration.java` + imports ファイル

### 3.2 inventory-monitoring-agent

- ☑ 同上構成
- ☑ `client/InventoryManagementClient.java`（既存 `inventory-management-service` 呼び出し）
- ☑ `tool/InventoryMonitoringToolService.java`（`@Tool getInventoryStatus`, `@Tool reserveInventory`, `@Tool releaseReservation` 等）

### 3.3 coupon-optimization-agent

- ☑ 同上構成
- ☑ `client/CouponServiceClient.java`, `client/PointServiceClient.java`
- ☑ `tool/CouponOptimizationToolService.java`（Evaluator-Optimizer ロジック）

### 3.4 equipment-matching-agent

- ☑ 同上構成
- ☑ `client/InventoryClient.java`（在庫検索 — inventory-monitoring-agent と REST URL は同じだが、責務分離のため別 Client）
- ☑ `tool/EquipmentMatchingToolService.java`
- ☑ **`invoker/RemoteWeatherInvoker.java`**（`@ConditionalOnProperty(distributed)`、agent-common の `WeatherInvoker` を REST 経由で実装）
- ☑ `service/EquipmentMatchingAgentService.java`（`WeatherInvoker` 注入：モノリス時 Local、分散時 Remote が自動選択）
- ☑ `config/EquipmentMatchingAgentAutoConfiguration.java` の `@Import` に `RemoteWeatherInvoker.class` を含める

### 3.5 dynamic-pricing-agent

- ☑ 3.4 と同様、`RemoteWeatherInvoker` を本モジュールにも配置
- ☑ `client/ProductCatalogClient.java`, `client/SalesManagementClient.java`
- ☑ `tool/DynamicPricingToolService.java`（Chain Workflow: BasePrice → ×Demand → ×Weather → ×Inventory → ×Tier → ×Season）

### 3.6 完了確認（各 Worker 共通）

```bash
mvn -pl ai-agent-services/<agent-module> -am clean install
```

✅ ビルド成功・`*.jar.original` が生成されない
✅ Bean 名規約（`<agent>AgentChatClient`, `<agent>AgentToolCallbacks`）が遵守されている — `grep -r 'name = "' src/main/java/**/Config.java` で確認
✅ Controller クラスに `@ConditionalOnProperty("agents.web.enabled", matchIfMissing=true)`
✅ SecurityConfig クラスに `@ConditionalOnProperty("agents.deployment.mode", havingValue="distributed")`
✅ ChatClient 注入箇所すべてに `@Qualifier(...)` がある — `grep -A1 'ChatClient ' src/main/java/**/*.java | grep -B1 -v Qualifier` で漏れチェック
✅ AutoConfiguration の `@Import` に Tool/Service/Config/Controller/SecurityConfig/Client/(Invoker) が **すべて** 列挙されている
✅ `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` に FQCN 1 行

⚠ 落とし穴: `equipment-matching-agent` と `dynamic-pricing-agent` の `RemoteWeatherInvoker` は **両方の standalone** 用に**それぞれ**配置する（agent-common に置けない理由は、REST 接続先 URL や RestClient の構築が standalone 個別の `application.yml` から取得されるため）

---

## Phase 4 — Worker ユニットテスト

### 4.1 テスト戦略

- ☑ ToolService: モック RestClient を使った正常系 + HTTP 4xx/5xx 異常系
- ☑ AgentService: `@MockBean ChatClient.Builder` でビルダー差し替え、`ChatClient.prompt().tools().call().entity()` のチェーンをモック
- ☑ Controller: `@WebMvcTest` + `@MockBean AgentService` + `MockMvc` で `/api/v1/agents/<x>/...` の HTTP 契約検証
- ☑ SecurityConfig: `@SpringBootTest(properties = "agents.deployment.mode=distributed")` で `X-Internal-Api-Key` 必須を確認、未付与時 401
- ☑ AutoConfiguration: `ApplicationContextRunner` で `@AutoConfiguration` の Bean 登録を確認

### 4.2 完了確認

```bash
mvn -pl ai-agent-services -am test
```

✅ 全 Worker のテストカバレッジ **分岐 80% 以上**（`.github/copilot-instructions.md` 規約準拠）
✅ 異常系テストケース数 ≥ 正常系テストケース数

⚠ 落とし穴: `ChatClient` をモックする際、Spring AI 1.0.0 の `ChatClient.Builder.build()` は immutable な `ChatClient` を返すため、`Mockito.mock(ChatClient.class, RETURNS_DEEP_STUBS)` を活用

---

## Phase 5 — orchestrator-agent

### 5.1 タスク

- ☑ `pom.xml`（依存: agent-common + 6 Worker Agent + common-lib + spring-ai-azure-openai-starter + spring-web）
- ☑ `dto/OrchestratorRequest.java`, `OrchestratorResponse.java`, `QuoteSummary.java`, `WorkflowStepResult.java`
- ☑ `invoker/WorkerAgentInvoker.java`（インターフェース、7 メソッド）
- ☑ `invoker/LocalWorkerAgentInvoker.java`（`@ConditionalOnProperty(monolith, matchIfMissing=true)`、6 Worker Service Bean を直接注入）
- ☑ `invoker/RemoteWorkerAgentInvoker.java`（`@ConditionalOnProperty(distributed)`、`WorkerAgentRestClient` 経由）
- ☑ `client/WorkerAgentRestClient.java`（`@ConditionalOnProperty(distributed)`、6 Worker への RestClient + payment-cart 用は **削除**して §5.1 の `PaymentCartClient` に分離）
- ☑ `client/PaymentCartClient.java`（モード非依存・常時有効、`@Tool buildCart` から呼ばれる）
- ☑ `client/UserManagementClient.java`（Orchestrator 用ユーザー情報取得、`UserProfile` record 含む）
- ☑ `tool/OrchestratorWorkerTools.java`（`WorkerAgentInvoker` + `PaymentCartClient` + `ObjectMapper` 注入、設計書 §4.3 のコードをそのまま実装）
- ☑ `service/OrchestratorAgentService.java`（`@Qualifier("orchestratorChatClient")`）
- ☑ `config/OrchestratorAgentConfig.java`（`orchestratorChatClient` + `orchestratorWorkerToolCallbacks` Bean、6 Worker の `*ToolCallbacks` を `@Qualifier` で集約）
- ☑ `controller/OrchestratorController.java`（`@ConditionalOnProperty("agents.web.enabled", matchIfMissing=true)`）
- ☑ `config/OrchestratorSecurityConfig.java`（`@ConditionalOnProperty("agents.deployment.mode", "distributed")`）
- ☑ `config/OrchestratorAgentAutoConfiguration.java`（`@Import` に上記すべて、ただしモード別 Bean は `@Conditional` で自然に絞り込まれる）

### 5.2 完了確認

```bash
mvn -pl ai-agent-services/orchestrator-agent -am clean install
```

✅ コンパイル成功
✅ `OrchestratorWorkerTools` の **8 つの `@Tool` メソッドすべて**が `invoker.invokeXxx(...)` または `paymentCart.buildCart(...)` を呼んでいる（旧 `restClient.callXxx(...)` 残存ゼロ）
  - 確認コマンド: `grep -c 'restClient\.call' src/main/java/.../tool/OrchestratorWorkerTools.java` → 期待値 0
✅ `OrchestratorWorkerTools.matchEquipment` が `EquipmentMatchRequest` を **9 引数で正しい順序**で呼んでいる（P1 再確認）
✅ ユニットテスト
  - `OrchestratorWorkerToolsTest`: `@MockBean WorkerAgentInvoker` + `@MockBean PaymentCartClient` で 8 Tool すべての引数パース・JSON 化を検証
  - `LocalWorkerAgentInvokerTest`: 6 Worker Service Bean モックで委譲を検証
  - `RemoteWorkerAgentInvokerTest`: `@MockBean WorkerAgentRestClient` で REST 委譲を検証
  - `OrchestratorAgentServiceTest`: `@MockBean ChatClient` でツール呼び出し連鎖をモック
✅ ApplicationContextRunner テストで以下を確認:
  - `agents.deployment.mode=monolith` 時: `LocalWorkerAgentInvoker` のみが Bean 化、`RemoteWorkerAgentInvoker` と `WorkerAgentRestClient` は登録されない
  - `agents.deployment.mode=distributed` 時: 逆

⚠ 落とし穴: `OrchestratorAgentConfig` で 6 Worker の `ToolCallback[]` を集約する Bean は、各 Worker の `*AgentToolCallbacks` Bean を `@Qualifier` で注入。モノリス時はすべて存在、分散時（orchestrator-standalone）は **存在しない**（standalone の orchestrator は Worker Agent モジュールに依存しないため）→ この場合 `@Autowired(required = false)` で注入し、null チェックで除外する

---

## Phase 6 — agent-runtime-monolith

### 6.1 タスク

- ☑ `agent-runtime-monolith/pom.xml`（packaging=jar、依存: agent-common + 7 Agent + common-lib、`spring-boot-maven-plugin` の `<plugin>` を **宣言**）
- ☑ `AgentRuntimeApplication.java`（`@SpringBootApplication(scanBasePackages = {...})` で 9 パッケージをスキャン）
- ☑ `config/MonolithSecurityConfig.java`（**単一 `SecurityFilterChain`**: `/api/v1/orchestrator/**` を JWT 認証、`/actuator/health` を permitAll、その他 `/api/v1/agents/**` は管理用として `hasRole("AGENT_ADMIN")` などで保護）
- ☑ `application.yml`（[apply-existing-services.md §0.5](apply-existing-services.md) のテンプレートを使用）
- ☑ `Dockerfile`（multi-stage、ベース `eclipse-temurin:21-jre`）

### 6.2 完了確認

```bash
mvn -pl ai-agent-services/agent-runtime-monolith -am clean package
java -jar ai-agent-services/agent-runtime-monolith/target/agent-runtime-monolith-*.jar
```

✅ ビルド成功（`*.jar.original` あり = repackage 実行された）
✅ 起動ログに以下を確認:
  - `Tomcat started on port(s): 8100`
  - `LocalWorkerAgentInvoker` が Bean 登録された旨
  - `RemoteWorkerAgentInvoker` / `WorkerAgentRestClient` / 各 `*AgentSecurityConfig` が **登録されない** 旨（`@ConditionalOnProperty` ログ）
  - 7 Agent の `*AutoConfiguration` がすべて適用された旨
✅ ヘルスチェック: `curl http://localhost:8100/actuator/health` → `{"status":"UP"}`
✅ Worker Controller 疎通: `curl http://localhost:8100/api/v1/agents/weather/current?location=Naeba` → `agents.web.enabled=true` のためレスポンスあり、ただし JWT がないと 401（MonolithSecurityConfig の管理者保護）
✅ Orchestrator 疎通: 有効な JWT で `POST /api/v1/orchestrator/recommend` → 200 + `OrchestratorResponse`

⚠ 落とし穴: `MonolithSecurityConfig` 内では `InternalApiKeyAuthenticationFilter` を **登録しない**（モノリスでは Worker 間呼び出しが in-JVM のため）。ただし JWT フィルタは必要

⚠ 落とし穴: `scanBasePackages` から 1 パッケージでも漏れると当該 Agent の Bean が一切 Bean 化されない。`AgentRuntimeApplicationContextTest` で `assertThat(ctx.getBean(WeatherAgentService.class)).isNotNull()` を 7 Agent 分繰り返す

---

## Phase 7 — 既存サービス側改修

[apply-existing-services.md](apply-existing-services.md) の §1〜§10 に従う。各サービスごとに **新規・修正ファイルチェックリスト**を維持。

### 7.1 共通（全 8 既存サービス）

- ☑ `pom.xml` に `common-lib` 依存を追加
- ☑ 既存 `SecurityConfig` の `addFilterBefore(jwt, UsernamePassword.class)` の **直前** に `addFilterBefore(internalApiKey, jwt.class)` を追加
- ☑ `application.yml` / `application.properties` に `internal.api-key: ${INTERNAL_API_KEY}` を追加

### 7.2 サービス別新規 API

| 既存サービス | 新設 API | 呼び元 | 取り扱い |
|------------|----------|-------|---------|
| **payment-cart-service** | `POST /api/v1/cart/build`（cartItems + couponDiscount + pointDiscount → orderId/totalAmount/status） | Orchestrator `PaymentCartClient` | **新規実装必須** |
| **inventory-management-service** | `POST /api/v1/inventory/reservations`（orderId + items + ttl） | inventory-monitoring-agent | 既存 API があれば流用、なければ新設 |
| **inventory-management-service** | `GET /api/v1/inventory/products/{id}/availability` | equipment-matching-agent / inventory-monitoring-agent | 既存 API 確認 |
| **user-management-service** | `GET /api/v1/users/{id}/profile`（`customerTier`, `purchasedCategories`, `pointBalance` を含むこと） | customer-intent / orchestrator | 既存 profile API を拡張 |
| **point-service** | `GET /api/v1/points/{userId}/balance` | coupon-optimization-agent | 既存 API 確認、なければ新設 |
| **sales-management-service** | `GET /api/v1/sales/products/{id}/demand?days=30` | dynamic-pricing-agent | 既存 API 確認 |
| **coupon-service** | `GET /api/v1/coupons/available?userId=...` | coupon-optimization-agent | 既存 API 確認 |
| **api-gateway-service** | ルーティング: `/api/v1/orchestrator/**` → `agent-runtime-monolith:8100` を追加。`/api/v1/agents/**` は **拒否** | フロントエンド | 必須 |

### 7.3 完了確認

```bash
# 全サービスビルド
mvn -DskipTests=false clean install

# 内部 API キー疎通確認（任意のドメインサービスに対して）
curl -H "X-Internal-Api-Key: $INTERNAL_API_KEY" -H "X-Caller-Service: weather-agent" \
     http://localhost:8081/api/v1/users/u-123/profile
# → 200 OK
curl http://localhost:8081/api/v1/users/u-123/profile
# → 401 Unauthorized（API キーなし）
```

✅ 全 8 サービスのテスト成功
✅ 新規 API すべてに OpenAPI ドキュメント追記
✅ api-gateway 経由で `/api/v1/agents/**` が 403（または 404）を返す

⚠ 落とし穴: `InternalApiKeyAuthenticationFilter` の挿入順を JWT フィルタの前にしないと、無効 JWT で先に 401 になり API キー認証経路が試されない

---

## Phase 8 — 統合テスト・モノリス起動検証

### 8.1 タスク

- ☑ `docker-compose.yml` をハイブリッド対応に更新（[apply-existing-services.md §11.1](apply-existing-services.md)）
- ☑ `scripts/health-check.sh` を更新（agent-runtime も追加）
- ☑ E2E テストシナリオ作成（`load-tests/scripts/orchestrator-e2e.js` 等）

### 8.2 完了確認

```bash
docker-compose up -d
./scripts/health-check.sh

# Orchestrator 経由の E2E
JWT=$(curl -X POST http://localhost:8086/api/v1/auth/token -d '{"username":"alice","password":"..."}' | jq -r .accessToken)

curl -X POST http://localhost:8080/api/v1/orchestrator/recommend \
     -H "Authorization: Bearer $JWT" \
     -H "Content-Type: application/json" \
     -d '{
       "userId": "alice",
       "userMessage": "週末に苗場でスキーをしたいので、初心者向けのスキー板とウェアを 5 万円以内で揃えたい",
       "usePoints": true
     }'
```

✅ レスポンスに以下が含まれる:
  - `orderId`（payment-cart で確定済み）
  - `recommendations[]`（ランキング）
  - `couponEvaluation`（適用クーポン）
  - `quoteSummary.totalAmount`（クーポン・ポイント差引後）
  - `reservationExpiresAt`（在庫予約期限）
✅ 各 Worker ログに `Tool xxxxx called` のトレースが出ている（GPT が `OrchestratorWorkerTools` を計画通り呼んだ証拠）
✅ Prometheus メトリクスに `agent_invocations_total{agent="weather"}` 等が記録されている

⚠ 落とし穴: Azure OpenAI のレート制限（TPM/RPM）に注意。E2E テストは **直列実行**

⚠ 落とし穴: GPT の Tool 呼び出し順は確率的なため、E2E は「最終結果が条件を満たすか」で判定（Tool 呼び出し順そのものは strict にアサートしない）

---

## Phase 9（オプション） — Standalone 化と分散検証

### 9.1 タスク

特定 Agent を分離する場合のみ実施。例として `weather-standalone` を作成する場合:

- ☑ `agent-runtime-standalone/weather-standalone/pom.xml`（依存: weather-agent + agent-common + common-lib のみ）
- ☑ `WeatherStandaloneApplication.java`（`scanBasePackages = {"com.example.skishop.agent.common", "com.example.skishop.agent.weather", "com.example.skishop.common"}`）
- ☑ `application.yml`（`server.port=8100`, `agents.deployment.mode=distributed`）
- ☑ Dockerfile
- ☑ `docker-compose.distributed.yml` に追加

### 9.2 完了確認

```bash
docker-compose -f docker-compose.distributed.yml up -d weather-agent
curl -H "X-Internal-Api-Key: $INTERNAL_API_KEY" -H "X-Caller-Service: orchestrator-agent" \
     http://localhost:8100/api/v1/agents/weather/current?location=Naeba
# → 200 OK
curl http://localhost:8100/api/v1/agents/weather/current?location=Naeba
# → 401（API キーなし）
```

✅ standalone 起動時に `WeatherAgentSecurityConfig` が Bean 登録されている（ログ確認）
✅ standalone 起動時に `LocalWeatherInvoker` が Bean 化されない（standalone 側 Equipment/Pricing が `RemoteWeatherInvoker` を使うことを確認）

⚠ 落とし穴: `equipment-matching-standalone` を作る場合、`weather-agent` モジュール依存は **外す**（`weather-standalone` を REST で呼ぶのみ）。これによって `WeatherAgentService` Bean が同 JVM に存在しないため `LocalWeatherInvoker` の `@ConditionalOnBean` が成立せず、`RemoteWeatherInvoker` が選ばれる

---

## Phase 10 — 監視・運用整備

### 10.1 タスク

- ☑ Prometheus scrape config に `agent-runtime` ジョブを追加
- ☑ Grafana ダッシュボード新規作成: 「Multi-Agent Operations」
  - パネル: 各 Agent の呼び出し回数・レイテンシ p95・エラー率
  - パネル: Azure OpenAI トークン消費量（`spring_ai_chat_client_*` メトリクス）
- ☑ `docs/runbook.md` に「Agent 起動失敗時の調査手順」「LocalWeatherInvoker / RemoteWeatherInvoker の切替確認方法」を追記
- ☑ `docs/rollback-plan.md` に「分散→モノリス回帰手順」を追記
- ☑ アラート: 各 Agent の 5xx 率 > 5% で Slack 通知

### 10.2 完了確認

✅ Grafana で Multi-Agent ダッシュボードが表示される
✅ 意図的に Worker を 1 台落として 5xx を発生させ、Slack 通知が来る
✅ runbook の手順に沿って `agents.deployment.mode` を環境変数で切り替えるとプロセス再起動だけで回帰可能なことを検証

---

## 11. 全体完了確認（最終ゲート）

| # | 確認項目 | コマンド / 観察対象 |
|---|---------|------------------|
| F1 | 全モジュールビルド成功 | `mvn -DskipTests=false clean install` |
| F2 | カバレッジ 80%+ | `mvn jacoco:report` の `index.html` |
| F3 | モノリス起動成功 | `java -jar agent-runtime-monolith.jar` のログ |
| F4 | E2E 成功 | Phase 8.2 の curl |
| F5 | Bean 名規約遵守 | `grep -rE '@Bean\b.*ChatClient\b' ai-agent-services/*/src/main/java` で 7 Bean が `<agent>AgentChatClient` 形式 |
| F6 | `@Qualifier` 漏れゼロ | `grep -rE 'ChatClient\s+\w+' ai-agent-services/*/src/main/java` で `@Qualifier` が直前にあること |
| F7 | `@ConditionalOnProperty` 漏れゼロ | `grep -L 'ConditionalOnProperty' ai-agent-services/*/src/main/java/**/*SecurityConfig.java` で空 |
| F8 | OWASP 観点 | 入力 DTO すべてに `@NotBlank` / `@Size` / `@Positive` のいずれか、SQL は JPA/MyBatis のパラメータバインドのみ |
| F9 | ログレベル | `System.out.println` ゼロ、SLF4J 経由のみ |
| F10 | コミット規約 | `git log --oneline | head` が Conventional Commits 形式 |

すべて ✅ になれば実装完了。

---

## 12. 推奨実施順（カレンダー目安なし）

1. **Phase 0 → 1**: 基盤確立（agent-common までで他フェーズの依存を解消）
2. **Phase 2**: weather-agent（他の Worker が `WeatherInvoker` の参照実装を必要とするため最優先）
3. **Phase 3**: 他 Worker 5 つ（並列実装可能、ただし PR は順次レビュー）
4. **Phase 4**: ユニットテスト（各 Worker 完成後すぐ）
5. **Phase 5**: orchestrator-agent
6. **Phase 6**: agent-runtime-monolith（ここで初めてフルスタックビルド）
7. **Phase 7**: 既存サービス改修（並行可能、特に payment-cart-service の新規 API は Phase 6 と同時着手推奨）
8. **Phase 8**: 統合テスト
9. **Phase 9**: standalone（必要が生じたとき）
10. **Phase 10**: 監視・運用

---

## 13. レビュー時のチェックリスト（PR テンプレート用）

各 PR で必ず確認:

- [ ] 詳細設計書のどの章に対応するか PR 説明に記載
- [ ] DTO record のフィールド順が agent-common の宣言と一致
- [ ] Bean 名が `<agent>AgentChatClient` / `<agent>AgentToolCallbacks` 等の規約に従う
- [ ] 全 ChatClient 注入に `@Qualifier`
- [ ] Controller に `@ConditionalOnProperty("agents.web.enabled", matchIfMissing=true)`
- [ ] SecurityConfig に `@ConditionalOnProperty("agents.deployment.mode", "distributed")`
- [ ] AutoConfiguration の `@Import` 漏れなし、`META-INF/spring/...AutoConfiguration.imports` に FQCN 1 行
- [ ] テストカバレッジ 80% 以上（差分カバレッジ）
- [ ] OWASP: 入力検証 / 認証認可 / 秘密情報のハードコードなし
- [ ] `System.out.println` ゼロ、SLF4J 使用
- [ ] コミット: Conventional Commits 形式
