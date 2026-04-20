# AI Analyzer 詳細設計書

> **対象機能**: 管理者アナリティクス画面に追加する AI 駆動の売上分析・季節仕入予測・週次トレンドサマリー
> **対象画面**: `frontend/src/app/(admin)/admin/analytics/page.tsx`（「レポート生成」タブ → 「AI 分析」タブにリネーム拡張）
> **対象サービス**: `ai-support-service`（Spring AI / Azure OpenAI）
> **作成日**: 2026-04-18
> **ステータス**: Draft v1.0

---

## 1. 目的とスコープ

### 1.1 目的
管理者が自然言語ベースで売上動向を理解し、季節要因を考慮した仕入計画を立案できる **AI アシスタント** を analytics 画面に統合する。生データを読み解く負担を AI が肩代わりし、意思決定までの時間を短縮する。

### 1.2 提供する 3 機能

| # | 機能 | ユースケース | 出力形式 |
|---|------|------------|---------|
| **F1** | 売上分析 & 仕入アドバイス | 「過去 90 日の売上を分析し、何を仕入れるべきか教えて」 | チャット応答（マークダウン + 構造化アクション提案） |
| **F2** | 季節トレンド予測 | 「来季の冬商戦に向けた仕入計画を提案して」 | カテゴリ別仕入量予測表 + AI 解説 |
| **F3** | 週次購買トレンドサマリー | 毎週月曜 07:00 自動 / オンデマンドで「今週の動き」をレポート化 | サマリーカード + 主要 KPI 変化 + 注目商品 |
| **F4** | 滞留在庫レーダー & 値下げ提案 | 「動かない在庫を捌くための値下げ案を AI に作らせる」 | 滞留 SKU 一覧 + 推奨割引率 + 1 クリック クーポン発行 |
| **F5** | 検索ゼロヒット → 仕入候補発見 | 「お客様が探したのに買えなかった商品から仕入候補を抽出」 | ゼロヒットクエリ集約表 + AI による仕入カタログ提案 |

### 1.3 非スコープ（本フェーズ対象外）
- 自動発注（Purchase Order 起票まで）→ 提案までに留め人間承認は必須
- 競合他社価格スクレイピング
- 顧客個別レコメンド（既存 `recommendation-service` の責務）

---

## 2. 実現可能性評価

### 2.1 既存資産マッピング

| 資産 | 現状 | 本機能での活用 |
|------|------|---------------|
| `ai-support-service` (Spring AI **1.0.0**, Azure OpenAI) | チャット・推薦は実動作 (`ChatClient.Builder` 注入パターン) | **再利用** — 新コントローラ `AdminAnalyzerController` を追加 |
| `ChatClient` Bean | `ChatService` / `RecommendationService` / `SearchService` で稼働 | F1〜F5 全てで Function Calling / RAG 風プロンプトに利用 |
| `AnalyticsService` (ai-support-service) | スタブ実装（ハードコード値を返す） | F2 の `getSalesForecast` を実データ統合で本実装化 |
| `sales-management-service` 売上集計 API | `/api/v1/admin/orders/analytics/{summary,trends}` 稼働中 | AI へ渡すコンテキスト源（**Tool 関数**として ChatClient に登録） |
| `user-management-service` ユーザ集計 API | `/api/v1/admin/users/analytics/summary` 稼働中 | F3 サマリーの UU / 新規ユーザ集計に利用 |
| `inventory-management-service` 商品 / 在庫 API | `/api/v1/products`, `/api/v1/inventory/{productId}`, `/api/v1/inventory/low-stock` 稼働中 | 仕入提案時の現在在庫水準を AI に提示 |
| `inventory-management-service` 検索集計 API | `/api/v1/products/analytics/search-summary` 稼働中 | F5 ゼロヒット集約のベース |
| `frontend/src/components/agent/*`（TipCard / WaitProgressBar / use-agent-stream） | スキー装備アドバイザで使用 | **そのまま流用** — analytics 画面に同 UX を移植 |
| analytics ページ「レポート生成」タブ | 既存（スタブ） | AI 分析タブへ拡張 |
| Azure OpenAI デプロイ（gpt-4o 系想定） | 環境変数で接続済 | F1〜F3 共通で利用 |

### 2.2 実装ブロッカー（事前解消必須）

| # | 課題 | 対策 |
|---|------|------|
| B1 | ai-support-service から sales-management-service へのサーバ間呼び出し設定 | 既存の `internalApiKey`（`local-dev-internal-api-key-...`）を再利用、`WebClient` Bean を追加 |
| B2 | LLM 呼び出しのレート制限・コスト | 1 ユーザ・1 機能あたり レート制限（例: F3 週次レポートは 1h キャッシュ） |
| B3 | 個人情報（顧客名・メール）が LLM に流出するリスク | プロンプト構築時に **顧客 ID/姓名はマスク**、集計値のみ渡す |
| B4 | 予測結果の **ハルシネーション** | 数値は LLM に生成させず、Java 側で集計→LLM は「解釈と提案」のみ担当 |

→ いずれも軽微で、**本フェーズで解消可能**。

---

## 3. アーキテクチャ

### 3.1 配置図

```text
┌─────────────────── Browser (Next.js) ───────────────────┐
│  /admin/analytics  →  Tab: AI 分析                       │
│  ┌──────────────────────────────────────────────────┐   │
│  │ ChatPanel (新規)    TrendSummaryCard (新規)       │   │
│  │   ↑ SSE                ↑ fetch                    │   │
│  └────────────┬─────────────────────────┬───────────┘   │
└───────────────┼─────────────────────────┼───────────────┘
                │                          │
       /api/admin/ai-analyzer/*  (Next.js BFF Route Handler — 認証ヘッダ付与)
                │                          │
                ▼                          ▼
┌──────────── api-gateway-service (8090) ──────────────────┐
│  Path-based routing                                       │
└─────┬──────────────────────────────┬─────────────────────┘
      │                                │
      ▼                                ▼
┌─ ai-support-service ─┐     ┌─ sales-management-service ───────┐
│ AdminAnalyzerCtrl    │◄───►│  /api/v1/admin/orders/analytics/*│
│ ├ /chat (SSE)         │     │  /api/v1/admin/orders/*          │
│ ├ /weekly-summary     │     └──────────────────────────────────┘
│ ├ /seasonal-forecast  │     ┌─ user-management-service ────────┐
│ ├ /dead-stock         │◄───►│  /api/v1/admin/users/analytics/* │
│ └ /zero-hit-…         │     └──────────────────────────────────┘
│                       │     ┌─ inventory-management-service ───┐
│ ChatClient (Spring AI │◄───►│  /api/v1/products,               │
│   1.0.0)              │     │  /api/v1/inventory/{productId},  │
│ ↓ Function Tools      │     │  /api/v1/inventory/low-stock,    │
│ Azure OpenAI          │     │  /api/v1/products/analytics/*    │
└──────────┬───────────┘     └──────────────────────────────────┘
           │                  ┌─ coupon-service ─────────────────┐
           └─────────────────►│  POST /api/v1/coupons (発行)      │
                              └──────────────────────────────────┘
```

### 3.2 モジュール責務

| レイヤ | 役割 |
|--------|------|
| **Frontend (Next.js)** | UI のみ。BFF 経由で AI エンドポイントを呼び、SSE で結果ストリーム表示 |
| **BFF Route Handler** (`/app/api/admin/ai-analyzer/*`) | 既存 admin BFF 同様に JWT 検証 → Bearer 付与 → ai-support-service へプロキシ |
| **ai-support-service.AdminAnalyzerController** | エンドポイント公開、`@PreAuthorize("hasRole('ADMIN')")` |
| **ai-support-service.AdminAnalyzerService** | プロンプト組立、Function Tool 登録、ChatClient 呼び出し |
| **AnalyticsToolFunctions** | `@Tool` メソッド群。LLM が自律的に売上 / 在庫 / 顧客 API を叩ける |
| **WeatherEnricher**（任意・既存 `weather-agent` 連携） | F2 で気象長期予報を文脈追加 |

### 3.3 データフロー（F1: 対話分析）

