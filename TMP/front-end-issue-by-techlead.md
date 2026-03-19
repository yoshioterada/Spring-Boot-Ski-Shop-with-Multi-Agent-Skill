# front-end-need.md アーキテクトレビュー指摘事項

## architect レビューレポート

### サマリー

- 判定: ⚠️ Warning
- 指摘件数: Critical: 1, High: 6, Medium: 7, Low: 4
- レビュー対象: [front-end-need.md](front-end-need.md)
- レビュー日時: 2026-03-19
- レビュー深度: アドホック（フロントエンド要件定義書のアーキテクチャ観点レビュー）

### 前提条件の確認

- [x] ソースコードへのアクセス: OK（バックエンド 9 サービス + Gateway + common-lib）
- [x] 依存関係定義（pom.xml）: OK
- [x] 設計ドキュメント: あり（design-docs/, structure.md）
- [ ] フロントエンド技術スタック未確定（エスカレーション事項 #2 に記載あり）

---

## アーキテクチャ健全性スコアカード

| # | 評価軸 | 評価 | 備考 |
|---|---|---|---|
| 1 | 構造設計（レイヤー・依存関係） | ⚠️ | フロントエンド-バックエンド間の API 契約定義が不十分。エラーレスポンス仕様の記載なし |
| 2 | 設計原則（SOLID/DRY/KISS） | ✅ | 画面単位で分割されており、責務は明確 |
| 3 | API 設計 | ⚠️ | 既存実装との不整合、ページネーション契約未定義、レスポンス型の記載なし |
| 4 | データアーキテクチャ | ⚠️ | フロントエンド状態管理戦略の欠如、BFF パターンの検討なし |
| 5 | 横断的関心事 | ❌ | エラーハンドリング契約・認証トークン管理・API タイムアウトの具体仕様が欠落 |
| 6 | 非機能要件（可用性・信頼性・観測可能性） | ⚠️ | サーキットブレーカー fallback 時の UI 記述なし、フロントエンド監視戦略なし |
| 7 | 障害モード耐性 | ❌ | バックエンドサービス障害時のフロントエンド縮退動作が未定義 |
| 8 | スケーラビリティ | ⚠️ | CDN・静的アセット戦略が曖昧、SSR/SSG の具体方針未確定 |
| 9 | 統合パターン | ⚠️ | API 呼び出しの並列化・依存関係、BFF の検討なし |
| 10 | 進化可能性 | ✅ | MoSCoW による段階デリバリーが適切 |
| 11 | 技術スタック整合性 | ✅ | バックエンド API との整合は概ね取れている |
| 12 | アンチパターン | ⚠️ | Chatty API パターンの懸念あり（EC-HOME で 3-5 API 並列呼び出し） |

---

## 指摘事項一覧

### Critical（1件）

#### ARCH-CRIT-01: エラーレスポンス契約（RFC 7807）がフロントエンド向けに未定義 ✅ 対応済み

- **対象**: セクション 4.3, 全体
- **指摘内容**: バックエンドは `GlobalExceptionHandler` で RFC 7807 `ProblemDetail` 形式（`type`, `title`, `status`, `detail`, `errorCode`, `timestamp`, バリデーションエラー時は `errors` 配列）を返すが、この契約がドキュメントに記載されていない。フロントエンド開発者はエラーレスポンスのパース方法を知らないまま実装することになる
- **技術的根拠**: エラーハンドリングの不整合はユーザー体験の根幹に関わる。各サービスの `errorCode` 体系（`GW-5002` 等）を把握しないと、エラー種別ごとの適切な UI 表示が不可能
- **推奨対応**: セクション 4.2「エラーハンドリング」に RFC 7807 エラーレスポンスの JSON スキーマ、errorCode 一覧、バリデーションエラー時の `errors` 配列構造を追記
- **✅ 対応内容**:
  - `front-end-need.md` セクション 4.2.1 に RFC 7807 エラーレスポンス契約を追加（JSON スキーマ、type URI 一覧、全サービスの errorCode 一覧、バリデーション errors 配列構造、フロントエンド実装ガイドライン）
  - Gateway `FallbackController` を RFC 7807 形式に修正（技術的負債 #2 も同時に解消）
  - テストを RFC 7807 形式に更新（24テスト全パス、BUILD SUCCESS）

**バックエンド実装の参考情報**:

