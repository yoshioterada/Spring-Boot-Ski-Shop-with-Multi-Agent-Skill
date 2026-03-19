# 是正計画書 (modify-plan.md)

> **対象**: Gate 1–4 統合レビューレポート (`2025-07-14_gate-1-4-full-review.md`) の指摘事項
> **方針**: 人間の判断を要さず、コード修正のみで対処可能な全項目を段階的に是正する
> **前提**: `mvn test` が BUILD SUCCESS を維持すること（各ステップ完了後に検証）

---

## スコープ外（人間判断が必要なため本計画に含めない項目）

以下は法務・経営・組織横断の意思決定を伴うため、本計画の対象外とする。

| ID | 区分 | 内容 | 必要な判断者 |
|----|------|------|-------------|
| C-01 | コンプライアンス | 同意管理メカニズムの設計方針 | 法務部門 / DPO |
| C-02 | コンプライアンス | データ保持期間ポリシーの策定 | 法務部門 |
| C-03 | コンプライアンス | OpenAI 越境データ移転の法的根拠 (DPA/SCC) | 法務部門 |
| C-04 | コンプライアンス | データ主体の権利 API の要件定義 | 法務部門 / DPO |
| C-05 | コンプライアンス | AI プロファイリングのオプトアウト方針 | 法務部門 |
| C-06 | ビジネス | 受入基準・ユーザーストーリーの策定 | プロダクトオーナー |
| E-06 | アーキテクチャ | auth ↔ user-management データ重複の解消方針 | チーフアーキテクト |
| E-07 | インフラ | DR/BCP 方針・RPO/RTO 目標の策定 | CTO |
| E-10 | ビジネス | ROI / ビジネスケースの定義 | 経営層 |
| E-11 | ビジネス | MVP 定義・スコープの見直し | プロダクトオーナー |
| E-13 | テスト | UAT 計画策定 | プロダクトオーナー |
| — | インフラ | CI/CD パイプライン構築 (GitHub Actions) | インフラチーム |
| — | インフラ | Dockerfile / docker-compose.yml 作成 | インフラチーム |
| — | セキュリティ | ペネトレーションテスト / OWASP ZAP 実施 | セキュリティチーム |
| — | セキュリティ | SAST/DAST ツール選定・CI 統合 | セキュリティチーム |
| — | アーキテクチャ | AtomicLong によるオーダー番号生成の改善（複数インスタンス環境では DB シーケンスまたは分散 ID 生成が必要。設計判断を伴う） | チーフアーキテクト |
| — | インフラ | logback-spring.xml による構造化ログ設定（ログ形式・出力先はインフラ方針に依存） | インフラチーム |

---

## 修正計画（全 10 ステップ）

### Step 1: ビルド基盤 — JaCoCo 導入

**対応レポート指摘**: T-01 (qa-manager), audit-reviewer Critical
**目的**: テストカバレッジの計測基盤を整備し、以降のテスト追加の効果を定量的に確認可能にする

| # | 対象ファイル | 修正内容 |
|---|-------------|---------|
| 1-1 | `pom.xml` (親) | `<plugins>` に `jacoco-maven-plugin` を追加。`prepare-agent` + `report` ゴールを設定 |
| 1-2 | `pom.xml` (親) | `<rules>` で分岐カバレッジ最小値 0.80 を設定（将来の CI ゲート用） |

**検証**: `mvn verify` でビルド成功 + `target/site/jacoco/index.html` が各モジュールに生成されること

---

### Step 2: セキュリティ設定 — SecurityConfig 是正

**対応レポート指摘**: S-01, S-02 (security-reviewer, architect), Gateway permitAll
**目的**: 問題のある 4 サービスの SecurityConfig を修正し、認証を要求するデフォルト設定に変更する

> **注意**: Gateway は **WebFlux** ベース（`@EnableWebFluxSecurity` / `ServerHttpSecurity` / `SecurityWebFilterChain`）。
> 他の 8 サービスは **Servlet** ベース（`@EnableWebSecurity` / `HttpSecurity` / `SecurityFilterChain`）。
> API の違いに注意すること。

