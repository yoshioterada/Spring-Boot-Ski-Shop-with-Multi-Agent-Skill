# AI Analyzer 実装計画書（Phased Implementation Plan）

> **対象設計書**: [`design-docs/AI-analyzer-spec.md`](AI-analyzer-spec.md) v1.3
> **対象機能**: F1 対話分析 / F2 季節予測 / F3 週次サマリー / F4 滞留在庫レーダー / F5 検索ゼロヒット
> **目的**: 設計書を抜け漏れなく完全実装するため、依存関係を考慮した 7 フェーズに分割し、各フェーズに **完了チェック項目** を設けて段階的に検証する
> **作成日**: 2026-04-18
> **ステータス**: Draft v1.0

---

## 0. 全体方針

### 0.1 フェーズ構成の考え方

| 観点 | 方針 |
|------|------|
| **依存順** | 「基盤 → Tool → 機能」の順で積み上げる。LLM 呼出が UI を呼び出すと再現困難なので、Tool 層を先に整備する |
| **検証ファースト** | 各フェーズ末に **チェックリスト 100% 合格** を Gate とし、未達であれば次フェーズに進まない |
| **AI 機能はスタブから本番へ** | F3 narrative などは「Java 集計＋固定文 narrative」で先に UI を立ち上げ、後続フェーズで LLM 化する |
| **ADR 遵守** | § 22 の決定事項（D-COM/D-F1〜F5）違反は即座にリジェクト |
| **テスト並行作成** | 実装と同時に単体テストを書き、フェーズ完了時点で分岐カバレッジ ≥ 80% |

### 0.2 フェーズ一覧

| Phase | 名称 | 内容概要 | 主な成果物 |
|-------|------|---------|-----------|
| **P0** | 事前準備・基盤整備 | ブロッカー解消、共通設定、DB 準備 | WebClient Bean / DDL / 環境変数 |
| **P1** | F3 週次サマリー基盤（スタブ AI） | バッチ・キャッシュ・UI のみ先行 | Cron + MongoDB + 週次サマリーカード |
| **P2** | Function Tool 整備 + F3 narrative 本番化 | `AnalyticsToolFunctions` 8 種 + LLM narrative | ChatClient 統合済み F3 |
| **P3** | F1 対話分析（SSE） + F4/F5 Tool 連携 | チャット UI + Tool 4 種追加 | AiChatPanel + Function Tools 拡張 |
| **P4** | F2 季節予測（Java 計算 + AI ナラティブ） | 季節係数マスタ + 二段階予測 | SeasonalForecastPanel |
| **P5** | F4 滞留在庫レーダー専用 UI + クーポン発行 | DeadStockRadar + 監査ログ | DeadStockRadar.tsx + 発行モーダル |
| **P6** | F5 機会発見レーダー UI + F3 統合 | ZeroHitRadar + dismiss 機能 | ZeroHitRadar.tsx + F3 narrative 拡張 |
| **P7** | 監視・運用整備・本番リリース | Prometheus / Grafana / Runbook | SLO 達成 + Go 判定 |

### 0.3 共通の作業完了チェック手順（毎フェーズ末に実施）

各フェーズ末に **以下の 4 段階検証** を必ず実施する:

1. **コードレビュー**: § 22.8 PR チェックリスト全項目に ✅
2. **テスト**: 単体・結合・E2E がすべて Green、分岐カバレッジ ≥ 80%
3. **ADR 遵守確認**: 当該フェーズに該当する D-COM/D-FX 決定事項を ✅ で照合
4. **`phase-finished-verification` skill 実行**: 抜け漏れの最終チェック

→ 1 つでも未達なら **Go/No-Go レビューで No-Go 判定** とし、次フェーズに進まない。

---

## P0. 事前準備・基盤整備

### P0.1 目的
F1〜F5 すべての実装の前提となる **基盤（DB スキーマ・サービス間通信・環境変数・共通 Bean）を整備** し、以後のフェーズが互いに独立して進められる状態を作る。

### P0.2 作業内容

#### P0.2.1 ブロッカー解消（spec § 2.2）
- [ ] **B1**: ai-support-service 内に `WebClient` Bean を追加（sales / user / inventory / coupon サービス向け）
  - `internalApiKey` を `application.yml` から読み込み、`Authorization` ヘッダに付与
  - 接続先 URL は `service.sales.url` などのプロパティで外出し
- [ ] **B2**: `AiAnalyzerProperties`（`@ConfigurationProperties("ai-analyzer")`）追加
  - `cacheTtlMinutes`, `maxHistoryTurns`, `monthlyCostLimitUsd` などを集約
- [ ] **B3**: PII マスキングユーティリティ `PromptSanitizer` クラス追加（メール・氏名・住所・user_id を除去）
- [ ] **B4**: ハルシネーション抑止のため `ChatClient` 既定パラメータを `temperature=1`（gpt-5 系は変更不可）, `maxCompletionTokens=1500` に統一する Bean 設定

#### P0.2.2 DB スキーマ準備
- [ ] **PostgreSQL（sales-management-service）**
  - [ ] `seasonal_weights` テーブル DDL 追加（`docker/initdb/05_seasonal_weights.sql`）
  - [ ] `mv_sku_velocity` MATERIALIZED VIEW 追加（spec § 19.8.1、`product_sku` 列名で）
  - [ ] `mv_sku_velocity` の 1 時間ごと REFRESH ジョブ（`@Scheduled` または pg_cron）
- [ ] **PostgreSQL（coupon-service）**
  - [ ] `dead_stock_actions` テーブル DDL 追加（spec § 19.8.2、`actual_pct CHECK (0..40)` 必須）
- [ ] **MongoDB（ai-support-service）**
  - [ ] `weekly_summaries` コレクション + 90 日 TTL インデックス
  - [ ] `ai_analyzer_chat_sessions` コレクション + 30 日 TTL インデックス
  - [ ] `zero_hit_dismissals` コレクション + 90 日 TTL インデックス（`expiresAt`）
- [ ] **MongoDB（inventory-management-service）**
  - [ ] `search_logs` コレクションに partial index 追加（spec § 20.10.1）

