# ロールバック計画

> **プロジェクト**: SkiShop E-Commerce Microservices Platform
> **対象バージョン**: 1.0.0
> **作成日**: 2026-03-19
> **最終更新**: 2026-03-19

---

## 1. ロールバック方針

### 基本原則
- **Blue-Green デプロイ**: 新旧バージョンを並行稼働させ、問題発生時にルーティング切替のみでロールバック
- **ロールバック判断基準**: ヘルスチェック失敗率 > 5%、エラー率 > 1%、レイテンシ P99 > 3 秒
- **ロールバック決定権限**: SRE リーダーまたはオンコール担当者
- **ロールバック目標時間**: 5 分以内（ルーティング切替のみ）

### ロールバック不可能な変更
以下は DB マイグレーションを伴うため、ロールバックには専用の手順が必要:

| 変更 | 影響 | ロールバック方法 |
|------|------|----------------|
| `@Version` カラム追加 (V2〜V5) | 新カラムは NULL 許容 | カラムは残存可。旧バージョンで無視される |
| `TIMESTAMP` → `TIMESTAMP WITH TIME ZONE` | データ型変更 | 変換済みデータは互換性あり。逆変換不要 |
| `DOUBLE PRECISION` → `NUMERIC(5,2)` | 精度変更 | 旧バージョンでも読み取り可能 |

> **注**: 上記のマイグレーションは全て**前方互換** (forward-compatible) であり、旧バージョンのアプリケーションでも正常動作する。DB ロールバックは不要。

---

## 2. サービス別ロールバック手順

### 2.1 共通手順 (全サービス共通)

```bash
# 1. 現在のバージョンを確認
kubectl get deployment <service-name> -o jsonpath='{.spec.template.spec.containers[0].image}'

# 2. 前バージョンにロールバック
kubectl rollout undo deployment/<service-name>

# 3. ロールバック完了を確認
kubectl rollout status deployment/<service-name>

# 4. ヘルスチェック確認
curl -f http://<service-url>/actuator/health
```

### 2.2 Docker イメージによるロールバック

```bash
# 特定バージョンにロールバック
kubectl set image deployment/<service-name> \
  <service-name>=ghcr.io/<org>/<service-name>:<previous-tag>

# 例: authentication-service を前回リリースに戻す
kubectl set image deployment/authentication-service \
  authentication-service=ghcr.io/skishop/authentication-service:v0.9.0
```

### 2.3 サービス別固有事項

#### api-gateway-service (Port: 8090)
- **影響範囲**: 全サービスへのルーティング
- **ロールバック優先度**: 最高 (P0)
- **注意事項**: Gateway ロールバック時は全下流サービスのヘルスチェックも確認
- **ロールバック時の一時対応**: Nginx/LB で直接各サービスにルーティング可

#### authentication-service (Port: 8080)
- **影響範囲**: 全認証フロー
- **ロールバック優先度**: P0
- **注意事項**: JWT 署名鍵が変わっていないこと。鍵が変更済みの場合、既存トークンが無効化される
- **DB マイグレーション**: V2 (`version` カラム) — 前方互換のためロールバック影響なし

#### user-management-service (Port: 8081)
- **影響範囲**: ユーザープロフィール管理
- **ロールバック優先度**: P1
- **API パス変更**: `/api/users` → `/api/v1/users` — Gateway ルートも同時にロールバック必要
- **DB マイグレーション**: 前方互換

#### inventory-management-service (Port: 8082)
- **影響範囲**: 商品カタログ、在庫管理
- **ロールバック優先度**: P1
- **API パス変更**: `/api/products` → `/api/v1/products` — Gateway ルートも同時にロールバック必要
- **DB**: MongoDB — スキーマレスのためロールバック影響なし
- **キャッシュ**: Caffeine キャッシュ — ロールバック後にキャッシュは自動クリア

#### sales-management-service (Port: 8083)
- **影響範囲**: 注文処理
- **ロールバック優先度**: P0
- **DB マイグレーション**: V2 (`version` カラム + インデックス) — 前方互換

