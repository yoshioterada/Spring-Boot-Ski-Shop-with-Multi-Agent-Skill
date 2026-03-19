# CHANGELOG

本ドキュメントは [Conventional Commits](https://www.conventionalcommits.org/) に準拠する。

---

## [1.0.0] — 2026-03-19

### 概要

SkiShop E-Commerce Microservices Platform の初回リリース候補。Gate 1-5 統合レビューで検出された 203 件の指摘に対し、Phase 1〜3 の修正を全て実施。

---

### Phase 0: 初期実装 (modify-plan.md 10ステップ)

初回レビュー (2025-07-14 Gate 1-4) の指摘に基づく是正。45 → 233 テスト。

- **feat(build)**: JaCoCo プラグイン導入 (jacoco-maven-plugin) — テストカバレッジ計測基盤
- **fix(security)**: 全サービスの SecurityConfig 修正 — `permitAll()` を最小限に制限、`anyRequest().authenticated()` をデフォルトに
- **fix(security)**: DB_PASSWORD デフォルト値を削除 — 環境変数必須化 (coupon, point, auth-dev)
- **fix(db)**: DB スキーマ修正 — `DOUBLE PRECISION` → `NUMERIC(5,2)`, `TIMESTAMP` → `TIMESTAMP WITH TIME ZONE`
- **feat(db)**: `@Version` カラム追加 — PointTransaction, Coupon, UserCoupon, Order, Payment に楽観的ロック
- **fix(code)**: ResourceNotFoundException の誤用修正 — エラーコードではなくリソース名を使用 (coupon, point, ai-support)
- **fix(security)**: PII マスキング — メールアドレスのログ出力をマスキング (auth, user-mgmt)
- **feat(api)**: API パス統一 — 全サービスを `/api/v1/` プレフィックスに統一
- **fix(gateway)**: Gateway ルート不整合修正 — `/api/auth/**` → `/api/v1/auth/**`
- **perf(db)**: N+1 解消 — `@EntityGraph` 追加 (OrderRepository, CartRepository)
- **perf(db)**: HikariCP 設定追加 — 全 PostgreSQL サービスに明示的プール設定
- **perf(db)**: インデックス追加 — `idx_orders_customer_created`, ChatSession `@Indexed`
- **fix(ai)**: AI サービス耐性強化 — OpenAI タイムアウト設定、max-tokens 制限、メッセージ 20 件制限
- **feat(api)**: Pageable 上限設定 — `max-page-size=100`, `@PageableDefault(size=20)`
- **test**: テスト新規作成 — CartServiceTest, CampaignServiceTest, JwtTokenServiceTest, RecommendationServiceTest, SearchServiceTest
- **test**: 既存テスト改善 — 命名規約統一 (`should_xxx_when_yyy`), `@DisplayName`, `@Nested`, AAA コメント

---

### Phase 1: 即時対応 (Gate 1-5 レビュー Critical/High 是正)

233 → 261 テスト。JaCoCo 最低値を 0.60 に設定。

#### セキュリティ強化
- **feat(security)**: [P1-01] `@EnableMethodSecurity` + `@PreAuthorize` を全 8 サーブレットサービスに追加
  - 管理者操作に `@PreAuthorize("hasRole('ADMIN')")` を設定
  - ユーザー操作に `@PreAuthorize("isAuthenticated()")` を設定
  - 公開エンドポイント (商品一覧、ログイン、登録) を明示的に許可

#### テスト強化
- **feat(test)**: [P1-02] JaCoCo minimum を `0.00` → `0.60` に引き上げ (分岐カバレッジ)
- **test**: [P1-03] Controller 層 `@WebMvcTest` テストを全 8 サービスに追加
  - AuthController, UserController, ProductController, OrderController,
    PaymentController, PointController, CouponController, ChatController
  - MockMvc + `@WithMockUser` でセキュリティ統合テスト

#### データ整合性
- **feat(db)**: [P1-04] UserTier エンティティに `@Version` カラム追加 + DDL マイグレーション (`V5__add_version_to_user_tiers.sql`)

#### パフォーマンス
- **perf**: [P1-05] `processExpiredPoints()` — `findAll()` → chunk 処理 (`findByStatusAndExpiresAtBefore` + `Pageable`)
  - OOM リスクを解消
- **perf**: [P1-06] `bulkGenerateCoupons()` — 個別 `save()` → `saveAll()` バッチ処理
  - O(n) DB 呼び出しを O(1) に削減

---

### Phase 2: 短期対応 (2週間以内)

テスト数 261 維持。インフラ・運用基盤を整備。

#### インフラストラクチャ
- **feat(infra)**: [P2-07] 全 9 サービスに Dockerfile を作成
  - マルチステージビルド (eclipse-temurin:21-jdk → 21-jre)
  - 非 root ユーザー (`appuser`) でコンテナ実行
  - HEALTHCHECK (`actuator/health`) 設定
  - JVM メモリ設定 (`MaxRAMPercentage=75.0`)
  - Docker BuildKit キャッシュマウント
  - `.dockerignore` でビルドコンテキスト最適化
- **ci**: [P2-08] GitHub Actions CI/CD パイプライン (`.github/workflows/ci.yml`)
  - `build-and-test` ジョブ: JDK 21, `mvn verify`, JaCoCo/Surefire レポート upload
  - `docker-build` ジョブ: 9 サービスの Docker イメージビルド + GHCR push (main ブランチ)

#### API ドキュメント
- **feat(docs)**: [P2-09] springdoc-openapi 導入
  - 7 サーブレットサービスに `springdoc-openapi-starter-webmvc-ui` v2.8.5
  - api-gateway-service に `springdoc-openapi-starter-webflux-ui` v2.8.5
  - Gateway SecurityConfig に Swagger UI パス許可追加

#### 国際化
- **feat(i18n)**: [P2-10] 全 8 サービスに `messages.properties` / `messages_en.properties` 追加
  - 共通エラーメッセージ + サービス固有メッセージ (日本語デフォルト / 英語)
  - `spring.messages.basename=messages` + `spring.messages.encoding=UTF-8` 設定

#### パフォーマンス・信頼性
- **feat(db)**: [P2-11] HikariCP `leak-detection-threshold=60000` を 5 サービスに追加
  - authentication, user-management, sales-management, payment-cart, point-service
- **perf(cache)**: [P2-12] Caffeine キャッシュ導入
  - point-service: TierDefinition キャッシュ (30 分 TTL, maxSize=100)
  - inventory-management-service: Category キャッシュ (15 分 TTL, maxSize=500)
  - `@Cacheable` / `@CacheEvict` アノテーション適用

---

### Phase 3: 中期対応 (リリース前)

#### ドキュメント・ガバナンス
- **docs**: [P3-13] コンプライアンス要件チェックリスト作成 (`docs/compliance-requirements-checklist.md`)
  - C-01〜C-07: 同意管理, データ保持, 越境データ移転, データ主体権利, AI オプトアウト, ROPA, DPIA
  - 法務部門への判断依頼書として構成
- **docs**: [P3-14] ロールバック計画 (`docs/rollback-plan.md`) + CHANGELOG.md 作成
- **docs**: [P3-15] `operations-evidence.md` を最新状態に更新
- **docs**: [P3-16] 各ゲートの再審記録を作成・保存
- **feat(test)**: [P3-17] 負荷テスト基盤 (k6) 構築

---

### 統計

| 指標 | Phase 0 前 | Phase 0 後 | Phase 1 後 | Phase 2 後 | Phase 3 後 |
|------|-----------|-----------|-----------|-----------|-----------|
| テスト数 | 0 | 233 | 261 | 261 | 261+ |
| Java ソースファイル | ~167 | ~180 | ~200 | ~237 | ~237 |
| Java コード行数 | — | ~7,930 | ~9,500 | ~10,664 | ~10,664+ |
| SQL マイグレーション | 6 | ~20 | ~34 | 34 | 34 |
| Dockerfile | 0 | 0 | 0 | 9 | 9 |
| CI/CD パイプライン | 0 | 0 | 0 | 1 | 1 |
| i18n ファイル | 0 | 0 | 0 | 32 | 32 |

---

### 既知の制限事項 (Scope Out — 人間判断待ち)

| ID | 内容 | 判断者 |
|----|------|--------|
| C-01〜C-05 | コンプライアンス対応 (同意管理, データ保持, 越境移転, データ主体権利, AI オプトアウト) | 法務部門/DPO |
| E-06 | auth ↔ user-management データ重複解消 | チーフアーキテクト |
| E-07 | DR/BCP 方針・RPO/RTO 目標 | CTO |
| E-10/E-11 | ROI/ビジネスケース, MVP 定義 | 経営層/PO |
| E-13 | UAT 計画 | プロダクトオーナー |