#### P0.2.3 環境変数・docker-compose
- [ ] `docker-compose.yml` の ai-support-service に以下を追加
  - `AI_ANALYZER_ENABLED=true`
  - `AI_ANALYZER_CACHE_TTL_MINUTES=60`
  - `AI_ANALYZER_MAX_HISTORY_TURNS=8`
  - `AI_ANALYZER_MONTHLY_COST_LIMIT_USD=30`
  - `SERVICE_SALES_URL=http://sales-management-service:8084`
  - `SERVICE_USER_URL=http://user-management-service:8081`
  - `SERVICE_INVENTORY_URL=http://inventory-management-service:8082`
  - `SERVICE_COUPON_URL=http://coupon-service:8086`
- [ ] `INTERNAL_API_KEY` を 4 サービス（ai-support / sales / user / inventory / coupon）で共通化

#### P0.2.4 Resilience4j 設定（D-COM-07）
- [ ] `ai-support-service` の `application.yml` に CircuitBreaker / Retry 設定を追加
  - LLM 呼出: 失敗率 50% / 5 リクエスト → OPEN、30 秒後 HALF_OPEN
  - Service 間呼出: 失敗 3 回連続 → OPEN

### P0.3 完了チェック項目

| # | チェック項目 | 確認方法 |
|---|-------------|---------|
| ✅ P0-1 | `WebClient` Bean が 4 サービスに対して定義され、`/actuator/beans` で確認できる | `curl localhost:8087/actuator/beans \| grep WebClient` |
| ✅ P0-2 | sales-management-service の `mv_sku_velocity` が作成済み | `psql ... \dm` で表示される |
| ✅ P0-3 | `mv_sku_velocity` の REFRESH ジョブが 1 時間に 1 回動作するログがある | `docker compose logs sales-management-service \| grep "Refreshed mv_sku_velocity"` |
| ✅ P0-4 | coupon-service の `dead_stock_actions` テーブルが存在し、`CHECK` 制約が効く | `INSERT ... actual_pct=50` が `chk_pct_range` 違反でエラーになる |
| ✅ P0-5 | MongoDB の TTL インデックス 3 種がすべて設定済み | `db.weekly_summaries.getIndexes()` などで `expireAfterSeconds` 確認 |
| ✅ P0-6 | inventory-management-service の `search_logs` に partial index がある | `db.search_logs.getIndexes()` で `idx_zero_hit_recent` 確認 |
| ✅ P0-7 | docker-compose 起動時に ai-support-service が `AI_ANALYZER_ENABLED=true` を読み込む | 起動ログに `AiAnalyzerProperties: enabled=true` 出力 |
| ✅ P0-8 | ai-support-service → sales-management-service の疎通テストが Green | テストコード `WebClientIntegrationTest` で /admin/orders/analytics/summary が 200 を返す |
| ✅ P0-9 | `PromptSanitizer.sanitize("user@example.com 田中太郎")` が PII を除去する | 単体テスト `PromptSanitizerTest` Green |
| ✅ P0-10 | Resilience4j 設定が適用されている | `/actuator/circuitbreakers` で 5 個（LLM + 4 サービス）が表示 |

### P0.4 ADR 遵守確認
- D-COM-04（`@PreAuthorize` 必須）: P0 ではエンドポイント未追加のためスキップ
- D-COM-07（Resilience4j 適用）: ✅ P0-10 で確認

### P0.5 Gate 判定
全 10 項目が ✅ → P1 へ進む。1 つでも ❌ → P0 内で修正。

---

## P1. F3 週次サマリー基盤（スタブ AI）

### P1.1 目的
F3 の **データパイプライン（バッチ → MongoDB → API → UI）** を先に構築し、AI ナラティブ部分は固定文プレースホルダで動かす。早期に「動くもの」を作って業務担当のフィードバックを得る。

### P1.2 作業内容

#### P1.2.1 バックエンド（ai-support-service）
- [ ] `AdminAnalyzerController` 新規作成
  - `GET /api/v1/admin/ai-analyzer/weekly-summary`（キャッシュヒット時 < 200ms）
  - `POST /api/v1/admin/ai-analyzer/weekly-summary/refresh`（管理者強制再生成、レート制限 1h/1 回）
  - 全エンドポイントに `@PreAuthorize("hasRole('ADMIN')")` 付与（D-COM-04）
- [ ] `WeeklySummaryService` 実装
  - sales-management-service `/api/v1/admin/orders/analytics/summary` から「今週」「先週」「昨年同週」取得
  - user-management-service `/api/v1/admin/users/analytics/summary` から UU 取得
  - KPI（wow / yoy）を Java 側で計算
  - `narrative` は固定文「（AI ナラティブは P2 で実装予定）」を入れる
- [ ] `WeeklySummaryRepository`（MongoDB / `weekly_summaries` コレクション）
- [ ] `WeeklySummaryScheduler` `@Scheduled(cron = "0 0 7 * * MON", zone = "Asia/Tokyo")`
  - 起動時に当週分が無ければ即時生成
- [ ] レスポンス DTO は spec § 4.2.2 のスキーマに完全準拠

#### P1.2.2 BFF（フロントエンド）
- [ ] `frontend/src/app/api/admin/ai-analyzer/weekly-summary/route.ts`（GET / POST）
  - 既存 `app/api/admin/analytics/sales/route.ts` のパターンを踏襲
  - JWT 検証 + Bearer 転送 + `internalApiKey` 付与
- [ ] orval で OpenAPI から型生成（`WeeklySummaryResponse`）

#### P1.2.3 フロントエンド UI
- [ ] `frontend/src/components/admin/ai-analyzer/WeeklySummaryCard.tsx` 新規作成
  - KPI 4 種（売上・注文・UU・AOV）+ wow/yoy バッジ
  - ハイライト箇条書き
  - narrative エリア（プレースホルダ表示）
  - 「再生成」ボタン → `POST /weekly-summary/refresh`
  - アクセシビリティ: `aria-live="polite"`、KPI に `aria-label`
- [ ] `analytics/page.tsx` の Tabs に「AI 分析」タブを追加（既存「レポート生成」を改名拡張）

#### P1.2.4 テスト
- [ ] 単体: `WeeklySummaryServiceTest`（KPI 計算、wow/yoy、年またぎ）
- [ ] 結合（TestContainers）: MongoDB + WireMock で `/weekly-summary` が < 200ms（キャッシュヒット）
- [ ] E2E（Playwright）: AI 分析タブ表示 → KPI 数値が API レスポンスと一致

### P1.3 完了チェック項目

