<!-- markdownlint-disable MD024 MD029 -->

# 未実装・モック実装項目の修正計画

作成日: 2026-05-28

## 目的

この文書は、プロジェクト全体の再解析で抽出した未実装、スタブ、モック、固定 fallback、placeholder のうち、実装として修正可能な項目を正しく修正するための詳細計画である。

今回はコード修正そのものは行わず、実装時に迷わないように以下を明確化する。

- どの項目を修正対象にするか。
- 現在のコード上、何が原因で未実装・モック扱いになっているか。
- 既存の API / Repository / Service をどう使えば実データ化できるか。
- 実装順序、影響範囲、テスト観点、完了条件。

既存の `impl-plan-for-unimple-fun2.md` は受け入れ基準チェックリスト未完了項目の実装計画であり、本書はその後に見つかった残課題の修正計画である。

## 前提

- 既存の未コミット差分は巻き戻さない。
- Java 実装時は `.github/instructions/java-coding-standards.instructions.md` と、Controller 変更時は `.github/instructions/api-design.instructions.md` を読む。
- 本番データの代わりに固定値を表示する修正は避ける。取得不能時は `unavailable`、HTTP error、空表示、または degraded UI として明示する。
- LLM は推薦理由や自然文生成の補助に限定する。商品 ID、価格、在庫、売上、割引は DB / Service API を source of truth とする。
- `rg` はこの環境に存在しないため、検証検索は `grep_search` または `git grep` / IDE 検索で実施する。

## 修正優先度サマリ

| 優先度 | 項目 | 主な対象 | 修正方針 |
| --- | --- | --- | --- |
| P0 | 売上数 API スタブ | sales-management-service | `0` 固定返却を Repository 集計へ置換する。 |
| P0 | クーポン dummy fallback | coupon-service | 未存在クーポンは dummy 生成せず 404 / usable=false へ置換する。 |
| P0 | 検索 semantic / feedback route 欠落・feedback 記録薄い実装 | frontend / ai-support-service | 既存 `/api/v1/search/semantic` と `/api/v1/search/feedback` へ BFF を接続し、検索 feedback はログ返却だけでなく永続化する。 |
| P1 | Admin AI モック・存在しない model route | frontend / ai-support-service | BFF のパス不一致と存在しない個別 route を直し、モデル一覧・学習・status・deploy を実 API に接続する。 |
| P1 | 管理画面の固定 fallback データ | frontend | 偽データ表示をやめ、部分取得失敗を degraded 表示にする。 |
| P1 | メールログ画面の固定統計 fallback | frontend / mailsend-service | 固定の日別送信数を廃止し、実統計を返すか取得不可状態を表示する。 |
| P1 | 在庫発注推奨の random 値 | frontend / sales-management-service | `sku-velocity` 等の実売上速度から推奨数を算出する。 |
| P1 | 決済 simulated provider の本番混入 | payment-cart-service | simulated を local/test 限定にし、本番 profile では明示 provider を必須化する。 |
| P2 | EC ホーム固定カテゴリ fallback / footer i18n TODO | frontend | API 失敗時に固定カテゴリを実データ風に出さず、footer 文言は i18n 辞書へ移す。 |
| P2 | 天気 fallback 東京固定 | weather-agent | geocoding 失敗時に東京データを返さず unavailable を明示する。 |
| P2 | カテゴリ名 TODO / sort 空実装 | frontend / inventory-management-service | Category API と URL query 更新へ接続する。 |
| P2 | `Math.random()` UUID fallback | frontend | `crypto.getRandomValues` ベース fallback へ置換する。 |
| P2 | Terraform quickstart image placeholder | infra | image を変数化し、実デプロイ対象 image を必須化する。 |
| P3 | CircuitBreaker の空値 fallback | ai-support-service ほか | 偽データではなく `DataAvailability` / degraded response を一貫させる。 |

## 現状調査メモ

### sales-management-service

- `InternalSalesController.getSalesCount()` は `count: 0` 固定返却で、コメントにも「現状スタブ」とある。
- `OrderRepository` には日次売上、Top 商品、顧客購入サマリなどの native query はあるが、商品 ID 単位の期間販売数 query はない。
- `orders` と `order_items` は `order_items.order_id = orders.id` で結合できる。
- 既存集計では `CANCELLED` / `RETURNED` を除外しているため、販売数 API も同じ基準に合わせる。

### coupon-service

- `CouponRepository.findByCode(String code)` は存在する。
- `InternalCouponController.findByCode()` は未存在時に `dummy-<code>`、10% 割引、最低注文額 3000 円などの架空値を返している。
- `Coupon.isUsable()` で active / usageLimit / expiresAt の利用可否判定ができる。

### frontend / ai-support-service search

- `ai-support-service` には `SearchController` があり、`POST /api/v1/search/semantic` と `POST /api/v1/search/feedback` が実装済み。
- `api-gateway-service` は `/api/v1/search/**` を ai-support-service へ route している。
- frontend の `/search` 画面は `/api/search/semantic` と `/api/search/feedback` を呼ぶが、Next.js BFF route は存在しない。
- frontend の semantic fallback では `SKU-SEM-*`、在庫数 10、カテゴリ空文字などの固定値を生成している。
- `SearchFeedbackRequest` の実 field は `query`, `resultId`, `relevant`, `userId` である。現状 `SearchService.recordSearchFeedback()` はログ出力と `FeedbackResponse` 返却のみで、検索 analytics へ関連度 feedback を永続化していない。

### frontend Admin AI