```text
管理者: 「過去 30 日のスキーブーツの売れ行きを分析して」
   │
   ▼
[BFF] POST /api/admin/ai-analyzer/chat  (JWT 検証)
   │
   ▼
[AdminAnalyzerController.chat()]  → SSE stream 開始
   │
   ▼
[ChatClient.prompt(...).tools(salesTool, inventoryTool).stream()]
   │  ┌─ LLM「getCategorySales(category=cat-boots, days=30) を呼びたい」
   │  └─ Spring AI が Function Tool 自動実行 → sales-management-service 呼出
   │     → 結果を JSON で LLM に返却
   ▼
[LLM 解釈 → 自然言語応答 + アクション提案 chunks]
   │
   ▼
[Browser に SSE token-by-token 表示]
```

---

## 4. API 設計

### 4.1 ai-support-service（新規エンドポイント）

ベース: `/api/v1/admin/ai-analyzer`

| HTTP | パス | 用途 | 認可 |
|------|------|------|------|
| `POST` | `/chat` | F1 対話型分析（SSE） | `ROLE_ADMIN` |
| `GET` | `/weekly-summary` | F3 週次サマリー（キャッシュ済み） | `ROLE_ADMIN` |
| `POST` | `/weekly-summary/refresh` | F3 強制再生成 | `ROLE_ADMIN` |
| `POST` | `/seasonal-forecast` | F2 季節予測 | `ROLE_ADMIN` |

### 4.2 リクエスト / レスポンス

#### 4.2.1 `POST /chat` (F1)

```json
// Request
{
  "sessionId": "uuid-or-null",
  "message": "過去 30 日のスキーブーツの売上を分析して",
  "context": { "days": 30, "category": "cat-boots" }   // 省略可
}
```

レスポンス: `text/event-stream`

```text
event: phase
data: {"phase":"INTENT"}

event: phase
data: {"phase":"FETCHING_DATA","tool":"getCategorySales"}

event: token
data: {"text":"過去 30 日のスキーブーツの売上は"}

event: token
data: {"text":" 35 件・¥3,250,000 で、"}

event: action
data: {"type":"PROCUREMENT_HINT","sku":"BTS-007","reason":"在庫 12 / 直近 30 日 9 売上 → 残 1.3 か月","severity":"warn"}

event: phase
data: {"phase":"COMPLETED","tokens":284,"costUsd":0.0091}
```

#### 4.2.2 `GET /weekly-summary` (F3)

```json
// Response
{
  "weekStart": "2026-04-13",
  "weekEnd":   "2026-04-19",
  "generatedAt": "2026-04-19T07:00:01Z",
  "cacheHit": true,
  "kpis": {
    "revenue":   { "value": 28500000, "wow": -0.04, "yoy": 0.12 },
    "orders":    { "value":   532,    "wow":  0.02, "yoy": 0.08 },
    "uniqueCustomers": { "value": 410, "wow": 0.05, "yoy": 0.15 },
    "avgOrderValue":   { "value": 53571, "wow": -0.06, "yoy": 0.04 }
  },
  "highlights": [
    { "icon": "📈", "text": "ヘルメット売上が前週比 +38%。HLM-017 が単週ベスト" },
    { "icon": "⚠️",  "text": "GLV-002 在庫が安全水準を下回り、要発注" }
  ],
  "narrative": "今週はシーズン終盤の駆け込み需要…（800 字程度の AI 生成サマリー）",
  "topRisingProducts": [ /* SKU ・売上・前週比 */ ],
  "topFallingProducts": [ /* 同上 */ ]
}
```

#### 4.2.3 `POST /seasonal-forecast` (F2)

```json
// Request
{
  "horizon": "NEXT_SEASON",            // NEXT_MONTH | NEXT_SEASON | NEXT_YEAR
  "categories": ["cat-ski","cat-boots","cat-wear"],   // 省略時は全カテゴリ
  "considerWeather": true
}

// Response
{
  "horizonLabel": "2026-2027 冬季シーズン (10 月〜3 月)",
  "generatedAt": "2026-04-18T05:12:00Z",
  "categories": [
    {
      "categoryId": "cat-boots",
      "categoryName": "スキーブーツ",
      "predictedDemandUnits": 1240,
      "confidenceLow": 980,
      "confidenceHigh": 1480,
      "yoyGrowth": 0.07,
      "topSkus": [
        { "sku": "BTS-007", "predictedUnits": 145, "stockNow": 60, "recommendOrder": 100 }
      ]
    }
  ],
  "narrative": "気象庁長期予報では暖冬傾向。エントリ層需要は…",
  "assumptions": [ "過去 2 シーズンの月次販売を基に…", "気象長期予報: 暖冬 60%" ]
}
```

### 4.3 BFF Route Handlers（フロント）

| パス | メソッド | 転送先 |
|------|---------|--------|
| `/app/api/admin/ai-analyzer/chat/route.ts` | POST (SSE pipe) | `ai-support-service:8087/api/v1/admin/ai-analyzer/chat` |
| `/app/api/admin/ai-analyzer/weekly-summary/route.ts` | GET / POST | 〃 `/weekly-summary` |
| `/app/api/admin/ai-analyzer/seasonal-forecast/route.ts` | POST | 〃 `/seasonal-forecast` |

実装パターンは既存 `/app/api/admin/analytics/sales/route.ts` を踏襲（JWT 検証 + Bearer 転送 + `internalApiKey`）。

---

## 5. プロンプト & Function Tool 設計

### 5.1 共通システムプロンプト（要旨）

```text
あなたはスキー EC ショップの「販売アナリスト AI」です。
- 数値の生成や推測は禁止。必ず提供されたツールから取得した値のみ使用する。
- 仕入や発注の最終判断は人間の管理者が行う。あなたは「材料を提示する」役割。
- 出力は日本語、簡潔なマークダウン。表は最大 10 行、長文の場合は箇条書き。
- 個人情報（メール・氏名）には言及しない。
- 推奨 SKU は必ず "提案: SKU=XXX 数量=N 理由=..." の形式を 1 行ずつ末尾に列挙する
  （フロント側で構造化抽出する）。
```

### 5.2 Function Tool 一覧

`@Bean` として登録、`ChatClient.tools(...)` 経由で LLM が呼出可能。

| Tool 名 | パラメータ | 内部呼出 |
|---------|----------|---------|
| `getDailyRevenue(days, category?)` | days: 7-365 | sales-management `/admin/analytics/sales` |
| `getTopProducts(days, limit, category?)` | | 〃 |
| `getCategoryShare(days)` | | 〃 |
| `getInventoryLevels(category?)` | | inventory-management `/inventory` |
| `getReorderPoints(sku?)` | | inventory-management（在庫 + 設定値） |
| `getWeeklyComparison(thisWeekStart)` | | sales-management（差分比較は Java 側で算出） |
| `getSeasonalHistorical(months)` | | sales-management（過去 N か月の月次集計） |
| `getWeatherForecast(region, weeksAhead)` | 任意 | weather-agent（既存 / 失敗時は省略） |

### 5.3 ハルシネーション抑止ルール

- **数値はすべて Tool 経由**で取得し、LLM に「この値を解釈して」と指示
- 予測の **ポイント値は Java 側で計算**（移動平均 + YoY スケーリング）し、LLM は「ナラティブと推奨」のみ生成
- レスポンスに `assumptions` 配列を必ず含めて根拠を可視化
- ChatClient 設定: gpt-5 系のため `temperature=1`（変更不可）, `maxCompletionTokens=1500`、再現性確保には `seed` を固定

---

## 6. 季節予測アルゴリズム（F2）

### 6.1 二段階予測

```text
Step 1 (確定的・Java): カテゴリ別 月次需要ポイント予測
   月次需要[m] = 過去2年同月平均 × 全体成長率 × 季節係数
   - 季節係数: 04_seed_dashboard_seasonal.sql の月次重み表をマスタ化
   - 全体成長率: 直近 90 日の YoY から線形外挿
   - 信頼区間: 過去残差の ±1.5σ

Step 2 (生成的・LLM):
   - Step 1 の予測表 + 気象長期予報 + 在庫水準を渡し、
     "narrative" と "topSkus.recommendOrder" を生成
```

### 6.2 マスタテーブル新規追加

`docker/initdb/05_seasonal_weights.sql`:

```sql
CREATE TABLE IF NOT EXISTS seasonal_weights (
    category_id  varchar(64) NOT NULL,
    month        smallint    NOT NULL CHECK (month BETWEEN 1 AND 12),
    weight       numeric(5,3) NOT NULL,
    PRIMARY KEY (category_id, month)
);
-- 例: cat-ski は 5 月（早期受注ピーク）に 1.8、8 月に 0.2
```

---

## 7. F3 週次サマリーの自動生成

### 7.1 スケジューラ
`ai-support-service` に `@Scheduled(cron = "0 0 7 * * MON")` を追加し、毎週月曜 07:00 JST にバッチ実行。

### 7.2 キャッシュ
- 結果は `weekly_summaries` MongoDB コレクションに保存（既存 ai-support-service の MongoDB 設定再利用）
- フロントの `GET /weekly-summary` はキャッシュヒット時 < 100ms
- 強制再生成は `POST /weekly-summary/refresh`（管理者操作 / 失敗時のリトライ）

### 7.3 サマリー生成手順

```text
1. sales API から「今週」「先週」「昨年同週」の集計を取得
2. KPI を Java 側で計算（wow / yoy）
3. 在庫 API から「現在水準 vs 来週予測需要」の差を取り、要発注 SKU を抽出
4. 上記すべてを構造化 JSON にまとめ、システムプロンプト + ユーザプロンプトに埋込
5. ChatClient.call() で 800 字程度の "narrative" を生成
6. JSON 全体を MongoDB に upsert
```

---

## 8. UI / UX 設計

### 8.1 タブ構成変更

`analytics/page.tsx` の TabsList:

```text
[売上分析] [ユーザー分析] [トレンド分析] [検索分析] [AI 分析]  ← 「レポート生成」を改名拡張
```

### 8.2 「AI 分析」タブのレイアウト

```text
┌─ 週次サマリー (F3) ──────────────────────────────────┐
│ 2026-04-13 〜 04-19  [🔄 再生成] [📅 過去サマリー]    │
│ ┌─KPI─┐ ┌─KPI─┐ ┌─KPI─┐ ┌─KPI─┐                    │
│ │売上 │ │注文 │ │UU   │ │AOV  │                     │
│ └─────┘ └─────┘ └─────┘ └─────┘                     │
│ ハイライト: 📈 ヘルメット +38% / ⚠️ GLV-002 要発注    │
│ ──── narrative (AI 生成) ────                       │
└─────────────────────────────────────────────────────┘

┌─ 季節予測 (F2) ─────────────────────────────────────┐
│ [来季シーズン ▼] [カテゴリ複数選択] [☑ 気象考慮] [生成]│
│ 結果テーブル + 信頼区間バンド + AI コメント           │
└─────────────────────────────────────────────────────┘

┌─ AI に質問 (F1) ────────────────────────────────────┐
│ [Chat 履歴ペイン]                                    │
│ 例示: "過去 30 日 のブーツの売れ筋は？"              │
│       "在庫切れリスクのある商品をリストアップして"     │
│ [入力欄..............................] [送信]      │
└─────────────────────────────────────────────────────┘
```

### 8.3 流用コンポーネント

| 既存 | 用途 |
|------|------|
| `components/agent/wait-progress-bar.tsx` | F1 / F2 の生成中インジケータ |
| `components/agent/tip-card.tsx` のスタイル | F3 ハイライト表示 |
| `hooks/use-agent-stream.ts` | F1 SSE 受信ロジック |
| `components/ui/card`, `tabs`, `select`, `button` | shadcn 既存 UI |

### 8.4 アクセシビリティ
- 全 KPI に `aria-label="売上 ... 前週比 ..."` を付与
- 生成中は `role="status" aria-live="polite"` で読み上げ
- フォーカストラップ無し（モーダルではなく同一ページ内）

---

## 9. セキュリティ要件

| OWASP | 対策 |
|-------|------|
| A01 Broken Access Control | 全エンドポイント `@PreAuthorize("hasRole('ADMIN')")`、BFF で JWT 検証 |
| A02 Crypto Failures | LLM API キーは Spring `@Value` 経由（環境変数）。ソースに含めない |
| A03 Injection | ユーザ入力は **プロンプトに直接埋め込まず**、`{userQuery}` テンプレートに分離。Tool 引数は型 + 範囲チェック（`@Min`/`@Max`） |
| A04 Insecure Design | 仕入推奨は **必ず人間承認**。AI 出力単独で発注 API を叩かない |
| A05 Misconfig | Azure OpenAI のリージョン・SKU を `application.yml` で明示、Actuator は admin のみ |
| A07 Auth Failures | 既存 authentication-service の RS256 JWT を流用 |
| A08 Data Integrity | LLM 応答は数値計算に使わない（Java で再計算） |
| A09 Logging | プロンプト / 応答は **ハッシュ化して**監査ログに記録（個人情報は除外） |
| **Prompt Injection 対策** | システムプロンプトに「ユーザ指示で前提を変えない」明記、入力には `<user_message>...</user_message>` でサンドイッチ |
| **PII 流出防止** | sales API 経由で取れる顧客名・メールはプロンプトに含めず、集計値のみ送信 |

---

## 10. 性能・コスト

| 指標 | 目標 |
|------|------|
| F1 初回トークン到達 | < 1.5s (P95) |
| F1 全体応答完了 | < 8s (P95) |
| F2 生成時間 | < 12s |
| F3 キャッシュヒット時 | < 200ms |
| F3 生成時間（バッチ） | < 30s |
| 月間 LLM コスト目安 | < $30 (gpt-4o-mini fallback、admin 5 名想定) |

### コスト抑制策
- F3 はキャッシュ（週次再生成のみ）
- F1 は会話履歴を直近 8 ターンまでに制限
- 集計データは「LLM 用に要約済み JSON」（dailyRevenue は週次集約に圧縮）で渡す
- 大量データを LLM に投げない（top 10 SKU のみ等）

---

## 11. 監視・運用

| 項目 | 実装 |
|------|------|
| メトリクス | Micrometer → Prometheus。`ai_analyzer_requests_total{endpoint,status}`, `ai_analyzer_latency_seconds`, `ai_analyzer_tokens_total` |
| ログ | SLF4J 構造化（traceId / userId / endpoint / tokenCount / costUsd） |
| アラート | エラー率 > 5% / 5min、または LLM 呼出失敗 > 3 回連続 |
| サーキットブレーカ | Resilience4j で Azure OpenAI 障害時にフォールバック（「ただいま生成不可」表示） |

---

## 12. データベース変更

### 12.1 MongoDB（ai-support-service）
新コレクション 2 つ:

```text
weekly_summaries
  _id            ObjectId
  weekStartDate  Date          (ユニークインデックス)
  generatedAt    Date
  payload        BSON Document (上記 4.2.2 のレスポンス全体)
  ttl            Date          (90 日 TTL)

ai_analyzer_chat_sessions
  _id            String (UUID)
  userId         String (admin user id)
  messages       [ { role, content, timestamp, tokenCount } ]
  createdAt      Date
  ttl            Date          (30 日 TTL)
```

### 12.2 PostgreSQL（sales-management-service）
- `seasonal_weights` テーブル新設（§ 6.2）
- 既存 orders/order_items テーブルへの変更は **無し**

---

## 13. テスト戦略

### 13.1 単体テスト
- `AdminAnalyzerService` のモックテスト（ChatClient をモック化、Tool 経路の検証）
- `SeasonalForecaster`（Java 計算部）の境界値・年またぎテスト
- プロンプトインジェクション固定文字列テスト（"Ignore previous instructions" 等を入力 → 既定システムプロンプトが優先されることを Tool 呼出ログで確認）

### 13.2 結合テスト（TestContainers）
- WireMock で Azure OpenAI をスタブ化、決定論的な応答で SSE フローを検証
- MongoDB / PostgreSQL は TestContainers で起動

### 13.3 フロントエンド E2E（Playwright）
- AI 分析タブ → 質問 → SSE 受信 → 末尾に「提案:」行が表示されること
- 週次サマリーカードの KPI 数値が API レスポンスと一致

