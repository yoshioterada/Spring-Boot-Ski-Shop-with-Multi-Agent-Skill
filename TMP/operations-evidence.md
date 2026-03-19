# 開発運用エビデンス — SkiShop E-Commerce Microservices

> **最終更新**: 2026-03-19 (Phase 3 完了時点)

## プロジェクト概要

| 項目 | 内容 |
|---|---|
| プロジェクト名 | SkiShop — AI 駆動スキーショップ EC マイクロサービス |
| 技術スタック | Java 25, Spring Boot 3.5.0, Spring AI 2.0, Maven |
| アーキテクチャ | マイクロサービス（10 モジュール構成） |
| DB | PostgreSQL (7 サービス), MongoDB (2 サービス) |
| ゲートウェイ | Spring Cloud Gateway (WebFlux) |
| CI/CD | GitHub Actions (.github/workflows/ci.yml) |
| コンテナ | Docker (9 Dockerfiles, GHCR push) |
| テストカバレッジ | JaCoCo (分岐カバレッジ minimum 0.60) |
| API ドキュメント | springdoc-openapi (Swagger UI) |
| 国際化 | messages.properties (日本語/英語) |

## モジュール構成

| モジュール | ポート | DB | テスト数 | @PreAuthorize | Dockerfile | 状態 |
|---|---|---|---|---|---|---|
| common-lib | — | — | — | — | — | ✅ 完了 |
| authentication-service | 8080 | PostgreSQL | 43 | ✅ | ✅ | ✅ 完了 |
| user-management-service | 8081 | PostgreSQL | 29 | ✅ | ✅ | ✅ 完了 |
| inventory-management-service | 8082 | MongoDB | 25 | ✅ | ✅ | ✅ 完了 |
| sales-management-service | 8083 | PostgreSQL | 21 | ✅ | ✅ | ✅ 完了 |
| payment-cart-service | 8084 | PostgreSQL | 19 | ✅ | ✅ | ✅ 完了 |
| point-service | 8085 | PostgreSQL | 27 | ✅ | ✅ | ✅ 完了 |
| coupon-service | 8088 | PostgreSQL | 34 | ✅ | ✅ | ✅ 完了 |
| ai-support-service | 8087 | MongoDB | 39 | ✅ | ✅ | ✅ 完了 |
| api-gateway-service | 8090 | — | 24 | — (WebFlux) | ✅ | ✅ 完了 |

## ビルド・テスト結果