```json
// 通常エラー（404, 403, 401, 422, 503 等）
{
  "type": "https://skishop.example.com/errors/not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "User with ID 123 was not found",
  "instance": "/api/v1/users/123",
  "errorCode": "RESOURCE_NOT_FOUND",
  "timestamp": "2026-03-19T12:00:00Z"
}

// バリデーションエラー（400）
{
  "type": "https://skishop.example.com/errors/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "detail": "入力内容に誤りがあります（2件）",
  "errors": [
    { "field": "email", "message": "有効なメールアドレスを入力してください", "rejectedValue": "invalid" },
    { "field": "name", "message": "名前は必須です", "rejectedValue": null }
  ],
  "timestamp": "2026-03-19T12:00:00Z"
}

// Gateway サーキットブレーカー（503）※ 現在 RFC 7807 非準拠
{
  "timestamp": "2026-03-19T12:00:00Z",
  "status": 503,
  "error": "Service Unavailable",
  "code": "GW-5002",
  "message": "サービスが一時的に利用できません: payment",
  "path": "/api/v1/payments/intent"
}
```

---

### High（6件）

#### ARCH-HIGH-01: API レスポンス型（DTO 構造）がどの画面要件にも記載されていない ✅ 対応済み

- **対象**: セクション 6, 全体
- **指摘内容**: 例: `AuthResponse` は `{userId, email, firstName, lastName, role, accessToken, refreshToken, expiresAt}` を返すが、フロントエンドはどのフィールドを受け取るか不明。`OrderResponse`, `ProductResponse` 等の主要 DTO のフィールド一覧がないと画面実装が進められない
- **技術的根拠**: フロントエンド開発者は Swagger UI で個別確認可能だが、要件定義書として API 契約の最低限の記載がないと進行がブロックされる。特に EC-CHECKOUT フローのような複数 API を連携する画面で致命的
- **推奨対応**: 主要画面ごとに利用 API のリクエスト/レスポンス型の概要を追記。最低限、認証レスポンス（`AuthResponse`）、注文レスポンス（`OrderResponse`）、商品レスポンス（`ProductResponse`）の構造を記載。詳細は Swagger リンクで参照可とする
- **✅ 対応内容**:
  - `front-end-need.md` セクション 6.1「主要 API レスポンス型リファレンス」を追加
  - 全9サービスの Swagger UI リンク一覧を掲載
  - 8カテゴリ（認証、ユーザー、商品・カテゴリ、注文、カート・決済、ポイント、キャンペーン・クーポン、AI・検索・レコメンド）の主要 DTO 構造を記載
  - リクエスト型（LoginRequest, RegisterRequest, CreateOrderRequest, CreatePaymentIntentRequest）のバリデーションルール含めて記載
  - レスポンス型（AuthResponse, UserResponse, ProductResponse, CategoryResponse, OrderResponse, CartResponse, PaymentResponse, UserTierResponse, PointTransactionResponse, CampaignResponse, CouponResponse, ChatMessageResponse, RecommendationResponse, SearchResponse）の全フィールドを記載

**バックエンド実装の参考情報（主要 DTO）**:

```
AuthResponse:
  userId: UUID, email: String, firstName: String, lastName: String,
  role: String, accessToken: String, refreshToken: String, expiresAt: Instant

JWT Claims:
  sub: userId (UUID), email: String, role: String, type: "access" | "refresh"
  Access Token 有効期限: 60分（設定可）, Refresh Token: 7日間
```

#### ARCH-HIGH-02: ページネーションレスポンス契約が未定義 ✅ 対応済み

- **対象**: セクション 5, 全体
- **指摘内容**: Spring Data の `Page<T>` レスポンスは `{content, pageable, totalElements, totalPages, size, number, ...}` という独自構造を返す。さらに `PageImpl` のシリアライズは不安定（テストログに警告: `Serializing PageImpl instances as-is is not supported`）。`PagedModel`（VIA_DTO モード）への移行が推奨されているが、現在は `PageImpl` を直接返している
- **技術的根拠**: フロントエンドがページネーション UI を実装するには、レスポンスの `totalElements`, `totalPages`, `number` 等のフィールド名を正確に把握する必要がある。Spring のデフォルト構造は破壊的変更のリスクがある
- **推奨対応**: ページネーションレスポンスの JSON 構造を明示的に文書化。バックエンド側で `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` の採用を検討し、安定した構造を保証
- **✅ 対応内容**:
  - `common-lib` に `PageSerializationConfig` を追加。`@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` を全 Servlet ベースサービスに自動適用（技術的負債 #1 も同時に解消）
  - `front-end-need.md` セクション 4.2.2「ページネーションレスポンス契約」を追加（VIA_DTO 形式の JSON スキーマ、リクエストパラメータ、全16 API のページネーション対応一覧、EC/管理画面の実装ガイドライン）
  - 全テスト合格、BUILD SUCCESS

#### ARCH-HIGH-03: バックエンドサービス障害時のフロントエンド縮退動作が未定義 ✅ 対応済み