### 13.4 カバレッジ目標
- バックエンド分岐カバレッジ: ≥ 80%
- フロント主要コンポーネント: ≥ 70%

---

## 14. 段階的リリース計画

| フェーズ | 期間目安 | 内容 | 完了条件 |
|---------|---------|------|---------|
| **P1** | Week 1 | F3 週次サマリー（バッチ + 表示のみ、AI 部分はスタブ narrative） | Cron 起動・MongoDB 保存・UI 表示成功 |
| **P2** | Week 2 | F3 narrative を ChatClient で本番化、Function Tools 整備 | E2E で narrative 800 字 ±20% で生成 |
| **P3** | Week 3 | F1 対話分析（SSE） | プロンプトインジェクションテスト 10 ケース合格 |
| **P4** | Week 4 | F2 季節予測（Java 計算 + AI ナラティブ） | 信頼区間が ±15% 以内で実需と整合 |
| **P5** | Week 5 | 監視・アラート整備、本番リリース | SLO 達成・Runbook 完成 |

各フェーズ末に `phase-finished-verification` skill による検証を実施。

---

## 15. 実装タスク分解（バックログ）

### バックエンド (`ai-support-service`)
- [ ] `AdminAnalyzerController` 新規作成（4 エンドポイント）
- [ ] `AdminAnalyzerService` 実装
- [ ] `AnalyticsToolFunctions` 8 メソッド `@Tool` 実装
- [ ] `WebClient` Bean 追加（sales/inventory への RPC）
- [ ] `WeeklySummaryScheduler`（`@Scheduled`）
- [ ] `SeasonalForecaster`（Java 計算）
- [ ] `WeeklySummary` / `AiChatSession` MongoDB Repository
- [ ] OpenAPI ドキュメント更新（`springdoc-openapi`）
- [ ] Resilience4j 設定（CircuitBreaker / Retry）
- [ ] 単体・結合テスト

### バックエンド (`sales-management-service`)
- [ ] `seasonal_weights` テーブル DDL（V202604xx_create_seasonal_weights.sql）
- [ ] `/admin/analytics/comparison?thisWeek=...` 新規エンドポイント（前週・前年比較を返す）

### フロントエンド
- [ ] `app/api/admin/ai-analyzer/{chat,weekly-summary,seasonal-forecast}/route.ts` 3 ファイル
- [ ] `components/admin/ai-analyzer/WeeklySummaryCard.tsx`
- [ ] `components/admin/ai-analyzer/SeasonalForecastPanel.tsx`
- [ ] `components/admin/ai-analyzer/AiChatPanel.tsx`（既存 use-agent-stream を流用）
- [ ] `analytics/page.tsx` のタブ「レポート生成」を「AI 分析」に拡張
- [ ] orval スキーマ生成（OpenAPI 経由）
- [ ] Playwright E2E 3 シナリオ

### インフラ・運用
- [ ] `docker-compose.yml` の ai-support-service 環境変数追加（`AI_ANALYZER_ENABLED=true`）
- [ ] Prometheus rules / Grafana ダッシュボード
- [ ] Runbook（`docs/runbook.md` に AI Analyzer セクション追記）
- [ ] コスト監視 Alert（Azure OpenAI 使用量 > 閾値）

---

## 16. 受け入れ基準（最終 Gate）

1. 管理者が AI 分析タブを開いて 1.5 秒以内に週次サマリーが表示される
2. 自然言語質問「直近 30 日のブーツのトップ 3」に対し、LLM が `getTopProducts` Tool を呼び、実 DB の値で回答する（数値の捏造ゼロ）
3. 季節予測のナラティブに「気象長期予報」が引用される（`considerWeather=true` 時）
4. プロンプトインジェクション攻撃 10 種すべてで「システムロール固持」が確認できる
5. 1 ヶ月の LLM コストがダッシュボードで可視化され、$30 を下回る
6. すべてのエンドポイントが `ROLE_ADMIN` 必須で、一般ユーザの呼出は 403 を返す

---

## 17. 既知のリスク・将来拡張

| リスク | 対策 / 残課題 |
|-------|--------------|
| LLM 応答の品質が業務担当の期待と乖離 | プロンプトを A/B テスト、業務 SME のフィードバックループ |
| 季節予測の精度（2 年データのみ） | 年数が増えれば自動改善。MAPE を継続観測 |
| 別言語（英語管理者） | システムプロンプトの言語指示を `Accept-Language` で動的切替（次フェーズ） |
| 自動発注機能 | 本フェーズ非対応。`procurement-service` 立ち上げ時に承認ワークフロー前提で連携 |
| ベクトル DB によるナレッジ検索 | 商品マスタやマニュアル PDF の RAG 化は P6 以降 |

---

## 18. 参照

- 既存実装: `ai-support-service/src/main/java/com/example/skishop/ai/service/{ChatService,AnalyticsService,RecommendationService}.java`
- 既存集計 API: `sales-management-service` `/admin/analytics/{sales,users,trends,search}`
- UI 流用元: `frontend/src/components/agent/{tip-panel,tip-card,wait-progress-bar}.tsx`、`frontend/src/hooks/use-agent-stream.ts`
- 季節データ: `docker/initdb/04_seed_dashboard_seasonal.sql`
- セキュリティ規約: `.github/instructions/security-coding.instructions.md`
- API 設計規約: `.github/instructions/api-design.instructions.md`

---

**承認**: 本設計に基づき P1 から実装着手可能。各フェーズ末に Stage Gate Review を実施し、Go/No-Go を判定する。

---

## 19. F4: 滞留在庫レーダー & 値下げ自動提案（追加機能）

### 19.1 ビジネス要件

| 項目 | 内容 |
|------|------|
| 目的 | 動きの止まった在庫を AI が検知し、適切な値下げ率を提案して現金化を加速 |
| 主要 KPI | 在庫回転率向上、棚卸資産月数の短縮、シーズンオフ在庫の捌き残量削減 |
| 想定ユーザ | 店長 / 商品仕入担当 / 経営層 |
| 利用頻度 | 週次（F3 サマリーと連動）+ オンデマンド |

### 19.2 滞留判定ロジック（Java 確定的計算）

ハルシネーション抑止のため、判定はすべて Java で実施し、LLM は**「解釈と推奨割引率の根拠説明」のみ**生成する。

```text
入力:
  - SKU ごとの現在在庫数 stock(sku)
  - 直近 N 日の販売数 sales_N(sku)
  - 平均販売価格 avg_price(sku)
  - 商品マスタの category, season_tag

スコア算出:
  velocity(sku)        = sales_30(sku) / 30                       [units/day]
  daysOfSupply(sku)    = stock(sku) / max(velocity(sku), 0.01)    [days]
  noSaleDays(sku)      = 直近で販売 0 だった連続日数
  inventoryValue(sku)  = stock(sku) × avg_cost(sku)               [JPY]

滞留フラグ (いずれか TRUE で「滞留候補」):
  - daysOfSupply > 90  かつ  inventoryValue > 50,000
  - noSaleDays >= 30   かつ  stock >= 5
  - 季節終了タグ (off_season=true) かつ stock > 0

severity:
  - CRITICAL: daysOfSupply > 180 かつ inventoryValue > 500,000
  - HIGH    : daysOfSupply > 120 または inventoryValue > 200,000
  - MEDIUM  : 上記以外の滞留候補
```

### 19.3 推奨割引率の算出（Java + LLM 二段階）

#### Step 1: Java による下限値計算
```text
target_days_to_clear = 21  (3 週間で在庫を捌く)
required_velocity    = stock / target_days_to_clear
velocity_lift_needed = required_velocity / current_velocity

弾力性係数 elasticity を過去クーポン実績から算出:
  - cat-ski:    -1.4   (1% 値下げで 1.4% 売上増)
  - cat-boots:  -1.6
  - cat-wear:   -2.1   (オフシーズンで弾力性高い)
  - cat-gloves: -1.8
  - cat-goggles:-1.3
  - cat-helmets:-1.2
  - cat-poles:  -1.5

discount_min = (velocity_lift_needed - 1) / |elasticity|     [0.0〜0.4 でクリップ]
gross_loss   = stock × avg_price × discount_min              [売上ベース粗利損]
```

