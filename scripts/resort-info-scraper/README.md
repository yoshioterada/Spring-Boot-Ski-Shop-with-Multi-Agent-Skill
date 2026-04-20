# Resort Info Scraper (プロトタイプ)

スキー場の公式 Web サイトから「現地らしい」情報 (シグネチャ・ゲレ食・温泉・キッズ施設・安全注意・ローカル小ネタ) を Azure OpenAI で構造化抽出し、`frontend/src/lib/tips/resort-dictionary.ts` の `ResortMeta` キュレーション欄に取り込むためのバッチプロトタイプです。

## 位置付け

- **目的**: フロントエンドの「待ち時間 Tip」表示に厚みを持たせる現地情報を、運用コスト最小で集める。
- **方針**: 完全自動投入ではなく、**LLM が一次ドラフトを作成 → 人手レビュー → ts ファイルに取り込み** の半自動運用を想定。
- **法的留意**:
  - 各スキー場サイトの `robots.txt` および利用規約を必ず確認してください。
  - 公開情報のみを対象とし、個人情報・課金情報・連絡先は抽出しません。
  - 出力文章は「〜と書かれています」「〜と言われています」など出典を明示する語尾になるよう、プロンプトで強制しています。

## 必要環境

- Node.js 20 以上
- Azure OpenAI リソース (`gpt-4o-mini` デプロイ推奨。Structured Outputs / `json_schema` 対応モデル)

## セットアップ

```bash
cd scripts/resort-info-scraper
npm install
```

## 環境変数

| 変数 | 必須 | 例 / デフォルト |
| --- | --- | --- |
| `AZURE_OPENAI_ENDPOINT` | ✅ | `https://xxx.openai.azure.com` |
| `AZURE_OPENAI_API_KEY`  | ✅ | `********` |
| `AZURE_OPENAI_DEPLOYMENT` | — | `gpt-4o-mini` |
| `AZURE_OPENAI_API_VERSION` | — | `2024-10-21` |
| `MAX_HTML_CHARS` | — | `30000` (LLM に渡す上限文字数) |
| `FETCH_TIMEOUT_MS` | — | `15000` |
| `REQUEST_INTERVAL_MS` | — | `1500` (各 fetch の間隔) |

## 実行

> 以下のサンプルは、リポジトリ直下の `.env` に
> `AZURE_OPENAI_ENDPOINT` / `AZURE_OPENAI_API_KEY` / `AZURE_OPENAI_DEPLOYMENT`
> が記載されている前提で、コマンド先頭で読み込む例にしています。

### A. 全 5 リゾートを一括抽出

```bash
cd scripts/resort-info-scraper

# 初回のみ
npm install

# .env (リポジトリ直下) から環境変数を読み込んで実行
set -a; source ../../.env; set +a
node scrape.mjs
# または
npm run scrape
```

### B. 1 リゾートだけ抽出

```bash
cd scripts/resort-info-scraper
set -a; source ../../.env; set +a

node scrape.mjs --resort 白馬
# 苗場 / 白馬 / 志賀高原 / 野沢温泉 / ニセコ から指定
```

### C. 環境変数をその場で渡す (`.env` を使わない場合)

```bash
cd scripts/resort-info-scraper

AZURE_OPENAI_ENDPOINT=https://xxx.openai.azure.com \
AZURE_OPENAI_API_KEY=*** \
AZURE_OPENAI_DEPLOYMENT=gpt-4o-mini \
AZURE_OPENAI_API_VERSION=2024-10-21 \
node scrape.mjs
```

### D. デプロイ名を一時的に切り替え

```bash
cd scripts/resort-info-scraper
set -a; source ../../.env; set +a

# 例: 一時的に gpt-4o-mini を強制
AZURE_OPENAI_DEPLOYMENT=gpt-4o-mini node scrape.mjs --resort 野沢温泉
```

### 出力先

- 各リゾート 1 ファイル: `scripts/resort-info-scraper/output/{resortKey}.json`
- ディレクトリは存在しなければ自動作成されます
- `output/` は `.gitignore` 対象 (人手レビュー前の中間生成物)

### コンソール出力例

```text
=== 白馬 ===
  fetching: https://www.happo-one.jp/
  fetching: https://www.hakubavalley.com/
  extracting via Azure OpenAI (gpt-4o-mini)...
  -> /path/to/scripts/resort-info-scraper/output/白馬.json

Done.
```

### よくあるエラーと対処

| エラー | 原因 / 対処 |
| --- | --- |
| `[ERROR] AZURE_OPENAI_ENDPOINT と AZURE_OPENAI_API_KEY を設定してください。` | 環境変数未設定。`set -a; source ../../.env; set +a` を実行したか確認 |
| `[WARN] ... -> HTTP 403 / 503` | 公式サイト側でブロック。時間をあけて再試行、または該当 URL を `resort-urls.json` から外す |
| `[WARN] fetch failed: ... AbortError` | `FETCH_TIMEOUT_MS` を伸ばす (例: `FETCH_TIMEOUT_MS=30000`) |
| `[ERROR] LLM extraction failed: 400 ...` | デプロイがモデル仕様 (Structured Outputs / `json_schema`) に対応しているか確認。`AZURE_OPENAI_API_VERSION=2024-10-21` 以降を推奨 |
| `Empty LLM response` | 入力 HTML が空。`MAX_HTML_CHARS` や URL を見直す |

## 出力例

```json
{
  "signature": "1998 年長野五輪のダウンヒルコース...と書かれています。",
  "courses": {
    "beginnerRatio": 0.3,
    "intermediateRatio": 0.5,
    "advancedRatio": 0.2,
    "longestRunM": 8000,
    "maxSlopeDeg": 35,
    "note": "..."
  },
  "kidsFacility": { "name": "...", "ageMin": 4, "ageMax": 12, "price": "...", "note": "..." },
  "recommendedRestaurants": [ ... ],
  "nearbyOnsen": [ ... ],
  "safetyNotes": [ ... ],
  "localTips": [ ... ],
  "lastCuratedAt": "2026-04-18",
  "_meta": {
    "sourceUrls": ["https://..."],
    "deployment": "gpt-4o-mini",
    "extractedAt": "2026-04-18T01:23:45.000Z",
    "note": "LLM 抽出結果。人手レビュー後に取り込むこと。"
  }
}
```

## 取り込みフロー (推奨)

1. `node scrape.mjs --resort 白馬`
2. `output/白馬.json` をレビュー
   - 事実誤認 / 文体 / 出典明示 / NG 表現 をチェック
3. `frontend/src/lib/tips/resort-dictionary.ts` の `白馬` エントリに必要フィールドを反映
4. `_meta` 欄は ts には取り込まない
5. PR を作成し、編集者 / 法務にレビュー依頼

## 制限事項

- 公式サイトが SPA (JS で描画) の場合、本スクレイパーは初期 HTML しか取得できません。必要に応じ Playwright 等への置換を検討してください。
- 抽出は LLM のため、誤情報・古い情報を含む可能性があります。**必ず人手レビューしてから取り込んでください**。
- 1 リゾートあたり 1〜2 円程度のコスト目安 (`gpt-4o-mini`、30k chars 入力)。

## URL 設定の追加

`resort-urls.json` に下記の形式で追加してください。

```json
"妙高": {
  "officialUrls": [
    "https://www.example.com/myoko/"
  ],
  "note": "短い説明"
}
```
