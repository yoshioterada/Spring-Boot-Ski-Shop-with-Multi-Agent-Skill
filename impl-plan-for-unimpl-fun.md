# 未完成機能の洗い出しと実装計画

作成日: 2026-05-27

## 目的

この文書は、現在の Ski Shop E-Commerce Platform に存在する「形はあるが本番品質としては未完成」「サンプル値・暫定値・スタブで動いている」「外部連携や業務保証が不足している」箇所を洗い出し、本来どのように実装すべきかを具体化するための実装計画である。

対象は主に以下の領域とする。

- 決済 Gateway / Webhook
- AI 推薦・検索
- Orchestrator 向け User Profile 統合
- AI Analyzer の実データ連携
- Model Management
- Kafka / Outbox / イベント配送保証
- Agent Cart / Inventory / Coupon 連携の業務整合性
- フロントエンド上の小さな暫定実装

## 現状サマリ

プロジェクト全体は、Spring Boot マイクロサービス、Next.js BFF/フロントエンド、Spring AI ベースの AI Support / Multi-Agent System、Kafka イベント連携、PostgreSQL / MongoDB、Docker / Kubernetes / Azure 配備定義まで広く実装されている。

一方で、以下のような箇所は本番機能としては未完成である。

| 領域 | 現状 | 本来あるべき姿 |
| --- | --- | --- |
| 決済 | `simulated` 決済、Webhook は `return null` | PSP 連携、署名検証、冪等 Webhook、返金同期、注文状態連動 |
| AI 推薦 | LLM 呼び出し後に固定商品 ID を返す箇所あり | 行動履歴・購買履歴・商品 DB を使ったランキング |
| AI 検索 | クエリ拡張は AI、検索結果は固定値 | inventory の商品検索・検索ログ・ゼロヒットを統合 |
| User Profile | Orchestrator 用 profile が tier / point / purchase history を暫定値で返す | point / sales / coupon / preference を統合した購買文脈 |
| AI Analyzer Tool | 一部 Tool が空リストや 0 を返す | sales / inventory / search_logs / coupon から実データ取得 |
| AI Analytics API | product performance / trends / dashboard などが 0・空配列中心 | 実イベント、検索、推薦、注文データから集計 |
| Model Management | training は RUNNING 作成のみ、performance は空 Map | 学習ジョブ実行、状態遷移、モデル成果物、評価メトリクス |
| イベント配送 | Kafka 送信は best-effort、Outbox は選択式 | 業務イベントは Outbox + relay + retry + DLQ |
| Agent Cart | カート永続化は best-effort | 推薦、価格、在庫予約、カート内容を原子的に整合 |
| Frontend | 一部 TODO / BFF fallback | API 連携と表示整合性の仕上げ |

## 再確認結果メモ

2026-05-27 に本計画を再確認した結果、以下を補足する。

- inventory-management-service には `/api/v1/products/search` と `SearchAnalyticsService.logSearchAsync` があり、検索ログとゼロヒット集約の土台は既に存在する。ただし検索条件は現状 name / brand の部分一致が中心で、ai-support-service の `SearchService` は固定検索結果を返すため、AI 検索全体としては未完成である。
- sales-management-service の `SalesAnalyticsController` は summary / trends / sku-velocity / monthly-revenue / seasonal-weights / monthly-sales / yoy-growth を既に提供している。したがって AI Analyzer 側は「sales に API がない」ではなく「既存 API を ToolFunctions が十分に利用できていない」ことが主問題である。
- coupon-service には `dead_stock_actions` migration はあるが、`DeadStockAction` entity / repository / controller は見当たらない。DeadStockService が呼ぶ `/api/v1/dead-stock-actions` 系 API は計画どおり不足と判断する。
- ai-support-service の `AnalyticsService` には、product performance / trends / customer segments / dashboard などで 0 や空配列を返す暫定実装が残っている。初版計画ではこの観点が薄かったため、独立項目として追記する。
- payment-cart-service には PSP client / webhook event / refund table に相当する実装は見当たらず、決済 Gateway / Webhook の未完成判定は妥当である。

## 優先度

### P0: 業務フローの正しさに直結するもの

- 決済 Gateway / Webhook の本実装
- 注文、決済、在庫、ポイント、クーポンの状態遷移連携
- Kafka / Outbox による業務イベント配送保証
- Orchestrator が使う User Profile の実データ化

### P1: AI 体験の品質に直結するもの

- AI 推薦を実商品・実履歴に接続
- AI 検索を inventory / search_logs に接続
- AI Analyzer Tool の空実装を実データに接続
- Agent Cart / Inventory reservation の整合性強化

### P2: 運用・分析・改善サイクル

- Model Management の学習ジョブ化
- LLM コスト、Tool 成功率、業務 KPI の observability
- 管理画面でのエラー・部分成功・再試行 UI

### P3: UI 仕上げ・細部

- カテゴリ名 TODO など小さな暫定表示
- i18n 未完了箇所
- BFF fallback の標準化

## 1. 決済 Gateway / Webhook

### 該当箇所

- `payment-cart-service/src/main/java/com/example/skishop/payment/service/PaymentService.java`
- `payment-cart-service/src/main/java/com/example/skishop/payment/model/Payment.java`
- `payment-cart-service/src/main/resources/db/migration/V1__create_payment_tables.sql`
- `payment-cart-service/src/main/java/com/example/skishop/payment/controller/PaymentController.java`
- `frontend/src/app/api/payments/intent/route.ts`
- `frontend/src/app/api/payments/[id]/process/route.ts`
- `frontend/src/app/api/admin/payments/[id]/refund/route.ts`

### 現状

`PaymentService.processPayment` は以下のように擬似成功として処理している。

- status を `CAPTURED` にする
- `gatewayProvider` に `simulated` を設定
- `gatewayResponse` に固定 JSON を保存
- `PaymentProcessed` イベントを発行

`handleWebhook` はコメント上も stub で、署名検証、payload 解析、状態反映が未実装である。

### 問題

- 外部 PSP の決済状態と DB 状態が一致しない。
- Webhook の重複配送、順序逆転、遅延配送に対応できない。
- 支払い成功後の注文確定、在庫引当確定、ポイント付与、クーポン消込が保証されない。
- 返金の部分返金、二重返金、返金失敗が表現できない。
- PSP の event id による冪等性がない。
- 管理画面から見る支払い状態が外部決済事実と乖離する。

### 本来の実装方針

PSP 抽象化レイヤーを導入し、まずは Stripe 互換の構造を想定する。将来 PayPay / GMO / Adyen などへ差し替えられるように、ドメイン層は PSP 固有型に依存しない。

#### 追加する interface

```java
public interface PaymentGatewayClient {
    GatewayPaymentIntent createIntent(CreateGatewayIntentCommand command);
    GatewayPaymentResult confirmPayment(ConfirmGatewayPaymentCommand command);
    GatewayRefundResult refund(RefundGatewayCommand command);
    VerifiedWebhookEvent verifyAndParseWebhook(String payload, String signature);
}
```

#### 実装クラス

- `SimulatedPaymentGatewayClient`: ローカル開発・テスト用
- `StripePaymentGatewayClient`: 本番用の候補
- `PaymentGatewayProperties`: provider, secret, webhookSecret, timeout, retry を設定

#### DB 追加案

`payments` に以下を追加する。

- `gateway_provider`
- `gateway_payment_id`
- `gateway_customer_id`
- `idempotency_key`
- `authorized_at`
- `captured_at`
- `failed_at`
- `failure_code`
- `failure_reason`
- `raw_gateway_status`