#### Step 2: LLM によるナラティブと推奨理由生成
- Step 1 の数値表 + カテゴリ・シーズン情報を渡す
- LLM は「なぜこの割引率か」「いつ実施すべきか」「どのセグメントに訴求すべきか」を 2-3 文で生成
- 数値の生成・改変は禁止

### 19.4 API 設計

#### 19.4.1 `GET /api/v1/admin/ai-analyzer/dead-stock`
**Request Query**:
- `severity` (optional): `CRITICAL` | `HIGH` | `MEDIUM` | `ALL` (default: ALL)
- `category` (optional): `cat-ski` 等
- `limit` (default: 50, max: 200)

**Response**:
```json
{
  "generatedAt": "2026-04-18T07:00:00Z",
  "summary": {
    "totalDeadStockSkus": 23,
    "totalDeadStockValueJpy": 4250000,
    "potentialRecoveryJpy": 3187500,
    "estimatedDiscountLossJpy": 510000
  },
  "items": [
    {
      "sku": "WER-005",
      "productName": "PHENIX Thunderbolt ジャケット",
      "categoryId": "cat-wear",
      "stock": 18,
      "daysOfSupply": 142,
      "noSaleDays": 23,
      "inventoryValueJpy": 990000,
      "severity": "HIGH",
      "recommendedDiscountPct": 22,
      "estimatedClearDays": 21,
      "estimatedGrossLossJpy": 217800,
      "narrative": "シーズンオフのジャケット。エントリ層向けに 22% OFF を 3 週間限定で訴求すれば在庫消化が見込める。",
      "suggestedSegment": "ENTRY_LEVEL",
      "suggestedCouponWindowDays": 21
    }
  ]
}
```

#### 19.4.2 `POST /api/v1/admin/ai-analyzer/dead-stock/{sku}/issue-coupon`
**目的**: 滞留 SKU に対して提案された割引率でクーポンを 1 クリック発行（**承認は人間操作必須**）

**Request**:
```json
{
  "discountPct": 22,
  "validDays": 21,
  "targetSegment": "ENTRY_LEVEL",
  "approverUserId": "admin-user-uuid",
  "memo": "AI 提案 v1.0 採用"
}
```

**Response**:
```json
{
  "couponCode": "AI-WER-005-22OFF-2026Q2",
  "couponId": "uuid",
  "validFrom": "2026-04-18T00:00:00Z",
  "validTo":   "2026-05-09T23:59:59Z",
  "auditLogId": "uuid"
}
```

実装は既存 `coupon-service` の `POST /api/v1/coupons` を ai-support-service から `WebClient` で呼び出す。**承認者 ID は必須**で、監査ログに記録。

### 19.5 Function Tool 追加（F1 連携）

LLM が F1 チャットから呼び出せる Tool として下記を追加:

| Tool 名 | パラメータ | 説明 |
|---------|----------|------|
| `getDeadStock(severity?, category?)` | severity, category | 滞留在庫一覧を取得 |
| `getInventoryVelocity(sku)` | sku | 単一 SKU の販売速度 / 在庫水準を返す |

これにより F1 チャットで「在庫が捌けない商品はある？」と質問すれば自動で本機能が呼ばれる。

### 19.6 UI / UX

`analytics/page.tsx` の「AI 分析」タブに **「滞留在庫レーダー」** カードを追加:

```text
┌─ 滞留在庫レーダー (F4) ─────────────────────────────┐
│ 滞留候補 23 SKU / 在庫額 ¥4.25M                     │
│ [Severity: 全て ▼] [カテゴリ: 全て ▼] [🔄 再計算]   │
│ ┌────────────────────────────────────────────────┐ │
│ │ SKU      商品名         在庫  DoS  評価  推奨   │ │
│ │ WER-005  PHENIX Thnd…   18   142  HIGH  22% OFF│ │
│ │   └ AI: シーズンオフのジャケット…              │ │
│ │   [クーポン発行を提案]  [詳細]                  │ │
│ │ ⋯                                              │ │
│ └────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

クリック動作:
- **「クーポン発行を提案」**: 確認モーダル → `POST .../issue-coupon` → トースト表示
- **「詳細」**: SKU の販売推移グラフ（Recharts）+ AI による発生理由分析

### 19.7 セキュリティ・統制

| 項目 | 対策 |
|------|------|
| 過剰割引リスク | `discountPct` 上限を Java 側で 40% に **強制クリップ**（LLM が大きな値を返しても無視） |
| 不正発行防止 | `issue-coupon` は `ROLE_ADMIN` 必須 + `approverUserId` を JWT subject と一致確認 |
| 監査証跡 | 全発行を `dead_stock_actions` テーブルに記録（誰が / いつ / どの AI 提案を採用） |
| 二重発行防止 | 同一 SKU で 30 日以内に発行されたクーポンが既にあれば 409 |

### 19.8 データベース変更

#### 19.8.1 PostgreSQL（sales-management-service）— 新規ビュー

```sql
-- 滞留在庫判定用の集計ビュー（パフォーマンス重視で MATERIALIZED VIEW）
-- order_items の SKU カラム名は product_sku、ステータス enum は PENDING/PROCESSING/SHIPPED/DELIVERED/CANCELLED/RETURNED
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_sku_velocity AS
SELECT
    oi.product_sku                                                       AS sku,
    COUNT(*) FILTER (WHERE o.created_at >= now() - interval '30 days') AS sales_30,
    COUNT(*) FILTER (WHERE o.created_at >= now() - interval '90 days') AS sales_90,
    MAX(o.created_at)                                                  AS last_sold_at,
    AVG(oi.unit_price)                                                 AS avg_price
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
WHERE o.status NOT IN ('CANCELLED', 'RETURNED')
GROUP BY oi.product_sku;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_sku_velocity_sku ON mv_sku_velocity(sku);
-- 1 時間ごとに REFRESH MATERIALIZED VIEW CONCURRENTLY
```

#### 19.8.2 PostgreSQL（coupon-service）— 監査テーブル追加

```sql
CREATE TABLE IF NOT EXISTS dead_stock_actions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sku             varchar(64) NOT NULL,
    coupon_id       uuid        NOT NULL REFERENCES coupons(id),
    ai_suggested_pct numeric(4,1) NOT NULL,
    actual_pct      numeric(4,1) NOT NULL,
    approver_user_id varchar(64) NOT NULL,
    memo            text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    -- 30 日以内の重複発行ブロック用 partial index
    CONSTRAINT chk_pct_range CHECK (actual_pct BETWEEN 0 AND 40)
);
CREATE INDEX idx_dead_stock_actions_sku_created ON dead_stock_actions(sku, created_at DESC);
```

### 19.9 受け入れ基準

1. AI 分析タブで滞留 SKU が即座に表示され、severity でフィルタできる
2. AI が提案する割引率が **0〜40% の範囲内**に必ず収まる（境界値テスト）
3. 「クーポン発行を提案」操作で `coupon-service` に正しくクーポンが発行され、`dead_stock_actions` に監査ログが残る
4. 同一 SKU に対する 30 日以内の重複発行が 409 で拒否される
5. F1 チャットから「滞留在庫を教えて」と質問すると `getDeadStock` Tool が自動実行される

---

## 20. F5: 検索ゼロヒット → 仕入候補発見（追加機能）

### 20.1 ビジネス要件

| 項目 | 内容 |
|------|------|
| 目的 | お客様が検索したのにヒットしなかった商品から **取扱品目拡大の機会** を発見 |
| 主要 KPI | ゼロヒット率の低下、新規取扱 SKU 起点の売上増、機会損失の定量可視化 |
| 想定ユーザ | 商品仕入担当 / カテゴリマネージャ |
| 利用頻度 | 週次（F3 サマリーに連動）+ オンデマンド |

### 20.2 データソース

既存の検索ログ基盤をそのまま活用:

- **保管先**: `inventory-management-service` の MongoDB `search_logs` コレクション（`SearchLog.java`）
- **TTL**: `createdAt` に 90 日 TTL インデックス、自動削除
- **匿名ログ**: `userId` ・ `IP` ・ `sessionId` は **保存していない**（設計上 PII 保護を踏踍）

| フィールド | 型 | 用途 |
|-----------|------|------|
| `keyword` | string | 正規化済みクエリ（既に lower-case + trim 済） |
| `hitCount` | long | `0` のものを抽出 |
| `durationMs` | long | 検索処理時間 |
| `createdAt` | Instant | 期間集約 + TTL 対象 |

> ⚠️ ユーザ単位の重複や同一セッション内再検索の分析は、現状のスキーマでは不可能。実装上は `keyword` 集約 + 件数ベースで評価する。

### 20.3 集計ロジック (MongoDB Aggregation)

```text
入力期間: 直近 30 日 (default) / 7 日 / 90 日 切替可