| # | チェック項目 | 確認方法 |
|---|-------------|---------|
| ✅ P1-1 | `GET /weekly-summary` が `ROLE_ADMIN` 必須で、一般ユーザは 403 | curl + 一般ユーザ JWT で 403 確認 |
| ✅ P1-2 | キャッシュヒット時の応答時間 < 200ms (P95) | k6 / wrk による測定 |
| ✅ P1-3 | スケジューラが毎週月曜 07:00 JST に発火する | テスト用 cron `*/30 * * * * *` で動作確認 |
| ✅ P1-4 | レスポンスに `weekStart`, `weekEnd`, `kpis`, `highlights`, `narrative` が全て含まれる | スキーマバリデーション |
| ✅ P1-5 | `narrative` フィールドに「P2 で実装予定」のプレースホルダが入っている | レスポンスボディ検証 |
| ✅ P1-6 | `weekly_summaries` MongoDB に upsert される（同一 weekStart で重複無し） | `db.weekly_summaries.find().count()` |
| ✅ P1-7 | `POST /weekly-summary/refresh` が 1 時間に 1 回までしか実行できない（429 を返す） | 連続実行で 2 回目が 429 |
| ✅ P1-8 | AI 分析タブが UI に表示され、KPI が読みやすく整形される | 手動確認 + Playwright E2E |
| ✅ P1-9 | 単体テスト分岐カバレッジ ≥ 80%（`WeeklySummaryService`） | JaCoCo レポート |
| ✅ P1-10 | Playwright E2E がすべて Green | `npm run test:e2e` |
| ✅ P1-11 | KPI に `aria-label` が付き、スクリーンリーダで読める | Lighthouse / axe-core スキャン |
| ✅ P1-12 | 個人情報（顧客名・メール）が一切レスポンスに含まれない（D-COM-02） | レスポンスを正規表現でスキャン |

### P1.4 ADR 遵守確認
- ✅ D-COM-02（PII 流出防止）: 集計値のみ
- ✅ D-COM-04（`@PreAuthorize`）
- ✅ D-COM-07（Resilience4j）: WebClient 呼出に適用
- ✅ D-F3-01（毎週月曜 07:00 JST 固定）
- ✅ D-F3-02（90 日 TTL）
- ✅ D-F3-03（手動再生成 1h/1 回）

### P1.5 Gate 判定
全 12 項目 ✅ + ADR 6 項目 ✅ → P2 へ。

---

## P2. Function Tool 整備 + F3 narrative 本番化

### P2.1 目的
LLM が呼び出せる **Function Tool 8 種を実装**し、F3 の `narrative` を本番 LLM 生成に切り替える。以後の F1/F2/F4/F5 はこの Tool 層の上に乗る。

### P2.2 作業内容

#### P2.2.1 Function Tool 実装（spec § 5.2、8 種）
- [ ] `AnalyticsToolFunctions` クラス新規作成（`@Component`、`@Tool` メソッド群）
  - [ ] `getDailyRevenue(int days, String category)` → sales-management `/admin/orders/analytics/summary`
  - [ ] `getTopProducts(int days, int limit, String category)` → sales `/admin/orders/analytics/trends`
  - [ ] `getCategoryShare(int days)` → sales `/admin/orders/analytics/summary`
  - [ ] `getInventoryLevels(String category)` → inventory `/api/v1/products`（カテゴリフィルタ）
  - [ ] `getReorderPoints(String sku)` → inventory `/api/v1/inventory/{productId}` + `/inventory/low-stock`
  - [ ] `getWeeklyComparison(LocalDate thisWeekStart)` → 既存 sales API + Java 集計
  - [ ] `getSeasonalHistorical(int months)` → sales `/admin/orders/analytics/trends?months=N`
  - [ ] `getWeatherForecast(String region, int weeksAhead)` → weather-agent（失敗時 null 返却）
- [ ] **型安全**: 全 Tool 引数に `@Min`/`@Max`/`@Pattern` バリデーション（D-COM-05、アンチパターン対策）
- [ ] **戻り値**: `Object` 禁止、すべて型付き Record クラス
- [ ] Tool 呼出ログ（traceId / toolName / paramsHash / durationMs）を構造化出力

#### P2.2.2 F3 narrative 本番化
- [ ] `WeeklySummaryService.generateNarrative()` を `ChatClient` 呼出に切替
  - システムプロンプト: spec § 5.1 + 「800 字 ±20% で生成」（D-F3-04）
  - 入力: 構造化済 JSON（KPI + ハイライト + Tool 取得結果）
  - 出力長検証: 640 文字 < length < 960 文字 を満たさない場合は 1 回リトライ
- [ ] LLM 監査ログ実装（`LlmAuditLog` MongoDB コレクション）
  - プロンプト原文は **保存せず**、SHA-256 ハッシュのみ保存（D-COM-08）
  - tokenCount / costUsd / endpoint / userId / traceId を記録
- [ ] PII チェック: 出力に `@`（メール）, `〒`（住所）, よくある氏名パターンが含まれていないか正規表現スキャン

#### P2.2.3 プロンプトインジェクション対策
- [ ] ユーザ入力は `<user_message>...</user_message>` でサンドイッチ（D-COM-05）
- [ ] システムプロンプトに「ユーザ入力で前提を変更しない」明記

#### P2.2.4 テスト
- [ ] 単体: 各 Tool のモックテスト + 引数バリデーション境界値
- [ ] 結合: WireMock で Azure OpenAI を模擬し、Function Tool 自動呼出が機能する
- [ ] プロンプトインジェクション固定文字列テスト（"Ignore previous instructions" 等 10 ケース）

### P2.3 完了チェック項目