- `ai-support-service` のモデル管理 API は `/api/v1/models/**`。
- `api-gateway-service` も `/api/v1/models/**` を ai-support-service へ route している。
- frontend BFF は `/api/v1/ai/models` と `/api/v1/ai/models/train` を呼んでおり、パスが合っていない。
- frontend BFF の `/api/admin/ai/models/[id]` と `/api/admin/ai/models/[id]/deploy` は、backend に存在しない `/api/v1/ai/models/{id}` / `/api/v1/ai/models/{id}/deploy` を呼んでいる。backend の deploy は `POST /api/v1/models/deploy` に request body を送る形である。
- Admin AI 画面は API 失敗時に `MOCK_MODELS` を表示し、学習進捗も `Math.random()` で進めている。

### frontend Admin Dashboard / Inventory

- Admin Dashboard は KPI、売上グラフ、低在庫、最近注文に固定 fallback データを持つ。
- Admin Inventory の発注推奨は `Math.random()` で週次売上見込みを作り、推奨発注数を算出している。
- sales-management-service には `/api/v1/admin/orders/analytics/sku-velocity` があり、SKU 別 `sales30`, `sales90`, `units30`, `units90`, `lastSoldAt`, `avgPrice` を取得できる。

### frontend Mail Logs / EC Home

- `frontend/src/app/(admin)/admin/mail-logs/page.tsx` は `/api/admin/mail/stats` が日別配列を返さない場合、月〜日の固定 `sentCount` / `successRate` を表示する。
- mailsend-service の `/api/v1/mail/stats` は `totalSent`, `totalFailed`, `totalPending`, `successRate`, `sentByTemplate` の集計を返すが、現状の日別 chart 用 `daily` は返さない。
- `frontend/src/components/ec/category-section.tsx` はカテゴリ API が空または失敗した場合に `cat-ski` など固定カテゴリカードを表示する。これは skeleton ではなく実リンク付きの業務データ風 fallback である。
- `frontend/src/components/layout/ec-footer.tsx` には footer 紹介文の `TODO: i18n` が残っている。
- `frontend/src/lib/tips/index.ts` の `Math.random()` は UX 上の Tip ランダム表示であり、売上・在庫・割引・ID 生成ではない。修正対象にする場合は「全 main code から Math.random を消す」方針の一部として扱う。

### payment-cart-service

- `SimulatedPaymentGatewayClient` が `PaymentGatewayClient` の唯一の実装として Component 登録されている。
- `PaymentGatewayProperties.DEFAULT_PROVIDER` と `application.properties` の default が `simulated`。
- `pm_fail` によるカード失敗シナリオは simulated provider としては有用だが、本番 profile の既定値として残すべきではない。

### weather-agent

- geocoding 失敗、または location 空の場合に東京座標を返す。
- ユーザーが北海道や長野を意図していても、取得失敗時に東京の天気が正常値として扱われる。

## 実装順序

1. backend の明確なスタブ / dummy 返却を先に消す。
2. frontend の存在しない BFF route とパス不一致を直し、モック fallback が発動しない状態にする。
3. 管理 UI の固定データと random 計算を実データ / degraded 表示に置き換える。
4. 本番混入リスクがある simulated / placeholder を profile や変数で制御する。
5. fallback の表現を `DataAvailability` / degraded UI として一貫させる。
6. 最後に横断検索と focused tests で、`dummy`, `MOCK`, `Math.random`, `TODO`, `placeholder` の残りを分類確認する。

## Phase 1: Sales count stub の実データ化

### 対象ファイル

- `sales-management-service/src/main/java/com/example/skishop/sales/controller/InternalSalesController.java`
- `sales-management-service/src/main/java/com/example/skishop/sales/repository/OrderRepository.java`
- `sales-management-service/src/test/java/com/example/skishop/sales/controller/InternalSalesControllerTest.java` 新規候補
- `sales-management-service/src/test/java/com/example/skishop/sales/repository/OrderRepositoryTest.java` 新規候補

### 修正方針

`getSalesCount(productId, days)` の `count: 0` 固定返却をやめ、`order_items.product_id` 単位の販売数量を返す。

動的価格エージェントが利用する値としては「注文数」より「販売数量」が自然であるため、`SUM(oi.quantity)` を `count` として返す。将来的な混乱を避けるため、response には `count` に加えて `quantity` も同値で含めるか、互換性を確認したうえで `count` のみにする。

集計条件は既存 analytics query と揃える。

- `orders.created_at >= since`
- `orders.status NOT IN ('CANCELLED', 'RETURNED')`
- `order_items.product_id = :productId`

### 実装手順

1. `OrderRepository` に以下の query を追加する。

```java
@Query(value = """
        SELECT COALESCE(SUM(oi.quantity), 0)
        FROM order_items oi
        JOIN orders o ON o.id = oi.order_id
        WHERE oi.product_id = :productId
          AND o.created_at >= :since
          AND o.status NOT IN ('CANCELLED', 'RETURNED')
        """, nativeQuery = true)
long countSoldQuantityByProductIdSince(@Param("productId") String productId,
                                       @Param("since") Instant since);
```

2. `InternalSalesController.getSalesCount()` で `days` を validation する。

- `days < 1` は 400 にする。Controller に `@Validated` を付けて `@Min(1) @Max(365)` を使うのが望ましい。
- 動的 pricing 用で過大期間が不要なら `@Max(365)` にする。

3. `Instant since = Instant.now().minus(days, ChronoUnit.DAYS)` を計算する。
4. Repository から販売数量を取得し、以下の形式で返す。

```json
{
  "productId": "...",
  "days": 30,
  "count": 12,
  "quantity": 12,
  "source": "sales-management-service"
}
```

5. 既存の `TODO` コメントを削除する。

### テスト

- product A の有効注文が 2 件、quantity 合計 3 の場合、`count=3` を返す。
- `CANCELLED` / `RETURNED` 注文は除外される。
- 期間外注文は除外される。
- productId が存在しない場合は `count=0` を返す。
- `days=0` / `days=366` は validation error になる。

### 完了条件

- `InternalSalesController` から `TODO` と `count: 0` 固定値が消える。
- 動的価格エージェントが販売実績 0 固定にならない。