- **対象**: セクション 4.2
- **指摘内容**: Gateway にはサーキットブレーカー + フォールバック（503 `Service Unavailable` + `GW-5002` コード）が実装済み。しかし、フロントエンドが 503 を受けた時にどう振る舞うかの要件がない
- **技術的根拠**: EC-HOME は recommendations(AI)、categories(inventory)、campaigns(coupon) の 3 サービスに依存。AI サービス障害時にホームページ全体が表示不能になるのは許容できない
- **推奨対応**: 各画面の API 依存度を分析し、サービス障害時の縮退 UI を定義
- **✅ 対応内容**:
  - `front-end-need.md` セクション 4.2.3「サービス障害時の縮退 UI（Graceful Degradation）」を追加
  - 基本原則（Promise.allSettled、必須/任意 API の区別、自動リトライ、手動リトライ導線）を定義
  - EC サイト縮退マトリクス（22 API × 画面の必須/任意と縮退動作）を網羅的に記載
  - 管理画面の縮退マトリクス（6画面）を記載
  - 縮退 UI コンポーネント仕様（セクション非表示、エラープレースホルダー、フォールバックコンテンツ）を定義
  - EC-HOME の縮退実装例（Promise.allSettled パターン）を記載

**推奨する縮退戦略の例**:

| 画面 | 障害サービス | 縮退動作 |
|------|-------------|---------|
| EC-HOME | ai-support-service | トレンド・レコメンドセクション非表示。カテゴリ・検索は正常表示 |
| EC-HOME | coupon-service | キャンペーンバナー非表示。他セクション正常表示 |
| EC-CHECKOUT | payment-cart-service | 「現在決済を処理できません」エラー + リトライ導線 |
| EC-CART | point-service | ポイント残高「取得中...」表示。カートの基本操作は可能 |
| EC-AI-CHAT | ai-support-service | 「AI チャットは現在ご利用いただけません」+ 問い合わせフォームへの導線 |

#### ARCH-HIGH-04: JWT トークンのライフサイクル具体仕様が不足 ✅ 対応済み

- **対象**: セクション 4.3
- **指摘内容**: 「Access Token を httpOnly Cookie またはメモリに保存」とあるが、「またはメモリ」では開発者が任意選択してしまう。現在のバックエンド実装では Access Token 有効期限=60 分（設定可）、Refresh Token=7 日間。JWT には `{sub: userId, email, role, type: "access"}` が含まれる
- **技術的根拠**: httpOnly Cookie とメモリでは XSS 耐性が根本的に異なる。方針が曖昧だとセキュリティホールの原因になる。また Access Token の `expiresAt` フィールドを使った自動リフレッシュのタイミング計算仕様が必要
- **推奨対応**: トークン保存方式を確定（httpOnly Cookie 推奨）。Access Token 有効期限・Refresh Token 有効期限を明記。自動リフレッシュの具体ロジック（`expiresAt` の 5 分前にバックグラウンドリフレッシュ）を仕様化。401 受信時のリフレッシュ→リトライフローを図示
- **✅ 対応内容**:
  - `front-end-need.md` セクション 4.3 を全面改訂、4.3.1「JWT トークンライフサイクル仕様」を追加
  - トークン保存方式を確定: Access Token → インメモリ変数、Refresh Token → httpOnly Cookie（`Secure`, `SameSite=Strict`）
  - トークン仕様表（有効期限、保存先、送信方法、JWT Claims、XSS 耐性）を記載
  - 認証フローを 6 パターン図示: ログイン、API リクエスト、自動リフレッシュ（expiresAt 5分前）、401 リトライフロー（キューイング含む）、ログアウト、ページリロード復元
  - セキュリティ要件（XSS/CSRF/トークン漏洩/並行タブ対策）を明記

**推奨する認証フロー**:

```
1. ログイン成功 → AuthResponse の accessToken / refreshToken を取得
2. accessToken を Authorization: Bearer ヘッダーで送信
3. expiresAt の 5分前にバックグラウンドで POST /api/v1/auth/refresh を実行
4. 401 受信時 → refreshToken で再認証を試みる
5. refreshToken も期限切れ → ログイン画面にリダイレクト
6. refreshToken で POST /auth/refresh 時に type="refresh" でない場合は 401（バックエンド実装済み）
```

#### ARCH-HIGH-05: 画面ロード時の API 呼び出し順序・並列化戦略が未定義 ✅ 対応済み