```
[INFO] Tests run: 261, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

全 261 テストが成功。コンパイルエラー 0 件。JaCoCo 分岐カバレッジ minimum 0.60 を全サービスで達成。

### テスト構成

| テスト種別 | 数 | 備考 |
|---|---|---|
| Service 層ユニットテスト | ~160 | Mockito + AssertJ |
| Controller 層テスト (@WebMvcTest) | ~80 | MockMvc + @WithMockUser |
| Gateway テスト (@WebFluxTest) | ~24 | WebTestClient |
| **合計** | **261** | |

## コード統計

| 項目 | 数値 |
|---|---|
| Java ソースファイル (main) | 237 |
| Java テストファイル | 24 |
| Java コード行数 (main) | 約 10,664 行 |
| SQL マイグレーション | 34 ファイル |
| 設定ファイル (properties) | 36 ファイル |
| POM ファイル | 11 ファイル |
| Dockerfile | 9 ファイル |
| i18n ファイル (messages*.properties) | 32 ファイル |
| GitHub Actions ワークフロー | 1 ファイル |

## 実装フェーズ

### Phase 1: 基盤構築 (初期実装)
- **common-lib**: sealed `ApplicationException` 例外階層 (5 サブクラス)、`GlobalExceptionHandler` (RFC 7807)、`PageResponse<T>`、`DomainEvent<T>`、`Role` enum、`CorrelationIdFilter`
- **authentication-service**: JWT 認証、ユーザー登録・ログイン、ロール管理

### Phase 2: ユーザー・在庫管理
- **user-management-service**: ユーザープロフィール CRUD、パスワード変更、権限管理、アクティビティ追跡
- **inventory-management-service**: 商品・カテゴリ管理 (MongoDB)、在庫管理、低在庫検出

### Phase 3: 販売・決済
- **sales-management-service**: 注文処理、10% 税計算、出荷管理、返品処理、ステータス遷移検証
- **payment-cart-service**: カート管理（自動再計算）、決済処理（シミュレーション）、返金機能

### Phase 4: ポイント・クーポン
- **point-service**: ポイント付与（ティア倍率適用）、ポイント交換、ポイント移転、4 段階ティアシステム (BRONZE/SILVER/GOLD/PLATINUM)、自動ティアアップグレード
- **coupon-service**: キャンペーン管理、クーポン発行・検証・利用、割引計算 (PERCENTAGE/FIXED_AMOUNT/BOGO/FREE_SHIPPING)

### Phase 5: AI サポート
- **ai-support-service**: Spring AI ChatClient によるチャットサポート、商品レコメンデーション、セマンティック検索、クエリ拡張

### Phase 6: API ゲートウェイ
- **api-gateway-service**: Spring Cloud Gateway (WebFlux)、10 ルート定義、CORS 設定、Correlation ID フィルタ、リクエストログフィルタ

## 是正対応 (Gate 1-4 レビュー → modify-plan.md 10 ステップ)

| Step | 内容 | 状態 |
|---|---|---|
| 1 | JaCoCo 導入 — テストカバレッジ計測基盤 | ✅ 完了 |
| 2 | SecurityConfig 修正 — `permitAll()` 最小化 + `authenticated()` デフォルト | ✅ 完了 |
| 3 | DB_PASSWORD 外部化 — デフォルト値削除、環境変数必須化 | ✅ 完了 |
| 4 | DB スキーマ修正 — `DOUBLE→NUMERIC`, `TIMESTAMP WITH TIME ZONE`, `@Version` 追加 | ✅ 完了 |
| 5 | コード品質修正 — ResourceNotFoundException 正名化、PII マスキング | ✅ 完了 |
| 6 | API パス統一 — 全サービスを `/api/v1/` に統一、Gateway ルート修正 | ✅ 完了 |
| 7 | パフォーマンス — `@EntityGraph` (N+1 解消)、HikariCP 設定、インデックス追加 | ✅ 完了 |
| 8 | AI 耐性強化 — OpenAI タイムアウト、max-tokens、メッセージ 20 件制限 | ✅ 完了 |
| 9 | テスト新規作成 — 45→233 テスト | ✅ 完了 |
| 10 | テスト改善 — 命名規約統一、`@Nested`, `@DisplayName`, AAA コメント | ✅ 完了 |

## 是正対応 (Gate 1-5 レビュー — Phase 1: 即時)

| # | 内容 | 状態 |
|---|---|---|
| P1-01 | `@EnableMethodSecurity` + `@PreAuthorize` を全 8 サービスに追加 | ✅ 完了 |
| P1-02 | JaCoCo minimum を 0.00 → 0.60 に引き上げ | ✅ 完了 |
| P1-03 | Controller 層 `@WebMvcTest` テスト追加 (全 8 サービス + Gateway) | ✅ 完了 |
| P1-04 | UserTier `@Version` + DDL マイグレーション (`V5__add_version_to_user_tiers.sql`) | ✅ 完了 |
| P1-05 | `processExpiredPoints()` — chunk 処理化 (OOM 解消) | ✅ 完了 |
| P1-06 | `bulkGenerateCoupons()` — `saveAll()` バッチ処理 (O(n)→O(1)) | ✅ 完了 |

## 是正対応 (Gate 1-5 レビュー — Phase 2: 短期)

| # | 内容 | 状態 |
|---|---|---|
| P2-07 | 全 9 サービスに Dockerfile 作成 (マルチステージ, 非 root, HEALTHCHECK) | ✅ 完了 |
| P2-08 | GitHub Actions CI/CD パイプライン | ✅ 完了 |
| P2-09 | springdoc-openapi 導入 (8 サービス) | ✅ 完了 |
| P2-10 | messages.properties / messages_en.properties 追加 (8 サービス) | ✅ 完了 |
| P2-11 | HikariCP `leak-detection-threshold=60000` 追加 (5 サービス) | ✅ 完了 |
| P2-12 | Caffeine キャッシュ導入 (point-service: TierDefinition, inventory: Category) | ✅ 完了 |

## 是正対応 (Gate 1-5 レビュー — Phase 3: 中期)

| # | 内容 | 状態 |
|---|---|---|
| P3-13 | コンプライアンス要件チェックリスト作成 (法務部門判断依頼書) | ✅ 完了 |
| P3-14 | ロールバック計画 + CHANGELOG.md 作成 | ✅ 完了 |
| P3-15 | operations-evidence.md 最新化 (本ドキュメント) | ✅ 完了 |
| P3-16 | 各ゲート再審記録の作成・保存 | ✅ 完了 |
| P3-17 | 負荷テスト基盤 (k6) 構築 | ✅ 完了 |

## 共通設計パターン

- **例外処理**: sealed `ApplicationException` → 5 個のトップレベルサブクラス (`ResourceNotFoundException`, `BusinessRuleViolationException`, `ExternalServiceException`, `AuthenticationFailedException`, `AuthorizationDeniedException`)
- **DTO**: record クラス + Bean Validation (`@Valid`, `@NotNull`, `@Size`)
- **DI**: コンストラクタベース（Lombok 不使用）
- **セキュリティ**: `SecurityConfig` — stateless セッション、CSRF 無効化 (API)、`@EnableMethodSecurity` + `@PreAuthorize`
- **テスト**: Service 層 (Mockito + AssertJ) + Controller 層 (`@WebMvcTest` + MockMvc)
- **イベント**: `EventPublishingService` — ドメインイベント発行スタブ
- **マイグレーション**: Flyway (PostgreSQL サービス)
- **ログ**: SLF4J/Logback（`System.out.println` 禁止、PII マスキング適用）
- **キャッシュ**: Caffeine (point-service: TierDefinition, inventory: Category)
- **API ドキュメント**: springdoc-openapi (Swagger UI at `/swagger-ui/index.html`)
- **国際化**: Spring MessageSource (`messages.properties` / `messages_en.properties`)

## 修正履歴 (初期実装時)

| 問題 | 対応 |
|---|---|
| `ApplicationException.XxxException` ネスト参照 | トップレベル例外クラスの個別 import に修正 (全サービス) |
| `DuplicateResourceException` 不在 | `BusinessRuleViolationException` で代替 |
| `TierDefinition.getTierLevel()` 不在 | `getLevel()` に修正 |
| `BigDecimal` → `double` 暗黙変換 | `.doubleValue()` 明示呼び出し |
| `findByTierLevel` リポジトリメソッド | `findByLevel` に修正（フィールド名 `level` 対応） |
| `List.of()` 不変リストの `.sort()` | `new ArrayList<>()` でコピー後にソート |
| `PointTransaction.getId()` null (テスト) | リフレクションで ID セット |
| `Coupon.getId()` null (テスト) | `setUp()` でリフレクション ID 設定 |
| Mockito + Java 25 互換性 | surefire に `--add-opens` JVM 引数追加 |
| `ChatClient.ChatClientRequest` 不在 | `ChatClient.ChatClientRequestSpec` に修正 |
| `chatClient.prompt(any())` あいまい | `any(Prompt.class)` で型指定 |

## セキュリティ対応

- **OWASP Top 10** を意識した設計
- **入力バリデーション**: `@Valid`, `@NotNull`, `@Size` 適用済み
- **SQL インジェクション防止**: Spring Data JPA パラメータバインド使用
- **秘密情報**: ハードコードなし、環境変数/外部設定利用、DB_PASSWORD デフォルト値削除
- **認証・認可**: Spring Security + JWT + `@EnableMethodSecurity` + `@PreAuthorize`
- **CORS**: ゲートウェイで一元管理 (`@Value` による許可オリジン設定)
- **PII 保護**: メールアドレスのマスキング (auth, user-mgmt)、ログ出力からの PII 除去
- **楽観的ロック**: 主要エンティティに `@Version` カラム追加 (UserTier, PointTransaction, Coupon, UserCoupon, Order, Payment)
- **Actuator 制限**: `management.endpoints.web.exposure.include=health,info,prometheus` (全サービス)
- **HikariCP**: `leak-detection-threshold=60000`, 明示的プール設定

## インフラストラクチャ

### Docker
- 全 9 サービスに Dockerfile 完備 (マルチステージビルド)
- ベースイメージ: `eclipse-temurin:21-jdk` (ビルド) → `eclipse-temurin:21-jre` (ランタイム)
- 非 root ユーザー (`appuser`) でコンテナ実行
- HEALTHCHECK: `wget -qO- http://localhost:PORT/actuator/health`
- JVM チューニング: `MaxRAMPercentage=75.0`
- `.dockerignore` でビルドコンテキスト最適化