## Phase 2: Coupon dummy fallback の廃止

### 対象ファイル

- `coupon-service/src/main/java/com/example/skishop/coupon/controller/InternalCouponController.java`
- `coupon-service/src/main/java/com/example/skishop/coupon/repository/CouponRepository.java`
- `coupon-service/src/test/java/com/example/skishop/coupon/controller/InternalCouponControllerTest.java` 新規候補
- coupon API を呼ぶ frontend BFF / client

### 修正方針

存在しないクーポンコードに対して架空の 10% 割引を返さない。正しい挙動は以下のどちらかに統一する。

推奨案は **404 Not Found**。

- 未存在: `404` + `{ "error": "COUPON_NOT_FOUND", "couponCode": code }`
- 存在するが期限切れ / 使用上限超過 / inactive: `200` + `usable=false` と理由を返す。
- 存在して利用可能: DB の値を返す。

AI agent や frontend が「未存在でも 200 を期待している」場合は互換案として `200 usable=false` も選べる。ただし dummy ID は絶対に返さない。

### 実装手順

1. `findByCode()` の `coupon == null` branch を削除する。
2. `couponRepository.findByCode(code)` が空なら `ResponseEntity.notFound().build()` か、共通例外 `ResourceNotFoundException` へ置換する。
3. response field を既存 coupon の source of truth に揃える。

必須 field:

- `couponId`
- `couponCode`
- `couponType`
- `discountType`
- `discountValue`
- `minimumOrder`
- `maximumDiscount`
- `expiresAt`
- `usageLimit`
- `usedCount`
- `usable`

4. 期限切れや使用済みの場合は `usable=false` とし、可能なら `unusableReason` を追加する。
5. frontend の coupon validate route が 404 を受け取った場合、ユーザー向けには「クーポンが見つかりません」と表示する。

### テスト

- 存在する coupon は DB 値を返す。
- 未存在 coupon は dummy ID を返さない。
- 期限切れ coupon は `usable=false`。
- 使用上限到達 coupon は `usable=false`。
- `discountRate` と `discountAmount` のような派生 field を返す場合も、DB 値から計算されること。

### 完了条件

- `dummy-` 文字列が main code から消える。
- 未存在クーポンで割引が発生しない。

## Phase 3: Frontend search semantic / feedback route の接続

### 対象ファイル

- `frontend/src/app/(ec)/search/page.tsx`
- `frontend/src/app/api/search/semantic/route.ts` 新規
- `frontend/src/app/api/search/feedback/route.ts` 新規
- `frontend/src/app/api/search/route.ts`
- `ai-support-service/src/main/java/com/example/skishop/ai/controller/SearchController.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/dto/SearchFeedbackRequest.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/service/SearchService.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/model/SearchFeedback.java` 新規候補
- `ai-support-service/src/main/java/com/example/skishop/ai/repository/SearchFeedbackRepository.java` 新規候補
- `frontend/src/__tests__/**` 新規または既存テスト追加

### 修正方針

frontend が呼んでいる BFF route を実装し、既存の ai-support-service API へ proxy する。

既存 backend API:

- `POST /api/v1/search/semantic`
- `POST /api/v1/search/feedback`

api-gateway は `/api/v1/search/**` を ai-support-service に route 済みなので、frontend BFF は `API_GATEWAY_URL` へ接続すればよい。

semantic fallback で `SKU-SEM-*` や `stockQuantity: 10` を生成する実装は削除し、backend から返った inventory 由来 product fields だけを使う。

検索 feedback は BFF route 欠落だけではなく、backend 側もログ出力と receipt 返却だけで終わっている。`SearchFeedbackRequest` の実 field は `query`, `resultId`, `relevant`, `userId` なので、frontend の `{ query, positive }` は `relevant` に変換し、可能ならクリック/表示された `resultId` も送る。

### 実装手順

1. `frontend/src/app/api/search/semantic/route.ts` を追加する。

処理内容:

- request body を JSON parse。
- `query` が空なら 400。
- `POST ${API_GATEWAY_URL}/api/v1/search/semantic` へ body を転送。
- `Authorization` が必要な構成なら `getServerSession(authOptions)` の access token を付与する。
- upstream status をそのまま返す。
- upstream が失敗した場合は 502 を返し、空商品を捏造しない。

2. `frontend/src/app/api/search/feedback/route.ts` を追加する。

処理内容:

- body: `{ query, positive, userId?, sessionId? }`
- ai-support-service の `SearchFeedbackRequest(query, resultId, relevant, userId)` に合わせて field を変換する。
- `positive=true` は `relevant=true`、`positive=false` は `relevant=false` として送る。
- 現状の search page が query 単位 feedback しか持たない場合は、`resultId=null` を許容するか、UI を結果単位 feedback に変更して productId / resultId を送る。
- backend の `SearchService.recordSearchFeedback()` はログだけでなく、`SearchFeedback` collection/table または既存 `SearchAnalytics` の feedback field に永続化する。
- analytics 集計では `relevant=false` の query / resultId を zero-hit 改善や synonym 改善に利用できる形にする。

3. `search/page.tsx` の semantic fallback mapping を修正する。

- `sku` は backend result の `sku` がない場合だけ productId を使う。
- `stockQuantity` / `availableQuantity` は backend result の `inStock` または在庫 field から決める。固定 10 は使わない。
- `categoryId` は backend result の値を使う。空 fallback は最後の手段にする。

4. feedback 送信失敗時は握りつぶしのみではなく、内部状態に失敗を保存するか、少なくとも console ではなく UI の再試行可能状態にする。

### テスト

- `/api/search/semantic` が gateway の `/api/v1/search/semantic` に body を転送する。
- gateway 502 時に frontend は架空商品を表示しない。
- semantic result の SKU / 在庫 / category は backend 応答から作られる。
- `/api/search/feedback` が正しい DTO で backend へ転送される。
- feedback が ai-support-service に永続化され、ログだけで終わらない。
- feedback route が存在しないことによる 404 がなくなる。