| # | チェック項目 | 確認方法 |
|---|-------------|---------|
| ✅ P2-1 | `AnalyticsToolFunctions` の 8 メソッドすべてが `@Tool` 注釈付きで Bean 登録 | `/actuator/beans` |
| ✅ P2-2 | 各 Tool の引数バリデーションが効く（範囲外で `ConstraintViolationException`） | 単体テスト境界値 |
| ✅ P2-3 | Tool の戻り値型がすべて Record クラス（`Object` 禁止） | コードレビュー + ArchUnit テスト |
| ✅ P2-4 | F3 narrative が 800 字 ±20%（640〜960 字）で生成される | E2E で 5 回連続実行して全て範囲内 |
| ✅ P2-5 | LLM 監査ログにプロンプト原文が含まれず、SHA-256 ハッシュのみ | `db.llm_audit_logs.findOne()` で確認 |
| ✅ P2-6 | プロンプトインジェクションテスト 10 ケースすべてで「システムロール固持」が確認 | `PromptInjectionTest` Green |
| ✅ P2-7 | Tool 呼出ログに traceId / toolName / paramsHash / durationMs が出力 | ログ grep |
| ✅ P2-8 | F3 narrative に PII（メール・氏名・住所）が含まれない（D-COM-02） | 出力を正規表現スキャン |
| ✅ P2-9 | weather-agent が落ちていても F3 が完走する（フェイルオープン、D-F2-05） | weather-agent 停止状態で生成成功 |
| ✅ P2-10 | LLM 呼出に Resilience4j CircuitBreaker が適用（OPEN 時はフォールバックメッセージ） | LLM 強制エラーで「ただいま生成不可」表示 |
| ✅ P2-11 | F3 narrative 生成時間 < 30s（バッチ）、< 10s（手動再生成 P95） | パフォーマンステスト |
| ✅ P2-12 | 単体テスト分岐カバレッジ ≥ 80% | JaCoCo |

### P2.4 ADR 遵守確認
- ✅ D-COM-01（数値 LLM 生成禁止）: Tool 経由で取得、narrative のみ生成
- ✅ D-COM-02（PII 流出防止）
- ✅ D-COM-05（プロンプトインジェクション対策）
- ✅ D-COM-06（コスト抑制）: Tool 戻り値を要約済 JSON で渡す
- ✅ D-COM-07（Resilience4j）
- ✅ D-COM-08（監査ログハッシュ化）
- ✅ D-F3-04（narrative 800 字 ±20%）
- ✅ アンチパターン: Tool 引数 `Object` 型禁止

### P2.5 Gate 判定
全 12 項目 ✅ + ADR 8 項目 ✅ → P3 へ。

---

## P3. F1 対話分析（SSE） + F4/F5 Tool 連携

### P3.1 目的
**F1 チャット UI と SSE ストリーミング**を実装し、同時に F4/F5 用 Function Tool 4 種を追加する（UI は P5/P6 で実装、ここでは F1 から呼べる状態にする）。

### P3.2 作業内容

#### P3.2.1 F1 SSE エンドポイント
- [ ] `AdminAnalyzerController.chat()` 実装
  - `POST /api/v1/admin/ai-analyzer/chat`、`text/event-stream` を返す
  - イベント種別: `phase` / `token` / `action` / `error`
  - SSE 仕様は spec § 4.2.1 に完全準拠
- [ ] `AdminAnalyzerService.chatStream()` 実装
  - `ChatClient.prompt(...).tools(salesTool, inventoryTool, ...).stream()`
  - ストリーム中に Tool 呼出が発生したら `phase` イベントで通知
  - 末尾の「提案: SKU=XXX 数量=N 理由=...」行を構造化抽出して `action` イベント送信（D-F1-04）
- [ ] `AiChatSession` MongoDB 永続化（30 日 TTL、D-F3-02）
- [ ] 会話履歴は直近 8 ターンに制限（D-F1-01）

#### P3.2.2 F4/F5 Function Tool 追加（spec § 19.5 / § 20.6）
- [ ] `AnalyticsToolFunctions` に 4 種追加
  - [ ] `getDeadStock(String severity, String category)` → `mv_sku_velocity` クエリ
  - [ ] `getInventoryVelocity(String sku)` → 単一 SKU の販売速度
  - [ ] `getZeroHitOpportunities(int days, int limit)` → MongoDB Aggregation
  - [ ] `searchProductCatalog(String keyword)` → inventory `/api/v1/products?q=...`

#### P3.2.3 BFF（フロントエンド）
- [ ] `app/api/admin/ai-analyzer/chat/route.ts`
  - SSE pipe（既存 `agent` 系で使われているパターンを流用）
  - JWT 検証 + Bearer 転送

#### P3.2.4 フロントエンド UI
- [ ] `components/admin/ai-analyzer/AiChatPanel.tsx` 新規作成
  - 既存 `hooks/use-agent-stream.ts` を流用
  - チャット履歴表示（直近 8 ターン）+ 入力欄 + 送信ボタン
  - SSE `phase` イベント時に `WaitProgressBar` 表示
  - SSE `action` イベント時に `TipCard`（既存）でアクション提案表示
- [ ] `analytics/page.tsx` AI 分析タブに「AI に質問」セクション追加

#### P3.2.5 テスト
- [ ] 単体: SSE フレーム生成テスト、`action` 抽出正規表現テスト
- [ ] 結合: WireMock で「getCategorySales 呼出 → 自然言語回答」フローを Green
- [ ] プロンプトインジェクション 10 ケース（既存 P2 テスト + 対話特有のもの）
- [ ] E2E: 質問 → SSE 受信 → `action` 行が UI に表示

### P3.3 完了チェック項目

| # | チェック項目 | 確認方法 |
|---|-------------|---------|
| ✅ P3-1 | `POST /chat` が `text/event-stream` で応答 | `curl -N` で SSE フレーム受信 |
| ✅ P3-2 | SSE イベント種別 `phase`/`token`/`action`/`error` がすべて出力 | フレーム解析テスト |
| ✅ P3-3 | F1 初回トークン到達 < 1.5s (P95)、全体応答完了 < 8s (P95) | パフォーマンステスト |
| ✅ P3-4 | 会話履歴が 8 ターン超過時に古いメッセージが要約に置換 | 9 ターン目入力時のプロンプトサイズ検証 |
| ✅ P3-5 | 「提案: SKU=XXX 数量=N 理由=...」形式が `action` イベントに構造化抽出される | E2E + 単体テスト |
| ✅ P3-6 | F4/F5 Tool 4 種（`getDeadStock` 等）が F1 チャットから自動呼出される | 「滞留在庫を教えて」入力で Tool 呼出ログ |
| ✅ P3-7 | プロンプトインジェクション 10 ケースすべて「システムロール固持」 | `PromptInjectionTest` Green |
| ✅ P3-8 | `AiChatPanel.tsx` で SSE が token-by-token 表示される | E2E + 手動確認 |
| ✅ P3-9 | `WaitProgressBar` が `phase` イベント受信時に表示される | E2E |
| ✅ P3-10 | チャットセッションが MongoDB に保存され、30 日後に自動削除 | TTL インデックス確認 |
| ✅ P3-11 | LLM が数値を捏造しない（Tool 経由でのみ取得、D-COM-01） | 監査ログ + 手動レビュー |
| ✅ P3-12 | 単体テスト分岐カバレッジ ≥ 80% | JaCoCo |