| # | 対象ファイル | 修正内容 |
|---|-------------|---------|
| 2-1 | `api-gateway-service/.../SecurityConfig.java` | **[WebFlux]** `.anyExchange().permitAll()` → 公開エンドポイント (`/actuator/health`, `/actuator/info`, `/api/v1/auth/**`, `/api/products/**`, `/api/v1/recommendations/**`, `/api/v1/search/**`) のみ `permitAll()`、他は `authenticated()` に変更。`ServerHttpSecurity` の API を使用すること |
| 2-2 | `coupon-service/.../SecurityConfig.java` | **[要修正]** `.requestMatchers("/api/**").permitAll()` + `.requestMatchers("/actuator/**").permitAll()` → `/actuator/health`, `/actuator/info`, `/v3/api-docs/**`, `/swagger-ui/**` のみ `permitAll()`、`anyRequest().authenticated()` に変更 |
| 2-3 | `point-service/.../SecurityConfig.java` | **[要修正]** 同上（coupon-service と同一パターン） |
| 2-4 | `ai-support-service/.../SecurityConfig.java` | **[要修正]** 同上（coupon-service と同一パターン） |
| 2-5 | `authentication-service/.../SecurityConfig.java` | **[修正不要]** 既に `/api/v1/auth/register`, `/api/v1/auth/login` のみ `permitAll()` + `anyRequest().authenticated()` 設定済み |
| 2-6 | `user-management-service/.../SecurityConfig.java` | **[修正不要]** 既に `/api/users`, `/api/users/check-email` のみ `permitAll()` + `anyRequest().authenticated()` 設定済み |
| 2-7 | `inventory-management-service/.../SecurityConfig.java` | **[修正不要]** 既に `/api/products/**` 等のカタログ系のみ `permitAll()` + `anyRequest().authenticated()` 設定済み |
| 2-8 | `sales-management-service/.../SecurityConfig.java` | **[修正不要]** 既に `actuator/health,info` + swagger のみ `permitAll()` + `anyRequest().authenticated()` 設定済み |
| 2-9 | `payment-cart-service/.../SecurityConfig.java` | **[修正不要]** 既に `actuator/health,info` + swagger のみ `permitAll()` + `anyRequest().authenticated()` 設定済み |
| 2-10 | `api-gateway-service/.../CorsConfig.java` | **[Reactive CorsWebFilter]** `@Value("${app.cors.allowed-origins:http://localhost:3000}") List<String> allowedOrigins` フィールドを追加し、`allowedOriginPatterns(List.of("*"))` → `allowedOriginPatterns(allowedOrigins)` に変更（Java コード内の `List.of("${...}")` では Spring プロパティプレースホルダは解決されないため `@Value` 注入が必要） |
| 2-11 | `api-gateway-service/.../application.properties` | `app.cors.allowed-origins=http://localhost:3000` プロパティを追加 |
| 2-12 | `coupon-service`, `point-service`, `ai-support-service` の `application.properties` | `management.endpoints.web.exposure.include=health,info,metrics` → `health,info,prometheus` に変更（auth, user-mgmt, inventory, sales, payment-cart は既に `health,info,prometheus` 設定済み。Gateway は `health,info,metrics,gateway` でありゲートウェイ運用上適切なため変更不要） |
| 2-13 | `authentication-service/.../application-dev.properties` | `management.endpoints.web.exposure.include=*` → `health,info,prometheus,env` に制限 |

**検証**: `mvn test` 成功（テストはモック主体のため SecurityConfig 変更の影響は限定的）

---

### Step 3: セキュリティ設定 — 秘密情報の外部化・DB パスワードデフォルト値削除

**対応レポート指摘**: S-08 (security-reviewer), High
**目的**: ハードコードされたパスワードのデフォルト値を削除し、環境変数必須にする