### 完了条件

- `frontend/src/app/(ec)/search/page.tsx` から `SKU-SEM-*` と固定在庫 10 が消える。
- `frontend/src/app/api/search/semantic/route.ts` と `frontend/src/app/api/search/feedback/route.ts` が存在する。
- 検索 feedback が 404 にならない。
- `SearchService.recordSearchFeedback()` が `UUID.randomUUID()` receipt のみで完了せず、feedback を後続分析に使える形で保存する。

## Phase 4: Catalog category name TODO と sort 空実装の修正

### 対象ファイル

- `frontend/src/app/(ec)/catalog/[categorySlug]/page.tsx`
- `frontend/src/app/api/categories/[id]/route.ts` 新規候補
- `frontend/src/app/api/admin/categories/[id]/route.ts` 既存参考
- `inventory-management-service/src/main/java/com/example/skishop/inventory/controller/CategoryController.java`

### 修正方針

カテゴリ名は inventory-management-service の Category API から取得する。sort Select は URL query を更新し、`fetchProducts()` が再実行されるようにする。

既存 backend には `CategoryService.getCategoryById(String categoryId)` があり、CategoryController も存在する。frontend の EC 側に公開用 BFF がなければ追加する。

### 実装手順

1. EC 用 BFF `/api/categories/[id]` を追加する。admin route と違い、公開でよいか認証要否を確認する。
2. `CategoryContent` の `categoryName` state を実際に更新する。
3. `categorySlug` 変更時にカテゴリ取得を実行する。
4. 取得失敗時は `categorySlug` を表示し、TODO コメントは削除する。
5. `Select` の `onValueChange` で router に `?sort=<value>` を反映する。
6. 現在 `sort` query を fetch に渡しているため、URL 更新後に既存 `useEffect` で再取得される。

### テスト

- category API 成功時に breadcrumb / H1 がカテゴリ名になる。
- category API 失敗時は slug 表示で落ちない。
- sort 変更で URL query が更新され、商品 API の sort param が変わる。

### 完了条件

- `TODO: fetch category name from API` が消える。
- `onValueChange={() => {}}` が消える。

## Phase 5: Admin AI 画面の MOCK_MODELS / random progress 廃止

### 対象ファイル

- `frontend/src/app/(admin)/admin/ai/page.tsx`
- `frontend/src/app/api/admin/ai/models/route.ts`
- `frontend/src/app/api/admin/ai/models/train/route.ts`
- `frontend/src/app/api/admin/ai/models/status/[trainingId]/route.ts` 新規候補
- `frontend/src/app/api/admin/ai/models/[id]/deploy/route.ts`
- `frontend/src/app/api/admin/ai/models/[id]/route.ts`
- `ai-support-service/src/main/java/com/example/skishop/ai/controller/ModelManagementController.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/service/ModelManagementService.java`

### 修正方針

Admin AI BFF の呼び先を `/api/v1/models/**` に修正し、画面側は mock model へ fallback しない。学習進捗は `setInterval + Math.random()` ではなく、trainingId を使って `/api/v1/models/status/{trainingId}` を polling する。

backend に存在する endpoint は `POST /api/v1/models/train`, `GET /api/v1/models/status/{trainingId}`, `GET /api/v1/models/versions?modelType=...`, `POST /api/v1/models/deploy`, `GET /api/v1/models/performance` である。`GET /api/v1/models/{id}` や `POST /api/v1/models/{id}/deploy` は存在しないため、frontend BFF は削除または正しい aggregate route へ置換する。

### 実装手順

1. `frontend/src/app/api/admin/ai/models/route.ts` の呼び先を修正する。

現在:

```text
${API_GATEWAY_URL}/api/v1/ai/models
```

修正後候補:

```text
${API_GATEWAY_URL}/api/v1/models/versions?modelType=RECOMMENDATION
```

2. UI が複数 model type を一覧したい場合は、BFF で `RECOMMENDATION`, `SEARCH`, `DEMAND_FORECAST`, `FRAUD_DETECTION` などの modelType を順に取得して統合する。
3. `MOCK_MODELS` を削除する。API 失敗時は `models=[]` とし、画面に「データ取得不可」を表示する。
4. train API は `/api/v1/models/train` に修正する。
5. `handleTrain()` は `TrainingJobResponse.trainingId` を保持する。
6. Next.js BFF に `/api/admin/ai/models/status/[trainingId]` を追加し、`GET /api/v1/models/status/{trainingId}` へ proxy する。
7. 5 秒ごとの polling で `/api/admin/ai/models/status/{trainingId}` を呼び、backend の status / metrics を表示する。
8. deploy API は `/api/v1/models/deploy` に統一し、`modelVersionId` を request body に入れて送る。現在の `/api/v1/ai/models/{id}/deploy` 呼び出しは削除する。
9. `/api/admin/ai/models/[id]` は backend に対応 endpoint がないため、一覧取得後の client-side lookup にするか、backend に `GET /api/v1/models/versions/{id}` を追加する別タスクにする。
10. UI の `accuracy`, `precision`, `recall` は `ModelVersionResponse.performance` から取り出す。存在しない場合は `null` 表示にする。

### テスト

- BFF が `/api/v1/models/versions` を呼ぶ。
- API 失敗時に `MOCK_MODELS` が表示されない。
- train 開始後、random progress ではなく status endpoint の応答で進捗が変わる。
- deploy ボタンが `/api/v1/models/deploy` へ正しい body を送る。
- frontend BFF から `/api/v1/ai/models` と `/api/v1/ai/models/{id}/deploy` への呼び出しが消える。
- 存在しない model 個別 GET route を UI が前提にしない。

### 完了条件