Step 1: ゼロヒット集約 (MongoDB Aggregation)
  db.search_logs.aggregate([
    { $match: { hitCount: 0,
                createdAt: { $gte: <now-30d> } } },
    { $group: { _id: "$keyword",
                searchCount: { $sum: 1 },
                lastSearchedAt: { $max: "$createdAt" },
                avgDurationMs: { $avg: "$durationMs" } } },
    { $match: { searchCount: { $gte: 3 } } },     // ノイズ除去
    { $sort: { searchCount: -1 } },
    { $limit: 50 }
  ])
  // ⚠️ search_logs は匿名ログのため、unique_users / unique_sessions は
  //   スキーマ拡張がない限り算出不可。現フェーズでは searchCount のみを使用する。

Step 2: クエリ正規化
  - すでに SearchLog 保存時に lower-case + trim 済。
  - さらに全角/半角統一、サイズ表記統一 (165cm / 165 cm / １６５ → "165cm") を
    集計時に適用し、同義クエリをマージ。

Step 3: 機会損失推定 (集計後に Java で計算)
  category_avg_price = LLM によるカテゴリ推定後、そのカテゴリの平均購買単価
  conversion_rate    = 0.05 (検索からの購買率の付近値)
  estimated_loss     = searchCount × conversion_rate × category_avg_price
```

### 20.4 LLM による解釈と仕入提案

集計結果を LLM に渡し、以下を生成:

1. **クエリのカテゴリ推定** — `カービングスキー 165cm レディース` → `cat-ski` / 性別: F / 用途: カービング
2. **既存商品マスタとの照合** — Tool `searchProductCatalog(keyword)` で類似 SKU を検索、ヒットなしを確認
3. **仕入候補ブランド・モデル提案** — 一般的な市場知識に基づく具体ブランド名（例: "ATOMIC Cloud Q シリーズ"）
4. **優先度判定** — `search_count`, `unique_users`, `estimated_loss` から HIGH / MEDIUM / LOW

**ハルシネーション抑止**:
- 「想像上の SKU 番号」は禁止 → ブランド名・モデル系列のみ提案
- 数値（検索回数等）は Java 集計値をそのまま転記
- 商品マスタにある SKU と紐付ける場合は必ず Tool 経由で実在確認

### 20.5 API 設計

#### 20.5.1 `GET /api/v1/admin/ai-analyzer/zero-hit-opportunities`
**Request Query**:
- `days` (default: 30, range: 7-90)
- `minSearchCount` (default: 3)
- `category` (optional): 推定カテゴリでフィルタ
- `limit` (default: 30, max: 100)

**Response**:
```json
{
  "generatedAt": "2026-04-18T07:00:00Z",
  "periodDays": 30,
  "summary": {
    "totalZeroHitQueries": 142,
    "totalSearchVolume": 873,
    "estimatedTotalLossJpy": 1850000,
    "topCategoryGap": "cat-ski"
  },
  "opportunities": [
    {
      "rank": 1,
      "normalizedKeyword": "カービングスキー 165cm レディース",
      "searchCount": 47,
      "avgDurationMs": 87,
      "lastSearchedAt": "2026-04-17T22:14:00Z",
      "estimatedCategory": "cat-ski",
      "estimatedGenderTarget": "FEMALE",
      "estimatedLossJpy": 232500,
      "priority": "HIGH",
      "narrative": "165cm 前後のレディース向けカービングスキーが検索されているが在庫なし。Atomic Cloud Q シリーズ、Salomon S/Max W シリーズなどの取扱を検討。",
      "suggestedBrands": [
        { "brand": "ATOMIC", "model": "Cloud Q シリーズ" },
        { "brand": "SALOMON", "model": "S/Max W シリーズ" },
        { "brand": "HEAD",   "model": "Joy Series" }
      ],
      "relatedExistingSkus": []
    }
  ]
}
```

> ⚠️ `unique_users` / `unique_sessions` は `search_logs` コレクションに該当フィールドが存在しないため、レスポンスに含めない。将来、ログ拡張された際に追加する。

#### 20.5.2 `POST /api/v1/admin/ai-analyzer/zero-hit-opportunities/{rank}/dismiss`
**目的**: 「対応済み」「対象外」としてマーク（次回集計から除外）

**Request**:
```json
{
  "reason": "ALREADY_PROCURING" | "NOT_OUR_TARGET" | "OTHER",
  "memo": "Q3 仕入計画に組込済み"
}
```

### 20.6 Function Tool 追加（F1 連携）

| Tool 名 | パラメータ | 説明 |
|---------|----------|------|
| `getZeroHitOpportunities(days, limit)` | days, limit | ゼロヒット検索の集約結果を取得 |
| `searchProductCatalog(keyword)` | keyword | 商品マスタを検索して既存 SKU の有無を確認 |

F1 チャットで「最近お客様が探しても見つからない商品は？」と聞けば本機能が自動実行される。

### 20.7 UI / UX

`analytics/page.tsx` の「AI 分析」タブに **「機会発見レーダー」** カードを追加:

```text
┌─ 機会発見レーダー (F5) ─────────────────────────────┐
│ ゼロヒット 142 件 / 影響ユーザ 387 名               │
│ 推定機会損失: ¥1.85M [期間: 30日 ▼]                │
│ ┌──────────────────────────────────────────────┐   │
│ │ # キーワード               回数 UU  推定損失   │   │
│ │ 1 カービングスキー 165cm…  47   31  ¥232.5K   │   │
│ │   └ AI: 165cm レディース向け…                 │   │
│ │   提案: ATOMIC Cloud Q / SALOMON S/Max W      │   │
│ │   [仕入検討中にマーク] [対象外]                │   │
│ │ 2 ⋯                                          │   │
│ └──────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

### 20.8 F3 週次サマリー連動

F3 の `narrative` 生成プロンプトに「今週の機会発見トップ 3」を追加コンテキストとして渡し、レポートの末尾に **「機会損失アラート」** セクションを自動挿入:

```text
🔍 機会発見ハイライト
  - 「カービングスキー 165cm レディース」で 47 件のゼロヒット (推定損失 ¥232K)
  - 165cm レディース向けカービングスキーの取扱検討を推奨
```

### 20.9 セキュリティ・統制

| 項目 | 対策 |
|------|------|
| PII 流出防止 | `search_logs` は設計上匿名ログとして `userId` / `IP` / `sessionId` を保存していないため、LLM へのコンテキスト提供ではキーワード + 件数のみとなり、追加マスキング不要 |
| 推測ブランドの責任範囲 | UI に「AI による一般的な市場知識からの提案であり、取扱可否は別途調査が必要」と注記表示 |
| 競合監視や著作権リスク | 商品名・モデル名の使用は一般公開情報の範囲内 |
| 「対象外」マーク権限 | `ROLE_ADMIN` 必須 + 監査ログ記録 |

### 20.10 データベース変更

#### 20.10.1 MongoDB（inventory-management-service）— ゼロヒット集計インデックス

```javascript
// 既存の search_logs コレクションにゼロヒット集計高速化用の複合インデックスを追加
db.search_logs.createIndex(
  { hitCount: 1, createdAt: -1 },
  { name: "idx_zero_hit_recent",
    partialFilterExpression: { hitCount: 0 } }
);
```

#### 20.10.2 MongoDB（ai-support-service）— Dismiss 状態管理コレクション

```text
zero_hit_dismissals
  _id                ObjectId
  normalizedKeyword  string  (ユニークインデックス)
  reason             string  (ALREADY_PROCURING | NOT_OUR_TARGET | OTHER)
  memo               string
  dismissedBy        string  (admin user id)
  dismissedAt        Date
  expiresAt          Date    (TTL インデックス: 90 日後に自動削除 → 集計対象に自動復帰)
```