### CI/CD
- GitHub Actions: `.github/workflows/ci.yml`
- `build-and-test` ジョブ: JDK 21, `mvn verify`, JaCoCo/Surefire レポート upload
- `docker-build` ジョブ: 9 サービスの Docker イメージビルド + GHCR push (main ブランチ)

### 負荷テスト
- k6 負荷テスト基盤: `load-tests/` ディレクトリ
- 対象: 認証フロー、商品一覧/検索、カート操作、注文処理
- GitHub Actions で CI/CD パイプラインから実行可能

## ドキュメント一覧

| ドキュメント | パス | 内容 |
|---|---|---|
| プロジェクト仕様 | `spec.md` | システム概要、アーキテクチャ、ビジネス要件 |
| 是正計画書 | `modify-plan.md` | Gate 1-4 レビュー指摘の是正計画 (10 ステップ) |
| 変更履歴 | `CHANGELOG.md` | 全変更の時系列記録 (Phase 0-3) |
| ロールバック計画 | `docs/rollback-plan.md` | サービス別ロールバック手順 |
| コンプライアンス要件 | `docs/compliance-requirements-checklist.md` | 法務部門判断依頼書 (C-01〜C-07) |
| 設計ドキュメント | `design-docs/` | 13 サービス設計書 |
| レビューレポート | `.github/review-reports/` | Gate 1-5 統合レビュー結果 + 再審記録 |
| 運用エビデンス | `operations-evidence.md` | 本ドキュメント |
