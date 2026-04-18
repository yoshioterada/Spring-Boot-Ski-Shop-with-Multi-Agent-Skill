# Ski Shop E-Commerce 運用手順書（Runbook）

## 1. サービス概要

### システム全体

| 項目 | 内容 |
|------|------|
| システム名 | Ski Shop E-Commerce Platform |
| 用途 | スキー用品のオンライン販売プラットフォーム。商品管理、注文管理、決済、ポイント、クーポン、AI サポートを提供 |
| 技術スタック | Java 21 / Spring Boot 3.5 / Spring AI / PostgreSQL / MongoDB |
| デプロイ先 | コンテナ（Docker Compose） |
| 重要度 | Tier 1（最重要） |
| SLA / SLO | 稼働率 99.9%、API レスポンスタイム p95 < 500ms |

### サービス一覧

| サービス名 | ポート | DB | 重要度 | 説明 |
|---|---|---|---|---|
| api-gateway-service | 8080 | なし | Tier 1 | API ゲートウェイ、リクエストルーティング |
| authentication-service | 8081 | PostgreSQL | Tier 1 | ユーザー認証、JWT 発行 |
| user-management-service | 8082 | PostgreSQL | Tier 1 | ユーザー情報管理 |
| inventory-management-service | 8083 | MongoDB | Tier 1 | 商品・在庫管理 |
| payment-cart-service | 8084 | PostgreSQL | Tier 1 | カート・決済処理 |
| point-service | 8085 | PostgreSQL | Tier 2 | ポイント管理 |
| sales-management-service | 8086 | PostgreSQL | Tier 1 | 注文・出荷・返品管理 |
| ai-support-service | 8087 | なし | Tier 3 | AI チャットサポート |
| coupon-service | 8088 | PostgreSQL | Tier 2 | クーポン管理 |

### 依存関係マップ

| 依存先 | 種別 | 利用サービス | 障害時の影響 | フォールバック |
|--------|------|------------|------------|-------------|
| PostgreSQL | データベース | auth, user, payment, point, sales, coupon | サービス停止 | なし（必須依存） |
| MongoDB | データベース | inventory | 在庫管理停止 | なし（必須依存） |
| 外部決済ゲートウェイ | 外部サービス | payment-cart | 決済不可 | エラー応答 + リトライ |
| OpenAI API | 外部 AI | ai-support | AI サポート停止 | 縮退運転（AI 機能無効化） |

### ネットワーク構成

| ポート | 用途 | 公開範囲 |
|--------|------|---------|
| 8080 | API Gateway | ロードバランサー経由（外部公開） |
| 8081-8088 | 各マイクロサービス | 内部ネットワークのみ |
| `/actuator/**` | 管理エンドポイント | 内部ネットワークのみ |

---

## 2. 通常運用手順

### 起動手順

```bash
# 1. 前提条件の確認
# - PostgreSQL / MongoDB が起動していること
# - 環境変数が設定されていること（DB_URL, DB_PASSWORD, JWT_SECRET 等）

# 2. 全サービス起動
docker compose up -d

# 3. 各サービスの起動確認（ヘルスチェック）
for port in 8080 8081 8082 8083 8084 8085 8086 8087 8088; do
  timeout 120 bash -c "until curl -sf http://localhost:${port}/actuator/health/readiness; do sleep 5; done"
  echo "ポート ${port}: 起動完了"
done
```

### 停止手順（グレースフルシャットダウン）

```bash
# 1. API Gateway を先に停止（新規リクエストの受入停止）
docker compose stop api-gateway-service
sleep 10

# 2. 残りのサービスを停止
# server.shutdown=graceful / spring.lifecycle.timeout-per-shutdown-phase=30s が設定済み
docker compose stop

# 3. 停止確認
docker compose ps  # 全コンテナが Exit 0 であること
```

### ログ確認

```bash
# リアルタイムログ（全サービス）
docker compose logs -f

# 特定サービスのリアルタイムログ
docker compose logs -f payment-cart-service

# 直近のエラーログ
docker compose logs --since 30m | grep -i error

# 特定の Trace ID でのログ検索
docker compose logs | grep "traceId=abc123"
```

---

## 3. ヘルスチェック

### エンドポイント一覧

| エンドポイント | 目的 | 正常応答 | 異常時のアクション |
|---|---|---|---|
| `/actuator/health/liveness` | プロセスの生存確認 | `{"status":"UP"}` | コンテナ再起動 |
| `/actuator/health/readiness` | トラフィック受入可否 | `{"status":"UP"}` | LB から除外 |
| `/actuator/health` | 全体ヘルス | `{"status":"UP"}` | 詳細を確認 |
| `/actuator/prometheus` | メトリクスエクスポート | Prometheus 形式テキスト | Grafana で確認 |