- **対象**: EC-HOME, EC-CHECKOUT
- **指摘内容**: EC-HOME は最低 3 API（categories, trending, campaigns/active）+ ログイン時は追加で recommendations/:userId を呼ぶ。EC-CHECKOUT はさらに複雑（cart, points/balance, coupons/validate, payments/intent, orders）。呼び出し順序と依存関係が不明
- **技術的根拠**: 逐次呼び出しでは LCP 2.5 秒の非機能要件を達成困難。並列化可能な API と直列が必須な API（payments/intent → payments/process → orders）を明示する必要がある
- **推奨対応**: 主要画面ごとに API シーケンス図（並列/直列）を追加。特に EC-HOME（全並列可）と EC-CHECKOUT（cart 取得 → intent 作成 → process → order の直列チェーン）。BFF（Backend for Frontend）パターンの採用も検討
- **✅ 対応内容**:
  - `front-end-need.md` セクション 4.2.4「画面ロード時の API 呼び出し戦略（並列化・シーケンス）」を追加
  - 9画面の API 呼び出しパターンを網羅: EC-HOME, EC-CATALOG, EC-DETAIL, EC-CART, EC-CHECKOUT, EC-ORDERS/ORDER-DETAIL, EC-AI-CHAT, EC-POINTS, ADMIN-DASHBOARD
  - EC-CHECKOUT の 6 ステップ直列フロー（Step 3→4→5 の依存チェーン）を図示。各ステップの成功/失敗時の動作を明記
  - 並列/直列の区別、必須/任意 API の区別、ユーザー操作トリガーの API を分離して記載

**推奨する EC-HOME API 呼び出しパターン**:

```
並列実行（Promise.all / Promise.allSettled 相当）:
  ├── GET /api/v1/categories
  ├── GET /api/v1/recommendations/trending
  ├── GET /api/v1/campaigns/active
  └── GET /api/v1/recommendations/:userId  ※ログイン済み時のみ

各 API が独立しているため、1 つが失敗しても他は正常表示可能
→ Promise.allSettled を使用し、失敗した API のセクションのみ非表示
```

**推奨する EC-CHECKOUT API 呼び出しパターン**:

```
Step 1（並列）: GET /cart + GET /points/balance/:userId
Step 2（ユーザー操作後）: POST /coupons/validate ※クーポン入力時のみ
Step 3（決済実行）: POST /payments/intent
Step 4（決済処理）: POST /payments/:id/process ※Step 3 の結果に依存
Step 5（注文作成）: POST /orders ※Step 4 の成功に依存
Step 6（ポイント利用）: POST /points/redeem ※Step 5 と並列可
```

#### ARCH-HIGH-06: CSRF 保護方針がバックエンド実装と矛盾

- **対象**: セクション 4.3, Gateway
- **指摘内容**: ドキュメントでは「SameSite Cookie 属性 + CSRF トークン」と記載しているが、バックエンド Gateway の `SecurityConfig` では `.csrf(ServerHttpSecurity.CsrfSpec::disable)` で CSRF を完全に無効化している。各マイクロサービスの SecurityConfig でも同様に disabled
- **技術的根拠**: JWT ベースの SPA では CSRF トークンは不要（Cookie にトークンを保存しない場合）。しかし httpOnly Cookie で Access Token を保存する場合は SameSite=Strict + CSRF トークンが必要。ここの整合が取れていない
- **推奨対応**: トークン保存方式の確定に連動して CSRF 方針を統一。Authorization ヘッダー方式なら「CSRF 不要」と明記。httpOnly Cookie 方式ならバックエンド側の CSRF 有効化が必要

---

### Medium（7件）

#### ~~ARCH-MED-01: フロントエンド状態管理アーキテクチャが未言及~~ ✅ 対応済み

- **対象**: 全体
- **指摘内容**: 認証状態、カート状態、ユーザープロフィール等のクライアントサイド状態管理戦略がない。技術スタック未確定ゆえか
- **技術的根拠**: 状態管理方針（グローバルストア vs コンポーネントローカル vs サーバーステート）はアーキテクチャの根幹であり、技術スタック確定後に最初に決めるべき事項
- **推奨対応**: Phase 1 発足時に「フロントエンド状態管理方針」セクションを追加（認証状態、カート状態、API キャッシュ戦略、楽観的更新の要否）

**対応内容**:
- セクション 4.5「フロントエンド状態管理方針」を追加
- 状態カテゴリと管理戦略（認証状態・カート・プロフィール・UI・検索・チェックアウト）を定義
- API キャッシュ戦略（Stale-While-Revalidate / フェッチ都度 / ミューテーション連動 / 長期キャッシュ）を定義
- 楽観的更新の適用対象とロールバック条件を定義

#### ~~ARCH-MED-02: フロントエンド観測可能性（Observability）要件が欠如~~ ✅ 対応済み