### P3.4 ADR 遵守確認
- ✅ D-COM-01 / D-COM-02 / D-COM-04 / D-COM-05 / D-COM-07 / D-COM-08
- ✅ D-F1-01（履歴 8 ターン制限）
- ✅ D-F1-02（gpt-5: temperature=1, maxCompletionTokens=1500, seed 固定）
- ✅ D-F1-03（数値は Tool 経由）
- ✅ D-F1-04（提案行の固定形式）

### P3.5 Gate 判定
全 12 項目 ✅ + ADR 9 項目 ✅ → P4 へ。

---

## P4. F2 季節予測（Java 計算 + AI ナラティブ）

### P4.1 目的
**二段階予測**（Step 1: Java 確定計算、Step 2: LLM ナラティブ）を実装し、季節要因と気象予報を考慮した仕入計画を生成する。

### P4.2 作業内容

#### P4.2.1 マスタデータ
- [ ] `seasonal_weights` テーブルへ初期データ投入（`docker/initdb/05_seasonal_weights.sql`）
  - 7 カテゴリ × 12 か月 = 84 行（業務担当からヒアリング）
- [ ] 既存 `04_seed_dashboard_seasonal.sql` から係数を移植

#### P4.2.2 Java 確定計算（Step 1）
- [ ] `SeasonalForecaster` クラス新規作成
  - 月次需要 = 過去 2 年同月平均 × 全体成長率 × 季節係数
  - 全体成長率: 直近 90 日 YoY から線形外挿
  - 信頼区間: 過去残差の ±1.5σ（D-F2-03、LLM に変更させない）
  - SKU 別レコメンド: `topSkus.recommendOrder = max(0, predicted - stockNow)`
- [ ] 単体テスト: 年またぎ・うるう年・データ不足時のフォールバック

#### P4.2.3 LLM ナラティブ（Step 2）
- [ ] `SeasonalForecastService.generateNarrative()`
  - 入力: Step 1 結果 + `getWeatherForecast` Tool 結果 + 在庫水準
  - 気象 Tool 失敗時はフェイルオープン（`assumptions` に「気象データなし」記録、D-F2-05）
  - レスポンスに `assumptions` 配列必須（D-F2-04）

#### P4.2.4 API
- [ ] `POST /api/v1/admin/ai-analyzer/seasonal-forecast`（spec § 4.2.3）
- [ ] `ROLE_ADMIN` 必須

#### P4.2.5 BFF + UI
- [ ] `app/api/admin/ai-analyzer/seasonal-forecast/route.ts`
- [ ] `components/admin/ai-analyzer/SeasonalForecastPanel.tsx`
  - horizon セレクタ（NEXT_MONTH / NEXT_SEASON / NEXT_YEAR）
  - カテゴリ複数選択
  - 「気象考慮」チェックボックス
  - 結果テーブル（信頼区間バンド表示）+ AI コメント
- [ ] AI 分析タブに「季節予測」セクション追加

#### P4.2.6 テスト
- [ ] 単体: `SeasonalForecasterTest` 境界値・年またぎ
- [ ] 結合: 気象 Tool 失敗ケースで完走確認
- [ ] E2E: 季節予測生成 → narrative に `assumptions` の根拠が引用される

### P4.3 完了チェック項目

| # | チェック項目 | 確認方法 |
|---|-------------|---------|
| ✅ P4-1 | `seasonal_weights` テーブルに 84 行（7 カテゴリ × 12 か月）投入済み | `SELECT COUNT(*) FROM seasonal_weights` |
| ✅ P4-2 | F2 生成時間 < 12s (P95) | パフォーマンステスト |
| ✅ P4-3 | レスポンスに `categories[].predictedDemandUnits, confidenceLow, confidenceHigh` がある | スキーマ検証 |
| ✅ P4-4 | LLM が信頼区間を変更していない（D-F2-03） | Step 1 出力と Step 2 入力の差分テスト |
| ✅ P4-5 | レスポンスの `assumptions` 配列が必ず 1 件以上含まれる（D-F2-04） | 全パターンで確認 |
| ✅ P4-6 | weather-agent が落ちていても予測が完走する（D-F2-05） | weather-agent 停止状態で生成成功 |
| ✅ P4-7 | 季節係数がコードに直書きされていない（マスタ参照のみ、D-F2-02） | `grep -r "1.8\|0.2" src/` で検出されない |
| ✅ P4-8 | `considerWeather=true` 時に narrative に「気象長期予報」が引用される | 出力検査 |
| ✅ P4-9 | 信頼区間が ±15% 以内で実需と整合（過去データバックテスト） | バックテスト結果レポート |
| ✅ P4-10 | `SeasonalForecastPanel.tsx` で結果が表 + 信頼区間バンドで表示 | E2E + 手動確認 |
| ✅ P4-11 | `SeasonalForecasterTest` 分岐カバレッジ ≥ 80% | JaCoCo |
| ✅ P4-12 | LLM が数値を改変していない（Step 1 と Step 2 の数値が完全一致、D-F2-01） | 比較テスト |

### P4.4 ADR 遵守確認
- ✅ D-COM-01 / D-COM-02 / D-COM-04 / D-COM-07 / D-COM-08
- ✅ D-F2-01（二段階予測）
- ✅ D-F2-02（季節係数マスタ化）
- ✅ D-F2-03（信頼区間 ±1.5σ 固定）
- ✅ D-F2-04（assumptions 必須）
- ✅ D-F2-05（気象失敗時フェイルオープン）

### P4.5 Gate 判定
全 12 項目 ✅ + ADR 10 項目 ✅ → P5 へ。

---

## P5. F4 滞留在庫レーダー専用 UI + クーポン発行

### P5.1 目的
F4 の **専用 UI（DeadStockRadar）** を実装し、AI 提案からワンクリックでクーポン発行できる業務フローを完成させる。

### P5.2 作業内容