Webhook 用に `payment_webhook_events` を追加する。

- `id`
- `provider`
- `event_id`
- `event_type`
- `payment_id`
- `payload_hash`
- `received_at`
- `processed_at`
- `status` (`RECEIVED`, `PROCESSED`, `DUPLICATE`, `FAILED`, `IGNORED`)
- `error_message`

返金を正規化するため `payment_refunds` を追加する。

- `id`
- `payment_id`
- `gateway_refund_id`
- `amount`
- `status` (`REQUESTED`, `SUCCEEDED`, `FAILED`, `CANCELLED`)
- `reason`
- `requested_by`
- `created_at`
- `completed_at`

#### 状態遷移

Payment status は最低限以下に拡張する。

- `PENDING`
- `REQUIRES_ACTION`
- `AUTHORIZED`
- `CAPTURED`
- `FAILED`
- `CANCELLED`
- `PARTIALLY_REFUNDED`
- `REFUNDED`

状態遷移はサービス内で明示的に検証する。

| 現在 | 許可する遷移 |
| --- | --- |
| PENDING | REQUIRES_ACTION, AUTHORIZED, CAPTURED, FAILED, CANCELLED |
| REQUIRES_ACTION | AUTHORIZED, CAPTURED, FAILED, CANCELLED |
| AUTHORIZED | CAPTURED, CANCELLED, FAILED |
| CAPTURED | PARTIALLY_REFUNDED, REFUNDED |
| PARTIALLY_REFUNDED | PARTIALLY_REFUNDED, REFUNDED |
| FAILED / CANCELLED / REFUNDED | 原則終端 |

### Webhook 実装詳細

Webhook 処理は以下の順序にする。

1. raw payload と signature を受け取る。
2. `PaymentGatewayClient.verifyAndParseWebhook` で署名検証する。
3. PSP event id を `payment_webhook_events` に insert する。
4. unique constraint により重複 event を検出する。
5. event type ごとに Payment / Refund を更新する。
6. 状態更新後に domain event を Outbox に記録する。
7. 処理完了後 `payment_webhook_events.status=PROCESSED` にする。

重複 event は 200 OK を返し、再配送を止める。署名エラーは 400、処理一時失敗は 5xx で PSP に retry させる。

### 注文・在庫・ポイント・クーポン連携

決済成功後は `PaymentCaptured` を発行し、以下を後続処理する。

- sales-management-service: order paymentStatus を `PAID` に更新
- inventory-management-service: reserved stock を sold stock として確定、または stockOut
- point-service: 購入金額に応じてポイント付与
- coupon-service: クーポン利用を確定
- mailsend-service: 注文確定メール送信

決済失敗またはキャンセル時は以下を行う。

- order paymentStatus を `FAILED` または `CANCELLED`
- inventory reservation release
- coupon reservation / redemption rollback

### 実装ステップ

1. `PaymentGatewayClient` と DTO を追加する。
2. `SimulatedPaymentGatewayClient` を既存ロジックから切り出す。
3. `PaymentService` を gateway 経由に変更する。
4. Webhook event table migration を追加する。
5. Webhook 署名検証と冪等 insert を実装する。
6. `PaymentCaptured`, `PaymentFailed`, `RefundProcessed` を Outbox 経由で発行する。
7. sales / inventory / point / coupon の consumer または internal API 連携を実装する。
8. 管理画面で payment event history と refund status を表示する。

### テスト

- Gateway client mock による `createPaymentIntent`
- `processPayment` の成功 / 失敗 / requires_action
- Webhook 署名不正
- Webhook 重複 event
- Webhook 順序逆転
- 部分返金と全額返金
- 二重返金防止
- PaymentCaptured から Order / Inventory / Point / Coupon へ連携する統合テスト

## 2. AI 推薦

### 該当箇所

- `ai-support-service/src/main/java/com/example/skishop/ai/service/RecommendationService.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/model/Recommendation.java`
- `inventory-management-service/src/main/java/com/example/skishop/inventory/service/ProductService.java`
- `sales-management-service/src/main/java/com/example/skishop/sales/service/OrderService.java`
- `frontend/src/components/ec/personalized-section.tsx`
- `frontend/src/components/ec/trending-section.tsx`

### 現状

`RecommendationService` は ChatClient を呼び出しているが、実際に返す商品は `prod-001`, `prod-002`, `prod-003` や `similar-001`, `trend-001` などの固定値である。

### 問題

- 実在しない productId を返す可能性が高い。
- 在庫切れ、販売停止、価格、カテゴリ、ユーザー購買履歴を考慮できない。
- 推薦クリックや購買結果が次回推薦に反映されない。
- LLM の出力を使っているように見えるが、ランキングには実質使われていない。

### 本来の実装方針

AI 推薦は「候補生成」「特徴量取得」「ランキング」「説明生成」を分ける。

#### 候補生成

最低限、以下の候補ソースを統合する。

- 同カテゴリの人気商品
- ユーザーの過去購入カテゴリに近い商品
- 閲覧履歴に近い商品
- カート内商品の類似商品
- 季節・天候・在庫状況に応じた商品
- 新着商品
- セール中商品

#### 必要なデータ

inventory-management-service:

- productId
- sku
- categoryId
- brand
- price
- salePrice
- tags
- attributes
- stockQuantity
- availableQuantity
- status

sales-management-service:

- user purchase history
- product sales count
- category sales trend
- co-purchase relation

ai-support-service:

- recommendation impression
- click
- feedback
- conversion

#### API 追加案

inventory-management-service:

- `GET /api/v1/internal/products/recommendation-candidates`
- `POST /api/v1/internal/products/batch-summary`

sales-management-service:

- `GET /api/v1/internal/sales/users/{userId}/purchase-profile`
- `GET /api/v1/internal/sales/products/trending`
- `GET /api/v1/internal/sales/products/{productId}/co-purchased`

ai-support-service:

- `POST /api/v1/recommendations/impression`
- `POST /api/v1/recommendations/{recommendationId}/click`
- `POST /api/v1/recommendations/conversion`

### ランキング方式

初期実装は deterministic scoring で十分である。

スコア例:

```text
score =
  categoryAffinity * 0.30
  + purchaseHistoryAffinity * 0.20
  + trendScore * 0.15
  + stockScore * 0.10
  + priceFitScore * 0.10
  + seasonalityScore * 0.10
  + noveltyScore * 0.05
```

LLM はランキングそのものではなく、上位候補の「推薦理由」を短く生成する用途に限定する。これにより、架空 productId や在庫切れ商品推薦を防げる。

### 実装ステップ

1. 実商品候補を取得する `ProductCandidateClient` を ai-support-service に追加。
2. sales-management-service に購買 profile API を追加。
3. RecommendationService から固定 productId を削除。
4. deterministic ranking を実装。
5. LLM は上位 3-10 件に対する説明生成に限定。
6. 推薦 impression / click / conversion を保存。
7. trending / similar / personalized をそれぞれ実データ化。

### テスト

- 在庫切れ商品を推薦しない。
- 存在しない productId を返さない。
- category filter が効く。
- ユーザー履歴がない場合は trending fallback。
- LLM 失敗時も推薦リストは返る。
- click / conversion が保存される。

## 3. AI 検索

### 該当箇所

- `ai-support-service/src/main/java/com/example/skishop/ai/service/SearchService.java`
- `inventory-management-service/src/main/java/com/example/skishop/inventory/controller/SearchAnalyticsController.java`
- `inventory-management-service/src/main/java/com/example/skishop/inventory/service/SearchAnalyticsService.java`
- `docker/initdb-mongo/03_ai_analyzer_search_log_index.js`
- `docker/initdb-mongo/05_seed_search_logs.js`
- `frontend/src/app/(ec)/search/page.tsx`