#### payment-cart-service (Port: 8084)
- **影響範囲**: カート、決済処理
- **ロールバック優先度**: P0
- **DB マイグレーション**: V2 (`version` カラム) — 前方互換
- **注意事項**: 進行中の決済トランザクションの整合性確認

#### point-service (Port: 8085)
- **影響範囲**: ポイント管理、ティアシステム
- **ロールバック優先度**: P1
- **API パス変更**: `/api/points` → `/api/v1/points` — Gateway ルートも同時にロールバック必要
- **DB マイグレーション**: V2-V5 — 前方互換
- **キャッシュ**: Caffeine キャッシュ — ロールバック後にキャッシュは自動クリア

#### coupon-service (Port: 8088)
- **影響範囲**: クーポン管理
- **ロールバック優先度**: P2
- **DB マイグレーション**: V2 (`version` カラム + TIMESTAMP WITH TIME ZONE) — 前方互換

#### ai-support-service (Port: 8087)
- **影響範囲**: AI チャット、レコメンデーション
- **ロールバック優先度**: P2
- **DB**: MongoDB — スキーマレスのためロールバック影響なし
- **注意事項**: OpenAI API 設定 (タイムアウト、max-tokens) はロールバック後に旧設定に戻る

---

## 3. ロールバックシナリオ

### シナリオ A: 単一サービス障害

```
1. 障害サービスを特定
2. 該当サービスのみ kubectl rollout undo
3. Gateway のルーティングは変更不要 (サービスディスカバリで自動切替)
4. ヘルスチェック確認
5. 影響分析 + ポストモーテム
```

### シナリオ B: DB マイグレーション失敗

```
1. Flyway マイグレーション失敗を検知
2. アプリケーションデプロイを中止 (Pod は起動失敗状態)
3. DB の flyway_schema_history テーブルで失敗レコードを確認
4. 手動で修正マイグレーションを作成 (Vx+1__fix_migration.sql)
5. または: flyway_schema_history の失敗レコードを DELETE し、修正後に再実行
6. ⚠️ 本番 DB の直接操作は DBA + SRE のペア作業で実施
```

### シナリオ C: 全サービスロールバック

```
1. Gateway をメンテナンスモードに切替 (503 返却)
2. 全サービスを逆順にロールバック:
   a. ai-support-service, coupon-service (P2)
   b. point-service, inventory-management-service, user-management-service (P1)
   c. payment-cart-service, sales-management-service, authentication-service (P0)
   d. api-gateway-service (最後)
3. 各サービスのヘルスチェック確認
4. Gateway のメンテナンスモードを解除
5. E2E テストで全フロー確認
```

---

## 4. ロールバック後の確認チェックリスト

- [ ] 全サービスの `/actuator/health` が `UP`
- [ ] Gateway から全サービスへのルーティングが正常
- [ ] ログイン/登録フローの動作確認
- [ ] 商品一覧の表示確認
- [ ] カート操作の動作確認
- [ ] 注文処理フローの動作確認 (本番では手動テスト注文)
- [ ] エラー率が正常範囲内 (< 0.1%)
- [ ] レスポンスタイム P99 が正常範囲内 (< 1 秒)
- [ ] ログにスタックトレースが出力されていないこと

---

## 5. 連絡先・エスカレーション

| 段階 | 担当 | 連絡方法 | トリガー |
|------|------|---------|---------|
| L1 | オンコール SRE | Slack #incident | ヘルスチェック失敗 |
| L2 | SRE リーダー | Slack + 電話 | 5 分以内に復旧しない場合 |
| L3 | CTO | 電話 | 全サービスロールバックが必要な場合 |
| — | DBA | Slack #dba | DB マイグレーション関連の問題 |

---

## 6. テスト環境でのロールバック訓練

リリース前に以下のロールバック訓練をステージング環境で実施すること:

- [ ] 単一サービスロールバック (シナリオ A)
- [ ] DB マイグレーション失敗時の復旧 (シナリオ B)
- [ ] 全サービスロールバック (シナリオ C)
- [ ] ロールバック後の E2E テスト実行
- [ ] ロールバック所要時間の計測 (目標: 5 分以内)