| # | 対象ファイル | 修正内容 |
|---|-------------|---------|
| 3-1 | `coupon-service/.../application.properties` | `${DB_PASSWORD:postgres}` → `${DB_PASSWORD}` (デフォルト値削除) |
| 3-2 | `point-service/.../application.properties` | 同上 |
| 3-3 | `coupon-service/.../application.properties` | `spring.datasource.url=jdbc:postgresql://localhost:5432/coupon_db` → `spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/coupon_db}` (他サービスと同様に環境変数化) |
| 3-4 | `point-service/.../application.properties` | `spring.datasource.url=jdbc:postgresql://localhost:5432/point_db` → `spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/point_db}` (同上) |
| 3-5 | `authentication-service/.../application-dev.properties` | `${DB_PASSWORD:localdevpassword}` → `${DB_PASSWORD}` (dev プロファイルでもデフォルトパスワードを削除) |
| 3-6 | 上記以外のサービスの `application.properties` | DB_PASSWORD デフォルト値の有無を確認（auth, user-mgmt, sales, payment-cart は既に `${DB_PASSWORD}` でデフォルト値なし。修正不要） |

> **注**: テストは全て Mockito ベースのユニットテストであり、DB 接続を必要としないため、デフォルト値削除はテスト実行に影響しない。

**検証**: `mvn test` 成功

---

### Step 4: データベーススキーマ修正

**対応レポート指摘**: D-01, D-02 (dba-reviewer), ux-accessibility-reviewer (TIMESTAMP)
**目的**: 金銭精度の問題、楽観的ロック欠如、タイムゾーンなし TIMESTAMP を是正する

> **注意**: point-service と coupon-service の V1 マイグレーションでは `TIMESTAMP` (タイムゾーンなし) を使用。
> 他サービス (auth, user-mgmt, sales, payment-cart) は既に `TIMESTAMP WITH TIME ZONE` を使用しており修正不要。

| # | 対象ファイル | 修正内容 |
|---|-------------|---------|
| 4-1 | `point-service/.../V1__create_point_tables.sql` | `point_multiplier DOUBLE PRECISION` → `point_multiplier NUMERIC(5,2)` |
| 4-2 | `point-service/.../V1__create_point_tables.sql` | `TIMESTAMP` → `TIMESTAMP WITH TIME ZONE` (全カラム) |
| 4-3 | ~~`point-service/.../model/TierDefinition.java`~~ | **修正不要**: 既に `BigDecimal` 型（`@Column(precision = 3, scale = 2)`）で正しくマッピング済み |
| 4-4 | `point-service/.../model/PointTransaction.java` | `@Version` アノテーション付き `version` カラムを追加 |
| 4-5 | `coupon-service/.../V1__create_coupon_tables.sql` | `TIMESTAMP` → `TIMESTAMP WITH TIME ZONE` (全カラム) |
| 4-6 | `coupon-service/.../model/Coupon.java` | `@Version` アノテーション付き `version` カラムを追加 |
| 4-7 | `coupon-service/.../model/UserCoupon.java` | `@Version` アノテーション付き `version` カラムを追加（redeemCoupon の Lost Update 防止） |
| 4-8 | `sales-management-service/.../model/Order.java` | `@Version` アノテーション追加（注文ステータス更新の並行性制御） |
| 4-9 | `payment-cart-service/.../model/Payment.java` | `@Version` アノテーション追加 |
| 4-10 | SQL マイグレーション | 各サービスに `V2__add_version_columns.sql` を新規作成し、`version` カラムを ALTER TABLE で追加 |

**検証**: `mvn test` 成功 + マイグレーション適用確認

---

### Step 5: コード品質修正 — ResourceNotFoundException・エラーコード統一・PII マスキング

**対応レポート指摘**: tech-lead High (コンストラクタ誤用, エラーコード不統一, PII ログ)
**目的**: 例外の誤用修正、エラーコード形式の統一、ログからの PII 除去

