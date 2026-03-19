# front-end-need.md 再レビュー指摘事項

## business-analyst 再レビューレポート

### サマリー

- 判定: ✅ Pass
- 指摩件数: Critical: 0 (3件対応済), High: 0 (7件対応済), Medium: 5, Low: 2
- レビュー対象: [front-end-need.md](front-end-need.md)
- レビュー日時: 2026-03-19
- レビュー深度: 全体レビュー（バックエンド実装コードとの照合含む）

---

## 要件品質スコアカード

| # | 評価軸 | 評価 | 備考 |
|---|---|---|---|
| 1 | 要件の完全性（機能・非機能・制約） | ✅ | Critical 3件・High 7件全て対応済。未文書化 API の文書化完了 |
| 2 | 要件品質（明確性・測定可能性） | ⚠️ | 一部画面でビジネス価値・受入基準が欠落 |
| 3 | ユーザーストーリー品質（INVEST） | ✅ | 画面単位で独立・テスト可能に分割されている |
| 4 | 受入基準の品質 | ⚠️ | EC-RETURNS, EC-COUPONS, EC-ORDER-DETAIL に受入基準なし |
| 5 | 要件間の整合性 | ✅ | MoSCoW Phase 1 に EC-PW-RESET 昇格済み、矛盾解消 |
| 6 | スコープ管理 | ✅ | Won't Have が明確。ただしスコープ内に実現不可能な要件あり |
| 7 | ステークホルダー分析 | ✅ | 4 ペルソナが適切に定義されている |
| 8 | ビジネスリスク評価 | ✅ | 季節性ビジネスリスクをリスクマトリクスに追加済み |
| 9 | ガバナンス基準適合 | ✅ | ドキュメント構成は適切 |
| 10 | ROI・ビジネスケース | ✅ | トランザクションメール・レコメンド履歴等の CVR 直結施策を追加済み |

---

## 指摘事項一覧

### Critical（0件 — 全件対応済み）

> **CRIT-01** (商品 PUT/DELETE 未実装): ✅ 対応済み — `ProductController` に `PUT /products/{id}` と `DELETE /products/{id}`（論理削除）を実装。`ProductService` に `updateProduct()` / `deleteProduct()` を追加、テスト 9件追加済み
>
> **CRIT-02** (Gateway categories ルート未定義): ✅ 対応済み — `RouteConfig.java` の inventory-management-service ルートに `"/api/v1/categories/**"` を追加
>
> **CRIT-03** (CampaignController アクセス制御): ✅ 対応済み — `SecurityConfig` で `GET /api/v1/campaigns/active` を `permitAll()` に設定、`CampaignController` の `getActiveCampaigns()` に `@PreAuthorize("permitAll()")` を付与しクラスレベル制約をオーバーライド

---

### High（7件）

#### HIGH-01: トランザクションメール要件の欠落

- **対象**: 全体
- **指摘内容**: **トランザクションメール（注文確認、発送通知、パスワードリセット、メール認証）の要件が完全に欠落**。EC サイトにおいて注文後のメール確認はユーザー信頼性の根幹
- **ビジネスインパクト**: 注文完了後にメールが届かないと、ユーザーは注文が成功したか不安になる。CS 問い合わせ急増、カゴ落ち率上昇
- **推奨対応**: セクション 4 に「4.4 トランザクションメール要件」を追加。最低限：注文確認、発送通知、パスワードリセットリンク、メール認証リンク
- **✅ 対応済み**: `front-end-need.md` にセクション 4.4（トランザクションメール要件）を追加。バックエンド `mailsend-service` の詳細設計書（[mailsend-service-design.md](design-docs/mailsend-service-design.md)）を作成。Azure Communication Services Email を利用したイベント駆動メール配信サービスとして設計。API マトリクス・管理画面（ADM-MAIL-LOGS）も追加済み

#### HIGH-02: パスワードリセットの優先度矛盾