### 現状

SearchService は AI で query enhancement を行うが、検索結果は固定値である。

一方、inventory-management-service 側には `/api/v1/products/search` があり、`ProductController` から `SearchAnalyticsService.logSearchAsync` を呼んで検索ログを保存している。さらに `SearchAnalyticsService.getZeroHitAggregation` により search_logs からゼロヒット検索クエリを集約できる。

したがって正確には、「検索ログ基盤が未実装」ではなく、「ai-support-service の AI 検索が inventory の実検索・実ログに接続されていない」「inventory の検索条件が name / brand 中心で、semantic / faceted search としては不足している」状態である。

### 問題

- ユーザー検索結果が商品 DB と一致しない。
- ai-support-service 経由の検索が inventory の search_logs に残らない可能性がある。
- 検索改善の feedback loop が成立しない。
- autocomplete が単純な suffix 生成で、実商品・検索履歴に基づかない。
- inventory の検索条件が name / brand に寄っており、SKU、タグ、属性、価格、在庫、カテゴリを横断した検索としては弱い。

### 本来の実装方針

検索の実行主体は inventory-management-service に寄せ、ai-support-service は query understanding / semantic expansion / ranking explanation を担う。

#### Search pipeline

1. 入力 query を正規化する。
2. ai-support-service で query expansion / intent classification を行う。
3. inventory-management-service へ検索する。
4. inventory が以下を実行する。
   - name / brand / sku text search
   - category filter
   - tag / attributes filter
   - price range
   - stock status
   - sort
5. 検索結果と検索ログを保存する。
6. 0 件なら zero-hit log を保存する。
7. ai-support-service が必要に応じて検索結果の説明・関連クエリを付与する。

#### MongoDB index

products:

- text index: `name`, `description`, `brand`, `tags`
- compound index: `categoryId`, `status`, `regularPrice`
- `attributes` は必要に応じて key ごとの index

search_logs:

- `normalizedQuery`
- `resultCount`
- `category`
- `userIdHash`
- `createdAt`
- `clickedProductId`
- `converted`

### API 追加 / 修正案

inventory-management-service:

- `GET /api/v1/products/search`
  - `q`
  - `category`
  - `minPrice`
  - `maxPrice`
  - `tags`
  - `inStock`
  - `sort`
  - `page`
  - `size`
  - `enhancedQuery`
  - `source`
- `GET /api/v1/search/autocomplete`
- `POST /api/v1/search/events/click`
- `POST /api/v1/search/events/conversion`

ai-support-service:

- `POST /api/v1/search/semantic`
  - query understanding を行い、inventory search を呼ぶ。

### 実装ステップ

1. ProductRepository に text search / filter query を追加。
2. SearchAnalyticsService に検索ログ保存 API を統合。
3. SearchService の固定結果を削除し、inventory WebClient を呼ぶ。
4. autocomplete を search_logs + product names から生成。
5. zero-hit aggregation が実検索ログを使うように統一。
6. フロント検索画面の result schema を実 API に合わせる。
7. ai-support-service 経由の semantic search でも inventory 側に search log が残るよう、source と enhancedQuery を渡す。

### テスト

- 商品名、ブランド、SKU、タグ検索。
- category / price / stock filter。
- 0 件時に zero-hit log が保存される。
- autocomplete が頻出検索語と商品名から返る。
- LLM query expansion 失敗時は原 query で検索される。

## 4. Orchestrator 向け User Profile

### 該当箇所

- `user-management-service/src/main/java/com/example/skishop/usermanagement/service/UserService.java`
- `user-management-service/src/main/java/com/example/skishop/usermanagement/dto/UserProfileResponse.java`
- `ai-agent-services/orchestrator-agent/src/main/java/com/example/skishop/agent/orchestrator/client/UserManagementClient.java`
- `ai-agent-services/orchestrator-agent/src/main/java/com/example/skishop/agent/orchestrator/service/OrchestratorAgentService.java`
- `point-service/src/main/java/com/example/skishop/point/controller/InternalPointController.java`
- `coupon-service/src/main/java/com/example/skishop/coupon/controller/InternalCouponController.java`
- `sales-management-service/src/main/java/com/example/skishop/sales/controller/InternalSalesController.java`

### 現状

UserService の `getUserProfile` は Orchestrator 向けの拡張 profile として存在するが、以下が暫定値である。

- customerTier: `STANDARD`
- purchasedCategories: empty list
- skillLevel: user preference の `preferred_skill_level` か `INTERMEDIATE`
- pointBalance: `0`

### 問題

- Dynamic Pricing Agent の customerTier が実会員ランクを反映しない。
- Coupon Optimization Agent がポイント残高や利用可能クーポンを正しく使えない。
- Equipment Matching Agent が過去購入カテゴリを使えない。
- 購入支援のパーソナライズ精度が低い。

### 本来の実装方針

User Profile は user-management-service が集約 API を提供する。ただし、他サービス DB を直接参照せず internal API で集約する。

#### 集約する項目

- 基本情報
  - displayName
  - emailVerified
  - status
  - role
- preference
  - skillLevel
  - preferredResorts
  - preferredCategories
  - budgetRange
  - height / weight / bootSize など任意
- point
  - currentBalance
  - currentTier
  - tierName
  - pointMultiplier
- sales
  - purchasedCategories
  - recentPurchasedProductIds
  - totalOrders
  - totalSpent
  - lastPurchaseAt
- coupon
  - availableCouponCount
  - bestAvailableCoupon summary

#### 追加 API

point-service:

- `GET /api/v1/internal/points/{userId}/profile`

sales-management-service:

- `GET /api/v1/internal/sales/users/{userId}/purchase-profile`

coupon-service:

- `GET /api/v1/internal/coupons/users/{userId}/summary`

user-management-service:

- `GET /api/v1/users/{id}/profile` で上記を集約。

### 障害時の方針

Orchestrator の profile 取得は UX に直結するため、部分失敗を許容する。

- point-service 失敗: tier=`STANDARD`, balance=0, `profileWarnings` に記録
- sales-service 失敗: purchase history empty
- coupon-service 失敗: coupon summary unknown

レスポンスには `profileCompleteness` と `warnings` を入れるとよい。

### 実装ステップ

1. `UserProfileResponse` を拡張する。
2. point / sales / coupon に internal summary API を追加。
3. user-management-service に WebClient client を追加。
4. timeout / circuit breaker / fallback を設定。
5. Orchestrator prompt に `profileCompleteness` を含める。
6. Agent 側で unknown / fallback を扱う。

### テスト

- 全サービス成功時に tier / point / purchasedCategories が入る。
- point-service 失敗時も profile が返る。
- sales-service 失敗時も Orchestrator が動く。
- JWT / internal API key の保護確認。

## 5. AI Analyzer Tool 群の実データ接続

### 該当箇所

- `ai-support-service/src/main/java/com/example/skishop/ai/tool/AnalyticsToolFunctions.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/service/AdminAnalyzerService.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/service/SeasonalForecastService.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/service/DeadStockService.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/service/ZeroHitOpportunityService.java`
- `sales-management-service/src/main/java/com/example/skishop/sales/controller/SalesAnalyticsController.java`
- `inventory-management-service/src/main/java/com/example/skishop/inventory/controller/SearchAnalyticsController.java`

### 現状