> **実態調査結果**:
> - **正しい使用** (resourceType にリソース名を渡している): auth-service (`"User"`), user-mgmt (`"User"`), inventory (`"Product"`), sales (`"Order"`, `"Shipment"`), payment-cart (`"CartItem"`, `"Payment"`)
> - **誤った使用** (resourceType にエラーコードを渡している): coupon-service (`"CPN-4041"`, `"CPN-4042"`), campaign-service (`"CMP-4041"`), ai-support/ChatService (`"CHAT-4041"`), point-service (`"PNT-4041"`, `"PNT-4043"`)

| # | 対象ファイル | 修正内容 |
|---|-------------|---------|
| 5-1 | `coupon-service/.../CouponService.java` | `new ResourceNotFoundException("CPN-4041", ...)` → `new ResourceNotFoundException("Coupon", couponId)` 等に修正（3 箇所） |
| 5-2 | `coupon-service/.../CampaignService.java` | `new ResourceNotFoundException("CMP-4041", ...)` → `new ResourceNotFoundException("Campaign", campaignId)` 等に修正（2 箇所）|
| 5-3 | `ai-support-service/.../ChatService.java` | `new ResourceNotFoundException("CHAT-4041", ...)` → `new ResourceNotFoundException("ChatSession", sessionId)` 等に修正（3 箇所）|
| 5-4 | `point-service/.../PointService.java` | `new ResourceNotFoundException("PNT-4041", ...)` / `("PNT-4043", ...)` → `new ResourceNotFoundException("UserTier", userId)` / `("PointTransaction", ...)` 等に修正（計 6 箇所）|
| 5-5 | `authentication-service/.../AuthenticationService.java` | `log.info("Processing registration for email: {}", request.email())` → メールアドレスをマスキング (`maskEmail()` ヘルパーを追加)。**login 時の PII ログ（L72）も同様に修正**（計 2 箇所） |
| 5-6 | `user-management-service/.../UserService.java` | `log.info("Creating user with email: {}", request.email())` → マスキング（1 箇所） |
| 5-7 | 他サービスの全ログ出力箇所 | PII (email, 氏名等) が出力されている箇所を grep で検出し修正（現時点で上記以外の PII ログは未検出） |

**検証**: `mvn test` 成功 + `grep -r "email\|メール" --include="*.java" */service/` で PII 直接出力が残っていないことを確認

---

### Step 6: API パス統一・Pageable 制限

**対応レポート指摘**: ux-accessibility-reviewer High (API パス不統一), performance-reviewer High (Pageable 無制限)
**目的**: 全サービスの API パスを `/api/v1/` に統一し、ページネーションにサイズ上限を設定する

> **実態調査結果**:
> - **既に `/api/v1/` パターン**: auth (`/api/v1/auth`), sales (`/api/v1`), payment-cart (`/api/v1/payments`, `/api/v1/cart`), coupon (`/api/v1/coupons`, `/api/v1/campaigns`), ai-support (`/api/v1/chat`, `/api/v1/recommendations`, `/api/v1/search`) → 修正不要
> - **`/api/` パターン（v1 なし）**: user-mgmt (`/api/users`, `/api/admin/users`), inventory (`/api/products`, `/api/inventory`, `/api/prices`), point (`/api/points`, `/api/tiers`) → 要修正
> - **Gateway ルート不整合**: RouteConfig で auth を `/api/auth/**` でルーティングしているが、auth controller は `/api/v1/auth/**`。現状でパスが合わないバグあり。