- `MOCK_MODELS` と `Math.random()` 進捗が削除される。
- Admin AI 画面が実 model training / version データを表示する。

## Phase 6: Admin Dashboard の固定 fallback データ廃止

### 対象ファイル

- `frontend/src/app/(admin)/admin/dashboard/page.tsx`
- `frontend/src/app/api/admin/dashboard/route.ts`
- 関連 backend analytics endpoint

### 修正方針

固定 KPI / 固定注文 / 固定低在庫を表示しない。管理画面で fake business data を表示すると運用判断を誤るため、取得不能時は degraded state として明示する。

### 実装手順

1. `fallbackKpi`, `fallbackSalesDaily`, `fallbackSalesWeekly`, `fallbackSalesMonthly`, `fallbackLowStock`, `fallbackOrders` を削除する。
2. `DashboardPayload` に `availability` を追加する。

例:

```ts
type Availability = {
  analytics: 'available' | 'unavailable';
  lowStock: 'available' | 'unavailable';
  recentOrders: 'available' | 'unavailable';
  recentUsers: 'available' | 'unavailable';
  warnings: string[];
};
```

3. BFF route は各 upstream の成功 / 失敗を `availability` に記録する。
4. UI は unavailable な section に `DataUnavailableBadge` と空 state を表示する。
5. chart は空配列の場合に「データがありません」を表示する。
6. KPI は `0` を偽装表示するのではなく `-` または unavailable 表示にする。

### テスト

- 全 upstream 成功時は実データが表示される。
- orders API 失敗時に固定 `ORD-20240301-*` が表示されない。
- analytics API 失敗時に固定売上 `284000` が表示されない。
- lowStock API 失敗時に固定 SKU が表示されない。

### 完了条件

- Admin Dashboard main code から `fallbackKpi`, `fallbackOrders` などの固定 business data が消える。
- 取得不能時に fake data ではなく degraded state が表示される。

## Phase 7: Admin Inventory 発注推奨の random 計算廃止

### 対象ファイル

- `frontend/src/app/(admin)/admin/inventory/page.tsx`
- `frontend/src/app/api/admin/inventory/reorder-recommendations/route.ts` 新規候補
- `sales-management-service/src/main/java/com/example/skishop/sales/controller/SalesAnalyticsController.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/tool/AnalyticsToolFunctions.java` 参考

### 修正方針

`Math.random()` で週次売上見込みを作らず、既存の `sku-velocity` または在庫データから算出する。

既存 `sku-velocity` は以下を返す。

- `sku`
- `sales30`
- `sales90`
- `units30`
- `units90`
- `lastSoldAt`
- `avgPrice`

推奨計算例:

```text
weeklyEstimate = max(ceil(units30 / 30 * 7), ceil(units90 / 90 * 7), 0)
targetStock = max(lowStockThreshold, weeklyEstimate * 2)
reorderQty = max(targetStock - availableStock, 0)
```

### 実装手順

1. Next.js BFF `/api/admin/inventory/reorder-recommendations` を追加する。
2. BFF 内で以下を取得する。

- `/api/v1/inventory/low-stock?threshold=10`
- `/api/v1/admin/orders/analytics/sku-velocity`

3. `sku` で join して `weeklyEstimate`, `reorderQty`, `basis` を返す。
4. sales velocity がない SKU は `weeklyEstimate=0`, `reorderQty=max(lowStockThreshold - currentStock, 0)` とし、`basis='threshold-only'` を返す。
5. Admin Inventory page は `lowStockItems.slice(...).map()` 内の random 計算をやめ、BFF response を表示する。
6. 表示には「根拠: 30日販売数 / 90日販売数 / 閾値のみ」を含める。

### テスト

- `units30=30` の SKU は `weeklyEstimate=7` になる。
- `currentStock=3`, `weeklyEstimate=7` の場合、`targetStock=14`, `reorderQty=11` になる。
- velocity がない SKU でも random ではなく threshold basis になる。
- `Math.random()` が Admin Inventory から消える。

### 完了条件

- 在庫発注推奨の数値が同じ入力に対して常に同じになる。
- 発注推奨が実売上速度または在庫閾値を根拠にする。

## Phase 8: Payment simulated provider の本番混入防止

### 対象ファイル

- `payment-cart-service/src/main/java/com/example/skishop/payment/gateway/SimulatedPaymentGatewayClient.java`
- `payment-cart-service/src/main/java/com/example/skishop/payment/config/PaymentGatewayProperties.java`
- `payment-cart-service/src/main/resources/application.properties`
- `payment-cart-service/src/main/resources/application-test.properties`
- `payment-cart-service/src/test/java/com/example/skishop/payment/gateway/SimulatedPaymentGatewayClientTest.java`

### 修正方針

外部決済事業者の認証情報がない状態で Stripe 等の実 gateway を完全実装することはできない。修正可能な正しい対応は、simulated provider を local/test 明示設定に閉じ込め、本番 profile では simulated が既定にならないようにすることである。

### 実装手順

1. `PaymentGatewayProperties.DEFAULT_PROVIDER = "simulated"` を削除するか、`null` / 空文字を許さない validation にする。
2. `application.properties` の default `${PAYMENT_GATEWAY_PROVIDER:simulated}` をやめ、`${PAYMENT_GATEWAY_PROVIDER}` として環境変数必須にする。
3. `application-local.properties` または `application-dev.properties` を追加し、local profile のみ `skishop.payment.gateway.provider=simulated` を設定する。
4. `application-test.properties` は引き続き `simulated` を許可する。
5. `SimulatedPaymentGatewayClient` に `@Profile({"local", "dev", "test"})` または `@ConditionalOnProperty(name="skishop.payment.gateway.provider", havingValue="simulated")` を付ける。
6. 本番 profile で `provider=simulated` の場合は startup failure にする。

候補:

```java
@PostConstruct
void validateProvider() {
    if (productionProfile && "simulated".equalsIgnoreCase(provider)) {
        throw new IllegalStateException("simulated payment gateway is not allowed in production");
    }
}
```

7. 将来の実 gateway 用に `StripePaymentGatewayClient` などを追加する場合は別タスクにする。実装には provider secret, webhook secret, idempotency key 方針が必要。

### テスト

- test profile では simulated gateway が登録される。
- prod profile かつ provider 未設定では起動失敗する。
- prod profile かつ provider=simulated では起動失敗する。
- `pm_fail` は simulated provider のテスト内だけで有効。

### 完了条件

- 本番既定値として simulated が使われない。
- simulated は明示的な local/test 用実装として扱われる。

## Phase 9: Weather fallback 東京固定の廃止

### 対象ファイル

- `ai-agent-services/weather-agent/src/main/java/com/example/skishop/agent/weather/client/OpenMeteoClient.java`
- weather-agent の response DTO / controller
- weather-agent tests

### 修正方針

location が不明なときに東京の天気を正常応答として返さない。天気データが取得できない場合は `unavailable` として返す。

### 実装手順

1. `FALLBACK_LOCATION` を削除するか、dev/test 専用 property `weather.fallback-location.enabled=false` の背後に置く。
2. `geocode(String locationName)` の戻り値を `GeoLocation` から `Optional<GeoLocation>` に変更する。または `GeocodingFailedException` を投げる。
3. location 空の場合は 400 相当、geocoding 失敗の場合は 404 / 503 相当の response にする。
4. どうしても fallback を残す場合は response に `fallbackUsed=true`, `fallbackReason`, `sourceLocation` を含め、正常値と区別する。
5. 呼び出し側の `AnalyticsToolFunctions.getWeatherForecast()` は `null` / unavailable を受けた場合、季節予測の assumptions に「気象データなし」と記録して継続する。

### テスト

- 空 location で東京座標が返らない。
- 存在しない location で東京座標が返らない。
- 既知 resort は従来通り正しい座標を返す。
- geocoding API failure は unavailable として表現される。

### 完了条件

- `Tokyo (fallback)` が main code から消える、または dev/test 限定になる。
- 不明地点で東京天気を誤表示しない。

## Phase 10: Frontend UUID fallback の安全化

### 対象ファイル

- `frontend/src/lib/uuid.ts`
- UUID helper を使う frontend tests

### 修正方針

`crypto.randomUUID()` が使えない環境でも `Math.random()` ではなく `crypto.getRandomValues()` を使う。

### 実装手順

1. `crypto.randomUUID()` があれば現状通り使用する。
2. `crypto.getRandomValues()` があれば RFC 4122 v4 bytes を生成する。
3. `crypto` 自体が存在しない場合は、ID 発行を失敗させるか、server-side で発行する設計に切り替える。
4. `Math.random()` fallback は削除する。

実装例:

```ts
const bytes = new Uint8Array(16);
crypto.getRandomValues(bytes);
bytes[6] = (bytes[6] & 0x0f) | 0x40;
bytes[8] = (bytes[8] & 0x3f) | 0x80;
```

### テスト

- `crypto.randomUUID` がある場合はそれを使う。
- `crypto.randomUUID` がなく `crypto.getRandomValues` がある場合、v4 UUID 形式を返す。
- `Math.random` を spy しても呼ばれない。

### 完了条件

- `frontend/src/lib/uuid.ts` から `Math.random()` が消える。

## Phase 11: Terraform Container Apps image placeholder の修正

### 対象ファイル

- `infra/terraform/main.tf`
- `infra/terraform/variables.tf` 新規または既存
- `infra/terraform/terraform.tfvars.example` 新規候補
- deployment workflow

### 修正方針

`mcr.microsoft.com/k8se/quickstart:latest` を本番適用可能な既定値として残さない。Container Apps image は変数化し、未設定時は plan / apply 前に失敗させる。

### 実装手順

1. service ごとの image 変数を定義する。

```hcl
variable "container_images" {
  type = map(string)
  description = "Container image per service name"
  validation {
    condition = alltrue([for image in values(var.container_images) : length(trimspace(image)) > 0])
    error_message = "All container images must be set."
  }
}
```

2. `azurerm_container_app` の `image` に `var.container_images[each.key]` を使う。
3. quickstart image は削除する。
4. CI/CD で ACR push 後の digest または tag を tfvars / variable に渡す。
5. example tfvars には placeholder と分かるコメントだけを書き、実値は環境から渡す。

### テスト

- `terraform validate` が成功する。
- image 未設定時に validation error になる。
- quickstart image が `infra/terraform` から消える。

### 完了条件

- Terraform apply で quickstart コンテナが起動しない。

## Phase 12: 空値 fallback / DataAvailability の整備

### 対象ファイル

- `ai-support-service/src/main/java/com/example/skishop/ai/tool/AnalyticsToolFunctions.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/service/WeeklySummaryService.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/service/DeadStockService.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/service/ZeroHitOpportunityService.java`
- `ai-support-service/src/main/java/com/example/skishop/ai/service/SeasonalForecastService.java`
- frontend AI Analyzer 表示

### 修正方針

CircuitBreaker fallback 自体は必要であり、すべて削除するべきではない。修正すべき点は、`0` や空リストが「真の 0 件」と誤解されないよう、availability を DTO に含めて UI まで伝播することである。

### 実装手順

1. すでに `DataAvailability` を持つ DTO は、fallback 時に必ず `unavailable` を返す。
2. `DailyRevenueResult`, `TopProductsResult`, `CategoryShareResult`, `SeasonalHistoricalResult`, `WeatherForecastResult` など、availability を持たない DTO は field 追加を検討する。
3. `fallbackWeatherForecast()` の `return null` を避け、`WeatherForecastResult` に空 weeks と unavailable reason を返す。
4. `WeeklySummaryService.fetchSalesSummary()` / `fetchUserSummary()` は `Map.of()` だけでなく availability / warning を呼び出し元へ渡す設計にする。
5. frontend AI Analyzer は unavailable の場合に「データ取得不可」と表示し、0 件として扱わない。