- **対象**: セクション 5
- **指摘内容**: バックエンドには分散トレーシング基盤があるが、フロントエンドからの API 呼び出しに相関 ID（X-Request-Id）を付与する仕様がない。Gateway の CORS 設定では `X-Request-Id`, `X-Response-Time` を exposed headers として公開しているが、フロントエンドでの活用要件がない
- **技術的根拠**: エンドツーエンドの問題追跡ができない。ユーザーの「エラーが出ました」というフィードバックに対して、特定のリクエストを追跡する手段がない
- **推奨対応**: API 呼び出し時に `X-Request-Id`（UUID）をリクエストヘッダーに付与。エラー画面に Request ID を表示し、カスタマーサポートが調査可能にする。Web Vitals メトリクスの収集・送信方針を追加

**対応内容**:
- セクション 5 非機能要件テーブルに「観測可能性」4 行を追加（リクエスト追跡、エラー画面表示、Web Vitals、API レスポンスタイム）
- セクション 5.1「フロントエンド観測可能性実装要件」を新規追加（相関 ID フロー図、API クライアント実装要件、Web Vitals 収集仕様）
- Gateway `CorrelationIdFilter` / CORS `X-Request-Id`, `X-Response-Time` との連携フローを文書化

#### ~~ARCH-MED-03: カート API のユーザー識別方式が曖昧~~ ✅ 対応済み

- **対象**: EC-CART
- **指摘内容**: FR-CART-01 では `GET /api/v1/cart?userId=` と記載しているが、JWT から userId を自動抽出するのか、クエリパラメータで明示的に渡すのかが不明。セキュリティ上、他ユーザーの userId を指定できるのは IDOR 脆弱性リスク
- **技術的根拠**: バックエンドの実装を確認する必要があるが、JWT の `sub` claim から userId を取得するのが正しいパターン。クエリパラメータでの userId 渡しは認可バイパスの原因
- **推奨対応**: カート API は JWT トークンから userId を自動解決する方式に統一。`?userId=` パラメータは削除。API マトリクスとドキュメントを修正

**対応内容**:
- バックエンド実装を確認: `CartController` は `@RequestParam UUID userId` + `@PreAuthorize("#userId == authentication.principal or hasRole('ADMIN')")` で IDOR 防止済み
- FR-CART-01 のドキュメントを更新: `userId` は JWT `sub` claim から取得しクエリパラメータで渡す、`@PreAuthorize` による認可チェックがあるため他ユーザーのカートにはアクセス不可（403）であることを明記

#### ~~ARCH-MED-04: 決済フローの冪等性・二重送信防止の具体仕様が不足~~ ✅ 対応済み

- **対象**: EC-CHECKOUT
- **指摘内容**: AC-CHK-01「二重送信を防止」とあるが、UI 側の disable だけでは不十分。ネットワーク不安定時のリトライ安全性が未定義
- **技術的根拠**: 決済はシステムで最もクリティカルな操作。UI ボタン disable だけでは、ブラウザバックや戻るボタン、ネットワークタイムアウト後の再送信を防げない
- **推奨対応**: 冪等性キー（Idempotency-Key ヘッダー）の仕様を追加。フロントエンドで UUID を生成しリクエストヘッダーに付与。バックエンド側で同一キーのリクエストは同一結果を返す。決済完了画面ではブラウザバック防止（`history.replaceState`）を実装

**対応内容**:
- EC-CHECKOUT セクションに FR-CHK-09「冪等性・二重送信防止仕様」を新規追加
- `Idempotency-Key` ヘッダー仕様（UUID v4, `crypto.randomUUID()`, スコープ、バックエンド動作）を定義
- UI 二重送信防止策 4 項目（ボタン disabled、beforeunload、history.replaceState、自動リトライ）を定義
- 決済関連 3 API（payments/intent, payments/process, orders）に Idempotency-Key ヘッダーを付与
- 受入基準を AC-CHK-01 更新 + AC-CHK-04, AC-CHK-06 を追加

#### ~~ARCH-MED-05: 決済処理のタイムアウト仕様が未定義~~ ✅ 対応済み

- **対象**: EC-CHECKOUT
- **指摘内容**: Gateway の payment サーキットブレーカーは 10 秒タイムアウトだが、決済処理は外部決済プロバイダ依存で 10 秒超の可能性がある。フロントエンドのタイムアウト UI が不明
- **技術的根拠**: 決済中に Gateway タイムアウトが発生すると、「決済が完了したかどうか分からない」状態が生じる。これはユーザーにとって最悪の UX
- **推奨対応**: 決済処理のフロントエンド側タイムアウト（例: 30 秒）を定義。タイムアウト時は「決済処理中です。画面を閉じないでください」→ ポーリングで結果確認する設計を推奨。注文ステータス確認 API でリカバリ可能にする