| # | 対象ファイル | 修正内容 |
|---|-------------|---------|
| 6-1 | `user-management-service/.../UserController.java` | `@RequestMapping("/api/users")` → `@RequestMapping("/api/v1/users")` |
| 6-2 | `user-management-service/.../AdminUserController.java` | `@RequestMapping("/api/admin/users")` → `@RequestMapping("/api/v1/admin/users")` |
| 6-3 | `inventory-management-service/.../ProductController.java` | `@RequestMapping("/api")` → `@RequestMapping("/api/v1")`（エンドポイント: `/api/v1/products`, `/api/v1/inventory`, `/api/v1/prices`） |
| 6-4 | `point-service/.../PointController.java` | `@RequestMapping("/api")` → `@RequestMapping("/api/v1")`（エンドポイント: `/api/v1/points`, `/api/v1/tiers`） |
| 6-5 | `api-gateway-service/.../RouteConfig.java` | 全ルートのパスを更新: `/api/auth/**` → `/api/v1/auth/**`, `/api/users/**` → `/api/v1/users/**`, `/api/admin/users/**` → `/api/v1/admin/users/**`, `/api/products/**` → `/api/v1/products/**`, `/api/inventory/**` → `/api/v1/inventory/**`, `/api/prices/**` → `/api/v1/prices/**`, `/api/points/**` → `/api/v1/points/**`, `/api/tiers/**` → `/api/v1/tiers/**` |
| 6-6 | `api-gateway-service/.../SecurityConfig.java` | パスマッチャーも `/api/v1/` プレフィックスに合わせて更新（`/api/products/**` → `/api/v1/products/**` 等） |
| 6-7 | 各サービスの SecurityConfig | パス統一に伴い、`requestMatchers` のパスを `/api/v1/` プレフィックスに更新（user-mgmt, inventory, point） |
| 6-8 | 全サービスの `application.properties` | `spring.data.web.pageable.max-page-size=100` を追加 |
| 6-9 | 全コントローラーの Pageable パラメータ | `@PageableDefault(size = 20)` を追加 |

**検証**: `mvn test` 成功

---

### Step 7: パフォーマンス修正 — N+1 解消・インデックス追加・HikariCP 設定

**対応レポート指摘**: P-01 (N+1), performance-reviewer High (HikariCP, MongoDB index), dba-reviewer High (複合インデックス)
**目的**: 主要な N+1 クエリを解消し、DB 接続プール・インデックスを最適化する

| # | 対象ファイル | 修正内容 |
|---|-------------|---------|
| 7-1 | `sales-management-service/.../OrderRepository.java` | `@EntityGraph(attributePaths = "items")` 付きの `findByCustomerId` メソッドを追加 |
| 7-2 | `sales-management-service/.../OrderService.java` | `getCustomerOrders()` で上記リポジトリメソッドを使用するよう変更 |
| 7-3 | `payment-cart-service/.../CartRepository.java` | `@EntityGraph(attributePaths = "items")` 付きの `findByUserId` メソッドを追加 |
| 7-4 | `payment-cart-service/.../CartService.java` | `getOrCreateCart()` で上記メソッドを使用 |
| 7-5 | `sales-management-service/.../V2__*.sql` (新規) | `CREATE INDEX idx_orders_customer_created ON orders(customer_id, created_at DESC)` |
| 7-6 | `ai-support-service/.../model/ChatSession.java` | `userId` フィールドに `@Indexed` アノテーションを追加 |
| 7-7 | ~~`ai-support-service/.../application.properties`~~ | **修正不要**: 既に `spring.data.mongodb.auto-index-creation=true` が設定済み |
| 7-8 | 全 PostgreSQL サービスの `application.properties` | HikariCP 設定を追加: `spring.datasource.hikari.maximum-pool-size=20`, `connection-timeout=5000`, `idle-timeout=300000`, `max-lifetime=600000` |

**検証**: `mvn test` 成功

---

### Step 8: AI サービス耐性強化 — タイムアウト・メッセージ上限

**対応レポート指摘**: P-02, P-03 (performance-reviewer Critical)
**目的**: OpenAI API 呼び出しにタイムアウトを設定し、チャットセッションのメッセージ無制限成長を防止する

> **注意**: Spring AI 1.0 のタイムアウト設定プロパティ名は `spring.ai.openai.chat.options.timeout` ではなく、
> `spring.ai.retry.on-client-errors` / RestClient レベルの設定となる可能性がある。
> 実装時に Spring AI 1.0 のドキュメントを確認し、正しいプロパティ名を使用すること。

