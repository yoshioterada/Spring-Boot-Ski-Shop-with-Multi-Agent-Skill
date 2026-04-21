# Source Code Review Integrated Report

## Verdict
- **Target**: Full Application (全マイクロサービス — 12 モジュール, 425+ Java ファイル)
- **Verdict**: 🚨 ❌ Rejected — Critical 指摘が複数存在
- **Review Date**: 2026-04-21
- **Project**: SkiShop (Java 21 / Spring Boot 3.2.x Monolith EC Site)

---

## Findings Summary

| Agent | Verdict | Critical | High | Medium | Low | Score |
|-------|--------|----------|------|--------|-----|-------|
| tech-lead | ⚠️ | 3 | 8 | 9 | 4 | 15/20 |
| architecture-reviewer | ⚠️ | 2 | 4 | 6 | 3 | 17/25 |
| ddd-domain-reviewer | ⚠️ | 1 | 7 | 6 | 3 | 20/30 |
| api-endpoint-reviewer | ⚠️ | 4 | 14 | 10 | 4 | 18/25 |
| java-standards-reviewer | ⚠️ | 5 | 8 | 6 | 3 | 20/25 |
| async-concurrency-reviewer | ❌ | 4 | 8 | 3 | 2 | 13/25 |
| error-logging-reviewer | ⚠️ | 2 | 6 | 5 | 2 | 17/25 |
| data-access-reviewer | ⚠️ | 3 | 11 | 5 | 3 | 18/25 |
| config-di-reviewer | ⚠️ | 2 | 8 | 6 | 3 | 18/25 |
| security-reviewer | ⚠️ | 3 | 9 | 7 | 3 | 44/65 |
| dependency-reviewer | ⚠️ | 0 | 2 | 6 | 5 | 21/25 |
| test-quality-reviewer | ⚠️ | 0 | 14 | 10 | 4 | 14/25 |
| performance-reviewer | ⚠️ | 3 | 10 | 8 | 4 | 14/25 |
| resilience-reviewer | ⚠️ | 2 | 12 | 8 | 3 | 19/35 |
| **Total (重複統合後)** | | **≈25** | **≈80** | **≈60** | **≈30** | |

> 注: 重複統合により、実際のユニーク指摘数は上記の単純合計より少なくなります。

---

## Verdict Rationale

- **判定ルール適用結果**: Critical 指摘が1件以上存在 → 自動的に **❌ Rejected**
- **最も重大な指摘群**:
  1. **セキュリティ — 価格改竄脆弱性**: カート追加時にクライアント送信の `unitPrice` をそのまま使用。攻撃者が価格を0円に設定可能
  2. **並行性 — 在庫・ポイント・クーポンのレース条件**: `reserveStock()`, `redeemPoints()`, `transferPoints()`, `redeemCoupon()` に排他制御なし。二重販売・マイナス残高の発生リスク
  3. **セキュリティ — IDOR (softDeleteUser)**: 任意の認証済みユーザーが他ユーザーのアカウントを削除可能
  4. **設定 — JWT シークレットの安全でないフォールバック**: 環境変数未設定時に既知の値でサービスが起動し、トークン偽造が可能

---

## 🚨 Critical/High Findings List (Fix Required)

### Critical Findings