#### P5.2.1 バックエンド
- [ ] `DeadStockService` 実装（spec § 19.2 / § 19.3）
  - `mv_sku_velocity` から滞留候補抽出
  - severity 判定（CRITICAL / HIGH / MEDIUM、3 段階固定 D-F4-07）
  - 推奨割引率 Java 計算 + **0-40% に強制クリップ**（D-F4-01）
  - 弾力性係数は `application.yml` 外出し（D-F4-02）
- [ ] `DeadStockController` 実装（spec § 19.4）
  - `GET /dead-stock`
  - `POST /dead-stock/{sku}/issue-coupon`
- [ ] `LLM ナラティブ生成`（推奨割引率の根拠説明、2-3 文）
- [ ] `CouponClient`（coupon-service へ `WebClient` で `POST /api/v1/coupons`）
- [ ] **承認フロー**:
  - `approverUserId` 必須、JWT subject と一致確認（D-F4-03）
  - 同一 SKU で 30 日以内のクーポンが既にあれば 409（D-F4-04）
  - `dead_stock_actions` に「AI 提案値」と「実発行値」を両方記録（D-F4-05）

#### P5.2.2 BFF + UI
- [ ] `app/api/admin/ai-analyzer/dead-stock/route.ts`（GET）
- [ ] `app/api/admin/ai-analyzer/dead-stock/[sku]/issue-coupon/route.ts`（POST）
- [ ] `components/admin/ai-analyzer/DeadStockRadar.tsx`
  - 一覧テーブル（SKU・商品名・在庫・DoS・severity・推奨割引率）
  - severity フィルタ + カテゴリフィルタ
  - 各行に「クーポン発行を提案」ボタン
- [ ] `components/admin/ai-analyzer/DeadStockIssueModal.tsx`
  - 確認ダイアログ
  - 推奨割引率を編集可能（0-40% で UI 側でも制約）
  - メモ入力
  - 発行成功でトースト表示
- [ ] AI 分析タブに「滞留在庫レーダー」セクション追加

#### P5.2.3 テスト
- [ ] 単体: `DeadStockServiceTest`（境界値、severity 判定、クリップ処理）
- [ ] 結合: クーポン発行成功 → `dead_stock_actions` に記録、重複発行 → 409
- [ ] E2E: 滞留 SKU 一覧 → クーポン発行 → 重複ブロック確認

### P5.3 完了チェック項目

| # | チェック項目 | 確認方法 |
|---|-------------|---------|
| ✅ P5-1 | 推奨割引率が **必ず 0-40% の範囲内**（境界値テスト、D-F4-01） | LLM が 50% を提案しても 40% にクリップされる |
| ✅ P5-2 | severity が CRITICAL / HIGH / MEDIUM の 3 値のみ（D-F4-07） | enum 制約 + テスト |
| ✅ P5-3 | 弾力性係数が `application.yml` から読み込まれる（D-F4-02） | 設定変更で挙動変化を確認 |
| ✅ P5-4 | クーポン発行で `approverUserId` が JWT subject と一致しない場合 403（D-F4-03） | E2E |
| ✅ P5-5 | 同一 SKU の 30 日以内重複発行が 409 で拒否される（D-F4-04） | E2E |
| ✅ P5-6 | `dead_stock_actions` に AI 提案値と実発行値が両方記録される（D-F4-05） | DB 確認 |
| ✅ P5-7 | `mv_sku_velocity` から取得（リアルタイム集計でない、D-F4-06） | コードレビュー |
| ✅ P5-8 | `DeadStockRadar.tsx` で severity フィルタが機能する | E2E |
| ✅ P5-9 | クーポン発行モーダルで割引率編集時 0-40% を超えると送信ブロック | E2E |
| ✅ P5-10 | F1 チャットから「滞留在庫を教えて」で `getDeadStock` Tool が呼ばれる | チャット履歴で確認 |
| ✅ P5-11 | 単体テスト分岐カバレッジ ≥ 80% | JaCoCo |
| ✅ P5-12 | Playwright E2E 3 シナリオ Green | `npm run test:e2e` |

### P5.4 ADR 遵守確認
- ✅ D-COM-01 / D-COM-02 / D-COM-03（人間承認必須）/ D-COM-04 / D-COM-07 / D-COM-08
- ✅ D-F4-01〜D-F4-07 すべて
- ✅ アンチパターン: AI 応答だけで自動発行していない

### P5.5 Gate 判定
全 12 項目 ✅ + ADR 13 項目 ✅ → P6 へ。

---

## P6. F5 機会発見レーダー UI + F3 統合

### P6.1 目的
F5 の **機会発見レーダー UI** を実装し、F3 週次サマリーへ「機会発見ハイライト」を自動挿入する。

### P6.2 作業内容

#### P6.2.1 バックエンド
- [ ] `ZeroHitOpportunityService` 実装（spec § 20.3）
  - inventory-management-service の MongoDB `search_logs` に対する Aggregation Pipeline
  - `{ $match: { hitCount: 0 } } → $group → $match: searchCount >= 3 → $sort → $limit`
  - クエリ正規化: 全角/半角統一、サイズ表記統一（`165cm` / `１６５cm`、D-F5-03）
- [ ] LLM 連携
  - カテゴリ推定 + ブランド・モデル提案（**架空 SKU 番号は禁止**、D-F5-01）
  - `searchProductCatalog` Tool で実在確認（D-F5-02）
  - 機会損失推定 = `searchCount × 0.05 × category_avg_price`（D-F5-06）
- [ ] `ZeroHitOpportunityController`（spec § 20.5）
  - `GET /zero-hit-opportunities`
  - `POST /zero-hit-opportunities/{rank}/dismiss`
- [ ] `ZeroHitDismissalRepository`（MongoDB `zero_hit_dismissals`、TTL 90 日 D-F5-05）
- [ ] **PII 確認**: LLM プロンプトにキーワード + 件数のみ渡す（D-F5-07）

#### P6.2.2 F3 統合（spec § 20.8）
- [ ] `WeeklySummaryService.generateNarrative()` のプロンプトに「今週の機会発見トップ 3」を追加コンテキストとして渡す
- [ ] narrative 末尾に「🔍 機会発見ハイライト」セクションが自動挿入されるようプロンプト指示（D-F3-05）