- **対象**: セクション 9 MoSCoW
- **指摘内容**: **EC-PW-RESET（パスワードリセット）が Phase 2（Should Have）に分類されている**が、ユーザーがパスワードを忘れた場合にログイン不能になる。Phase 1 ではログイン機能を提供するため、パスワードリセットは必須
- **ビジネスインパクト**: 登録ユーザーの約 10-15% がパスワードリセットを使用する（業界平均）。この導線がないと CS コストが増大し、顧客離脱が発生
- **推奨対応**: EC-PW-RESET を Must Have（Phase 1）に昇格- **✅ 対応済み**: `front-end-need.md` セクション 9 MoSCoW の Phase 1 Must Have に `EC-PW-RESET` を昇格済み。「EC-LOGIN, EC-REGISTER, EC-VERIFY, EC-PW-RESET（認証・アカウント確認）」として Phase 1 に包含
#### HIGH-03: 未文書化 API エンドポイント

- **対象**: ADM-INVENTORY, API マトリクス（セクション 6）
- **指摘内容**: **バックエンドに存在する以下のエンドポイントが要件定義書に記載されていない**:
  1. `GET /api/v1/inventory/low-stock?threshold=` — 低在庫商品一覧
  2. `POST /api/v1/inventory/batch` — バッチ在庫取得
  3. `PUT /api/v1/prices/{productId}` — 商品価格更新
- **ビジネスインパクト**: 低在庫アラート（FR-AINV-06）の実装根拠が不明確。価格更新機能がフロントエンドで利用できない
- **推奨対応**: API マトリクス（セクション 6）にこれらのエンドポイントを追加し、対応画面を明記。特に `low-stock` は ADM-DASH, ADM-INVENTORY で活用
- **✅ 対応済み**: `front-end-need.md` に以下を追加済み:
  - FR-ADASH-03 に `GET /api/v1/inventory/low-stock?threshold=10` API を追加
  - FR-AINV-06 に `GET /api/v1/inventory/low-stock?threshold=` API を追加
  - FR-AINV-08（バッチ在庫取得: `POST /api/v1/inventory/batch`）を新規追加
  - FR-AINV-09（商品価格一括更新: `PUT /api/v1/prices/:productId`）を新規追加
  - FR-APRD-04 に `PUT /api/v1/prices/:productId` API を追加
  - API マトリクスに `GET /inventory/low-stock`, `POST /inventory/batch`, `PUT /prices/:productId` を追加

#### HIGH-04: ゲストカート戦略の未定義

- **対象**: EC-CART
- **指摘内容**: **ゲスト→会員のカートマージ戦略が未定義**。現在の要件では EC-CART は「認証: 必要」だが、商品詳細でカートに追加する際に初めて認証を要求される。ゲストがブラウザで仮カートを持ち、ログイン時にマージするパターンが未考慮
- **ビジネスインパクト**: EC サイトの業界データでは、カートに入れる前のログイン要求はカート放棄率を 25-35% 上昇させる
- **推奨対応**: ゲストカート（localStorage / sessionStorage）の仕様を定義し、ログイン時のマージロジックを要件化

#### HIGH-05: 季節性ビジネスリスクの未評価

- **対象**: 全体
- **指摘内容**: **スキーショップ固有の季節性ビジネスリスクが未評価**。スキー用品は 10 月〜3 月がピークシーズン、4 月〜9 月はオフシーズン。トップページ・レコメンド・キャンペーンの季節切替戦略が不在
- **ビジネスインパクト**: ピークシーズンの売上集中（年間売上の 70-80%）に対するフロントエンド対策がないと、最もクリティカルな期間に UX が最適化されない
- **推奨対応**:
  1. ホームページのシーズン別レイアウト切替要件を追加
  2. オフシーズン用の「早期予約」「メンテナンス用品」訴求セクション
  3. リスクマトリクスにシーズンピーク時の負荷対策を追加
- **✅ 対応済み**: `front-end-need.md` に以下を追加済み:
  - EC-HOME に FR-HOME-07（シーズン別ヒーローバナー自動切替: 10-3 月ウィンター / 4-9 月オフシーズン）を追加
  - EC-HOME に FR-HOME-08（オフシーズン訴求セクション: 早期予約割引・メンテナンス用品・クリアランスセール）を追加
  - 受入基準 AC-HOME-05（バナー季節切替）、AC-HOME-06（オフシーズン表示優先度）を追加
  - リスクマトリクス #6（シーズンピーク負荷集中: CDN・オートスケール・ロードテスト策）を追加

