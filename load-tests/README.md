# k6 負荷テスト基盤

## 概要

[k6](https://k6.io/) を使用した SkiShop E-Commerce マイクロサービスの負荷テスト基盤。

## 前提条件

- [k6](https://k6.io/docs/get-started/installation/) がインストール済みであること
- 対象サービスが起動済みであること (Gateway: `http://localhost:8090`)

```bash
# macOS
brew install k6

# Docker
docker run --rm -i grafana/k6 run - < scripts/health-check.js
```

## テストシナリオ

| スクリプト | 説明 | 対象エンドポイント |
|-----------|------|-----------------|
| `health-check.js` | 全サービスのヘルスチェック | `/actuator/health` (全9サービス) |
| `auth-flow.js` | 認証フロー (登録 → ログイン → トークン検証) | `/api/v1/auth/*` |
| `browse-products.js` | 商品閲覧 (一覧 → 詳細 → 検索 → カテゴリ) | `/api/v1/products/*`, `/api/v1/categories/*` |
| `cart-order-flow.js` | カート → 注文フロー | `/api/v1/cart/*`, `/api/v1/orders/*` |
| `full-scenario.js` | 全シナリオを統合したフルテスト | 上記全て |

## 実行方法

### スモークテスト (1 VU, 30 秒)
```bash
cd load-tests
k6 run --config config/smoke.json scripts/full-scenario.js
```

### ロードテスト (50 VU, 5 分)
```bash
k6 run --config config/load.json scripts/full-scenario.js
```

### ストレステスト (100→200 VU, 10 分)
```bash
k6 run --config config/stress.json scripts/full-scenario.js
```

### 個別テスト
```bash
k6 run --config config/smoke.json scripts/auth-flow.js
k6 run --config config/smoke.json scripts/browse-products.js
k6 run --config config/smoke.json scripts/cart-order-flow.js
```

## 閾値 (Thresholds)

| メトリクス | 閾値 | 説明 |
|-----------|------|------|
| `http_req_duration` (P95) | < 500ms | 95パーセンタイル応答時間 |
| `http_req_duration` (P99) | < 1000ms | 99パーセンタイル応答時間 |
| `http_req_failed` | < 1% | HTTP エラー率 |
| `http_req_duration` (avg) | < 200ms | 平均応答時間 |

## CI/CD 統合

GitHub Actions で `load-test.yml` ワークフローから実行可能。
手動トリガー (`workflow_dispatch`) で `testType` (smoke/load/stress) を指定して実行。