`AnalyticsToolFunctions` には本来 AI Analyzer が使う Tool があるが、以下は空実装または 0 固定値である。

- `getInventoryLevels`: inventory API を呼ぶが結果を捨てて空リストを返す。
- `getReorderPoints`: 0 固定。
- `getWeeklyComparison`: 0 固定。
- `getDeadStock`: 空リスト。
- `getInventoryVelocity`: 0 固定。
- `getZeroHitOpportunities`: 空リスト。
- `searchProductCatalog`: 空リスト。

一方で `DeadStockService` や `ZeroHitOpportunityService` には独自に実データへ近い実装があるため、ToolFunctions とサービス実装の責務が重複・分散している。

### 問題

- 管理者 AI チャットが、Tool を呼んでも空・0 の情報しか得られない場面がある。
- AI が「数値生成禁止」の制約を守るほど、回答が空になる。
- DeadStock / ZeroHit の専用 API とチャット Tool の結果が一致しない。
- 週次比較や発注点が本番判断に使えない。

### 本来の実装方針

AI Analyzer の Tool は、専用サービスまたは各マイクロサービスの analytics API を呼び、同じ計算ロジックを使うようにする。

#### Tool ごとの接続先

| Tool | 接続先 | 実装方針 |
| --- | --- | --- |
| getDailyRevenue | sales analytics summary | 既存 summary の total / dailyRevenue を正しく DTO に詰める |
| getTopProducts | sales analytics summary / sku velocity | productId, sku, name, qty, revenue |
| getCategoryShare | sales analytics + inventory category | categoryId/name/revenue/share |
| getInventoryLevels | inventory `/api/v1/inventory/all` | stock, available, reserved, reorderPoint |
| getReorderPoints | inventory product + sales velocity | currentStock, reorderPoint, safetyStock |
| getWeeklyComparison | sales summary を今週 / 先週で 2 回呼ぶ、または専用 API | revenue/orders/aov/delta |
| getSeasonalHistorical | sales monthly-revenue | monthly revenue/orders/yoy |
| getDeadStock | DeadStockService | 専用サービスの結果を Tool 型に変換 |
| getInventoryVelocity | sales `/sku-velocity` | sales30/sales90/velocity/DoS |
| getZeroHitOpportunities | ZeroHitOpportunityService | query/searchCount/loss/category |
| searchProductCatalog | inventory search | keyword に一致する実商品 |

#### 既存 API を使う際の注意

- sales-management-service の `/api/v1/admin/orders/analytics/summary` には `dailyRevenue`, `topProducts`, `categoryRevenue` が既に含まれる。ToolFunctions ではこの構造を正しく parse し、空リストに潰さない。
- `/api/v1/admin/orders/analytics/sku-velocity` は `mv_sku_velocity` を返すため、DeadStock / InventoryVelocity はこの API を第一候補にする。
- inventory-management-service の `/api/v1/inventory/all` は `ProductResponse` のリストを返す。ToolFunctions の `getInventoryLevels` は現在 response を捨てているため、sku / stockQuantity / availableQuantity / status に変換する。
- inventory-management-service の `/api/v1/products/analytics/zero-hit-queries` は既にゼロヒット集約を返す。ToolFunctions は ZeroHitOpportunityService と同じ正規化・dismiss 条件を使う。

### 発注点計算

`getReorderPoints` は以下の式から始める。

```text
dailyDemand = max(sales30 / 30, sales90 / 90, defaultDemand)
leadTimeDays = categoryLeadTimeDays(categoryId)
safetyStock = ceil(stddevDemand * serviceLevelFactor * sqrt(leadTimeDays))
reorderPoint = ceil(dailyDemand * leadTimeDays + safetyStock)
recommendedOrderQty = max(0, reorderPoint + targetCoverDays * dailyDemand - currentStock)
```

最初の実装で stddev がない場合は、カテゴリごとの係数で代替する。

### 実装ステップ

1. `AnalyticsToolFunctions` の空実装を一覧化してテストに落とす。
2. sales-management-service の analytics API に不足フィールドを追加。
3. inventory-management-service の inventory all / search / analytics API の schema を固定。
4. DeadStockService / ZeroHitOpportunityService を ToolFunctions から呼ぶ、または共通 adapter を作る。
5. fallback は空・0 のみではなく `dataUnavailable=true` と reason を返せる DTO に変更する。
6. AdminAnalyzerService の prompt に「dataUnavailable の場合はその旨を明記」と追加する。

### テスト

- ToolFunctions が実 WebClient response を DTO に変換する。
- 空データ時は「0」と「取得失敗」を区別する。
- DeadStock 専用 API と Tool の件数が一致する。
- ZeroHit 専用 API と Tool の件数が一致する。
- LLM を使わず Tool 単体で業務判断可能な値を返す。

## 6. Model Management

### 該当箇所

- `ai-support-service/src/main/java/com/example/skishop/ai/service/ModelManagementService.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/controller/ModelManagementController.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/model/ModelTraining.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/model/ModelVersion.java`

### 現状

- `trainModel` は `ModelTraining` を `RUNNING` で保存するだけ。
- 実際の学習ジョブは起動しない。
- `ModelVersion` は自動作成されない。
- `getModelPerformance` は空 Map を返す。

### 問題

- 管理画面上は「学習開始」に見えるが、完了しない。
- モデル version / performance / deploy の意味が薄い。
- 推薦・検索・予測の改善サイクルにつながらない。

### 本来の実装方針

最初から重い ML pipeline を作る必要はない。まずは「オフライン集計ジョブ」として deterministic model artifact を作る。

#### Phase 1: Lightweight batch model

- 推薦モデル:
  - category affinity
  - co-purchase matrix
  - trending score
  - user segment score
- 検索モデル:
  - query synonym dictionary
  - popular query dictionary
  - zero-hit keyword clusters
- 季節予測:
  - existing SeasonalForecaster parameters snapshot

artifact は MongoDB に保存する。

`model_versions` に追加:

- `artifactUri` または `artifactJson`
- `trainingDataRangeStart`
- `trainingDataRangeEnd`
- `metrics`
- `activatedAt`
- `createdBy`

#### 状態遷移

- `QUEUED`
- `RUNNING`
- `SUCCEEDED`
- `FAILED`
- `CANCELLED`

### 実装ステップ

1. `ModelTrainingJobRunner` を作る。
2. `trainModel` は `QUEUED` で保存し、非同期 executor または scheduler が拾う。
3. model type ごとに trainer を分ける。
4. 成功時に `ModelVersion` を作成。
5. `deployModel` は同 modelType の active version を排他更新する。
6. `getModelPerformance` は保存済み metrics を返す。

### テスト

- training lifecycle: QUEUED -> RUNNING -> SUCCEEDED。
- 失敗時 FAILED と error message。
- deploy activeImmediately で旧 version が inactive。
- performance が空 Map ではなく metrics を返す。

## 7. AI Analytics API の暫定値解消

### 該当箇所

- `ai-support-service/src/main/java/com/example/skishop/ai/service/AnalyticsService.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/controller/AnalyticsController.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/model/BehaviorMetrics.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/model/DemandForecast.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/model/UserProfile.java`
- `frontend/src/app/api/admin/analytics/route.ts`
- `frontend/src/app/api/admin/analytics/report/route.ts`

### 現状

`AnalyticsService` は一部で repository の実データを読んでいるが、以下は暫定値である。

- `getProductPerformance`: views / purchases / conversionRate / averageRating が固定 0。
- `getTrends`: 空リスト。
- `getCustomerSegments`: 空リスト。
- `generateCustomReport`: `status=generated` のみ。
- `getDashboard`: totalUsers / totalSearches / totalRecommendations / activeChatSessions が固定 0。