### テスト

- sales service down 時、売上 0 と unavailable を区別できる。
- inventory service down 時、在庫 0 と unavailable を区別できる。
- weather service down 時、季節予測に assumptions / warning が残る。

### 完了条件

- 主要 analytics fallback が真の 0 件と取得不能を区別する。

## Phase 13: Localhost / example secret の扱い整理

### 対象ファイル

- `frontend/src/lib/env.ts`
- frontend BFF route 全般
- `.env.example`
- `frontend/.env.example`
- `docker-compose.yml`
- README / runbook

### 修正方針

localhost default は開発用途として許容できるが、本番 build / runtime で暗黙に使われないよう profile / env validation を強める。example secret は実 secrets ではないが、誤用防止の警告を明確化する。

### 実装手順

1. `NODE_ENV === 'production'` かつ `API_GATEWAY_URL` 未設定の場合は起動時に validation error にする。
2. `AUTH_SECRET` は production で `your-secret-here` / `dev-secret-change-in-production` を拒否する。
3. `docker-compose.yml` は local compose 用と明記する。
4. `.env.example` の dummy 値に `DO_NOT_USE_IN_PRODUCTION` コメントを追加する。

### テスト

- production env で `AUTH_SECRET=your-secret-here` の場合、build または runtime validation が失敗する。
- development env では localhost default で動作する。

### 完了条件

- 本番環境で local fallback URL / dev secret が暗黙利用されない。

## Phase 14: Admin Mail Logs 固定統計 fallback の廃止

### 対象ファイル

- `frontend/src/app/(admin)/admin/mail-logs/page.tsx`
- `frontend/src/app/api/admin/mail/stats/route.ts`
- `mailsend-service/src/main/java/com/example/skishop/mailsend/controller/MailController.java`
- `mailsend-service/src/main/java/com/example/skishop/mailsend/service/MailService.java`
- `mailsend-service/src/main/java/com/example/skishop/mailsend/dto/MailStatsResponse.java`
- `mailsend-service/src/main/java/com/example/skishop/mailsend/repository/MailLogRepository.java`

### 修正方針

メールログ画面の送信統計 chart は、stats が空の場合に固定の曜日別送信数を表示している。これは Admin Dashboard と同じく運用データを偽装するため廃止する。

backend の `/api/v1/mail/stats` は現状 aggregate のみを返すため、日別 chart が必要なら backend に `daily` を追加する。日別統計をすぐ実装しない場合は、frontend は固定 chart を出さず「日別統計は取得できません」と表示する。

### 実装手順

1. `MailStatsResponse` に以下を追加する。

```java
List<DailyMailStat> daily
```

2. `DailyMailStat` は `date`, `sentCount`, `failedCount`, `successRate` を持つ record にする。
3. `MailLogRepository` に過去 7 日分の日別集計 query を追加する。
4. `MailService.stats()` で aggregate と daily を同時に返す。
5. `frontend/src/app/api/admin/mail/stats/route.ts` は response をそのまま返す。
6. `mail-logs/page.tsx` は `data.daily` がある場合だけ chart を表示する。
7. `fallbackStats` の固定 `月:120`, `火:95` などを削除する。
8. stats 取得失敗時は chart area に degraded state を表示する。

### テスト

- mail log が存在する場合、過去 7 日分の日別送信数が返る。
- mail log が 0 件の場合、全日 0 または空 state を返し、固定値は表示しない。
- `/api/admin/mail/stats` が backend 500 の場合、frontend が固定 chart を表示しない。

### 完了条件

- `fallbackStats` と固定曜日別送信数が main code から消える。
- メールログ画面の chart が実統計または取得不可状態だけを表示する。

## Phase 15: EC ホーム固定カテゴリ fallback と footer i18n TODO の整理

### 対象ファイル

- `frontend/src/components/ec/category-section.tsx`
- `frontend/src/components/layout/ec-footer.tsx`
- `frontend/src/lib/i18n.ts` または i18n 辞書ファイル
- `frontend/src/lib/tips/index.ts`

### 修正方針

EC ホームのカテゴリ欄は、API 失敗時に固定カテゴリカードを実リンク付きで表示している。seed データと同じ ID であっても、backend が取得できない状態をユーザーに実カテゴリとして見せるのは避ける。空状態、skeleton、または degraded section に変更する。

footer の `TODO: i18n` は実装残りとして解消する。既存の `t('shared.footer.*')` パターンに合わせ、紹介文を i18n 辞書に移す。

`frontend/src/lib/tips/index.ts` の `Math.random()` は Tip 表示の UX 用であり、UUID や発注推奨のような業務データ・セキュリティ用途ではない。ただし横断検索で `Math.random()` をゼロにしたい場合は、crypto ベース helper または test で seed 可能な picker に置換する。

### 実装手順

1. `CategorySection` の `categories.length === 0` で `PlaceholderCategories` を返さない。
2. API 未取得時は skeleton / degraded message / 空 section のいずれかにする。固定 `cat-ski` などのリンク付きカードは出さない。
3. `PlaceholderCategories` は削除するか、テスト専用 fixture に移す。
4. `ec-footer.tsx` の紹介文を `shared.footer.description` などの key に移し、TODO コメントを削除する。
5. `tips/index.ts` は今回の修正対象に含めるかを決める。含める場合は `randomFloat()` helper を作り、`crypto.getRandomValues` が使える環境ではそれを使う。テストでは deterministic RNG を注入できる設計にする。

### テスト