#### P6.2.3 BFF + UI
- [ ] `app/api/admin/ai-analyzer/zero-hit-opportunities/route.ts`（GET）
- [ ] `app/api/admin/ai-analyzer/zero-hit-opportunities/[rank]/dismiss/route.ts`（POST）
- [ ] `components/admin/ai-analyzer/ZeroHitRadar.tsx`
  - 一覧テーブル（キーワード・回数・推定損失・priority）
  - AI 提案ブランド・モデル表示
  - 「仕入検討中にマーク」「対象外」ボタン
  - **「AI による一般市場知識からの提案であり、取扱可否は別途調査が必要」注記**（D-F5-08）
- [ ] AI 分析タブに「機会発見レーダー」セクション追加

#### P6.2.4 テスト
- [ ] 単体: クエリ正規化テスト（`165cm` / `１６５cm` / `165 cm` が同一クエリに集約）
- [ ] 単体: 機会損失計算テスト
- [ ] 結合: dismiss → 90 日 TTL で再表示
- [ ] E2E: ゼロヒット一覧 → dismiss → 再表示されない、F3 narrative にハイライト挿入

### P6.3 完了チェック項目

| # | チェック項目 | 確認方法 |
|---|-------------|---------|
| ✅ P6-1 | クエリ正規化で `165cm` / `１６５cm` / `165 cm` が同一クエリに集約（D-F5-03） | 単体テスト |
| ✅ P6-2 | `searchCount >= 3` のクエリのみ集計（D-F5-04） | テストデータで確認 |
| ✅ P6-3 | LLM が架空 SKU 番号を生成していない（ブランド+モデル系列のみ、D-F5-01） | 出力検査 + 監査ログ |
| ✅ P6-4 | `searchProductCatalog` Tool で実在確認している（D-F5-02） | 監査ログで Tool 呼出履歴 |
| ✅ P6-5 | 機会損失計算式が `searchCount × 0.05 × category_avg_price`（D-F5-06） | 単体テスト |
| ✅ P6-6 | dismiss 後 90 日間同一クエリが UI に出ない（D-F5-05） | E2E + 90 日後 TTL 自動復帰 |
| ✅ P6-7 | LLM プロンプトに `userId` / `sessionId` / `IP` が含まれない（D-F5-07） | 監査ログハッシュ検証 |
| ✅ P6-8 | UI に「AI 提案であり別途調査必要」注記が表示される（D-F5-08） | 手動確認 |
| ✅ P6-9 | F3 週次サマリー narrative 末尾に「機会発見ハイライト」が自動挿入（D-F3-05） | F3 出力検査 |
| ✅ P6-10 | レスポンスに `unique_users` / `unique_sessions` が含まれない（spec 注記準拠） | スキーマ検証 |
| ✅ P6-11 | F1 チャットから「最近見つからない商品は？」で `getZeroHitOpportunities` Tool が呼ばれる | チャット履歴 |
| ✅ P6-12 | 単体テスト分岐カバレッジ ≥ 80%、E2E Green | JaCoCo + Playwright |

### P6.4 ADR 遵守確認
- ✅ D-COM-01 / D-COM-02 / D-COM-04 / D-COM-07 / D-COM-08
- ✅ D-F3-05（F5 ハイライト挿入）
- ✅ D-F5-01〜D-F5-08 すべて
- ✅ アンチパターン: 架空 SKU 列挙していない

### P6.5 Gate 判定
全 12 項目 ✅ + ADR 14 項目 ✅ → P7 へ。

---

## P7. 監視・運用整備・本番リリース

### P7.1 目的
本番環境で安定運用するための **メトリクス・アラート・Runbook** を整備し、SLO 達成を確認して Go/No-Go 判定を行う。

### P7.2 作業内容

#### P7.2.1 メトリクス（Micrometer / Prometheus）
- [ ] `ai_analyzer_requests_total{endpoint, status}` カウンタ
- [ ] `ai_analyzer_latency_seconds{endpoint}` ヒストグラム
- [ ] `ai_analyzer_tokens_total{endpoint, model}` カウンタ
- [ ] `ai_analyzer_cost_usd_total{endpoint}` カウンタ
- [ ] `dead_stock_coupon_issued_total{severity}` カウンタ
- [ ] `zero_hit_opportunities_dismissed_total{reason}` カウンタ

#### P7.2.2 Grafana ダッシュボード
- [ ] `monitoring/grafana/dashboards/ai-analyzer.json` 新規作成
- [ ] パネル: リクエスト数・レイテンシ・エラー率・トークン消費・月次コスト・F4/F5 アクション数

#### P7.2.3 アラート（Prometheus rules）
- [ ] LLM エラー率 > 5% / 5min → Slack 通知
- [ ] LLM 呼出失敗 > 3 回連続 → Slack 通知（CircuitBreaker OPEN 連動）
- [ ] 月次 LLM コスト > $30 → Slack 通知（D-COM-06）
- [ ] F3 週次バッチ失敗 → Slack 通知

#### P7.2.4 Runbook
- [ ] `docs/runbook.md` に「AI Analyzer」セクション追記
  - LLM 障害時の復旧手順
  - キャッシュ手動クリア手順
  - クーポン誤発行時のロールバック手順
  - コスト超過時の F1 一時無効化手順

#### P7.2.5 受け入れ基準（spec § 16 + § 21）の最終確認
- [ ] 1. AI 分析タブを開いて 1.5 秒以内に週次サマリー表示
- [ ] 2. 「直近 30 日のブーツのトップ 3」で実 DB 値（捏造ゼロ）
- [ ] 3. 季節予測の narrative に気象長期予報引用
- [ ] 4. プロンプトインジェクション攻撃 10 種すべて防御
- [ ] 5. 1 ヶ月の LLM コストがダッシュボード可視化、$30 以下
- [ ] 6. 全エンドポイントが `ROLE_ADMIN` 必須、一般ユーザは 403
- [ ] 7. F4: 滞留 SKU 一覧表示、推奨割引率 0-40% 内
- [ ] 8. F4: 監査ログに「AI 提案値 vs 実発行値」両方記録
- [ ] 9. F5: クエリ正規化機能（`165cm` / `１６５cm` 同一集約）
- [ ] 10. F5: dismiss 後 90 日 UI から消える
- [ ] 11. F4 + F5: F1 チャットから両機能を Tool 経由で呼び出せる

### P7.3 完了チェック項目