### 問題

- `/api/v1/analytics/**` を利用する管理画面や外部連携が、実態と異なる 0 値を表示する。
- AI Analyzer の dedicated service / ToolFunctions と、AnalyticsService の API で数値が一致しない。
- 「データが本当に 0」なのか「未実装で 0」なのか区別できない。

### 本来の実装方針

AnalyticsService は AI Support 内の MongoDB だけで完結させず、sales / inventory / user / recommendation / chat の各データを集約する。

#### API ごとの実データ接続案

| API | 接続先 | 実装方針 |
| --- | --- | --- |
| product-performance | sales order_items + inventory product + recommendation click | views, purchases, revenue, conversionRate |
| trends | sales trends + search popular keywords + recommendation conversions | category / timeframe 別 trend |
| customer-segments | user profile + purchase history + point tier | tier, spend band, skill preference |
| custom-report | reportType ごとの query plan | report id と集計結果、生成条件を保存 |
| dashboard | chat sessions, recommendations, search logs, sales summary | AI/EC 複合 KPI |

### データ不足時のレスポンス方針

固定 0 を返す代わりに、以下を DTO に含める。

- `dataAvailable`
- `partial`
- `missingSources`
- `generatedAt`

本当に 0 件の場合は `dataAvailable=true` かつ値 0。取得失敗や未接続の場合は `dataAvailable=false` または `partial=true` とする。

### 実装ステップ

1. Analytics DTO に data availability metadata を追加する。
2. sales / inventory / user / ai-support 内 repository への client/adapter を追加する。
3. product performance を最初に実データ化する。
4. dashboard を fixed 0 から実 KPI に置き換える。
5. custom report は reportType ごとの generator に分割する。
6. 管理画面に partial / unavailable 表示を追加する。

### テスト

- 各 upstream が成功した場合に実値が返る。
- upstream 一部失敗時に partial=true になる。
- データ 0 件と取得失敗を区別する。
- dashboard API が固定 0 を返さない。

## 8. Kafka / Outbox / イベント配送保証

### 該当箇所

- `common-lib/src/main/java/com/example/skishop/common/event/EventPublisherAutoConfiguration.java`
- `common-lib/src/main/java/com/example/skishop/common/event/SpringCloudStreamEventPublisher.java`
- `common-lib/src/main/java/com/example/skishop/common/event/OutboxEventPublisher.java`
- 各サービスの `V*_create_event_outbox_table.sql`
- `user-management-service/src/main/java/com/example/skishop/usermanagement/consumer/UserEventConsumer.java`
- `mailsend-service/src/main/java/com/example/skishop/mailsend/consumer/MailEventConsumer.java`

### 現状

Kafka publisher は仮想スレッドで非同期送信する best-effort 実装である。送信失敗は warn ログで、業務トランザクションの rollback や後続 retry にはつながらない。Outbox 実装と migration は存在するが、全サービスで業務イベントに必ず使われる設計にはなっていない。

### 問題

- ユーザー登録成功後にイベント送信失敗すると、メール送信や user-management 同期が欠落する。
- 注文作成、決済成功、ポイント付与などの重要イベントが失われる可能性がある。
- Kafka 障害時の再送・DLQ・運用確認が弱い。
- イベント consumer の冪等性がサービスごとにばらつく。

### 本来の実装方針

業務イベントは Transactional Outbox を標準にする。

#### Publisher 方針

- サービス内トランザクションで domain entity と `event_outbox` を同時 commit。
- 別プロセスまたは scheduler が outbox を Kafka に publish。
- publish 成功後に `published_at`, `status=SENT`。
- 失敗時は retry count / next_retry_at を更新。
- retry 上限超過で `FAILED` にし、DLQ または運用 alert。

#### outbox table 推奨 schema

- `id`
- `event_id`
- `event_type`
- `aggregate_type`
- `aggregate_id`
- `producer`
- `payload`
- `headers`
- `correlation_id`
- `status`
- `retry_count`
- `next_retry_at`
- `created_at`
- `published_at`
- `last_error`

#### consumer 冪等性

各 consumer 側に `processed_events` table を持つ。

- `event_id`
- `event_type`
- `processed_at`
- `status`

同じ event_id は再処理しない。処理中に失敗した場合は Kafka retry / DLQ の設計に委ねる。

### 実装ステップ

1. `skishop.event.mode=outbox|kafka|logging` を明示的に導入。
2. P0 イベントは outbox に統一。
3. `OutboxRelay` scheduler を common-lib または各サービスに導入。
4. retry / DLQ / metrics を追加。
5. consumer idempotency helper を common-lib に追加。
6. Grafana に outbox backlog / failed events を表示。

### テスト

- DB commit と outbox insert が同一 transaction。
- Kafka 停止中でも業務処理は成功し、outbox に残る。
- Kafka 復旧後に relay が送信する。
- 同一 event を consumer が二重処理しない。

## 9. Agent Cart / Inventory / Coupon の業務整合性

### 該当箇所

- `ai-agent-services/orchestrator-agent/src/main/java/com/example/skishop/agent/orchestrator/service/OrchestratorAgentService.java`
- `payment-cart-service/src/main/java/com/example/skishop/payment/service/CartBuildService.java`
- `inventory-management-service/src/main/java/com/example/skishop/inventory/service/ProductService.java`
- `coupon-service/src/main/java/com/example/skishop/coupon/service/CouponService.java`

### 現状

Orchestrator は 8 step で、装備推薦、在庫確認、価格計算、クーポン最適化、カート構築、在庫予約まで進める設計である。`CartBuildService` は実カートへ追加するが best-effort で、失敗しても preview response は返す。

### 問題

- カート追加成功、在庫予約失敗のような部分成功が起きる。
- dynamic price とカート価格が後でズレる可能性がある。
- coupon optimization が実クーポン予約・消込とつながっていない可能性がある。
- 在庫予約 TTL、解放、注文確定時の消込が一貫していない。

### 本来の実装方針

Agent の購入提案は「見積もり」と「確定」を分ける。

#### Quote model

`agent_quotes` を payment-cart-service または orchestrator 側に持つ。

- `quote_id`
- `user_id`
- `items`
- `dynamic_prices`
- `coupon_plan`
- `point_plan`
- `inventory_reservations`
- `subtotal`
- `discount`
- `total`
- `expires_at`
- `status` (`DRAFT`, `RESERVED`, `COMMITTED`, `EXPIRED`, `CANCELLED`)

#### Flow

1. Orchestrator creates quote。
2. inventory reservation は quoteId 紐づけ。
3. cart build は quote を参照して実カートを置き換える、または quote から checkout に進む。
4. checkout 時に quote の有効期限、価格、在庫予約、クーポンを再検証。
5. payment success で quote committed。
6. payment fail / timeout で reservation release。

### 実装ステップ

1. `BuildCartResponse.status` の意味を UI で明示する。
2. Quote API を追加する。
3. inventory reservation に TTL と quoteId を入れる。
4. coupon optimization を coupon reservation に接続する。
5. checkout は quoteId から決済 intent を作る。
6. quote expiration job を追加する。

### テスト

- reservation timeout で在庫が解放される。
- quote expired 後は checkout 不可。
- dynamic price が quote 内で固定される。
- payment success で quote committed。
- payment fail で reservation release。

## 10. Dead Stock Coupon 連携

### 該当箇所