- `/api/dashboard/home` が失敗しても固定カテゴリカードが表示されない。
- categories が返る場合は実カテゴリだけが表示される。
- footer に TODO コメントが残らず、辞書 key から紹介文が表示される。
- tips の乱択を修正対象に含めた場合、`Math.random()` が `frontend/src/lib/tips/index.ts` から消える、または横断検証の除外対象に明記される。

### 完了条件

- EC ホームで API 失敗時に固定カテゴリを実データ風に表示しない。
- footer の i18n TODO が消える。
- Tip ランダム表示を修正対象外にする場合は、その理由が横断検証の除外対象に記録されている。

## 修正対象外として扱う項目

以下は再検索で検出されるが、本計画では未実装・モック修正対象から除外する。横断検証で残存しても、理由付きで分類する。

| 項目 | 判断 |
| --- | --- |
| `src/test/**` の Mockito / MockRestServiceServer / MSW | テスト用 mock であり production mock ではない。 |
| `load-tests/**` の `Math.random()` | 負荷試験データのランダム化であり、本番ロジックではない。 |
| UI component の `placeholder` prop / input placeholder | HTML 入力補助文言であり、未実装 placeholder ではない。 |
| `return null` による optional rendering / parser fallback | React の非表示制御や optional parse の正常表現は対象外。API fallback として `null` を返し true zero と混同する箇所のみ Phase 12 で扱う。 |
| `frontend/src/lib/tips/index.ts` の Tip ランダム選択 | 業務データ・セキュリティ用途ではない。全 `Math.random()` 排除方針にする場合のみ Phase 15 で修正する。 |

## 横断検証計画

### 検索確認

`rg` がない環境では以下を `grep_search` または `git grep -n` で確認する。

対象 pattern:

```text
TODO|FIXME|未実装|現状スタブ|placeholder|dummy-|MOCK_MODELS|Math.random|quickstart:latest|provider=simulated|DEFAULT_PROVIDER = "simulated"|SKU-SEM
```

除外対象:

- `**/target/**`
- `**/.next/**`
- `**/node_modules/**`
- `**/coverage/**`
- `src/test/**` の Mockito / MockRestServiceServer
- load test / seed data の random

### Maven 検証

Java 変更後は focused module から実行する。

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/microsoft-21.jdk/Contents/Home mvn -pl sales-management-service -am test -Dtest=InternalSalesControllerTest,OrderRepositoryTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/microsoft-21.jdk/Contents/Home mvn -pl coupon-service -am test -Dtest=InternalCouponControllerTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/microsoft-21.jdk/Contents/Home mvn -pl ai-support-service -am test -Dtest=SearchServiceTest,ModelManagementServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/microsoft-21.jdk/Contents/Home mvn -pl payment-cart-service -am test -Dtest=PaymentGatewayPropertiesTest,SimulatedPaymentGatewayClientTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/microsoft-21.jdk/Contents/Home mvn -pl mailsend-service -am test -Dtest=MailServiceTest,MailControllerTest
```

### Frontend 検証

```bash
cd frontend
npm run lint
npm run test -- --run
npm run build
```

必要に応じて Playwright で以下を確認する。

- `/search?q=...` で semantic route が 404 にならない。
- Admin AI で mock model が表示されない。
- Admin Dashboard で backend down 時に fake KPI が表示されない。
- Admin Inventory で発注推奨値が reload ごとに変化しない。
- Admin Mail Logs で stats API 失敗時に固定曜日別 chart が表示されない。
- EC ホームで dashboard API 失敗時に固定カテゴリカードが表示されない。

### Infra 検証

```bash
cd infra/terraform
terraform fmt -check
terraform validate
```

## 完了判定チェックリスト

- [ ] `InternalSalesController.getSalesCount()` が DB 集計を返す。
- [ ] coupon 未存在時に dummy coupon が返らない。
- [ ] `/api/search/semantic` と `/api/search/feedback` が実装され、backend へ proxy される。
- [ ] search feedback が ai-support-service で永続化され、ログ返却のみで終わらない。
- [ ] search semantic fallback で固定 SKU / 固定在庫が生成されない。
- [ ] catalog category name が API から取得される。
- [ ] catalog sort が URL query と商品取得に反映される。
- [ ] Admin AI 画面から `MOCK_MODELS` が消える。
- [ ] Admin AI training progress が status API 由来になる。
- [ ] Admin AI BFF から存在しない `/api/v1/ai/models/**` 呼び出しが消える。
- [ ] Admin Dashboard から固定 KPI / 固定注文 / 固定低在庫 fallback が消える。
- [ ] Admin Mail Logs から固定曜日別 stats fallback が消える。
- [ ] Admin Inventory の発注推奨から `Math.random()` が消える。
- [ ] EC ホームの固定カテゴリ fallback が消え、footer i18n TODO が解消される。
- [ ] payment gateway の simulated provider が production default で使われない。
- [ ] weather-agent が geocoding 失敗時に東京天気を正常値として返さない。
- [ ] frontend UUID fallback が `crypto.getRandomValues()` ベースになる。
- [ ] Terraform Container Apps image が quickstart placeholder ではなく変数由来になる。
- [ ] fallback DTO / UI が真の 0 件と取得不能を区別する。
- [ ] production env で localhost default / dev secret が暗黙利用されない。

## 実装時の注意点

- すべてを 1 回で直すと backend / frontend / infra の blast radius が大きい。Phase 1 から Phase 4 を先に実施し、業務ロジック上の誤データ返却を止める。
- Admin Dashboard の fake data 削除は UI 表示に影響が大きい。ユーザー体験として空白にせず、明示的な degraded state を用意する。
- payment の real provider 化は外部決済仕様と秘密情報が必要なため、今回の計画では「本番で simulated が暗黙利用されない」ことを修正ゴールにする。
- fallback を削除しすぎると可用性が落ちる。削除対象は「偽データとして見える fallback」であり、障害時継続の deterministic fallback は availability を付けて残す。