| # | 対象ファイル | 修正内容 |
|---|-------------|---------|
| 8-1 | `ai-support-service/.../application.properties` | OpenAI API タイムアウト設定を追加（Spring AI 1.0 の正しいプロパティを確認の上設定。目標: 接続 5 秒、読み取り 30 秒） |
| 8-2 | `ai-support-service/.../application.properties` | `spring.ai.openai.chat.options.max-tokens=1024` を追加 |
| 8-3 | `ai-support-service/.../ChatService.java` | `buildPromptMessages()` にメッセージ数の上限を追加（直近 20 件のみ使用。`session.getMessages()` の全件を `ArrayList` に追加している現状コードを、末尾 20 件に制限） |
| 8-4 | `ai-support-service/.../application.properties` | `spring.data.web.pageable.max-page-size=100` を追加（Step 6-8 で未対応の場合） |

**検証**: `mvn test` 成功

---

### Step 9: テスト拡充 — 未テストサービスのテスト新規作成

**対応レポート指摘**: T-02〜T-05 (qa-manager Critical), audit-reviewer
**目的**: テスト 0 件の主要サービスにユニットテストを追加する

| # | 対象ファイル (新規作成) | テスト内容 |
|---|----------------------|----------|
| 9-1 | `payment-cart-service/.../service/CartServiceTest.java` | `getCart()`, `addItem()`, `updateItem()`, `removeItem()`, `clearCart()` の正常系 + 異常系 |
| 9-2 | `coupon-service/.../service/CampaignServiceTest.java` | `createCampaign()`, `getCampaign()`, `activateCampaign()`, `getCampaigns()`, `getActiveCampaigns()` の正常系 + 異常系 |
| 9-3 | `authentication-service/.../service/JwtTokenServiceTest.java` | `generateAccessToken()`, `generateRefreshToken()`, `validateToken()` (有効/期限切れ/改ざん/null), `extractUserId()` |
| 9-4 | `ai-support-service/.../service/RecommendationServiceTest.java` | `getPersonalizedRecommendations()`, `getRecommendationHistory()`, `recordClick()` |
| 9-5 | `ai-support-service/.../service/SearchServiceTest.java` | `semanticSearch()` の正常系 + 空クエリ |

**テスト規約**:

- 命名: `should_expectedResult_when_condition`
- `@DisplayName` で日本語のテスト説明を付与
- AAA パターン (`// Arrange`, `// Act`, `// Assert`) コメントを記述
- 各メソッドに正常系 + 主要異常系の両方を作成

**検証**: `mvn test` 成功 + テスト数が大幅に増加すること

---

### Step 10: 既存テスト改善 — 命名規約統一・@DisplayName 追加・追加テストケース

**対応レポート指摘**: qa-manager High (命名規約, @DisplayName, 境界値)
**目的**: 既存テストの命名規約・品質を統一し、不足しているテストケースを追加する

> **実態調査結果**:
> - **既に `should_xxx_when_yyy` 命名**: auth-service (`AuthenticationServiceTest`)
> - **`action_condition_result` 命名（要修正）**: point-service (`PointServiceTest`), coupon-service (`CouponServiceTest`), ai-support (`ChatServiceTest`)

| # | 対象ファイル | 修正内容 |
|---|-------------|---------|
| 10-1 | `point-service/.../PointServiceTest.java` | メソッド名を `should_xxx_when_yyy` に統一（例: `awardPoints_existingUser_addsPointsWithMultiplier` → `should_addPointsWithMultiplier_when_existingUser`）。全テストに `@DisplayName` 追加 |
| 10-2 | `coupon-service/.../CouponServiceTest.java` | 同上（例: `createCoupon_validRequest_succeeds` → `should_succeed_when_validCouponRequest`） |
| 10-3 | `ai-support-service/.../ChatServiceTest.java` | 同上（例: `createSession_validRequest_createsSession` → `should_createSession_when_validRequest`） |
| 10-4 | `authentication-service/.../AuthenticationServiceTest.java` | `findById()` のテスト追加（正常系 + 未存在） |
| 10-5 | `user-management-service/.../UserServiceTest.java` | `updateUser()`, `updateUserStatus()`, `listUsers()` のテスト追加 |
| 10-6 | `inventory-management-service/.../ProductServiceTest.java` | `getProductBySku()`, `listProducts()`, `stockIn()`, `updatePrice()` のテスト追加 |
| 10-7 | `sales-management-service/.../OrderServiceTest.java` | `getCustomerOrders()`, `createShipment()`, `updateShipmentStatus()` のテスト追加 |
| 10-8 | `payment-cart-service/.../PaymentServiceTest.java` | `getPaymentHistory()` のテスト追加 |
| 10-9 | 全テスト | 既存テストに AAA コメント (`// Arrange`, `// Act`, `// Assert`) を追加 |