#### HIGH-06: レコメンド履歴 API の未掲載

- **対象**: セクション 6 API マトリクス
- **指摘内容**: **`GET /api/v1/recommendations/history/{userId}`（レコメンド履歴）が API マトリクスに未掲載**。`RecommendationController` に実装済みだがフロントエンド側で消費されていない
- **ビジネスインパクト**: パーソナライゼーション精度の向上機会を逸失。「過去に見た商品」セクションはリピート購入促進の重要施策
- **推奨対応**: EC-HOME または EC-PROFILE に「閲覧・レコメンド履歴」セクションを追加
- **✅ 対応済み**: `front-end-need.md` に以下を追加済み:
  - EC-PROFILE に FR-PROF-05（レコメンド・閲覧履歴セクション: `GET /api/v1/recommendations/history/:userId`）を追加
  - API マトリクスに `GET /recommendations/history/:userId` → EC ✅ を追加

#### HIGH-07: 管理ダッシュボードの注文一覧 API 未指定

- **対象**: ADM-DASH FR-ADASH-04
- **指摘内容**: **FR-ADASH-04（最新注文一覧）で使用する API が未指定**。管理者向けの「全ユーザーの最新注文」を取得するエンドポイントが明確でない。`GET /api/v1/orders/customer/:id` は特定顧客の注文のみ
- **ビジネスインパクト**: 管理ダッシュボードの主要 KPI が表示できない可能性
- **推奨対応**: バックエンドに `GET /api/v1/admin/orders?sort=createdAt,desc&size=10`（管理者用全注文一覧）の追加を検討、または既存の `analytics/dashboard` API で対応可能か確認
- **✅ 対応済み**: バックエンド・フロントエンド両方を対応済み:
  - **バックエンド**: `OrderService.getAllOrders(Pageable)` と `GET /api/v1/admin/orders`（`@PreAuthorize("hasRole('ADMIN')")`、`createdAt` 降順、ページネーション対応）を `sales-management-service` に実装
  - **Gateway**: `RouteConfig.java` に `/api/v1/admin/orders/**` ルートを追加
  - **テスト**: `OrderServiceTest$GetAllOrders` を追加済み（BUILD SUCCESS）
  - **フロントエンド**: FR-ADASH-04 に `GET /api/v1/admin/orders?sort=createdAt,desc&size=10`、FR-AORD-01 に `GET /api/v1/admin/orders` を追加。API マトリクスに `GET /admin/orders` → Admin ✅ を追加

---

### Medium（5件）

#### MED-01: EC-RETURNS の受入基準欠落

- **対象**: EC-RETURNS
- **指摘内容**: **EC-RETURNS（返品管理）にビジネス価値と受入基準が未定義**
- **ビジネスインパクト**: 返品体験が曖昧なまま実装されると、CS 問い合わせ増加やユーザー不満につながる
- **推奨対応**: 受入基準を追加。例: 返品申請後 24 時間以内にステータスが「受付済み」に変わる、返品理由は必須入力、等

#### MED-02: EC-COUPONS の受入基準欠落

- **対象**: EC-COUPONS
- **指摘内容**: **EC-COUPONS にビジネス価値と受入基準が未定義**
- **ビジネスインパクト**: クーポン利用体験の品質基準が不明確
- **推奨対応**: ビジネス価値（例: クーポン利用による購入促進で客単価 X% 向上）と受入基準（クーポン有効期限切れはグレーアウト表示、等）を追加

#### MED-03: EC-ORDER-DETAIL の受入基準欠落

- **対象**: EC-ORDER-DETAIL
- **指摘内容**: **EC-ORDER-DETAIL にビジネス価値と受入基準が未定義**
- **ビジネスインパクト**: 注文詳細画面の品質基準が不明確。キャンセル可能条件の表示ロジック等
- **推奨対応**: 受入基準を追加。例: 未発送注文のみキャンセルボタン表示、配達完了後 14 日以内のみ返品申請ボタン表示、等

#### MED-04: 商品詳細のソーシャルシェア機能