集計時に ai-support-service 側で `dismissedKeywords` セットを fetch し、Java コードで in-memory 除外する（サービス間 DB ジョインを避けるため）。

### 20.11 受け入れ基準

1. ゼロヒットクエリが検索回数降順でソートされ、正規化（全角/半角・大小）が適用されている
2. AI が提案するブランド・モデルが**実在する**ものである（商品マスタとの照合、または既知ブランド辞書とのマッチング）
3. 「対象外」マーク後、90 日間は同一クエリが結果に出現しない
4. F3 週次サマリーに「機会発見ハイライト」が自動挿入される
5. F1 チャットから「最近見つからない商品は？」で `getZeroHitOpportunities` Tool が自動実行される
6. PII（`userId` 列等）が LLM プロンプトに含まれていないことが監査ログから確認できる（そもそも `search_logs` は匿名ログのため保存されていない）

---

## 21. F4 / F5 統合実装スケジュール

既存の P1〜P5 計画に組み込む形で追加:

| Phase | 既存スコープ | F4 / F5 追加スコープ |
|-------|-------------|--------------------|
| **P3** (Week 3) | F1 対話分析 (SSE) | **F4 + F5 の Function Tool 4 種を追加実装**（F1 から呼び出せる状態にする） |
| **P4** (Week 4) | F2 季節予測 | **F4 滞留在庫レーダー 専用 UI**（フル機能版） |
| **P5** (Week 5) | 監視・アラート | **F5 機会発見レーダー UI** + F3 サマリーへの統合 |

### 追加実装タスク（バックログ）

#### バックエンド (`ai-support-service`)
- [ ] `DeadStockService`（F4 集計 + 弾力性計算）
- [ ] `DeadStockController`（GET / issue-coupon）
- [ ] `ZeroHitOpportunityService`（F5 集計 + LLM 連携）
- [ ] `ZeroHitOpportunityController`（GET / dismiss）
- [ ] Function Tool 4 種を `AnalyticsToolFunctions` に追加
- [ ] CouponClient（coupon-service への WebClient ラッパ）

#### バックエンド (`sales-management-service`)
- [ ] `mv_sku_velocity` MATERIALIZED VIEW + 1h refresh ジョブ
- [ ] `idx_search_logs_zero_hit` インデックス
- [ ] `zero_hit_dismissals` テーブル + CRUD API

#### バックエンド (`coupon-service`)
- [ ] `dead_stock_actions` テーブル
- [ ] 重複発行チェックロジック追加

#### フロントエンド
- [ ] `components/admin/ai-analyzer/DeadStockRadar.tsx`
- [ ] `components/admin/ai-analyzer/DeadStockIssueModal.tsx`（クーポン発行確認）
- [ ] `components/admin/ai-analyzer/ZeroHitRadar.tsx`
- [ ] BFF: `app/api/admin/ai-analyzer/dead-stock/[...]/route.ts`
- [ ] BFF: `app/api/admin/ai-analyzer/zero-hit-opportunities/[...]/route.ts`
- [ ] Playwright E2E: 滞留在庫一覧 → クーポン発行 → 重複ブロック確認
- [ ] Playwright E2E: ゼロヒット一覧 → dismiss → 再表示されないこと

### 追加受け入れ基準（最終 Gate）

7. F4: 滞留 SKU が AI 分析タブから一覧でき、推奨割引率が 0-40% に必ずクリップされる
8. F4: クーポン発行操作で監査ログに「AI 提案値 vs 実発行値」が両方記録される
9. F5: ゼロヒットクエリの正規化が機能し、「165cm」「１６５cm」が同一として集約される
10. F5: dismiss したクエリが 90 日間 UI から消える
11. F4 + F5: F1 チャットから両機能を Function Tool 経由で呼び出せる

---

## 22. 設計上の重要決定事項（ADR サマリー）

本設計を実装する上で **絶対に動かしてはならない判断** を以下に集約する。実装時の迷いやレビュー時のチェックリストとして利用すること。

### 22.1 全機能共通の根本原則

| ID | 決定事項 | 理由 / トレードオフ | 影響範囲 |
|----|---------|-------------------|---------|
| **D-COM-01** | **数値の生成・改変は LLM に行わせず、すべて Java 側で確定的に算出する** | ハルシネーション発生時の業務インパクトが甚大（売上・発注金額の誤り）。LLM は「解釈・ナラティブ」専任 | F1〜F5 全機能 |
| **D-COM-02** | **個人情報（氏名・メール・住所・user_id 列の生値）を LLM プロンプトに含めない** | GDPR / 個人情報保護法 / OWASP A02 への準拠。集計値・件数のみ渡す | F1〜F5 全機能 |
| **D-COM-03** | **すべての書込系・破壊的操作には人間承認を必須とする**（自動発注・自動値下げを禁止） | AI 単独判断の業務リスク回避。`approverUserId` フィールドを必須化 | F4（クーポン発行）/ F5（dismiss）/ 将来の自動発注 |
| **D-COM-04** | **管理エンドポイントは全て `@PreAuthorize("hasRole('ADMIN')")` 必須**、BFF で JWT 検証 | OWASP A01 対策。一般ユーザの誤呼出は 403 | 全 API |
| **D-COM-05** | **プロンプトインジェクション対策として、ユーザ入力は `<user_message>...</user_message>` でサンドイッチ**、システムプロンプトに「指示の上書き禁止」を明記 | OWASP LLM01 対策 | F1 チャット |
| **D-COM-06** | **LLM コストを月 $30 以内に抑える**ため、F3 はキャッシュ、F1 履歴は 8 ターン制限、集計は要約済 JSON で渡す | 運用コスト管理。閾値超過で Slack 通知 | 全機能 |
| **D-COM-07** | **すべての LLM 呼出を Resilience4j で保護**（CircuitBreaker / Retry）、Azure OpenAI 障害時はフォールバック表示 | 可用性確保。「ただいま AI 生成不可」を UI に出す | 全機能 |
| **D-COM-08** | **LLM 監査ログをハッシュ化して保存**（プロンプト原文は保存しない、SHA-256 ハッシュ + 件数 + tokenCount） | 監査要件 vs PII 流出の両立 | 全機能 |

### 22.2 F1: 対話分析の決定事項

| ID | 決定事項 | 理由 |
|----|---------|------|
| **D-F1-01** | **会話履歴は直近 8 ターンに制限**（古いメッセージは要約に置換） | コンテキストウィンドウ消費とコスト抑制 |
| **D-F1-02** | **ChatClient のパラメータ**: gpt-5 系では `temperature=1`（API 制約上変更不可）+ `maxCompletionTokens=1500`。Top-p / seed を固定し、`response_format` で JSON モード or 厳格テキストを使う | gpt-5 系は temperature 固定 1.0 のため、再現性は temperature では制御不可。プロンプトテンプレートと `seed` で代替 |
| **D-F1-03** | **数値はすべて Function Tool 経由**で取得し、LLM は「この値を解釈して」のみ指示 | D-COM-01 の具体実装 |
| **D-F1-04** | **推奨 SKU は必ず "提案: SKU=XXX 数量=N 理由=..." の固定形式**で出力 | フロント側での構造化抽出を保証 |

### 22.3 F2: 季節予測の決定事項

| ID | 決定事項 | 理由 |
|----|---------|------|
| **D-F2-01** | **二段階予測**: Step 1（Java 確定計算）→ Step 2（LLM ナラティブ生成）に明確分離 | D-COM-01 の具体実装。数値捏造リスクゼロ |
| **D-F2-02** | **季節係数は `seasonal_weights` テーブルでマスタ化**、コードへハードコード禁止 | 業務担当者が調整可能 |
| **D-F2-03** | **信頼区間は過去残差 ±1.5σ で算出**、LLM に「広げる / 狭める」を許可しない | 統計的根拠の保証 |
| **D-F2-04** | **`assumptions` 配列を必ずレスポンスに含める** | 予測根拠の可視化、業務担当の検証可能性 |
| **D-F2-05** | **気象データ取得失敗時はフェイルオープン**（その文脈なしで予測を継続、`assumptions` に記録） | weather-agent 障害で本機能が停止しないように |