**対応内容**:
- EC-CHECKOUT セクションに FR-CHK-10「決済タイムアウト・リカバリ仕様」を新規追加
- フロントエンド 30 秒タイムアウト、「処理中」UI、ポーリングリカバリ（3秒×10回）、最終フォールバックを定義
- 決済フロータイムライン図（正常 / Gateway 503 / FE 30s超）を追加
- Gateway の実際の設定値（`slow-call-duration-threshold=10s`, `timeout-duration=10s`）を反映
- 受入基準 AC-CHK-05 を追加

#### ~~ARCH-MED-06: `GET /api/v1/categories` が Gateway SecurityConfig で `permitAll()` に含まれていない~~ ✅ 対応済み

- **対象**: API マトリクス
- **指摘内容**: 現在の Gateway SecurityConfig では `/api/v1/products/**` と `/api/v1/recommendations/**` と `/api/v1/search/**` が permitAll だが、`/api/v1/categories/**` は明示的に permitAll されていない。`anyExchange().authenticated()` に該当し、未認証ユーザーは EC-HOME でカテゴリ一覧を取得できない
- **技術的根拠**: EC-HOME は「認証: 不要」と定義されており、カテゴリ API を呼ぶ。Gateway で認証が必要だと矛盾する。ルート定義は inventory-management-service に `/api/v1/categories/**` が含まれているが、SecurityConfig の permitAll に含まれていない
- **推奨対応**: Gateway SecurityConfig に `.pathMatchers("/api/v1/categories/**").permitAll()` を追加。また `GET /api/v1/campaigns/active` も同様に permitAll が必要（ホームページのシーズンバナー表示用）

**対応内容**:
- Gateway `SecurityConfig.java` に `.pathMatchers("/api/v1/categories/**").permitAll()` を追加
- Gateway `SecurityConfig.java` に `.pathMatchers(HttpMethod.GET, "/api/v1/campaigns/**").permitAll()` を追加
- セキュリティテスト 2 件追加（categories 認証不要、campaigns GET 認証不要）→ 全 26 テスト PASS

~~**影響範囲**~~:
- ~~EC-HOME: FR-HOME-02（カテゴリ表示）が未認証ユーザーで動作しない~~ → 解消
- ~~EC-HOME: FR-HOME-07（シーズンバナー）が campaigns API を呼べない~~ → 解消
- ~~EC-CATALOG: FR-CAT-02（カテゴリフィルター）が未認証ユーザーで動作しない~~ → 解消

#### ARCH-MED-07: シーズン切替ロジックの判定基準が曖昧

- **対象**: EC-HOME FR-HOME-07
- **指摘内容**: 「サーバー日付ベースまたはキャンペーン API のアクティブキャンペーンで判定」と書かれているが、どちらを使うか未確定。サーバー日付ベースならフロントエンドが日時判定するため TZ（タイムゾーン）問題が生じる
- **技術的根拠**: クライアント側のタイムゾーンやシステム日時が不正確な場合、ピークシーズンの表示がずれる。「日本のスキーシーズン」はサーバー時間（JST）で判定すべき
- **推奨対応**: キャンペーン API ベースでの判定に統一を推奨。シーズンキャンペーンを ADMIN が管理し、`GET /api/v1/campaigns/active` のレスポンスの `type` や `tag` でフロントエンドが判定する方が運用柔軟性が高い

---

### Low（4件）

#### ~~ARCH-LOW-01: Gateway ルート定義でパスの二重定義~~ ✅ 対応済み

- **対象**: Gateway routes
- **指摘内容**: `/api/v1/orders/**` と `/api/v1/admin/orders/**` が同一サービスに二重定義されている。Spring Cloud Gateway のパスマッチングでは `/api/v1/orders/**` が `/api/v1/admin/orders` にもマッチする可能性がある（パスプレフィックスの衣突）
- **技術的根拠**: 現在の RouteConfig では同一ルート定義内にまとめられているため実害はないが、フロントエンドが `admin/orders` にアクセスした際のルーティング確実性を文書化すべき
- **推奨対応**: フロントエンド向けに管理系 API のベースパスを整理。`/api/v1/admin/*` は全て ADMIN ロール必須であることをドキュメントに明記

**対応内容**:
- セクション 6「API エンドポイント対応マトリクス」に「管理系 API パス規約」テーブルを追加
- 5 つの管理系パスプレフィックスと必要ロール・ SecurityConfig ルール・ルーティング先を文書化
- EC サイトと管理画面のパス使い分けルールを明記

#### ~~ARCH-LOW-02: API レート制限仕様が未定義~~ ✅ 対応済み