**検証**: `mvn test` 成功 + `mvn verify` でカバレッジレポート確認

---

## 実行順序と依存関係

```text
Step 1 (JaCoCo)
  │
  ├──→ Step 2 (SecurityConfig) ──→ Step 3 (秘密情報)
  │                                    │
  │                                    └──→ Step 6 (API パス統一)
  │
  ├──→ Step 4 (DB スキーマ) ──→ Step 7 (N+1・インデックス・HikariCP)
  │
  ├──→ Step 5 (コード品質)
  │
  ├──→ Step 8 (AI 耐性)
  │
  └──→ Step 9 (テスト新規作成) ──→ Step 10 (テスト改善)
```

- **Step 1** は全ステップの前提（カバレッジ計測基盤）
- **Step 2 → 3 → 6** はセキュリティ → 秘密情報 → パス統一の順序依存（Gateway SecurityConfig のパスマッチャーを Step 2 で変更後、Step 6 でパス統一）
- **Step 4 → 7** は DB スキーマ変更後に N+1 解消
- **Step 5, 8** は独立して並行実行可能
- **Step 9 → 10** はテスト新規作成後に既存テスト改善

---

## 各ステップ完了後の検証チェックリスト

各ステップ完了時に以下を実施する:

- [ ] `mvn clean test` → BUILD SUCCESS
- [ ] `mvn verify` → JaCoCo レポート生成（Step 1 以降）
- [ ] 新規/変更ファイルのコンパイルエラーなし
- [ ] 既存テストの回帰なし（テスト数が減らないこと）

---

## 期待される最終状態

| 指標 | 修正前 | 修正後 (目標) |
|------|--------|-------------|
| テスト数 | 45 | 120+ |
| JaCoCo | 未設定 | 有効 (分岐カバレッジ計測可能) |
| セキュリティ: permitAll サービス | 4 (gateway + coupon + point + ai-support) | 0 |
| @PreAuthorize | 0 箇所 | — (Step 2 で authenticated() を設定。メソッドレベル認可は人間判断後) |
| N+1 クエリ | 3 箇所 | 0 |
| PII ログ出力 | 3 箇所 (auth×2, user-mgmt×1) | 0 |
| ResourceNotFoundException 誤用 | 14 箇所 (coupon×5, point×6, ai×3) | 0 |
| @Version エンティティ | 0 | 4+ |
| API パス統一 | 混在 (/api/v1, /api, /api/users) | 全て /api/v1/ |
| Gateway ルート不整合 | `/api/auth/**` ≠ `/api/v1/auth/**` | 解消 |
| HikariCP 設定 | デフォルト | 全 PostgreSQL サービスに明示設定 |
| DB 型 (point_multiplier) | DOUBLE PRECISION | NUMERIC(5,2) |
| TIMESTAMP WITH TIME ZONE 未適用 | 2 サービス (point, coupon) | 0 |
| ChatSession メッセージ | 無制限 | 直近 20 件制限 |
| Pageable 上限 | なし | max-page-size=100 |
| DB_PASSWORD デフォルト値 | 3 箇所 (coupon, point, dev-profile) | 0 |
| DB URL 環境変数化 | 2 サービス未対応 (coupon, point) | 全サービス対応 |