### 22.4 F3: 週次サマリーの決定事項

| ID | 決定事項 | 理由 |
|----|---------|------|
| **D-F3-01** | **生成タイミングは毎週月曜 07:00 JST 固定**、結果は MongoDB にキャッシュ | バッチ性能 + キャッシュヒット時 < 200ms 達成 |
| **D-F3-02** | **`weekly_summaries` コレクションは 90 日 TTL**、`ai_analyzer_chat_sessions` は 30 日 TTL | データ量制御と GDPR 期間制限 |
| **D-F3-03** | **手動再生成は管理者のみ可能**、レート制限 1 時間 1 回 | 不要な LLM コール抑制 |
| **D-F3-04** | **narrative は 800 字 ±20% に収める**プロンプト指示 | UI レイアウト崩壊と読了負担の両回避 |
| **D-F3-05** | **F5 機会発見ハイライトを narrative 末尾に自動挿入** | 経営層の目に確実に止まる導線 |

### 22.5 F4: 滞留在庫レーダーの決定事項

| ID | 決定事項 | 理由 |
|----|---------|------|
| **D-F4-01** | **推奨割引率は Java 側で 0-40% に強制クリップ**、LLM が大きな値を返しても無視 | 過剰割引による粗利毀損の絶対防止 |
| **D-F4-02** | **弾力性係数はカテゴリ別に定数として持つ**（cat-ski: -1.4 など）、`application.yml` で外出し | 業務担当による調整可能性 + 単体テスト容易性 |
| **D-F4-03** | **クーポン発行は `approverUserId` 必須**、JWT subject と一致確認 | D-COM-03 の具体実装。なりすまし発行防止 |
| **D-F4-04** | **同一 SKU で 30 日以内のクーポン重複発行を 409 で拒否** | 顧客クレーム回避、社内統制 |
| **D-F4-05** | **`dead_stock_actions` 監査テーブルに「AI 提案値」と「実発行値」を両方記録** | 後日の AI 提案精度評価、社内監査対応 |
| **D-F4-06** | **滞留判定の元データは `mv_sku_velocity` MATERIALIZED VIEW から取得**（1h refresh） | リアルタイム集計で本番 DB に負荷を与えない |
| **D-F4-07** | **severity は 3 段階固定**（CRITICAL / HIGH / MEDIUM）、LLM に変更させない | UI の一貫性、フィルタ操作の単純化 |

### 22.6 F5: 検索ゼロヒット → 仕入候補の決定事項

| ID | 決定事項 | 理由 |
|----|---------|------|
| **D-F5-01** | **LLM が提案する仕入候補は「ブランド名 + モデル系列」までに限定**、架空 SKU 番号の生成を禁止 | ハルシネーション防止 + 業務担当の確認可能性 |
| **D-F5-02** | **既存商品マスタとの照合は必ず `searchProductCatalog` Tool 経由** | 「実は在庫していた」という誤検知防止 |
| **D-F5-03** | **クエリ正規化ルール**: 全角/半角統一、小文字化、サイズ表記統一（`165cm` / `１６５cm` → `165cm`） | 同一意図クエリの集約精度向上 |
| **D-F5-04** | **集計対象は `search_count >= 3` のクエリのみ**（タイポ・1 回限りのノイズ除去） | UI の信号対雑音比向上 |
| **D-F5-05** | **dismiss 操作は 90 日で自動失効**、再度集計対象に戻る | 季節性により再機会化する商品への対応 |
| **D-F5-06** | **機会損失推定は `unique_users × 0.05 conversion × category_avg_price`** の固定式 | 業界平均ベース、根拠の明示 |
| **D-F5-07** | **`search_logs` は設計上 `userId` / `sessionId` / `IP` を保存しない匿名ログとし、LLM プロンプトにはキーワード + 件数のみ渡す**（仮に将来ログ拡張される場合も件数集計値のみとする） | D-COM-02 の具体実装 |
| **D-F5-08** | **UI に「AI による一般市場知識からの提案であり、取扱可否は別途調査が必要」と注記表示** | LLM 提案の責任範囲明示 |

### 22.7 やってはいけないこと（アンチパターン）

実装中に「便利だから」の名目で以下を実装してはならない:

- ❌ LLM の応答テキストを正規表現で抽出して数値計算に使う（D-COM-01 違反）
- ❌ クーポン発行・在庫変更・発注を AI の応答だけで自動実行する（D-COM-03 違反）
- ❌ 「とりあえずデバッグのため」プロンプトに `user.email` や `order.shipping_address` を入れる（D-COM-02 違反）
- ❌ gpt-5 系 deployment に対して `temperature=0.7` 等を指定する（API エラー、D-F1-02 違反）
- ❌ 弾力性係数をコードに直書き（D-F4-02 違反）
- ❌ ゼロヒット LLM プロンプトに「実在しなさそうでも仕入候補を 5 つ列挙して」と指示（D-F5-01 違反）
- ❌ `weekly_summaries` の TTL を外す（D-F3-02 違反、無制限の MongoDB 肥大化）
- ❌ Function Tool に `Object` 型の引数を許す（型安全性低下、プロンプトインジェクション余地）

### 22.8 コードレビューチェックリスト（PR テンプレートに転記推奨）

PR 提出時に以下を確認:

- [ ] LLM レスポンスから数値を抽出して計算に使っていないか（D-COM-01）
- [ ] プロンプト構築コードに PII フィールドが含まれていないか（D-COM-02）
- [ ] 書込系操作に `approverUserId` 検証があるか（D-COM-03）
- [ ] 全エンドポイントに `@PreAuthorize` があるか（D-COM-04）
- [ ] ユーザ入力が `<user_message>` でサンドイッチされているか（D-COM-05）
- [ ] LLM 呼出に Resilience4j が適用されているか（D-COM-07）
- [ ] 監査ログにプロンプト原文が含まれていないか、ハッシュ化されているか（D-COM-08）
- [ ] F4 の場合、`discountPct` クリップ処理（max 40%）が Java 側で行われているか（D-F4-01）
- [ ] F5 の場合、`searchProductCatalog` Tool で実在確認しているか（D-F5-02）

---

**変更履歴**:
- v1.0 (2026-04-18): 初版（F1〜F3）
- v1.1 (2026-04-18): F4「滞留在庫レーダー」、F5「検索ゼロヒット → 仕入候補」を追加
- v1.2 (2026-04-18): § 22「設計上の重要決定事項（ADR サマリー）」を追加。8 つの根本原則 + 機能別 27 件の決定事項 + アンチパターン + PR チェックリストを明文化
- v1.3 (2026-04-18): コードベース実装との整合性検証に基づく修正。
  - Spring AI バージョンを `1.x` → `1.0.0` に修正（pom.xml 検証済み）
  - § 2.1 / § 3.1 / § 5.2 / § 18 / § 19 の API パスを実装に合わせて修正:
    - sales: `/api/v1/admin/orders/analytics/{summary,trends}`
    - users: `/api/v1/admin/users/analytics/summary`
    - inventory: `/api/v1/products`, `/api/v1/inventory/{productId}`, `/api/v1/inventory/low-stock`
    - 検索: `/api/v1/products/analytics/search-summary`（inventory-management-service）
    - クーポン発行: `POST /api/v1/coupons`（`/admin/` プレフィックスなし）
  - § 19.8.1 MV: `oi.sku` → `oi.product_sku`、状態 enum `REFUNDED` → `RETURNED`
  - § 20 (F5) 全体を `search_logs` 実装の実態に合わせて全面改訂:
    - 保管先: PostgreSQL → **MongoDB** in inventory-management-service
    - カラム名: `result_count` → `hitCount`、`searched_at` → `createdAt`
    - 匿名ログのため `userId` / `sessionId` / `IP` は **保存されていない**
    - 集計を SQL → MongoDB Aggregation Pipeline に書き換え
    - レスポンスから `uniqueUsers` / `uniqueSessions` を削除（データソースなし）
    - § 20.10.1 PG INDEX → MongoDB partial index、§ 20.10.2 dismiss テーブル → MongoDB collection
    - D-F5-07 を「匿名ログ前提」に再定義