- **対象**: セクション 5
- **指摘内容**: Gateway に Redis ベースのレート制限基盤が用意されているが（application.properties で RedisAutoConfiguration は exclude されており未有効化）、フロントエンド側の対応（429 レスポンス時の UI）が定義されていない
- **技術的根拠**: レート制限が有効化された場合、フロントエンドは 429 を受け取る。Retry-After ヘッダーの処理が未定義だと、レート制限が無意味になる
- **推奨対応**: 将来のレート制限有効化に備え、429 レスポンス時のフロントエンド対応（指数バックオフリトライ + ユーザー通知）を Phase 2 以降の要件として記録

**対応内容**:
- セクション 5 非機能要件テーブルに「耐障害性: レート制限対応 (429)」行を追加
- セクション 5.2「レート制限（429）対応仕様」を新規追加（Retry-After ヘッダー処理、指数バックオフ、ユーザー通知、決済 API の例外扱い）
- Phase 2 対応時期を明記、Phase 1 では拡張ポイントのみ用意する方針

#### ~~ARCH-LOW-03: API バージョニング戦略の明記なし~~ ✅ 対応済み

- **対象**: 全体
- **指摘内容**: 現在は全 API が `/api/v1/` だが、将来の v2 への移行戦略が未定義
- **技術的根拠**: フロントエンドが API バージョンをハードコードすると、バージョンアップ時に全画面の修正が必要になる
- **推奨対応**: API ベース URL を環境変数化し、バージョン切替を一箇所で行える設計を推奨。ドキュメントにバージョニング方針を追記

**対応内容**:
- セクション 1.4「API ベース URL とバージョニング方針」を新規追加
- `API_BASE_URL` 環境変数 + API クライアントの `baseURL` で一元管理する設計を定義
- コード例（環境変数設定、API クライアント設定、相対パス呼び出し）を記載

#### ~~ARCH-LOW-04: 多言語（i18n）のアーキテクチャ方針が不足~~ ✅ 対応済み

- **対象**: 全体
- **指摘内容**: 「日本語/英語切替対応を考慮した設計」とあるが、翻訳リソースの管理方式（静的ファイル vs API vs 外部サービス）、URL 戦略（`/en/products` vs `?lang=en`）、SEO 対応（hreflang タグ）が未定義
- **技術的根拠**: Phase 3 Could Have に分類されているが、Phase 1 で i18n を意識しない設計をするとリファクタリングコストが膘大になる
- **推奨対応**: Phase 1 で i18n-ready な設計を採用（ハードコード文字列禁止、翻訳キー方式）。URL 構造は Phase 3 で確定としても、コンポーネント設計は Phase 1 から考慮

**対応内容**:
- セクション 4.6「多言語（i18n）アーキテクチャ方針」を新規追加
- Phase 1 i18n-ready 設計ルール 6 項目（ハードコード禁止、翻訳キー命名、ファイル構成、Intl API 使用、通貨表示、画像内テキスト回避）を定義
- Phase 3 計画（URL 戦略、SEO hreflang、翻訳管理、コンテンツ翻訳）を記載
- セクション 4.2 の多言語対応行を更新（i18n-ready 設計・翻訳キー方式を明記）

---

## エスカレーション事項

| # | 区分 | 内容 | エスカレーション理由 |
|---|------|------|---------------------|
| 1 | ⚠️ 要アーキテクト判断 | **BFF（Backend for Frontend）パターンの採用可否**。EC-HOME は 3-5 API、EC-CHECKOUT は 5+ API を呼ぶ。API 集約レイヤーの必要性はトラフィック量とチーム体制に依存 | 新規レイヤー追加はアーキテクチャスタイルの変更に相当。Gateway に集約ロジックを追加するか、独立した BFF サービスを新設するか、あるいはフロントエンドでの並列呼び出しで十分かはビジネスコンテキストが必要 |
| 2 | ⚠️ 要アーキテクト判断 | **SSR / SSG / CSR のレンダリング戦略確定**。非機能要件で「SSR/SSG による SEO 最適化」と記載があるが、どの画面に SSR を適用するかのルールが未確定 | SSR はサーバーコスト・インフラ構成に直結。商品カタログ系（SEO 重要）は SSG/ISR、マイページ系（認証必要）は CSR という方針が妥当だが、Next.js / Nuxt 等の技術選定に依存 |
| 3 | ⚠️ 要アーキテクト判断 | **トークン保存方式の最終決定（httpOnly Cookie vs メモリ + Refresh Cookie）**。CSRF 方針、バックエンドの Cookie 設定、SameSite 属性に連鎖的影響がある | 不可逆ではないが、認証アーキテクチャの根幹であり後からの変更コストが高い |