- `ai-support-service/src/main/java/com/example/skishop/ai/service/DeadStockService.java`
- `coupon-service/src/main/resources/db/migration/V5__create_dead_stock_actions.sql`
- `coupon-service/src/main/java/com/example/skishop/coupon/controller/CouponController.java`
- `coupon-service/src/main/java/com/example/skishop/coupon/controller/InternalCouponController.java`

### 現状

DeadStockService は coupon-service に `/api/v1/coupons` と `/api/v1/dead-stock-actions` を呼ぼうとしている。一方、coupon-service 側の公開 controller 一覧を見る限り、dead-stock-actions の dedicated endpoint は確認できない。migration はあるため DB は用意されているが、API 実装が不足している可能性が高い。

また、`/api/v1/coupons` の通常 create coupon は campaignId や code を要求する設計であり、DeadStockService が送る `sku`, `discountPct`, `type` とは schema が合わない可能性がある。

### 問題

- Dead stock 画面からクーポン発行すると coupon-service 側で 400/500 になる可能性がある。
- 重複チェック API がない場合、安全側で常に拒否される。
- dead_stock_actions に監査ログが残らない。

### 本来の実装方針

coupon-service に Dead Stock 専用 API を追加する。

#### API

- `GET /api/v1/dead-stock-actions?sku={sku}&days=30`
- `POST /api/v1/dead-stock-actions`
- `POST /api/v1/coupons/dead-stock`

`POST /api/v1/coupons/dead-stock` は以下を受け取る。

- `sku`
- `discountPct`
- `memo`
- `approverUserId`
- `expiresAt`

内部で campaign を自動作成または dedicated campaign を取得し、実 coupon code を生成する。

### 実装ステップ

1. `DeadStockAction` entity / repository / controller を追加。
2. `DeadStockCouponRequest` を追加。
3. 30 日重複チェックを coupon-service 側で transaction 内に実装。
4. coupon 作成と action 記録を同一 transaction にする。
5. ai-support-service の DeadStockService は専用 API だけを呼ぶようにする。

### テスト

- 同一 SKU 30 日以内は 409。
- discountPct は 0-40 に clip。
- coupon と dead_stock_actions が同時に保存される。
- action 保存失敗時に coupon だけ残らない。

## 11. フロントエンドの暫定箇所

### 該当箇所

- `frontend/src/app/(ec)/catalog/[categorySlug]/page.tsx`
- `frontend/src/components/layout/ec-footer.tsx`
- `frontend/src/app/api/admin/dashboard/route.ts`
- 各 `frontend/src/app/api/**/route.ts`
- `frontend/src/app/(ec)/checkout/page.tsx`
- `frontend/src/app/(ec)/agent/page.tsx`
- `frontend/src/lib/orchestrator-client.ts`
- `frontend/src/hooks/use-agent-stream.ts`
- `frontend/src/app/(admin)/admin/ai/page.tsx`
- `frontend/src/app/(admin)/admin/analytics/page.tsx`
- `frontend/src/components/admin/ai-analyzer/*`
- `frontend/src/types/api/*.ts`

### 現状

確認できた TODO / fallback:

- カテゴリページに `TODO: fetch category name from API`
- footer に i18n TODO
- admin dashboard BFF に null fallback が複数存在
- 各 route が `API_GATEWAY_URL || http://127.0.0.1:8090` を直接持つ箇所が多い
- payment BFF の一部が `PAYMENT_SERVICE_URL` で payment-cart-service を直接呼んでおり、Gateway / JWT / 共通エラー処理を迂回している。
- admin payments BFF が `/api/v1/admin/payments/**` を呼んでいるが、payment-cart-service の controller は `/api/v1/payments/**` であるため、現状のままだと 404 になる可能性が高い。
- admin AI model BFF が `/api/v1/ai/models/**` を呼んでいるが、Gateway の RouteConfig は `/api/v1/models/**` を ai-support-service へルーティングするため、パス不整合がある。
- `frontend/src/app/(admin)/admin/ai/page.tsx` は backend 呼び出しに失敗すると `MOCK_MODELS` を表示し、training progress もフロント側乱数で進めている。
- `frontend/src/types/api/cart.ts` の `PaymentResponse.status` が `PROCESSING` / `COMPLETED` を含む一方、backend の現行 PaymentStatus は `PENDING` / `AUTHORIZED` / `CAPTURED` / `FAILED` / `REFUNDED` で不一致。
- checkout は `payment intent -> process payment -> create order` の順で、決済成功後に注文作成へ失敗する不整合が起きうる。将来の Webhook / Outbox 実装後は flow を見直す必要がある。
- Agent 画面は `CartBuildService.persistToUserCart` 前提で「すでにカートに追加されています」と表示しているが、backend は `CONFIRMED_PARTIAL` / `PREVIEW_ONLY` を返す可能性がある。quote / cart persistence status を UI が厳密に扱えていない。
- Analytics API に `dataAvailable` / `partial` / `missingSources` を追加する計画に対し、admin analytics / dashboard 画面は現状 null fallback 中心で、partial/unavailable を明示する UI が不足している。

### 本来の実装方針

- BFF 共通 gateway client を使う。
- null fallback ではなく、`data`, `error`, `partial` を明示する。
- 管理画面では一部カードの取得失敗をカード単位で表示する。
- カテゴリ名は categories API から取得し、metadata / breadcrumb に反映する。
- i18n は `locales/ja.json`, `locales/en.json` に寄せる。

### バックエンド修正計画に伴うフロントエンド影響

| バックエンド計画 | 影響するフロント | 必要な対応 |
| --- | --- | --- |
| Payment Gateway / Webhook | checkout, payment BFF, admin payment routes, payment types | 決済状態を PSP 非同期前提にし、status / nextAction / webhook反映待ちを扱う |
| Quote model | agent page, checkout, cart, orchestrator types | quoteId / expiresAt / status を表示し、quote から checkout へ遷移 |
| User Profile 実データ化 | agent page, mypage, auth/session | tier / point / profileCompleteness / warnings を表示可能にする |
| AI 検索実商品化 | search page, search-bar, search BFF, product card | semantic search response と inventory product response を統一 |
| AI 推薦実商品化 | personalized/trending sections, dashboard home | 存在商品だけを表示し、impression/click/conversion を送る |
| AI Analyzer Tool 実データ化 | admin ai-analyzer components | dataUnavailable / partial / action proposal を UI に出す |
| AnalyticsService 実データ化 | admin analytics, admin dashboard | fixed 0 前提をやめ、partial/unavailable metadata に対応 |
| Model Management 実装 | admin AI page, model BFF | mock model fallback を撤去し、training job lifecycle を poll |
| Dead Stock Coupon API | DeadStockIssueModal, BFF | approverUserId / expiresAt / audit result / duplicate reason を扱う |

### フロントエンド修正計画

#### 11.1 BFF 共通化と API パス不整合の修正

現状、`frontend/src/lib/gateway-fetch.ts` に `proxyToGateway` がある一方で、多くの API route が独自 `safeFetch` と `API_GATEWAY_URL` を持っている。まず BFF route は原則 `proxyToGateway` に寄せる。

修正対象:

- `frontend/src/app/api/admin/analytics/route.ts`
- `frontend/src/app/api/admin/dashboard/route.ts`
- `frontend/src/app/api/admin/payments/history/route.ts`
- `frontend/src/app/api/admin/payments/[id]/refund/route.ts`
- `frontend/src/app/api/admin/ai/models/**/route.ts`
- `frontend/src/app/api/payments/intent/route.ts`
- `frontend/src/app/api/payments/[id]/process/route.ts`
- `frontend/src/app/api/search/route.ts`
- `frontend/src/app/api/search/autocomplete/route.ts`