| # | Severity | Source Agent(s) | Category | Target File | Finding | Suggested Fix |
|---|----------|----------------|----------|-------------|---------|---------------|
| C-1 | Critical | security | 価格改竄 (A08) | [AddCartItemRequest.java](payment-cart-service/src/main/java/com/example/skishop/payment/dto/AddCartItemRequest.java#L16) | `unitPrice` がクライアント送信値をそのまま使用。攻撃者が `unitPrice: 0` で商品を無料購入可能 | `unitPrice` を DTO から削除。カート追加時に inventory-management-service から現在価格を取得 |
| C-2 | Critical | async-concurrency | レース条件 | [ProductService.java](inventory-management-service/src/main/java/com/example/skishop/inventory/service/ProductService.java#L124) | `reserveStock()` に check-then-act レース条件。`@Transactional` なし、排他ロックなし。並行予約で**在庫の二重販売**が発生 | `@Transactional` + `@Lock(PESSIMISTIC_WRITE)` または `@Version` による楽観ロック追加 |
| C-3 | Critical | async-concurrency | レース条件 | [PointService.java](point-service/src/main/java/com/example/skishop/point/service/PointService.java#L130) | `redeemPoints()` で残高チェック後に減算する際に排他制御なし。並行実行で**ポイント残高がマイナス**になる | `SELECT FOR UPDATE` または DB レベルの `CHECK (balance >= 0)` 制約 |
| C-4 | Critical | async-concurrency | レース条件 | [PointService.java](point-service/src/main/java/com/example/skishop/point/service/PointService.java#L162) | `transferPoints()` に同様の check-then-act 問題。加えて逆方向の同時転送でデッドロックの可能性 | ロック順序の統一（UUID の辞書順）＋ 楽観ロック |
| C-5 | Critical | security | IDOR (A01) | [AuthController.java](authentication-service/src/main/java/com/example/skishop/auth/controller/AuthController.java#L121) | `DELETE /api/v1/auth/users/{userId}` に `@PreAuthorize` なし。任意の認証済みユーザーが他ユーザーを削除可能 | `@PreAuthorize("#userId == authentication.principal or hasRole('ADMIN')")` 追加 |
| C-6 | Critical | config-di, security, tech-lead | JWT シークレット | [application.yml](ai-agent-services/agent-runtime-monolith/src/main/resources/application.yml#L44) | JWT シークレットに既知のフォールバック値。環境変数未設定で起動するとトークン偽造可能 | デフォルト値を削除: `secret: ${JWT_SECRET}` |
| C-7 | Critical | architecture, api-endpoint, tech-lead | レイヤー違反 | [InternalInventoryController.java](inventory-management-service/src/main/java/com/example/skishop/inventory/controller/InternalInventoryController.java#L33) | Controller が `ProductRepository` を直接注入し、170行超のビジネスロジックを含む | `InternalInventoryService` を新設し、全ロジックを移動 |
| C-8 | Critical | architecture, api-endpoint | レイヤー違反 | [SalesAnalyticsController.java](sales-management-service/src/main/java/com/example/skishop/sales/controller/SalesAnalyticsController.java#L34) | Controller が `JdbcTemplate` を直接注入し、6つの生 SQL クエリを実行 | `SalesAnalyticsRepository` または `SalesAnalyticsService` に移動 |
| C-9 | Critical | java-standards, tech-lead | Stub 実装 | [InternalCouponController.java](coupon-service/src/main/java/com/example/skishop/coupon/controller/InternalCouponController.java#L27), [InternalSalesController.java](sales-management-service/src/main/java/com/example/skishop/sales/controller/InternalSalesController.java#L26), [InternalPointController.java](point-service/src/main/java/com/example/skishop/point/controller/InternalPointController.java#L25) | 本番コードに TODO + ダミーデータを返す Stub 実装が残存 | 実際の Repository クエリを実装 |
| C-10 | Critical | java-standards | Stub 実装 | [PaymentService.java](payment-cart-service/src/main/java/com/example/skishop/payment/service/PaymentService.java#L104) | `handleWebhook()` が `null` を返す Stub。Webhook 署名検証なし | Webhook 処理を実装 |
| C-11 | Critical | data-access, performance, config-di, tech-lead | DoS リスク | [application.properties](sales-management-service/src/main/resources/application.properties#L39) | `max-page-size=50000` — 1リクエストで5万行を取得可能。OOM とスロークエリの原因 | 1000以下に制限。大量データはストリーミング/集計クエリで対応 |
| C-12 | Critical | data-access | スキーマ不整合 | [UserProfile.java](user-management-service/src/main/java/com/example/skishop/usermanagement/model/UserProfile.java#L30) | `address` フィールドに対応する Flyway マイグレーションなし。`ddl-auto=validate` でサービス起動失敗 | `V5__add_address_column.sql` を追加 |
| C-13 | Critical | data-access | 無制限クエリ | [SecurityLogRepository.java](authentication-service/src/main/java/com/example/skishop/auth/repository/SecurityLogRepository.java#L13) | セキュリティログの unbounded `List` 返却。大量ログで OOM 発生リスク | `Page<SecurityLog>` + `Pageable` に変更 |
| C-14 | Critical | async-concurrency | Poison Pill | [MailEventConsumer.java](mailsend-service/src/main/java/com/example/skishop/mailsend/consumer/MailEventConsumer.java#L68) | 処理不能メッセージで `throw new RuntimeException` → 無限リトライ | DLT (Dead Letter Topic) を設定し、処理不能メッセージは DLT に送る |
| C-15 | Critical | error-logging | PII 漏洩 | [MailService.java](mailsend-service/src/main/java/com/example/skishop/mailsend/service/MailService.java#L189) | メールアドレスがマスクなしでログ出力される | `maskEmail()` を適用 |
| C-16 | Critical | error-logging | Observability 欠損 | プロジェクト全体 | `logback-spring.xml` が存在しない。CorrelationIdFilter の MDC 値がログに反映されず、構造化ログなし | `logback-spring.xml` を作成し、`%X{correlationId}` を含むパターンを設定 |
| C-17 | Critical | performance | N+1 クエリ | [OrderService.java](sales-management-service/src/main/java/com/example/skishop/sales/service/OrderService.java#L83) | `getOrder()` で LAZY `items` にアクセスし N+1 発生。`getAllOrders()` でも同様 | `@EntityGraph(attributePaths = "items")` を追加 |
| C-18 | Critical | performance | N+1 クエリ | [UserService.java](user-management-service/src/main/java/com/example/skishop/usermanagement/service/UserService.java#L320) | `toResponse()` で LAZY `role` にアクセスし N+1 発生 | `@EntityGraph(attributePaths = "role")` を追加 |
| C-19 | Critical | performance | DB call in loop | [CouponService.java](coupon-service/src/main/java/com/example/skishop/coupon/service/CouponService.java#L200) | `bulkGenerateCoupons()` でループ内に `existsByCode()` 呼び出し（1000件=1000回DB呼び出し） | バッチで一括チェック、またはユニーク制約違反で catch |
| C-20 | Critical | config-di | セキュリティヘッダー欠損 | 全 SecurityConfig.java (9ファイル) | `headers(...)` DSL 未設定。`X-Content-Type-Options`, `X-Frame-Options`, `Strict-Transport-Security` なし | `.headers(h -> h.frameOptions(f -> f.deny()).contentTypeOptions(Customizer.withDefaults()))` 追加 |
| C-21 | Critical | resilience | タイムアウトなし | [OpenMeteoClient.java](ai-agent-services/weather-agent/src/main/java/com/example/skishop/agent/weather/client/OpenMeteoClient.java#L36) | 外部 API 呼び出しにタイムアウト設定なし。無期限ブロック | `SimpleClientHttpRequestFactory` でタイムアウト設定 |
| C-22 | Critical | resilience | タイムアウト/リトライ/CB なし | [WorkerAgentRestClient.java](ai-agent-services/orchestrator-agent/src/main/java/com/example/skishop/agent/orchestrator/client/WorkerAgentRestClient.java#L111) | 6つの worker agent 呼び出しにタイムアウト、リトライ、サーキットブレーカーなし | タイムアウト + `@Retry`/`@CircuitBreaker` 追加 |
| C-23 | Critical | tech-lead | ハードコードパスワード | [06_seed_points_backfill.sql](docker/initdb/06_seed_points_backfill.sql#L80) | `dblink` に `user=postgres password=postgres` がハードコード | 環境変数 or pg_hba trust を使用 |

### High Findings (上位抜粋・重要度順)

| # | Severity | Source Agent(s) | Category | Target File | Finding | Suggested Fix |
|---|----------|----------------|----------|-------------|---------|---------------|
| H-1 | High | security | レート制限なし (A04) | api-gateway 全体 | 設計ドキュメントにレート制限指定あるが**実装なし**。ログイン攻撃に対する防御なし | Redis-backed RequestRateLimiter を実装 |
| H-2 | High | security | IDOR (A01) | [ChatController.java](ai-support-service/src/main/java/com/example/skishop/ai/controller/ChatController.java) | 全エンドポイントに `@PreAuthorize` なし。他ユーザーのチャット履歴を閲覧可能 | オーナーシップ検証を追加 |
| H-3 | High | security | プロンプトインジェクション (A03) | [SearchService.java](ai-support-service/src/main/java/com/example/skishop/ai/service/SearchService.java#L64) | ユーザー検索クエリが LLM プロンプトに直接埋め込まれる | `PromptSanitizer.sanitize()` を適用 |
| H-4 | High | async-concurrency | レース条件 | [CouponService.java](coupon-service/src/main/java/com/example/skishop/coupon/service/CouponService.java#L135) | `redeemCoupon()` の usedCount チェック+インクリメントに排他制御なし | `@Lock(PESSIMISTIC_WRITE)` 追加 |
| H-5 | High | async-concurrency | 分散環境問題 | [OrderService.java](sales-management-service/src/main/java/com/example/skishop/sales/service/OrderService.java#L32) | `AtomicLong` で注文番号生成 → マルチインスタンスで衝突 | DB シーケンスまたは UUID に変更 |
| H-6 | High | data-access | @Version 欠損 | [User.java](authentication-service/src/main/java/com/example/skishop/auth/model/User.java), [Shipment.java](sales-management-service/src/main/java/com/example/skishop/sales/model/Shipment.java) 他6エンティティ | 7つの可変エンティティに楽観ロックなし | `@Version private Long version;` とマイグレーション追加 |
| H-7 | High | data-access | OLE ハンドリングなし | 全サービス | `OptimisticLockingFailureException` をキャッチする `@ControllerAdvice` がない | `GlobalExceptionHandler` に 409 Conflict ハンドラ追加 |
| H-8 | High | async-concurrency | Blocking + @Transactional | [MailService.java](mailsend-service/src/main/java/com/example/skishop/mailsend/service/MailService.java#L155) | `@Transactional` 内でリトライループ + HTTP 呼び出し（最大90秒）。DB コネクション枯渇リスク | トランザクション外に send ロジックを分離 |
| H-9 | High | config-di, security, tech-lead | デフォルト API キー | 8サービスの application.properties | `internal.api-key` に予測可能なフォールバック値 | デフォルト値を削除し、起動時にバリデーション |
| H-10 | High | resilience | Health Probes 欠損 | auth, user, sales, payment, point, mail の6サービス | `management.endpoint.health.probes.enabled=true` 未設定。K8s の liveness/readiness が機能しない | 全サービスに追加 |
| H-11 | High | api-endpoint | 入力検証欠損 | [ProductController.java](inventory-management-service/src/main/java/com/example/skishop/inventory/controller/ProductController.java#L88) | batch エンドポイントに `@Size` なし。無制限 List で DoS | `@Size(max = 200)` 追加 |
| H-12 | High | api-endpoint | 認可欠損 | [OrderController.java](sales-management-service/src/main/java/com/example/skishop/sales/controller/OrderController.java#L77) | `cancelOrder()` にオーナーシップチェックなし。任意のユーザーが任意の注文をキャンセル可能 | `SecurityUtils.verifyOwnershipOrAdmin()` 追加 |
| H-13 | High | ddd-domain | 貧血ドメインモデル | [Order.java](sales-management-service/src/main/java/com/example/skishop/sales/model/Order.java#L131), [User.java](authentication-service/src/main/java/com/example/skishop/auth/model/User.java#L112), [UserProfile.java](user-management-service/src/main/java/com/example/skishop/usermanagement/model/UserProfile.java#L97) | 公開 setter で状態遷移を許可。ビジネスルールが Service に漏洩 | ドメインメソッド化（`Order.cancel()`, `User.lockAccount()` 等） |
| H-14 | High | ddd-domain | Value Object 欠損 | [Order.java](sales-management-service/src/main/java/com/example/skishop/sales/model/Order.java#L36) | 金額を `BigDecimal` で直接保持。`Money` Value Object なし | `@Embeddable Money(BigDecimal amount, String currency)` 作成 |
| H-15 | High | test-quality | テストカバレッジ不足 | 全体 | `@DataJpaTest` ゼロ、Testcontainers ゼロ、JaCoCo 閾値 60%（目標80%と乖離） | Repository slice テスト追加、JaCoCo を80%に引き上げ |
| H-16 | High | config-di | Kafka auto-create-topics | 5つの `-kafka.properties` | `auto-create-topics=true` → 本番で不適切なパーティション数のトピック自動作成 | `false` に変更、IaC でトピック事前作成 |
| H-17 | High | resilience | Gateway リトライ | [RouteConfig.java](api-gateway-service/src/main/java/com/example/skishop/gateway/config/RouteConfig.java#L75) | 非冪等ルート（payments, orders）に POST リトライ3回 → 重複注文/決済のリスク | GET のみリトライ or べき等キー実装 |
| H-18 | High | config-di | Actuator /env 公開 | [application-dev.properties](authentication-service/src/main/resources/application-dev.properties#L10) | `/actuator/env` が開発プロファイルで公開 — 秘密情報を含む全環境変数がダンプされる | 公開リストから `env` を除外 |

---

## Escalation Items (Human Judgment Required)

| # | Priority | Source Agent(s) | Description | Recommended Decision Maker |
|---|----------|----------------|-------------|---------------------------|
| E-1 | **最高** | security | 価格改竄の修正アーキテクチャ: cart-service から inventory-service への同期呼び出し or イベント駆動での価格同期 | アーキテクト + プロダクトオーナー |
| E-2 | **最高** | async-concurrency | 在庫/ポイント/クーポンのロック戦略: 悲観ロック vs 楽観ロック vs DB 制約。スループットとユーザー体験への影響 | アーキテクト |
| E-3 | **高** | java-standards, tech-lead | Internal*Controller の Stub 実装: マルチエージェントシステムの機能に直結。今すぐ実装 or エージェント統合フェーズまで延期 | プロダクトオーナー |
| E-4 | **高** | security | レート制限の実装方式: Redis-backed トークンバケット or インメモリ。Redis の可用性に依存 | インフラチーム |
| E-5 | **高** | config-di, security | Internal API Key セキュリティモデル: 共有シークレット or mTLS or OAuth2 client-credentials | セキュリティチーム |
| E-6 | **高** | data-access | `max-page-size=50000` の業務要件確認: サーバーサイド集計クエリで代替可能か | プロダクトオーナー + DBA |
| E-7 | **通常** | error-logging | `logback-spring.xml` のログフォーマット・集約先決定: JSON for ELK/Datadog/CloudWatch | インフラチーム |
| E-8 | **通常** | test-quality | JaCoCo 閾値引き上げスケジュール: 段階的（60→70→80）or 一括 | テックリード |
| E-9 | **通常** | resilience | Gateway の20分タイムアウト: AI/オーケストレーター用の正当な設定かデバッグの残骸か | テックリード |
| E-10 | **通常** | resilience | SpringCloudStreamEventPublisher の障害時動作: fail-fast（例外スロー）vs best-effort（ログのみ） | アーキテクト |

---

## Conflict Resolution Record

| # | Agent A | Agent B | Conflict Description | Tech-Lead Adjudication | Rationale |
|---|---------|---------|---------------------|------------------------|-----------|
| — | — | — | 矛盾は検出されませんでした | — | — |

---

## Design Document Cross-Reference Results

### Deviations from Design Documents
- **api-gateway-design.md**: `RequestRateLimiter: 10 req/s, burst=20` が設計に記載されているが未実装
- **各サービスの design.md**: `HealthIndicator` カスタム実装が設計に記載されているが未実装
- **coupon-service-design.md / sales-management-design.md / point-service-design.md**: Internal API は「実装済み」と記載されるべきだが Stub のまま

### Unimplemented Design Elements
- レート制限（全ルート）
- カスタム HealthIndicator（全サービス）
- Internal API の実装（coupon, sales, point の3サービス）
- logback-spring.xml による構造化ログ

---

## Production Readiness Matrix

| Service | Graceful Shutdown | Health Probes | HikariCP | Log Level | Error Suppress | Prometheus | Overall |
|---------|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| authentication-service | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| user-management-service | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| inventory-management-service | ✅ | ✅ | N/A | ✅ | ✅ | ❌ | ⚠️ |
| sales-management-service | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| payment-cart-service | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| point-service | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| coupon-service | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| ai-support-service | ❌ | ❌ | N/A | ❌ | ❌ | ✅ | ❌ |
| mailsend-service | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| api-gateway-service | ✅ | ✅ | N/A | ❌ | ✅ | ❌ | ⚠️ |
| agent-runtime-monolith | ❌ | ❌ | N/A | ❌ | ❌ | ✅ | ❌ |

---

## Individual Agent Detail Reports

<details>
<summary>tech-lead Review Report</summary>

### Summary
- Critical: 3 / High: 8 / Medium: 9 / Low: 4 / Score: 15/20

### Key Findings
1. **Critical**: JWT secret hardcoded fallback in agent-runtime application.yml
2. **Critical**: dblink password hardcoded in seed SQL
3. **Critical**: Exception swallowing in ZeroHitOpportunityService
4. **High**: 3 Stub implementations in Internal*Controller (coupon, sales, point)
5. **High**: InternalInventoryController layer violation (Controller → Repository)
6. **High**: Redundant @Autowired in MailService
7. **High**: java.util.Date in JwtTokenService
8. **High**: Insecure default for internal API key
9. **High**: max-page-size=50000 in sales-management

### Production Readiness: 2/11 services fully ready; authentication-service, ai-support-service, agent-runtime-monolith have multiple config gaps
</details>

<details>
<summary>architecture-reviewer Review Report</summary>

### Summary
- Critical: 2 / High: 4 / Medium: 6 / Low: 3 / Score: 17/25

### Key Findings
1. **Critical**: InternalInventoryController directly injects ProductRepository (170+ lines of business logic)
2. **Critical**: SalesAnalyticsController directly injects JdbcTemplate (6 raw SQL queries)
3. **High**: PointController returns JPA entity TierDefinition directly
4. **High**: InternalInventoryController has complex stream filtering/mapping logic
5. **High**: SalesAnalyticsController contains YoY growth calculation logic
6. **High**: ChatController imports JPA entity inner enum
7. **Medium**: Duplicated JwtAuthenticationFilter (auth-service vs common-lib)
8. **Medium**: All services use flat dto/ (not split into request/response)
</details>

<details>
<summary>ddd-domain-reviewer Review Report</summary>

### Summary
- Critical: 1 / High: 7 / Medium: 6 / Low: 3 / Score: 20/30

### Key Findings
1. **Critical**: CartItemRepository violates Aggregate Root boundary
2. **High**: Anemic domain models — Order, User, UserProfile, Shipment, ReturnRequest expose public setters with business logic in services
3. **High**: Missing Value Objects — Money, Address not modeled
4. **Medium**: Event naming inconsistency (PascalCase vs dot notation)
5. **Medium**: Domain logic in service (Order status transitions)
</details>

<details>
<summary>api-endpoint-reviewer Review Report</summary>

### Summary
- Critical: 4 / High: 14 / Medium: 10 / Low: 4 / Score: 18/25

### Key Findings
1. **Critical**: InternalInventoryController layer violation + unvalidated Map<String,Object> input
2. **Critical**: Batch endpoints without @Size validation (DoS vector)
3. **Critical**: ai-support-service missing server.error.include-stacktrace=never
4. **High**: ChatController/RecommendationController/SearchController — no @PreAuthorize on any endpoint
5. **High**: OrderController — missing auth on cancelOrder/shipment/return endpoints
6. **High**: POST without Location header (coupon, campaign creation)
7. **High**: AuthController softDeleteUser IDOR
8. **Positive**: Excellent GlobalExceptionHandler with RFC 7807 ProblemDetail
</details>

<details>
<summary>java-standards-reviewer Review Report</summary>

### Summary
- Critical: 5 / High: 8 / Medium: 6 / Low: 3 / Score: 20/25

### Key Findings
1. **Critical**: 4 TODO/Stub implementations in production (InternalPoint, InternalSales, InternalCoupon, PaymentService.handleWebhook)
2. **Critical**: Hardcoded product IDs in RecommendationService
3. **High**: @Autowired on MailService constructor
4. **High**: Multiple catch(Exception) with missing stack trace in log
5. **High**: 19 instances of return null in production code (MailEventConsumer)
6. **Positive**: Record classes widely used for DTOs, naming conventions excellent, no System.out.println
</details>

<details>
<summary>async-concurrency-reviewer Review Report</summary>

### Summary
- Critical: 4 / High: 8 / Medium: 3 / Low: 2 / Score: 13/25

### Key Findings
1. **Critical**: reserveStock() race condition — no lock, no @Transactional
2. **Critical**: redeemPoints() race condition — negative balance possible
3. **Critical**: transferPoints() race condition + deadlock risk
4. **Critical**: MailEventConsumer poison pill (infinite retry)
5. **High**: ProductService all mutating methods lack @Transactional
6. **High**: MailService blocking I/O inside @Transactional (up to 90s)
7. **High**: CouponService.redeemCoupon() race condition on usageLimit
8. **High**: AtomicLong order number unsafe for distributed deployment
9. **Positive**: Virtual threads properly enabled across all applicable services
</details>

<details>
<summary>error-logging-reviewer Review Report</summary>

### Summary
- Critical: 2 / High: 6 / Medium: 5 / Low: 2 / Score: 17/25

### Key Findings
1. **Critical**: PII logging — raw email in MailService (L189)
2. **Critical**: No logback-spring.xml — correlationId in MDC but never appears in log output
3. **High**: No logstash-logback-encoder (production logs are plain text)
4. **High**: ai-support-service missing server.error.include-stacktrace=never
5. **High**: EmailSendException outside sealed ApplicationException hierarchy
6. **Positive**: SLF4J placeholders used consistently, no System.out.println, maskEmail() applied in most services
</details>

<details>
<summary>data-access-reviewer Review Report</summary>

### Summary
- Critical: 3 / High: 11 / Medium: 5 / Low: 3 / Score: 18/25

### Key Findings
1. **Critical**: UserProfile.address schema mismatch (no Flyway migration)
2. **Critical**: Unbounded SecurityLog query — OOM risk
3. **Critical**: max-page-size=50000 in sales-management
4. **High**: 7 mutable entities missing @Version (User, Shipment, ReturnRequest, Campaign, UserProfile, MailLog, VerificationToken)
5. **High**: Zero OptimisticLockException handling across entire codebase
6. **High**: OrderItem missing audit fields (createdAt/updatedAt)
7. **High**: N+1 risk on CampaignRepository.findByActiveTrue()
8. **Positive**: Good @EntityGraph usage on Cart/Order, consistent Instant usage, collections initialized
</details>

<details>
<summary>config-di-reviewer Review Report</summary>

### Summary
- Critical: 2 / High: 8 / Medium: 6 / Low: 3 / Score: 18/25

### Key Findings
1. **Critical**: Security headers not configured in any SecurityFilterChain
2. **Critical**: JWT secret hardcoded fallback in agent-runtime application.yml
3. **High**: Predictable dev fallback for internal API key in 8 services
4. **High**: Missing server.server-header suppression
5. **High**: max-page-size=50000
6. **High**: mailsend-service missing InternalApiKeyFilter
7. **High**: Kafka auto-create-topics=true in 5 services
8. **High**: Actuator /env exposed in dev profile
9. **Positive**: Constructor injection throughout, proper DI patterns
</details>

<details>
<summary>security-reviewer Review Report</summary>

### Summary
- Critical: 3 / High: 9 / Medium: 7 / Low: 3 / Score: 44/65

### Key Findings
1. **Critical**: Price tampering — client-submitted unitPrice in cart
2. **Critical**: IDOR on softDeleteUser (no @PreAuthorize)
3. **Critical**: Hardcoded default API key
4. **High**: No rate limiting (design docs specify 10 req/s, but not implemented)
5. **High**: IDOR in ChatController (any user reads any session)
6. **High**: Prompt injection in SearchService/RecommendationService
7. **High**: cancelOrder() missing ownership check
8. **High**: Timing-based user enumeration on login
9. **Positive**: Good IDOR protection in UserController/CartController/PaymentController, proper BCrypt usage, SecurityLogService audit
</details>

<details>
<summary>dependency-reviewer Review Report</summary>

### Summary
- Critical: 0 / High: 2 / Medium: 6 / Low: 5 / Score: 21/25

### Key Findings
1. **High**: jjwt versions hardcoded in 2 modules (not in root properties)
2. **High**: Redundant version specs on inter-module dependencies in agent sub-modules
3. **Medium**: Duplicate jjwt dependencies in authentication-service (transitive from common-lib)
4. **Medium**: Redundant resilience4j version overriding BOM
5. **Medium**: api-gateway-service missing common-lib dependency
6. **Low**: copilot-instructions.md declares Java 25 / Spring Boot 4.1 but pom.xml has Java 21 / Spring Boot 3.5.0
7. **Positive**: No SNAPSHOT dependencies, no prohibited dependencies, all licenses compatible (Apache 2.0/MIT/BSD)
</details>

<details>
<summary>test-quality-reviewer Review Report</summary>

### Summary
- Critical: 0 / High: 14 / Medium: 10 / Low: 4 / Score: 14/25

### Key Findings
1. **High**: Test naming violations in ai-agent-services (not should_X_when_Y, missing @DisplayName)
2. **High**: Zero @DataJpaTest repository slice tests
3. **High**: Zero Testcontainers usage
4. **High**: JaCoCo threshold 60% (project standard is 80%)
5. **High**: 6+ controllers missing @WebMvcTest integration tests
6. **High**: AuthenticationController (most critical) has no @WebMvcTest
7. **High**: Missing error-case tests for PaymentService, CartService
8. **Medium**: AAA section comments missing in many test files
9. **Positive**: Core service unit tests follow should_X_when_Y consistently, good IDOR security tests
</details>

<details>
<summary>performance-reviewer Review Report</summary>

### Summary
- Critical: 3 / High: 10 / Medium: 8 / Low: 4 / Score: 14/25

### Key Findings
1. **Critical**: N+1 Order→Items and UserProfile→Role
2. **Critical**: DB call in loop (CouponService.bulkGenerateCoupons)
3. **High**: getAllProducts() without Pageable (loads entire catalog)
4. **High**: max-page-size=50000
5. **High**: Duplicate API calls in WeatherAgentService
6. **High**: Sequential LLM calls in DynamicPricingAgent (no parallelization)
7. **High**: No DTO projections — full entities always fetched
8. **Medium**: Missing @Cacheable on getProductById(), getCouponByCode()
9. **Medium**: Unbounded ConcurrentHashMap in WeeklySummaryService (memory leak)
</details>

<details>
<summary>resilience-reviewer Review Report</summary>

### Summary
- Critical: 2 / High: 12 / Medium: 8 / Low: 3 / Score: 19/35

### Key Findings
1. **Critical**: OpenMeteoClient — no timeout, hardcoded URLs
2. **Critical**: WorkerAgentRestClient — no timeout/retry/CB for all 6 worker calls
3. **High**: 5 ai-agent REST clients without any timeout configuration
4. **High**: Fixed-interval retry (not exponential) in serviceCall
5. **High**: 6 services missing health probes
6. **High**: auth-service and ai-support-service missing graceful shutdown
7. **High**: Gateway retry on non-idempotent POST routes
8. **Medium**: No DLQ/DLT configured for any Kafka consumer
9. **Medium**: No bulkhead pattern anywhere in codebase
10. **Medium**: 20-minute gateway timeout (excessive)
11. **Positive**: Good Resilience4j setup in ai-support-service, gateway circuit breakers per service
</details>