---

## トレードオフ記録

| # | 設計判断 | 選択肢 | 得たもの | 犠牲にしたもの | リスクと緩和策 |
|---|---------|-------|---------|---------------|----------------|
| 1 | 全 API を Gateway 経由（BFF なし） | A: BFF 追加 / **B: Gateway 直接（現状）** | シンプルなアーキテクチャ、レイヤー数削減 | 画面固有の API 集約ができない。Chatty API パターンのリスク | 並列 API 呼び出しで LCP 要件を達成できるか検証が必要 |
| 2 | JWT HS256 対称鍵方式 | **A: HS256 対称鍵（現状）** / B: RS256 非対称鍵 | Gateway と Auth Service で同一鍵を共有するだけでシンプル | 鍵ローテーションが困難。全サービスの同時更新が必要 | secrets 管理で緩和。将来 RS256+JWKS に移行可能 |

---

## 技術的負債台帳

| # | 重要度 | 内容 | 影響範囲 | 推奨対応時期 | 返済コスト |
|---|--------|------|---------|-------------|-----------|
| 1 | ~~High~~ ✅ | ~~`PageImpl` を直接シリアライズしている（不安定な JSON 構造）~~ **対応済み**: `PageSerializationConfig` で VIA_DTO モードを全サービスに適用 | 全ページネーション API | ~~Phase 1 開始前~~ | ~~中~~ |
| 2 | ~~High~~ ✅ | ~~Gateway FallbackController が RFC 7807 形式でなく独自 JSON を返している~~ **対応済み**: RFC 7807 形式に修正 | 全サービス障害時 | ~~Phase 1 開始前~~ | ~~小~~ |
| 3 | ~~Medium~~ ✅ | ~~Gateway で categories パスが permitAll に含まれていない~~ **対応済み**: `/api/v1/categories/**` と `GET /api/v1/campaigns/**` を permitAll に追加 | EC-HOME（未認証閲覧） | ~~Phase 1 開始前~~ | ~~小~~ |
| 4 | Medium | Redis レート制限が未有効化 | Gateway 全体 | Phase 1 ローンチ前 | 中 |

---

## ADR 要否判定

| # | 設計判断の概要 | ADR 有無 | 推奨アクション |
|---|--------------|---------|---------------|
| 1 | JWT トークン保存方式（httpOnly Cookie vs メモリ） | 未作成 | ADR を作成すべき（セキュリティ影響大） |
| 2 | BFF パターン採用可否 | 未作成 | ADR を作成すべき（アーキテクチャスタイルに影響） |
| 3 | SSR/SSG/CSR レンダリング戦略 | 未作成 | ADR を作成すべき（インフラ・パフォーマンスに影響） |
| 4 | ページネーション JSON 構造（PageImpl vs PagedModel） | ✅ 対応済み | VIA_DTO モードを全サービスに適用済み。ADR 不要 |

---

## 推奨対応（優先順）

以下を **Phase 1 開発開始前** に対応すべき:

1. ~~**エラーレスポンス契約の文書化**（ARCH-CRIT-01）: RFC 7807 の JSON スキーマ、errorCode 体系、バリデーションエラー構造を要件定義書のセクション 4 に追加。Gateway fallback の 503 レスポンスも同形式に統一~~ ✅ 対応済み
2. ~~**主要 API のリクエスト/レスポンス型の概要記載**（ARCH-HIGH-01）: 全 DTO を網羅する必要はないが、`AuthResponse`, `OrderResponse`, `ProductResponse`, `Page<T>` の構造を記載~~ ✅ 対応済み
3. ~~**障害時の縮退 UI 定義**（ARCH-HIGH-03）: 各画面ごとに「どの API が落ちたら何が表示不能になるか」のマトリクスを追加~~ ✅ 対応済み
4. ~~**JWT ライフサイクルの仕様確定**（ARCH-HIGH-04）: トークン保存方式、リフレッシュフロー、401 ハンドリングのシーケンス図を追加~~ ✅ 対応済み
5. ~~**EC-CHECKOUT の決済フロー詳細化**（ARCH-MED-04, ARCH-MED-05）: 冪等性キー、タイムアウト、リカバリフローを仕様化~~ ✅ 対応済み
6. ~~**Gateway SecurityConfig の修正**（ARCH-MED-06）: `/api/v1/categories/**` と `GET /api/v1/campaigns/active` を permitAll に追加~~ ✅ 対応済み

これらは **全て対応済み**。技術スタック確定後に追加すべき事項（SSR 戦略、BFF 判断）はエスカレーション事項として明示した。状態管理方針はセクション 4.5 で技術スタック非依存の形で先行定義済み。