修正内容:

- payment 系 BFF は `PAYMENT_SERVICE_URL` 直呼びをやめ、Gateway の `/api/v1/payments/**` 経由に統一する。
- admin payment history は backend 実装に合わせ、`/api/v1/payments/history?userId=...` または管理者用 API が追加されるなら `/api/v1/payments/admin/history` のように backend と route を合わせる。
- admin refund は `/api/v1/payments/{paymentId}/refund` に合わせる。`/api/v1/admin/payments/{id}/refund` は backend 側に実装されない限り使わない。
- admin AI models は `/api/v1/ai/models/**` ではなく、Gateway route に合わせて `/api/v1/models/**` を呼ぶ。
- BFF は upstream が null / 空 body / 非 JSON を返しても、画面が壊れない typed response を返す。
- response に `upstreamStatus`, `partial`, `missingSources` を含められるよう共通型を用意する。

#### 11.2 決済・Checkout UI

Payment Gateway / Webhook 実装後は、決済は同期的に `CAPTURED` で完了するとは限らない。

必要な型変更:

```ts
type PaymentStatus =
  | 'PENDING'
  | 'REQUIRES_ACTION'
  | 'AUTHORIZED'
  | 'CAPTURED'
  | 'FAILED'
  | 'CANCELLED'
  | 'PARTIALLY_REFUNDED'
  | 'REFUNDED';

interface PaymentResponse {
  id: string;
  userId: string;
  orderId?: string | null;
  paymentIntentId: string;
  status: PaymentStatus;
  amount: number;
  currency: string;
  paymentMethod: string;
  gatewayProvider?: string | null;
  nextAction?: PaymentNextAction | null;
  refundedAmount: number;
  completedAt?: string | null;
  createdAt: string;
}
```

Checkout flow は以下へ変更する。

1. cart または quote から order draft を作る。
2. orderId 付きで payment intent を作る。
3. PSP の `clientSecret` / `redirectUrl` / `requiresAction` を処理する。
4. `CAPTURED` 即時完了なら complete へ遷移。
5. `PENDING` / `AUTHORIZED` / `REQUIRES_ACTION` なら `/checkout/processing?paymentId=...&orderId=...` を表示する。
6. processing 画面で payment / order status を poll する。
7. Webhook で order が PAID になったら complete へ遷移。
8. 失敗時は retry / payment method change / cart に戻る導線を出す。

注意:

- 現在の checkout は payment success 後に order 作成しているため、決済成功・注文失敗の不整合が起きる。バックエンド側の最終設計に合わせ、注文 draft -> payment -> webhook confirm の順に変更する。
- `Idempotency-Key` は payment intent / process / order draft で同一キーの使い回し可否を backend と合わせる。通常は operation ごとに別 key、checkout attempt id で相関する。
- 管理画面の payment history は `CAPTURED`, `FAILED`, `PARTIALLY_REFUNDED` など backend status をそのまま badge 表示する。

#### 11.3 Agent / Quote UI

Quote model 導入後、Agent 画面は「カートへ自動追加済み」前提をやめる。

必要な型変更:

```ts
interface QuoteSummary {
  quoteId: string;
  orderId?: string | null;
  status: 'DRAFT' | 'RESERVED' | 'COMMITTED' | 'EXPIRED' | 'CANCELLED';
  cartPersistenceStatus?: 'CONFIRMED' | 'CONFIRMED_PARTIAL' | 'PREVIEW_ONLY';
  items: QuoteItem[];
  subtotal: number;
  couponDiscount: number;
  pointDiscount: number;
  totalAmount: number;
  reservationId?: string | null;
  reservationExpiresAt?: string | null;
  warnings?: string[];
}
```

UI 方針:

- `CONFIRMED`: 「カートに追加済み」と表示。
- `CONFIRMED_PARTIAL`: 追加できた商品と失敗した商品を分けて表示し、「カートを確認」導線を出す。
- `PREVIEW_ONLY`: 「見積もりのみ。購入するにはカートへ追加してください」と表示し、明示的な `カートに追加` ボタンを出す。
- `quote.status=EXPIRED`: checkout ボタンを disabled にし、再見積もりを促す。
- `reservationExpiresAt` までの残り時間を表示する。
- `profileCompleteness` / `warnings` が Orchestrator response に追加された場合、推奨の信頼度・不足データを控えめに表示する。

#### 11.4 Search / Recommendation UI

AI 検索・推薦を実商品化する場合、UI は「AI SearchResponse」と「ProductResponse」を変換する境界を明確にする。

修正内容:

- `frontend/src/types/api/ai.ts` の `SearchResult` に `sku`, `imageUrl`, `categoryId`, `inStock`, `regularPrice`, `salePrice`, `score`, `reason` を追加する。
- `/api/search` BFF は inventory search response を受け取り、`results`, `totalHits`, `query`, `enhancedQuery`, `facets`, `source` を返す。
- 検索画面は 0 件時に「該当商品なし」と「ゼロヒット記録済み」を区別せず、ユーザーには自然な代替導線を出す。
- autocomplete は単純な product search だけでなく、将来的に `/api/v1/search/autocomplete` が実装されたらそちらを優先する。
- `personalized-section` / `trending-section` は推薦 API が返した productId を batch product API で検証し、存在しない商品は表示しない。
- 推薦カード表示時に impression、クリック時に click、購入完了時に conversion を送る。

#### 11.5 Admin Analytics / Dashboard UI

AnalyticsService と AI Analyzer が `dataAvailable`, `partial`, `missingSources` を返すようになったら、画面は以下に対応する。

- dashboard 全体ではなくカード単位で degraded state を出す。
- 値が 0 の場合は通常表示、`dataAvailable=false` の場合は `データ取得不可` 表示。
- `missingSources` がある場合は管理者向けに「sales-service 取得失敗」などの短い注記を出す。
- 再試行ボタンはカード単位にする。
- `/admin/analytics` の sales / users / search タブで partial badge を表示する。
- CSV / report export 時は partial report であることを header metadata に含める。

#### 11.6 Admin AI Model UI

Model Management 実装後、`MOCK_MODELS` fallback とフロント側乱数 progress は撤去する。

修正内容:

- `/api/admin/ai/models` は backend の `/api/v1/models/versions?modelType=...` など実 API に合わせる。
- train 開始後は `trainingId` を受け取り、`/api/admin/ai/models/train/{trainingId}` または backend の status API を poll する。
- status は backend と合わせて `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `ACTIVE` などにする。
- progress は backend が返す場合のみ表示し、なければ indeterminate progress にする。
- deploy は `modelVersionId` と `activateImmediately` を body に含める。
- performance は空 Map ではなく metrics を表示し、未取得なら unavailable として表示する。

#### 11.7 Dead Stock Coupon UI

Dead Stock 専用 coupon API 追加後、`DeadStockIssueModal` は以下を送る。

- `sku`
- `discountPct`
- `memo`
- `approverUserId`
- `expiresAt`

BFF は session user id を `approverUserId` として補完する。レスポンスには以下を期待する。

- `couponId`
- `couponCode`
- `actualPct`
- `aiSuggestedPct`
- `duplicateBlocked`
- `auditActionId`

UI は成功時に coupon code と audit id を表示する。409 の場合は backend の reason / existingActionCreatedAt を表示し、単なる固定文言だけにしない。

#### 11.8 型とテスト

追加・修正する型:

- `PaymentStatus`
- `PaymentWebhookEvent`
- `RefundResponse`
- `QuoteSummary`
- `ProfileCompleteness`
- `DataAvailability`
- `AnalyticsResponseMeta`
- `ModelTrainingJob`
- `ModelVersion`
- `RecommendationImpressionRequest`
- `SearchFacet`

テスト方針:

- BFF route unit test: backend path が正しいこと。
- checkout flow test: `REQUIRES_ACTION`, `PENDING`, `CAPTURED`, `FAILED`。
- agent page test: `CONFIRMED_PARTIAL` / `PREVIEW_ONLY` 表示。
- admin analytics test: partial / missingSources 表示。
- admin AI model test: mock fallback ではなく job polling。
- search test: 0 件、実商品あり、autocomplete。
- dead stock modal test: 409 reason、success coupon code。

### 実装ステップ

1. `frontend/src/lib/gateway-fetch.ts` または `api-client.ts` に BFF 共通処理を集約。
2. admin dashboard の各 fetch に typed fallback を導入。
3. category slug -> category API の変換を実装。
4. footer 文言を locale に移動。
5. E2E で部分失敗表示を確認。
6. payment / admin payment / admin AI models の BFF path 不整合を修正する。
7. backend の新 DTO に合わせて `frontend/src/types/api/*.ts` と `orchestrator-client.ts` を更新する。
8. checkout processing 画面と quote checkout flow を追加する。
9. admin AI page の mock fallback と乱数 progress を撤去し、training job polling に変更する。

## 12. 横断的な観測性

### 必要なメトリクス

決済:

- payment_intent_created_total
- payment_captured_total
- payment_failed_total
- payment_webhook_duplicate_total
- payment_webhook_failed_total
- refund_processed_total

Outbox:

- outbox_pending_count
- outbox_failed_count
- outbox_publish_duration
- outbox_retry_total

AI:

- ai_tool_call_total
- ai_tool_call_failed_total
- ai_tool_data_unavailable_total
- llm_tokens_in_total
- llm_tokens_out_total
- llm_cost_estimate_usd
- ai_recommendation_impression_total
- ai_recommendation_click_total
- ai_recommendation_conversion_total

Agent:

- orchestrator_step_duration
- orchestrator_step_failed_total
- quote_created_total
- quote_committed_total
- quote_expired_total

### ログ

全サービスで以下を揃える。

- `correlationId`
- `userId` は必要に応じて hash 化
- `eventId`
- `orderId`
- `paymentId`
- `quoteId`
- `agentSessionId`

## 13. 推奨実装ロードマップ

### Milestone 1: 決済とイベント保証

目的: EC の中核業務を壊れにくくする。

実施内容:

- PaymentGatewayClient 抽象化
- Simulated gateway の切り出し
- Webhook event table
- Webhook 冪等処理
- Payment status transition
- Outbox relay
- PaymentCaptured -> Order/Inventory/Point/Coupon 連携

完了条件:

- 決済成功から注文確定まで E2E で一貫する。
- Kafka 停止中でも outbox にイベントが残り、復旧後に送信される。
- Webhook 重複で二重処理されない。

### Milestone 2: Orchestrator personalization

目的: AI 購入支援が実ユーザー文脈を使えるようにする。

実施内容:

- UserProfileResponse 拡張
- point / sales / coupon internal summary API
- user-management-service で profile aggregation
- Orchestrator prompt 更新
- Quote model の導入検討

完了条件:

- Orchestrator が実 tier / point / purchasedCategories を受け取る。
- 一部サービス障害時も fallback profile で動く。

### Milestone 3: AI 検索・推薦の実商品化

目的: 顧客向け AI 機能から固定値をなくす。

実施内容:

- SearchService から固定検索結果を削除。
- inventory search に接続。
- RecommendationService を候補生成 + scoring + LLM reason に分離。
- click / conversion feedback を保存。

完了条件:

- 存在する商品だけが推薦・検索に出る。
- 在庫切れ商品を除外できる。
- zero-hit log が実検索から作られる。

### Milestone 4: AI Analyzer 実データ化

目的: 管理者 AI が本当に業務判断に使える数値を返す。

実施内容:

- AnalyticsToolFunctions の空実装を実データ接続。
- DeadStock / ZeroHit の専用サービスと Tool の結果を統一。
- AnalyticsService の product-performance / trends / dashboard 固定値を実データ化。
- dataUnavailable を DTO で表現。
- dashboard / admin AI UI の部分失敗表示。

完了条件:

- AI Analyzer が Tool 経由で実売上、在庫、販売速度、ゼロヒットを返す。
- 空データと取得失敗を区別できる。
- `/api/v1/analytics/**` が固定 0 / 空配列中心ではなく、実集計または partial/unavailable metadata を返す。

### Milestone 5: Model Management と改善ループ

目的: AI 機能を改善可能な運用機能にする。

実施内容:

- training job runner
- model artifact 保存
- model version active 切替
- performance metrics
- 推薦/検索で active model を参照

完了条件:

- training が RUNNING のまま止まらない。
- version deploy に意味がある。
- performance API が実 metrics を返す。

## 14. 受け入れ基準チェックリスト

### 決済

- [ ] Webhook 署名検証がある。
- [ ] Webhook event id で冪等処理される。
- [ ] Payment status の不正遷移が拒否される。
- [ ] PaymentCaptured で注文が支払い済みになる。
- [ ] PaymentFailed で在庫予約が解放される。
- [ ] 部分返金が表現できる。

### AI 推薦・検索

- [ ] 固定 productId が削除される。
- [ ] 存在しない商品が返らない。
- [ ] 在庫・販売停止ステータスが考慮される。
- [ ] 検索ログが zero-hit 分析につながる。
- [ ] LLM 障害時も deterministic fallback が返る。

### User Profile / Orchestrator

- [ ] tier / pointBalance が point-service 由来。
- [ ] purchasedCategories が sales-service 由来。
- [ ] availableCoupon summary が coupon-service 由来。
- [ ] 部分失敗が warnings として返る。

### AI Analyzer

- [ ] ToolFunctions が空リスト・0 固定を返さない。
- [ ] data unavailable と true zero を区別する。
- [ ] DeadStock 専用 API と Tool 結果が一致する。
- [ ] ZeroHit 専用 API と Tool 結果が一致する。

### AI Analytics API

- [ ] product-performance が views / purchases / conversionRate を実データから返す。
- [ ] trends / customer-segments が空配列固定ではない。
- [ ] dashboard が totalUsers / totalSearches / totalRecommendations / activeChatSessions を実集計する。
- [ ] custom-report が reportType ごとの実集計結果を返す。
- [ ] API レスポンスで true zero と data unavailable を区別できる。

### イベント

- [ ] P0 業務イベントが Outbox に保存される。
- [ ] Outbox relay が retry する。
- [ ] retry 上限超過が監視される。
- [ ] consumer が event id で冪等。

## 15. 変更時の注意

- 未コミット変更が存在するため、実装前に `git status --short` で差分を確認する。
- 既存の未コミット変更はユーザー作業の可能性があるため、勝手に revert しない。
- payment / order / inventory / coupon / point は業務整合性が絡むため、単体修正ではなく E2E で検証する。
- AI 機能は LLM の出力を業務事実として扱わない。productId、price、stock、discount、order amount は必ず DB / service 由来にする。
- LLM が失敗しても、検索・推薦・分析の最低限の deterministic result は返せるようにする。
- 管理者が意思決定する領域では、AI は「提案」に留め、承認者・実行値・監査ログを必ず残す。