### 手動ヘルスチェック手順

```bash
# 全サービスの一括ヘルスチェック
for port in 8080 8081 8082 8083 8084 8085 8086 8087 8088; do
  STATUS=$(curl -sf http://localhost:${port}/actuator/health | jq -r '.status' 2>/dev/null || echo "DOWN")
  echo "ポート ${port}: ${STATUS}"
done

# 特定サービスの詳細ヘルス
curl -sf http://localhost:8084/actuator/health | jq .

# Prometheus メトリクス確認
curl -sf http://localhost:8084/actuator/prometheus | head -50
```

---

## 4. 障害対応手順

### 障害レベルの定義

| レベル | 定義 | 応答時間 | 対応時間目標 |
|--------|------|---------|------------|
| **P1（Critical）** | 決済不能、サービス全停止、データ損失 | 即時 | 1 時間以内に復旧 |
| **P2（High）** | 主要機能の障害、性能劣化（SLA 違反） | 30 分以内 | 4 時間以内に復旧 |
| **P3（Medium）** | 一部機能の障害（AI サポート等）、回避策あり | 2 時間以内 | 翌営業日 |
| **P4（Low）** | 軽微な問題、ユーザー影響なし | 翌営業日 | 次回リリース |

### 障害パターン別対応手順

#### パターン 1: サービス停止（P1）

```
症状: ヘルスチェック失敗、リクエスト応答なし
```

1. **状況確認**
   ```bash
   docker compose ps                          # コンテナ状態確認
   docker compose logs <service> --tail 100    # 直近ログ確認
   ```

2. **原因切り分け**
   - コンテナが停止 → OOM Killer 確認: `dmesg | grep -i oom`
   - コンテナは起動中 → アプリケーション内部のデッドロック / ハング確認

3. **一次対応: 再起動**
   ```bash
   docker compose restart <service>
   curl -sf http://localhost:<port>/actuator/health
   ```

4. **再起動で復旧しない場合** → P1 エスカレーション

#### パターン 2: DB 接続不能（P1）

```
症状: ヘルスチェックの DB コンポーネントが DOWN、5xx エラー増加
```

1. **DB 状態確認**
   ```bash
   # PostgreSQL
   docker compose ps postgres
   docker compose exec postgres pg_isready

   # MongoDB
   docker compose ps mongodb
   docker compose exec mongodb mongosh --eval "db.adminCommand('ping')"
   ```

2. **コネクションプール確認**
   ```bash
   curl -sf http://localhost:<port>/actuator/metrics/hikaricp.connections.active | jq .
   curl -sf http://localhost:<port>/actuator/metrics/hikaricp.connections.pending | jq .
   ```

3. **一次対応**
   - DB が停止 → DB 再起動: `docker compose restart postgres`
   - DB は稼働中、コネクション枯渇 → アプリケーション再起動

4. **復旧確認**
   ```bash
   curl -sf http://localhost:<port>/actuator/health | jq '.components.db'
   ```

#### パターン 3: メモリ枯渇（P2）

```
症状: レスポンスタイム増大、GC 頻度増加、OOMKiller によるプロセス終了
```

1. **メモリ状況確認**
   ```bash
   curl -sf http://localhost:<port>/actuator/metrics/jvm.memory.used | jq .
   curl -sf http://localhost:<port>/actuator/metrics/jvm.gc.pause | jq .
   ```

2. **ヒープダンプ取得**（可能な場合）
   ```bash
   docker compose cp <service>:/tmp/heapdump.hprof ./heapdump_$(date +%Y%m%d_%H%M%S).hprof
   ```

3. **一次対応: 再起動**
   ```bash
   docker compose restart <service>
   ```

4. **再発する場合** → メモリリークの疑い。ヒープダンプを開発チームに引き渡し

#### パターン 4: 外部サービス障害（P2-P3）

```
症状: 特定機能のエラー、外部 API タイムアウト（決済ゲートウェイ、OpenAI 等）
```

1. **外部サービス状態確認**
   ```bash
   curl -sf http://localhost:<port>/actuator/health | jq '.components'
   ```

2. **縮退運転への切替**
   - 決済ゲートウェイ障害 → 注文受付を一時停止、ユーザーにリトライを案内
   - OpenAI 障害 → AI サポート機能の無効化（ai-support-service 停止）

3. **外部サービス復旧後の確認**
   - ヘルスチェックの該当コンポーネントが UP に復帰することを確認

#### パターン 5: ディスク満杯（P2）

```
症状: ログ出力停止、DB 書き込みエラー
```

1. **ディスク使用量確認**
   ```bash
   df -h
   du -sh /var/log/* | sort -rh | head -10
   ```

2. **一次対応: 不要ファイル削除**
   ```bash
   find /var/log -name "*.log" -mtime +7 -delete
   docker system prune -f
   ```