- **対象**: EC-DETAIL
- **指摘内容**: **商品詳細ページにソーシャルシェアボタンがない**。スキーは趣味性が高く、仲間内での情報共有が購買に直結する
- **ビジネスインパクト**: オーガニック流入の機会損失。スキーコミュニティでの自然な拡散効果が得られない
- **推奨対応**: FR-DET-07 として SNS シェアボタン（LINE、X(Twitter)、Instagram）を追加

#### MED-05: 非機能要件のキャッシュ・画像最適化目標の欠落

- **対象**: セクション 5 非機能要件
- **指摘内容**: **非機能要件にキャッシュ戦略・CDN・画像最適化の数値目標がない**
- **ビジネスインパクト**: 商品画像が多い EC サイトで画像最適化戦略がないとパフォーマンス目標（LCP 2.5s）達成が困難
- **推奨対応**: 画像フォーマット（WebP/AVIF）、リサイズ戦略、CDN キャッシュ TTL の目標値を追加

---

### Low（2件）

#### LOW-01: 注文履歴からの再注文機能

- **対象**: EC-ORDERS
- **指摘内容**: **注文履歴からの「再注文」機能が未定義**。消耗品（ワックス、グローブ等）のリピート購入を促進する重要機能
- **ビジネスインパクト**: リピート購入の導線が弱く、スキー消耗品の継続購入率に影響
- **推奨対応**: Could Have（Phase 3）として「再注文」ボタン（カートに同一商品を追加）の追加を検討

#### LOW-02: 最近チェックした商品セクション

- **対象**: EC-HOME
- **指摘内容**: **「最近チェックした商品」セクションが未定義**。閲覧履歴に基づく表示はコンバージョン率向上に寄与
- **ビジネスインパクト**: 実装コストは低いが CVR 向上効果が見込める
- **推奨対応**: Should Have として追加。localStorage ベースで実装可能（バックエンド不要）

---

## エスカレーション事項

| # | 区分 | 内容 | エスカレーション理由 |
|---|------|------|---------------------|
| 1 | ⚠️ 要人間判断 | ゲストカート戦略の決定（localStorage vs サーバーサイドセッション vs Cookie） | UX 設計とバックエンド実装の両方に影響する横断的判断 |
| 2 | ⚠️ 要人間判断 | シーズン別 UX 戦略（ピーク/オフシーズンのコンテンツ切替方針） | ビジネスオーナーのマーチャンダイジング方針に依存 |

---

## 要件間矛盾・競合一覧

| # | 要件 A | 要件 B | 矛盾・競合の内容 | 推奨解決方法 |
|---|--------|--------|-----------------|-------------|
| 1 | EC-CART「認証: 必要」 | AC-DET-04「未ログインでカート追加→ログインへリダイレクト」 | カート画面に認証が必須だが、ゲスト状態でのカート体験（仮カート）の定義がない。ログインを強制する時点が早すぎると離脱率が上がる | ゲスト仮カート→ログイン時マージの仕様を追加 |
| 2 | MoSCoW Phase 1「EC-LOGIN, EC-REGISTER」 | MoSCoW Phase 2「EC-PW-RESET」 | ~~ログイン機能を Phase 1 で提供するなら、パスワードリセットも同時に必要~~ | ✅ 解消済み — EC-PW-RESET を Phase 1 に昇格 |

---

## 推奨対応（優先順）

1. ~~**即座対応（Phase 1 ブロッカー）**~~: ✅ 完了 — 商品 PUT/DELETE 追加、Gateway categories ルート追加、CampaignController アクセス制御修正
2. ~~**Phase 1 スコープ修正**~~: ✅ 完了 — EC-PW-RESET を Must Have に昇格済み、トランザクションメール要件追加済み
3. **ゲストカート設計**: ログイン前のカート保持とマージ戦略を要件化（カート放棄率に直結）
4. ~~**季節性戦略の追加**~~: ✅ 完了 — EC-HOME にシーズン別バナー切替・オフシーズン訴求セクションを追加、リスクマトリクスにシーズンピーク負荷集中リスクを追加
5. ~~**欠落 API の文書化**~~: ✅ 完了 — `low-stock`, `inventory/batch`, `prices/{productId}`, `recommendations/history`, `admin/orders` を全て API マトリクス・画面要件に追加済み
6. **受入基準の補完**: EC-RETURNS, EC-COUPONS, EC-ORDER-DETAIL に具体的な受入基準を追加