| # | チェック項目 | 確認方法 |
|---|-------------|---------|
| ✅ P7-1 | Prometheus に 6 メトリクス全てが取得できる | `/actuator/prometheus` で grep |
| ✅ P7-2 | Grafana ダッシュボードで AI Analyzer パネルが表示される | 手動確認 |
| ✅ P7-3 | LLM エラー率 > 5% で Slack 通知が飛ぶ | アラート発火テスト |
| ✅ P7-4 | コスト > $30 で Slack 通知が飛ぶ | アラート発火テスト |
| ✅ P7-5 | Runbook の「AI Analyzer」セクションが完成 | docs/runbook.md レビュー |
| ✅ P7-6 | spec § 16 + § 21 の受け入れ基準 11 項目すべて Pass | 手動確認 + 自動テスト |
| ✅ P7-7 | F1〜F5 すべての SLO 目標達成（spec § 10） | パフォーマンステスト |
| ✅ P7-8 | OpenAPI ドキュメント（springdoc-openapi）が最新化 | `/swagger-ui` で確認 |
| ✅ P7-9 | docker-compose 起動 → 全サービス Healthy | `docker compose ps` |
| ✅ P7-10 | カナリアリリース 1 週間で重大インシデント 0 件 | 監視ログレビュー |
| ✅ P7-11 | バックエンド全体の分岐カバレッジ ≥ 80% | JaCoCo 集計 |
| ✅ P7-12 | フロント主要コンポーネントカバレッジ ≥ 70% | Vitest |

### P7.4 ADR 遵守確認（最終）
- ✅ D-COM-01〜D-COM-08（全 8 項目）
- ✅ D-F1-01〜D-F1-04
- ✅ D-F2-01〜D-F2-05
- ✅ D-F3-01〜D-F3-05
- ✅ D-F4-01〜D-F4-07
- ✅ D-F5-01〜D-F5-08
- ✅ アンチパターン 8 項目すべて 0 件

### P7.5 Go/No-Go 最終判定
- 全 12 項目 ✅ + ADR 全 37 項目 ✅ + アンチパターン 0 件 → **本番リリース Go**
- 1 つでも未達 → No-Go、該当フェーズに差し戻し

---

## 8. フェーズ依存関係マトリクス

| Phase | 依存元 | 提供物（後続が依存するもの） |
|-------|-------|----------------------------|
| **P0** | なし | WebClient Bean / DDL / TTL インデックス / Resilience4j |
| **P1** | P0 | `WeeklySummaryService` / MongoDB キャッシュ / AI 分析タブ |
| **P2** | P1 | Function Tool 8 種 / LLM 監査ログ / プロンプトサンドイッチ |
| **P3** | P2 | `AnalyticsToolFunctions` 拡張（F4/F5 Tool）/ SSE フレームワーク / `AiChatPanel` |
| **P4** | P2 | （P3 と並行可能）`SeasonalForecaster` / `seasonal_weights` |
| **P5** | P0 + P3 | `DeadStockService` / `CouponClient` / 監査ログテーブル |
| **P6** | P0 + P3 | `ZeroHitOpportunityService` / dismiss コレクション |
| **P7** | P1〜P6 | メトリクス / Grafana / Runbook / 受け入れ基準確認 |

→ **P3 と P4 は並行実施可能**（依存関係なし）。それ以外は逐次。

---

## 9. リスクと緩和策

| リスク | 緩和策 | 検知タイミング |
|-------|--------|--------------|
| LLM 応答品質が業務担当の期待と乖離 | プロンプト A/B テスト、業務 SME のフィードバックループ | P2 / P4 / P6 末 |
| 季節予測の精度（2 年データのみ） | バックテストで MAPE 観測、改善し続ける | P4 末 |
| 月次 LLM コスト超過 | F3 キャッシュ強化、F1 履歴制限、F2 オンデマンドのみに制限 | P7 監視で常時 |
| プロンプトインジェクション新手法 | 定期的に OWASP LLM Top 10 を確認、テストケース追加 | 各フェーズ末 |
| MongoDB / PostgreSQL 容量肥大 | TTL インデックスの動作確認、月次容量レポート | P0 設定後 / P7 監視 |
| クーポン誤発行による業務インパクト | `discountPct` の Java クリップ + 重複発行 409 + 監査ログ | P5 末 |

---

## 10. 各フェーズ末の検証フロー（標準化）

```mermaid
graph LR
    A[フェーズ実装完了] --> B[完了チェック項目を順番に確認]
    B --> C{全項目 ✅?}
    C -- いいえ --> D[該当タスクに差し戻し]
    D --> A
    C -- はい --> E[ADR 遵守確認]
    E --> F{全 ADR ✅?}
    F -- いいえ --> D
    F -- はい --> G[phase-finished-verification skill 実行]
    G --> H{抜け漏れなし?}
    H -- いいえ --> D
    H -- はい --> I[Stage Gate Review]
    I --> J{Go/No-Go?}
    J -- No-Go --> D
    J -- Go --> K[次フェーズへ]
```

---

## 11. 進捗管理テンプレート

各フェーズ完了時に以下を Markdown で記録（`docs/ai-analyzer-impl-progress.md` 推奨）:

```markdown
## Phase Pn 完了報告

- 完了日: YYYY-MM-DD
- 担当: @username
- 完了チェック項目: 12/12 ✅
- ADR 遵守確認: N/N ✅
- アンチパターン違反: 0 件
- カバレッジ: バックエンド XX% / フロント XX%
- 主要 PR: #123 #456
- 既知の課題（Pn+1 で対応）:
  - ...
- Stage Gate Review 判定: Go
```

---

## 12. 参照

- 設計書本体: [`design-docs/AI-analyzer-spec.md`](AI-analyzer-spec.md) v1.3
- ADR サマリー: spec § 22
- セキュリティ規約: `.github/instructions/security-coding.instructions.md`
- API 設計規約: `.github/instructions/api-design.instructions.md`
- テスト規約: `.github/instructions/test-standards.instructions.md`
- skill: `.github/skills/phase-finished-verification/SKILL.md`
- skill: `.github/skills/stage-gate-review/SKILL.md`

---

**変更履歴**:
- v1.0 (2026-04-18): 初版。P0〜P7 の 8 フェーズ + 各フェーズの完了チェック項目（合計 88 項目）+ ADR 遵守確認 + Gate 判定フロー