---

## 5. 監視項目

### 必須メトリクス

| カテゴリ | メトリクス | 閾値（Warning） | 閾値（Critical） | 確認コマンド |
|---------|----------|----------------|-----------------|------------|
| **リクエスト** | エラーレート | > 1% | > 5% | `actuator/metrics/http.server.requests` |
| **リクエスト** | レスポンスタイム（p95） | > 500ms | > 2000ms | `actuator/metrics/http.server.requests` |
| **JVM** | ヒープ使用率 | > 70% | > 85% | `actuator/metrics/jvm.memory.used` |
| **JVM** | GC 停止時間 | > 500ms | > 2000ms | `actuator/metrics/jvm.gc.pause` |
| **DB** | コネクションプール使用率 | > 70% | > 90% | `actuator/metrics/hikaricp.connections.active` |
| **DB** | コネクション待ち数 | > 0 | > 5 | `actuator/metrics/hikaricp.connections.pending` |
| **システム** | CPU 使用率 | > 70% | > 90% | OS コマンド / cAdvisor |
| **システム** | ディスク使用率 | > 70% | > 90% | `df -h` |

### アラート対応ルール

| アラートレベル | 対応 | 通知先 |
|---|---|---|
| **Critical** | 即時対応。障害対応手順に従う | オンコール担当（電話） |
| **Warning** | 勤務時間内に調査 | 運用チーム（チャット） |
| **Info** | 記録のみ | ログ |

---

## 6. エスカレーション

### エスカレーションフロー

```
一次対応（運用担当）
    ↓ 30 分以内に復旧不能 or P1 障害
二次対応（開発チーム）
    ↓ 1 時間以内に復旧不能 or データ損失
三次対応（アーキテクト + マネージャー）
    ↓ サービス全停止 2 時間超
経営層報告
```

### 連絡先

| 役割 | 担当者 | 連絡方法 | 備考 |
|------|--------|---------|------|
| 一次対応（オンコール） | （担当者名） | （電話番号 / チャット） | ローテーション表参照 |
| 二次対応（開発） | （担当者名） | （連絡先） | — |
| 三次対応（アーキテクト） | （担当者名） | （連絡先） | — |
| マネージャー | （担当者名） | （連絡先） | P1 障害時 |

---

## 7. バックアップ / リストア

### バックアップスケジュール

| 対象 | 方式 | 頻度 | 保持期間 | 保存先 |
|------|------|------|---------|--------|
| PostgreSQL（フルバックアップ） | `pg_dump` | 日次 | 30 日 | 外部ストレージ |
| PostgreSQL（WAL アーカイブ） | 継続的 | リアルタイム | 7 日 | 外部ストレージ |
| MongoDB（フルバックアップ） | `mongodump` | 日次 | 30 日 | 外部ストレージ |
| 設定ファイル | Git 管理 | コミット時 | 永続 | Git リポジトリ |

### PostgreSQL リストア手順

```bash
# 1. 復旧対象の特定
# RPO（許容データ損失）: 24 時間（日次バックアップ）
# RTO（復旧目標時間）: 1 時間

# 2. 対象サービスの停止
docker compose stop authentication-service user-management-service \
  payment-cart-service point-service sales-management-service coupon-service

# 3. バックアップファイルの確認
ls -la /backup/postgresql/

# 4. リストア実行（例: payment_db）
pg_restore -h localhost -U postgres -d payment_db --clean /backup/postgresql/payment_db_YYYYMMDD.dump

# 5. データ整合性確認
psql -h localhost -U postgres -d payment_db -c "SELECT count(*) FROM payments;"

# 6. サービス起動 + ヘルスチェック
docker compose start authentication-service user-management-service \
  payment-cart-service point-service sales-management-service coupon-service

for port in 8081 8082 8084 8085 8086 8088; do
  curl -sf http://localhost:${port}/actuator/health
done
```

### MongoDB リストア手順

```bash
# 1. inventory-management-service 停止
docker compose stop inventory-management-service

# 2. リストア実行
mongorestore --uri="mongodb://localhost:27017" --db inventory_db /backup/mongodb/inventory_db_YYYYMMDD/

# 3. サービス起動 + 確認
docker compose start inventory-management-service
curl -sf http://localhost:8083/actuator/health
```

---

## 8. 定期メンテナンス

| 作業 | 頻度 | 手順 | 備考 |
|------|------|------|------|
| ログローテーション確認 | 週次 | ディスク使用量・ログサイズ確認 | — |
| PostgreSQL VACUUM ANALYZE | 週次 | 各 DB に対して実行 | 大量削除後は即時実行 |
| MongoDB コンパクション | 月次 | `db.runCommand({compact:'collection'})` | メンテナンスウィンドウ内 |
| 証明書有効期限確認 | 月次 | TLS 証明書の有効期限チェック | 期限 30 日前にアラート |
| セキュリティパッチ確認 | 月次 | OS・ランタイム・依存ライブラリのパッチ確認 | — |
| コネクションプール監視レビュー | 月次 | HikariCP の active/idle 比率確認 | pool-size 調整の判断材料 |
| DR 訓練 | 四半期 | バックアップからのリストアテスト | 全 DB 対象 |

---

## 9. 環境変数一覧

| 変数名 | 対象サービス | 説明 | 必須 |
|--------|------------|------|------|
| `DB_URL` | auth, user, payment, point, sales, coupon | PostgreSQL 接続 URL | Yes |
| `DB_USERNAME` | auth, user, payment, point, sales, coupon | PostgreSQL ユーザー名 | Yes |
| `DB_PASSWORD` | auth, user, payment, point, sales, coupon | PostgreSQL パスワード | Yes |
| `JWT_SECRET` | 全サービス（gateway 除く） | JWT 署名シークレット | Yes |
| `MONGODB_URI` | inventory | MongoDB 接続 URI | Yes |
| `REDIS_HOST` | coupon | Redis ホスト | No |
| `REDIS_PORT` | coupon | Redis ポート | No |
| `OPENAI_API_KEY` | ai-support | OpenAI API キー | Yes（AI 機能利用時） |

> ⚠️ **注意**: 秘密情報は環境変数または外部シークレット管理サービス（Vault 等）で管理すること。ハードコード禁止。

---

## Multi-Agent Runtime トラブルシューティング {#multi-agent}

`agent-runtime-monolith` (port 8100) は Phase 6 で導入された統合 LLM 実行環境であり、6 つの Worker Agent (weather / customer-intent / equipment-matching / inventory-monitoring / dynamic-pricing / coupon-optimization) と Orchestrator を 1 JVM 内で起動します。

### 起動失敗時の調査手順

1. **コンテナログを確認**
   ```bash
   docker logs --tail 200 skishop-agent-runtime
   ```
2. **Bean 衝突 / `@ConditionalOnProperty` の状態を確認**
   - 起動時のログで `agents.deployment.mode=monolith` が反映されているか
   - 各 Worker の `*SecurityConfig` Bean が登録されていないこと（モノリス時は無効化）
   - 単一 `MonolithSecurityConfig` のみが Bean 化されていること
3. **Azure OpenAI への接続確認**
   ```bash
   docker exec skishop-agent-runtime sh -c 'env | grep AZURE_OPENAI'
   curl -i $AZURE_OPENAI_ENDPOINT/openai/deployments?api-version=2024-08-01-preview \
        -H "api-key: $AZURE_OPENAI_API_KEY"
   ```
4. **下流サービスへの内部 API キー疎通確認**
   ```bash
   docker exec skishop-agent-runtime sh -c \
     'curl -sf -H "X-Internal-Api-Key: $INTERNAL_API_KEY" -H "X-Caller-Service: orchestrator-agent" \
       http://user-management-service:8081/actuator/health'
   ```
   401 が返る場合は `INTERNAL_API_KEY` が compose の各サービスで一致していないため、`docker compose down && docker compose up -d` で再起動する。

### LocalWeatherInvoker / RemoteWeatherInvoker の切替確認

| 起動モード | 環境変数 | 期待される Bean |
|---|---|---|
| モノリス | `AGENTS_DEPLOYMENT_MODE=monolith` (既定) | `LocalWeatherInvoker` (`@ConditionalOnBean(WeatherAgentService.class)` 成立) |
| 分散 / weather standalone 起動側 | (同 JVM に WeatherAgentService あり) | `LocalWeatherInvoker` |
| 分散 / equipment / pricing standalone 側 | weather モジュール依存なし | `RemoteWeatherInvoker` (HTTP 呼び出し) |

確認コマンド:
```bash
docker exec skishop-agent-runtime sh -c \
  'curl -s http://localhost:8100/actuator/beans | grep -iE "weatherInvoker"'
```

### よくあるエラーと対処

| 症状 | 原因 | 対処 |
|---|---|---|
| `IllegalArgumentException: API key must not be blank` | `INTERNAL_API_KEY` 未設定 | `.env` に `INTERNAL_API_KEY=...` を設定して compose を再起動 |
| Orchestrator が 401 返却 | フロントから JWT 未送信 | フロント側で `Authorization: Bearer <token>` を付与 |
| `/api/v1/agents/**` が 404 | api-gateway が意図的にブロック中（仕様） | 内部呼び出し専用。外部公開不可 |
| Tool 呼び出しがログに残らない | `agents.web.enabled=false` で Controller 無効化 | `AGENTS_WEB_ENABLED=true` を確認 |
