# A-H-02: Gateway SPOF 解消 — ローカル開発環境 & 本番環境 実装詳細設計書・実装計画

## 1. エグゼクティブサマリー

### 1.1 採用方針

**推奨案1: Docker Compose DNS（ローカル）+ Azure Container Apps ネイティブ DNS（本番）** を採用する。

- **Java コード変更: ゼロ**（既存の環境変数 `${AUTH_SERVICE_URL:http://localhost:8080}` パターンをそのまま活用）
- **ローカル**: Docker Compose の内部 DNS によるサービス間名前解決
- **本番**: Azure Container Apps の内部 Ingress DNS による名前解決
- **開発効率**: シェルスクリプトで「インフラのみ」「部分起動」「全体起動」の 3 モードを即座に切替可能

### 1.2 対象スコープ

| カテゴリ | 内容 |
|---------|------|
| 新規作成ファイル | `docker-compose.yml`, `.env.example`, `docker/initdb/01_create_databases.sql`, `scripts/dev.sh`, `scripts/health-check.sh` |
| 修正ファイル | `monitoring/prometheus/prometheus.yml`（ポート番号修正）, `.gitignore`（`.env` 追加確認） |
| Java コード変更 | **なし** |
| 設定ファイル変更 | **なし**（`application.properties` は現状のまま） |

---

## 2. 現状分析

### 2.1 サービス一覧と接続先データストア

| # | サービス名 | ポート | データストア | DB 名 | DB 種別 |
|---|-----------|--------|------------|-------|---------|
| 1 | authentication-service | 8080 | PostgreSQL | `skishop_auth` | RDB |
| 2 | user-management-service | 8081 | PostgreSQL | `skishop_users` | RDB |
| 3 | inventory-management-service | 8082 | MongoDB | `skishop_inventory` | Document |
| 4 | sales-management-service | 8083 | PostgreSQL | `skishop_sales` | RDB |
| 5 | payment-cart-service | 8084 | PostgreSQL | `skishop_payment` | RDB |
| 6 | point-service | 8085 | PostgreSQL | `point_db` | RDB |
| 7 | ai-support-service | 8087 | MongoDB | `skishop_ai` | Document |
| 8 | coupon-service | 8088 | PostgreSQL | `coupon_db` | RDB |
| 9 | api-gateway-service | 8090 | なし（Redis 任意） | — | — |

> **Kafka について**: 全 8 サービス（gateway 除く）の `pom.xml` に `spring-cloud-stream-binder-kafka` 依存が宣言済みだが、現時点では `application.properties` に Spring Cloud Stream / Kafka の設定は一切なく、イベント発行はログ出力のみのスタブ実装（`LoggingEventPublisher`）である。Event Pub/Sub の Phase 2（Kafka 統合）に備え、ローカル・本番ともに Kafka インフラを準備しておく。詳細は [additional1-event-pubsub-impl-plan.md](additional1-event-pubsub-impl-plan.md) §4 を参照。

### 2.2 環境変数による接続先の外部化（現状）

各サービスの `application.properties` は既に環境変数で接続先を外部化済み。

**PostgreSQL サービス共通パターン**:
```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/<db_name>}
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD}
```

**MongoDB サービス共通パターン**:
```properties
spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/<db_name>}
```

**API Gateway のサービス URL**:
```properties
app.services.auth-url=${AUTH_SERVICE_URL:http://localhost:8080}
app.services.user-url=${USER_SERVICE_URL:http://localhost:8081}
app.services.inventory-url=${INVENTORY_SERVICE_URL:http://localhost:8082}
app.services.sales-url=${SALES_SERVICE_URL:http://localhost:8083}
app.services.payment-url=${PAYMENT_SERVICE_URL:http://localhost:8084}
app.services.point-url=${POINT_SERVICE_URL:http://localhost:8085}
app.services.coupon-url=${COUPON_SERVICE_URL:http://localhost:8088}
app.services.ai-url=${AI_SERVICE_URL:http://localhost:8087}
```

### 2.3 既存 Dockerfile 分析

全 9 サービスの Dockerfile は統一パターン:
- **ビルドステージ**: `eclipse-temurin:21-jdk` + Maven マルチモジュールビルド（`--mount=type=cache`）
- **実行ステージ**: `eclipse-temurin:21-jre` + 非 root ユーザー（`appuser`）
- **HEALTHCHECK**: `wget -qO- http://localhost:<port>/actuator/health || exit 1`
- **JVM オプション**: `MaxRAMPercentage=75%`, G1GC, HeapDumpOnOutOfMemoryError

### 2.4 発見された問題: Prometheus ポート番号の不整合

`monitoring/prometheus/prometheus.yml` のターゲットポートが実際の `server.port` と不一致:

| Job | prometheus.yml のポート | 実際のポート | 状態 |
|-----|----------------------|------------|------|
| api-gateway | 8080 | **8090** | ❌ 誤り |
| authentication-service | 8081 | **8080** | ❌ 誤り |
| user-management-service | 8082 | **8081** | ❌ 誤り |
| inventory-management-service | 8083 | **8082** | ❌ 誤り |
| payment-cart-service | 8084 | 8084 | ✅ 正しい |
| point-service | 8085 | 8085 | ✅ 正しい |
| sales-management-service | 8086 | **8083** | ❌ 誤り |
| ai-support-service | 8087 | 8087 | ✅ 正しい |
| coupon-service | 8088 | 8088 | ✅ 正しい |

> **対応方針**: 本実装計画の Step 7 で修正する。

---

## 3. アーキテクチャ設計

### 3.1 ローカル環境アーキテクチャ

```
┌──────────────────────────────────────────────────────────────────────┐
│                      Docker Compose Network                          │
│                      (skishop-network)                               │
│                                                                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐               │
│  │  PostgreSQL  │  │   MongoDB   │  │    Kafka     │               │
│  │  (postgres)  │  │   (mongo)   │  │   (kafka)    │               │
│  │  Port: 5432  │  │  Port: 27017│  │  Port: 9092  │               │
│  │             │  │             │  │  (KRaft mode) │               │
│  │ 6 databases: │  │ 2 databases:│  └─────────────┘               │
│  │ skishop_auth │  │ skishop_    │                                  │
│  │ skishop_users│  │  inventory  │  ┌──────────────┐               │
│  │ skishop_sales│  │ skishop_ai  │  │ Prometheus   │               │
│  │ skishop_    │  └─────────────┘  │ + Grafana    │               │
│  │  payment    │                    │  9090 / 3000 │               │
│  │ point_db    │                    └──────────────┘               │
│  │ coupon_db   │                                                    │
│  └─────────────┘                                                    │
│         │                │              │                           │
│         ▼                ▼              ▼                           │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                 Application Services                          │  │
│  │                                                              │  │
│  │  auth:8080  user:8081  inventory:8082  sales:8083            │  │
│  │  payment:8084  point:8085  ai:8087  coupon:8088              │  │
│  │  gateway:8090                                                │  │
│  │                                                              │  │
│  │  ※ Docker Compose DNS で互いに service 名で解決              │  │
│  │  例: gateway → http://authentication-service:8080            │  │
│  │  ※ 全 8 サービス（gateway 除く）が Kafka に接続              │  │
│  │  例: auth → kafka:9092 (Spring Cloud Stream)                 │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘

  ↕ ポートマッピング（ホスト → コンテナ）
  localhost:8080 → auth:8080
  localhost:8081 → user:8081
  ...
  localhost:8090 → gateway:8090
  localhost:5432 → postgres:5432
  localhost:27017 → mongo:27017
  localhost:9092 → kafka:9092
```

### 3.2 本番環境アーキテクチャ（Azure Container Apps）

```
┌──────────────────────────────────────────────────────────────────────┐
│                  Azure Container Apps Environment                    │
│                    (Internal VNet + DNS)                             │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Container App: api-gateway-service                           │  │
│  │  Internal FQDN: api-gateway-service.internal.<env>.azureca.io│  │
│  │  External Ingress: https://api.skishop.example.com            │  │
│  └──────────────────────────────────────────────────────────────┘  │
│         │                                                           │
│         ▼  (Azure Container Apps 内部 DNS)                         │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  auth-svc ← authentication-service.internal.<env>...         │  │
│  │  user-svc ← user-management-service.internal.<env>...        │  │
│  │  inventory-svc ← inventory-management-service.internal...    │  │
│  │  sales-svc ← sales-management-service.internal.<env>...      │  │
│  │  payment-svc ← payment-cart-service.internal.<env>...        │  │
│  │  point-svc ← point-service.internal.<env>...                 │  │
│  │  coupon-svc ← coupon-service.internal.<env>...               │  │
│  │  ai-svc ← ai-support-service.internal.<env>...               │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌────────────────────────────────────────────┐                    │
│  │  Azure PostgreSQL Flexible Server × 6      │                    │
│  │  (サービス毎に独立した Flexible Server)     │                    │
│  │                                            │                    │
│  │  auth-db.postgres.database.azure.com       │                    │
│  │  user-db.postgres.database.azure.com       │                    │
│  │  sales-db.postgres.database.azure.com      │                    │
│  │  payment-db.postgres.database.azure.com    │                    │
│  │  point-db.postgres.database.azure.com      │                    │
│  │  coupon-db.postgres.database.azure.com     │                    │
│  └────────────────────────────────────────────┘                    │
│                                                                      │
│  ┌────────────────────────────────────────────┐                    │
│  │  Azure Cosmos DB for MongoDB vCore × 1-2   │                    │
│  │  (inventory + ai で共有 or 分離)            │                    │
│  │                                            │                    │
│  │  skishop-mongo.mongocluster.cosmos.azure.com│                   │
│  └────────────────────────────────────────────┘                    │
│                                                                      │
│  ┌────────────────────────────────────────────┐                    │
│  │  Azure Event Hubs for Apache Kafka × 1     │                    │
│  │  (Kafka プロトコル互換のマネージドサービス)  │                    │
│  │                                            │                    │
│  │  skishop-eventhub.servicebus.windows.net    │                    │
│  │  (8 サービスの Event Pub/Sub に使用)         │                    │
│  └────────────────────────────────────────────┘                    │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.3 環境間の設定差分マッピング

| 環境変数 | ローカル（Docker Compose） | 本番（Azure Container Apps） |
|---------|---------------------------|------------------------------|
| `DB_URL` | `jdbc:postgresql://postgres:5432/<db>` | `jdbc:postgresql://<name>.postgres.database.azure.com:5432/<db>?sslmode=require` |
| `DB_USERNAME` | `postgres` | Azure Key Vault 経由 |
| `DB_PASSWORD` | `.env` ファイル（ダミー値） | Azure Key Vault 経由 |
| `MONGODB_URI` | `mongodb://mongo:27017/<db>` | `mongodb+srv://<user>:<pass>@<host>/<db>?...` |
| `AUTH_SERVICE_URL` | `http://authentication-service:8080` | `http://authentication-service.internal.<env>:8080` |
| `JWT_SECRET` | `.env` ファイル（テスト用固定値） | Azure Key Vault 経由 |
| `OPENAI_API_KEY` | `.env` ファイル（ダミー or テスト用） | Azure Key Vault 経由 |
| `KAFKA_BROKERS` | `kafka:9092` | `skishop-eventhub.servicebus.windows.net:9093` |

**ポイント**: アプリケーションコードも `application.properties` も一切変更不要。環境変数の値だけが環境間で異なる。

---

## 4. Docker Compose 詳細設計

### 4.1 profiles 設計

| Profile | 含まれるサービス | 用途 |
|---------|----------------|------|
| `infra` | postgres, mongo, kafka, prometheus, grafana | DB + Kafka + 監視（IDE 開発時） |
| `app` | 全 9 アプリケーションサービス | 全サービスコンテナ起動 |
| （profile 指定なし） | なし | `docker compose up` 単独では何も起動しない（安全側） |

**使い分けパターン**:
```bash
# パターン A: IDE でデバッグ（DB + 監視のみ Docker）
./scripts/dev.sh infra

# パターン B: 全サービスを Docker で起動（E2E テスト）
./scripts/dev.sh up

# パターン C: 特定サービスだけ Docker 外で開発
./scripts/dev.sh up
docker compose stop authentication-service  # auth だけ止めて IDE で起動
```

### 4.2 ネットワーク設計

```yaml
networks:
  skishop-network:
    driver: bridge
    name: skishop-network
```

全サービスを単一の bridge ネットワークに配置。Docker Compose の内部 DNS により、サービス名（`services:` のキー名）で名前解決可能。

### 4.3 ボリューム設計

| ボリューム名 | マウント先 | 用途 |
|------------|----------|------|
| `postgres-data` | `/var/lib/postgresql/data` | PostgreSQL データ永続化 |
| `mongo-data` | `/data/db` | MongoDB データ永続化 |
| `kafka-data` | `/var/lib/kafka/data` | Kafka メッセージデータ永続化 |
| `prometheus-data` | `/prometheus` | Prometheus メトリクスデータ |
| `grafana-data` | `/var/lib/grafana` | Grafana ダッシュボード・設定 |

### 4.4 サービス定義詳細

#### 4.4.1 PostgreSQL（共有インスタンス）

```yaml
postgres:
  image: postgres:16-alpine
  profiles: ["infra", "app"]
  container_name: skishop-postgres
  environment:
    POSTGRES_USER: postgres
    POSTGRES_PASSWORD: ${DB_PASSWORD}
  ports:
    - "5432:5432"
  volumes:
    - postgres-data:/var/lib/postgresql/data
    - ./docker/initdb:/docker-entrypoint-initdb.d:ro
  networks:
    - skishop-network
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U postgres"]
    interval: 5s
    timeout: 3s
    retries: 10
    start_period: 10s
```

#### 4.4.2 initdb スクリプト

PostgreSQL の公式イメージは `/docker-entrypoint-initdb.d/` 配下の `.sql` / `.sh` ファイルを**初回起動時のみ**自動実行する。

```sql
-- docker/initdb/01_create_databases.sql
-- ローカル開発用: 単一 PostgreSQL インスタンスに全サービスの DB を作成
-- 本番環境ではサービス毎に独立した Azure PostgreSQL Flexible Server を使用

CREATE DATABASE skishop_auth;
CREATE DATABASE skishop_users;
CREATE DATABASE skishop_sales;
CREATE DATABASE skishop_payment;
CREATE DATABASE point_db;
CREATE DATABASE coupon_db;
```

> **注意**: initdb スクリプトは **PostgreSQL データディレクトリが空の場合のみ** 実行される。既にデータが存在する（ボリュームが残っている）場合はスキップされる。DB を作り直したい場合は `docker volume rm skishop_postgres-data` でボリュームを削除する必要がある。

#### 4.4.3 MongoDB

```yaml
mongo:
  image: mongo:7
  profiles: ["infra", "app"]
  container_name: skishop-mongo
  ports:
    - "27017:27017"
  volumes:
    - mongo-data:/data/db
  networks:
    - skishop-network
  healthcheck:
    test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
    interval: 5s
    timeout: 3s
    retries: 10
    start_period: 10s
```

> MongoDB は認証なしで起動（ローカル開発用）。本番では Azure Cosmos DB for MongoDB vCore を使用し、接続文字列に認証情報を含める。

#### 4.4.4 Apache Kafka（KRaft モード）

Event Pub/Sub の Phase 2（Kafka 統合）に向けたインフラ準備。
現時点（Phase 1）では全サービスのイベント発行は `LoggingEventPublisher`（ログ出力のみ）だが、
`spring-cloud-stream-binder-kafka` 依存が全 8 サービスの `pom.xml` に既に存在するため、Kafka ブローカーを事前に用意しておく。

Phase 2 で `SpringCloudStreamEventPublisher` に切り替える際、環境変数 `KAFKA_BROKERS` を設定するだけで Kafka が有効化される。詳細は [additional1-event-pubsub-impl-plan.md](additional1-event-pubsub-impl-plan.md) §4.1、4.2 を参照。

```yaml
kafka:
  image: apache/kafka:3.9.0
  profiles: ["infra", "app"]
  container_name: skishop-kafka
  ports:
    - "9092:9092"
  environment:
    # KRaft モード（ZooKeeper 不要）
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: broker,controller
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:29093
    KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
    # リスナー設定
    KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:29093
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT
    KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
    # トピック設定
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
    KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    # ストレージ
    KAFKA_LOG_DIRS: /var/lib/kafka/data
    CLUSTER_ID: skishop-local-kafka-cluster-id
  volumes:
    - kafka-data:/var/lib/kafka/data
  networks:
    - skishop-network
  healthcheck:
    test: ["CMD-SHELL", "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list || exit 1"]
    interval: 10s
    timeout: 5s
    retries: 10
    start_period: 30s
```

> **KRaft モードの採用理由**: Apache Kafka 3.3 以降、ZooKeeper なしで動作する KRaft (Kafka Raft) モードが利用可能。ローカル開発では ZooKeeper コンテナが不要となりリソース消費を削減できる。本番では Azure Event Hubs for Apache Kafka を使用するため、ローカルの Kafka バージョンは開発利便性のみ考慮すればよい。

> **Phase 2 での接続設定**: 各サービスの `application.properties` に以下を追加することで Spring Cloud Stream が Kafka に接続する:
> ```properties
> spring.cloud.stream.kafka.binder.brokers=${KAFKA_BROKERS:localhost:9092}
> ```

#### 4.4.5 アプリケーションサービスのパターン

全 9 サービスは以下の共通パターンで定義する:

```yaml
<service-name>:
  build:
    context: .
    dockerfile: <service-dir>/Dockerfile
  profiles: ["app"]
  container_name: skishop-<short-name>
  ports:
    - "<host-port>:<container-port>"
  environment:
    # DB 接続（Docker DNS のホスト名 "postgres" を使用）
    DB_URL: jdbc:postgresql://postgres:5432/<db-name>
    DB_USERNAME: postgres
    DB_PASSWORD: ${DB_PASSWORD}
    # JWT
    JWT_SECRET: ${JWT_SECRET}
    # Kafka（Phase 2 で Spring Cloud Stream が参照）
    KAFKA_BROKERS: kafka:9092
  depends_on:
    postgres:
      condition: service_healthy
    kafka:
      condition: service_healthy
  networks:
    - skishop-network
  restart: unless-stopped
```

**MongoDB サービス（inventory, ai-support）は `DB_URL` の代わりに**:
```yaml
  environment:
    MONGODB_URI: mongodb://mongo:27017/<db-name>
    KAFKA_BROKERS: kafka:9092
  depends_on:
    mongo:
      condition: service_healthy
    kafka:
      condition: service_healthy
```

> **注意**: `KAFKA_BROKERS` は Phase 1 時点では各サービスの `application.properties` に `spring.cloud.stream.kafka.binder.brokers` が設定されていないため、環境変数として渡しても実際には使用されない。Phase 2 で `application.properties` に `spring.cloud.stream.kafka.binder.brokers=${KAFKA_BROKERS:localhost:9092}` を追加すると、その時点で自動的に有効化される。api-gateway-service は Kafka 依存がないため `KAFKA_BROKERS` は不要。

#### 4.4.6 API Gateway の特別な環境変数

Gateway はバックエンドサービスの URL を Docker DNS 名で指定する:

```yaml
api-gateway-service:
  environment:
    AUTH_SERVICE_URL: http://authentication-service:8080
    USER_SERVICE_URL: http://user-management-service:8081
    INVENTORY_SERVICE_URL: http://inventory-management-service:8082
    SALES_SERVICE_URL: http://sales-management-service:8083
    PAYMENT_SERVICE_URL: http://payment-cart-service:8084
    POINT_SERVICE_URL: http://point-service:8085
    COUPON_SERVICE_URL: http://coupon-service:8088
    AI_SERVICE_URL: http://ai-support-service:8087
```

> **重要**: Docker Compose の `services:` キー名が DNS ホスト名になる。例えば `authentication-service:` というキーで定義すると、同一ネットワーク内から `http://authentication-service:8080` で到達可能。

#### 4.4.7 Prometheus / Grafana（監視スタック）

```yaml
prometheus:
  image: prom/prometheus:latest
  profiles: ["infra", "app"]
  container_name: skishop-prometheus
  ports:
    - "9090:9090"
  volumes:
    - ./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    - prometheus-data:/prometheus
  networks:
    - skishop-network

grafana:
  image: grafana/grafana:latest
  profiles: ["infra", "app"]
  container_name: skishop-grafana
  ports:
    - "3000:3000"
  environment:
    GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-admin}
  volumes:
    - ./monitoring/grafana/provisioning:/etc/grafana/provisioning:ro
    - ./monitoring/grafana/dashboards:/var/lib/grafana/dashboards:ro
    - grafana-data:/var/lib/grafana
  depends_on:
    - prometheus
  networks:
    - skishop-network
```

### 4.5 IDE ハイブリッド開発モード

`./scripts/dev.sh infra` でインフラのみ起動した場合:

| アクセス元 | アクセス先 | ホスト名 | ポート |
|-----------|----------|---------|--------|
| IDE で起動したサービス | PostgreSQL | `localhost` | 5432 |
| IDE で起動したサービス | MongoDB | `localhost` | 27017 |
| IDE で起動したサービス | Kafka | `localhost` | 9092 |
| IDE で起動した Gateway | auth-service（IDE） | `localhost` | 8080 |
| ブラウザ | Gateway（IDE） | `localhost` | 8090 |

**全てデフォルト値（`localhost`）で動作するため、環境変数の設定は不要**。

IDE で起動する際に必要なのは `DB_PASSWORD` と `JWT_SECRET` のみ。これらは IDE の起動設定（Run Configuration）で `.env` ファイルを読み込むか、手動で環境変数に設定する。

Phase 2 で Kafka 統合を行う際は、IDE の起動設定に `KAFKA_BROKERS=localhost:9092` を追加するか、`application.properties` のデフォルト値 `${KAFKA_BROKERS:localhost:9092}` をそのまま使えばよい（Docker Compose の Kafka が `localhost:9092` にポートマッピングされているため）。

---

## 5. シェルスクリプト設計

### 5.1 `scripts/dev.sh` — メイン開発スクリプト

```bash
#!/usr/bin/env bash
set -euo pipefail

# プロジェクトルートに移動
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# .env ファイルの存在確認
if [ ! -f .env ]; then
    echo "⚠  .env ファイルが見つかりません。.env.example からコピーしてください:"
    echo "   cp .env.example .env"
    exit 1
fi

usage() {
    cat <<EOF
Usage: $0 <command>

Commands:
  infra       インフラのみ起動（PostgreSQL, MongoDB, Kafka, Prometheus, Grafana）
  up          全サービス起動（インフラ + アプリケーション）
  down        全サービス停止
  restart     全サービス再起動
  logs [svc]  ログ表示（サービス名省略で全体）
  ps          サービス状態一覧
  health      全サービスのヘルスチェック
  build       Maven ビルド + Docker イメージ再構築
  clean       ボリューム含め完全クリーンアップ（※データ削除）
  db-reset    PostgreSQL ボリューム削除 + 再作成（※データ削除）
EOF
}

case "${1:-}" in
  infra)
    echo "🔧 インフラサービスを起動中..."
    docker compose --profile infra up -d
    echo "✅ PostgreSQL: localhost:5432"
    echo "✅ MongoDB:    localhost:27017"
    echo "✅ Kafka:      localhost:9092"
    echo "✅ Prometheus: http://localhost:9090"
    echo "✅ Grafana:    http://localhost:3000"
    ;;
  up)
    echo "🚀 全サービスを起動中..."
    docker compose --profile infra --profile app up -d
    echo ""
    echo "起動を待機中..."
    "$SCRIPT_DIR/health-check.sh"
    ;;
  down)
    echo "🛑 全サービスを停止中..."
    docker compose --profile infra --profile app down
    ;;
  restart)
    echo "🔄 全サービスを再起動中..."
    docker compose --profile infra --profile app restart
    ;;
  logs)
    if [ -n "${2:-}" ]; then
      docker compose logs -f "$2"
    else
      docker compose --profile infra --profile app logs -f
    fi
    ;;
  ps)
    docker compose --profile infra --profile app ps
    ;;
  health)
    "$SCRIPT_DIR/health-check.sh"
    ;;
  build)
    echo "🔨 Maven ビルド中..."
    mvn clean package -DskipTests -Djacoco.skip=true -T 4
    echo "🐳 Docker イメージを再構築中..."
    docker compose --profile app build
    ;;
  clean)
    echo "⚠️  全コンテナ・ボリュームを削除します。データは失われます。"
    read -r -p "続行しますか？ (y/N): " confirm
    if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
      docker compose --profile infra --profile app down -v
      echo "✅ クリーンアップ完了"
    else
      echo "キャンセルしました"
    fi
    ;;
  db-reset)
    echo "⚠️  PostgreSQL と MongoDB のデータを削除して再作成します。"
    read -r -p "続行しますか？ (y/N): " confirm
    if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
      docker compose --profile infra stop postgres mongo
      docker volume rm -f spring-ai-sample_postgres-data spring-ai-sample_mongo-data 2>/dev/null || true
      docker compose --profile infra up -d postgres mongo
      echo "✅ DB リセット完了。Flyway が次回起動時にマイグレーションを実行します。"
    else
      echo "キャンセルしました"
    fi
    ;;
  *)
    usage
    exit 1
    ;;
esac
```

### 5.2 `scripts/health-check.sh` — ヘルスチェックスクリプト

```bash
#!/usr/bin/env bash
set -euo pipefail

# サービス一覧（名前:ポート）
SERVICES=(
  "authentication-service:8080"
  "user-management-service:8081"
  "inventory-management-service:8082"
  "sales-management-service:8083"
  "payment-cart-service:8084"
  "point-service:8085"
  "ai-support-service:8087"
  "coupon-service:8088"
  "api-gateway-service:8090"
)

MAX_WAIT=180  # 最大待機秒数
INTERVAL=5    # チェック間隔（秒）

echo "🏥 ヘルスチェックを実行中..."
echo ""

all_healthy=true

for entry in "${SERVICES[@]}"; do
  name="${entry%%:*}"
  port="${entry##*:}"
  url="http://localhost:${port}/actuator/health"

  elapsed=0
  healthy=false

  while [ $elapsed -lt $MAX_WAIT ]; do
    status=$(curl -sf -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")
    if [ "$status" = "200" ]; then
      healthy=true
      break
    fi
    sleep $INTERVAL
    elapsed=$((elapsed + INTERVAL))
  done

  if $healthy; then
    echo "  ✅ ${name} (port ${port}) — UP"
  else
    echo "  ❌ ${name} (port ${port}) — DOWN (${MAX_WAIT}秒待機後タイムアウト)"
    all_healthy=false
  fi
done

echo ""
if $all_healthy; then
  echo "🎉 全サービスが正常に起動しました"
else
  echo "⚠️  一部のサービスが起動していません。ログを確認してください:"
  echo "   ./scripts/dev.sh logs <service-name>"
  exit 1
fi
```

### 5.3 `.env.example` — 環境変数テンプレート

```bash
# ============================================================
# Ski Shop ローカル開発用 環境変数
# ============================================================
# このファイルを .env にコピーして使用してください:
#   cp .env.example .env
#
# ⚠️ .env ファイルは Git にコミットしないでください（.gitignore に登録済み）
# ============================================================

# --- PostgreSQL ---
DB_PASSWORD=localdev123

# --- JWT ---
JWT_SECRET=local-dev-jwt-secret-key-for-testing-only-not-for-production-use-minimum-256-bits

# --- OpenAI (AI Support Service) ---
# ダミー値: AI 機能を使用しない場合はこのままで起動可能
OPENAI_API_KEY=sk-dummy-local-dev-key

# --- Grafana ---
GRAFANA_PASSWORD=admin
```

---

## 6. 本番環境（Azure Container Apps）設計

### 6.1 Azure リソース構成

| リソース種別 | リソース名（例） | 対象サービス | SKU |
|------------|----------------|------------|-----|
| Container Apps Environment | `skishop-cae` | 全サービス共通 | — |
| Container App × 9 | `auth-svc`, `user-svc`, ... | 各マイクロサービス | — |
| PostgreSQL Flexible Server × 6 | `skishop-auth-db`, ... | 各 PostgreSQL サービス | Burstable B1ms〜 |
| Cosmos DB for MongoDB vCore × 1 | `skishop-mongo` | inventory + ai-support | M25 |
| Azure Key Vault × 1 | `skishop-kv` | 秘密情報一元管理 | Standard |
| Azure Container Registry × 1 | `skishopcr` | Docker イメージ | Basic〜Standard |
| Azure Redis Cache × 1 | `skishop-redis` | Gateway rate limiting | C0〜 |
| Azure Event Hubs × 1 | `skishop-eventhub` | Event Pub/Sub（Kafka プロトコル互換） | Standard (1 TU〜) |

### 6.2 サービス毎の Azure PostgreSQL Flexible Server 設計

| サービス | Flexible Server 名 | DB 名 | 理由 |
|---------|-------------------|-------|------|
| authentication-service | `skishop-auth-db` | `skishop_auth` | 認証 DB は独立（セキュリティ境界） |
| user-management-service | `skishop-user-db` | `skishop_users` | 個人情報を含むため隔離 |
| sales-management-service | `skishop-sales-db` | `skishop_sales` | 注文トランザクションの負荷分離 |
| payment-cart-service | `skishop-payment-db` | `skishop_payment` | PCI DSS 準拠に向けた隔離 |
| point-service | `skishop-point-db` | `point_db` | ポイント不整合防止 |
| coupon-service | `skishop-coupon-db` | `coupon_db` | キャンペーン時の負荷分離 |

**本番の接続文字列パターン**:
```
jdbc:postgresql://<server>.postgres.database.azure.com:5432/<db>?sslmode=require
```

### 6.3 Azure Container Apps のサービスディスカバリ

Azure Container Apps Environment 内のサービスは、内部 Ingress を有効にすると以下の FQDN で相互通信可能:

```
http://<container-app-name>.<environment-unique-id>.<region>.azurecontainerapps.io
```

または短縮形:
```
http://<container-app-name>
```

> Azure Container Apps Environment 内のアプリは、Container App 名だけで DNS 解決可能（Docker Compose と同じ感覚）。

**Gateway の環境変数設定（Azure Container Apps）**:
```bash
AUTH_SERVICE_URL=http://authentication-service
USER_SERVICE_URL=http://user-management-service
INVENTORY_SERVICE_URL=http://inventory-management-service
SALES_SERVICE_URL=http://sales-management-service
PAYMENT_SERVICE_URL=http://payment-cart-service
POINT_SERVICE_URL=http://point-service
COUPON_SERVICE_URL=http://coupon-service
AI_SERVICE_URL=http://ai-support-service
```

> **注意**: Azure Container Apps の内部 Ingress はデフォルトでポート 80 または Ingress で指定したポートを使用する。各サービスの `server.port` が 8080 等の場合、**Ingress ターゲットポート**を `8080` に設定すれば `http://authentication-service:8080` でも到達可能。あるいは Ingress ポートを 80 に設定し、URL からポートを省略する方法もある。プロジェクトの Container App 設定で統一すること。

### 6.4 ローカルと本番の切替方式

```
                    ┌─────────────────────────┐
                    │   application.properties │
                    │   （環境変数参照のみ）    │
                    │   DB_URL=${DB_URL:...}   │
                    └─────────┬───────────────┘
                              │
              ┌───────────────┼──────────────────┐
              │               │                  │
     ┌────────▼──────┐ ┌─────▼────────┐ ┌──────▼────────┐
     │   IDE (直接)   │ │Docker Compose│ │ Azure Container│
     │               │ │              │ │    Apps        │
     │ デフォルト値    │ │ environment: │ │ Key Vault +   │
     │ localhost:5432 │ │ postgres:5432│ │ <name>.postgres│
     └───────────────┘ └──────────────┘ │ .database.azure│
                                        │ .com:5432      │
                                        └────────────────┘
```

**Java コード変更: ゼロ。`application.properties` 変更: ゼロ。**

> **Kafka も同じ切替パターン**: Phase 2 で追加される `spring.cloud.stream.kafka.binder.brokers=${KAFKA_BROKERS:localhost:9092}` は、DB と同じように 3 環境で透過的に切り替わる:
> - **IDE（直接）**: デフォルト値 `localhost:9092` → Docker Compose の Kafka にポートマッピング経由で接続
> - **Docker Compose**: `KAFKA_BROKERS=kafka:9092` → 同一ネットワーク内の Kafka コンテナに接続
> - **Azure Container Apps**: `KAFKA_BROKERS=skishop-eventhub.servicebus.windows.net:9093` → Azure Event Hubs に接続（+ SASL_SSL 設定を Spring Profile で追加）

### 6.5 Azure Event Hubs for Apache Kafka（Event Pub/Sub 基盤）

Azure Event Hubs は Apache Kafka プロトコルに完全互換のマネージドサービスであり、Spring Cloud Stream の Kafka Binder から透過的に接続可能。

| 項目 | ローカル（Docker） | 本番（Azure Event Hubs） |
|------|-------------------|-------------------------|
| ブローカー | `kafka:9092` | `skishop-eventhub.servicebus.windows.net:9093` |
| プロトコル | PLAINTEXT | SASL_SSL |
| 認証 | なし | Azure AD (Managed Identity) or SAS |
| トピック自動作成 | `auto.create.topics=true` | Event Hub 名前空間で事前作成 |
| レプリケーション | 1（単一ノード） | 3（Azure 管理） |

**接続設定（本番用環境変数）**:

```bash
# Azure Event Hubs (Kafka 互換) の接続設定例
# Managed Identity 使用時は SASL の password に Azure AD トークンを使用
KAFKA_BROKERS=skishop-eventhub.servicebus.windows.net:9093
```

> **Phase 2 での追加設定**: Azure Event Hubs は SASL_SSL を要求するため、本番用 `application.properties` には `KAFKA_BROKERS` に加えて以下の設定が必要:
> ```properties
> spring.cloud.stream.kafka.binder.configuration.security.protocol=SASL_SSL
> spring.cloud.stream.kafka.binder.configuration.sasl.mechanism=OAUTHBEARER
> spring.cloud.stream.kafka.binder.configuration.sasl.login.callback.handler.class=...
> ```
> これらは Spring Profile (`application-azure.properties`) で環境別に切り替える。詳細は [additional1-event-pubsub-impl-plan.md](additional1-event-pubsub-impl-plan.md) §4.2 を参照。

**トピックマッピング**:

| ローカル（Kafka トピック） | Azure Event Hubs （Event Hub 名） | 対象サービス |
|-------------------------------|-------------------------------|----------------|
| `skishop.authentication-service.events` | `skishop-auth-events` | authentication |
| `skishop.user-management-service.events` | `skishop-user-events` | user-management |
| `skishop.sales-management-service.events` | `skishop-sales-events` | sales-management |
| `skishop.payment-cart-service.events` | `skishop-payment-events` | payment-cart |
| `skishop.inventory-management-service.events` | `skishop-inventory-events` | inventory-management |
| `skishop.point-service.events` | `skishop-point-events` | point |
| `skishop.coupon-service.events` | `skishop-coupon-events` | coupon |
| `skishop.ai-support-service.events` | `skishop-ai-events` | ai-support |

> トピック名は `additional1-event-pubsub-impl-plan.md` §4.2 の `spring.cloud.stream.bindings.domainEvents-out-0.destination=skishop.${spring.application.name}.events` に対応。

---

## 7. 実装における注意点

### 7.1 Critical な注意事項

| # | カテゴリ | 注意点 | リスク | 対策 |
|---|---------|--------|--------|------|
| 1 | **initdb 実行条件** | PostgreSQL の initdb スクリプトは**データディレクトリが空の場合のみ**実行される | DB を追加したい場合に initdb が再実行されない | `./scripts/dev.sh db-reset` で volume 削除して再作成。または `docker compose exec postgres psql -U postgres -c "CREATE DATABASE new_db;"` で手動追加 |
| 2 | **Docker Compose DNS とポート** | Docker Compose の内部 DNS はサービス名で解決するが、**ポートはコンテナ内部ポート**を使用する | `ports: "18080:8080"` のようにホストポートを変えても、内部通信は `service:8080` | サービス間通信は常に `<service-name>:<container-port>` で、`ports` マッピングはホストからのアクセス専用 |
| 3 | **`.env` ファイルのセキュリティ** | `.env` に秘密情報を含むため、Git にコミットしてはならない | 秘密情報の漏洩 | `.gitignore` に `.env` を追加（確認必須）。`.env.example` のみコミット |
| 4 | **Flyway と initdb の順序** | initdb で DB を作成 → アプリ起動時に Flyway がマイグレーション実行。Flyway は DB が存在しないとエラーになる | `depends_on: postgres: condition: service_healthy` だけでは DB 作成完了を保証しない | PostgreSQL の healthcheck（`pg_isready`）は initdb 完了後に healthy になるため、通常は問題ない。ただし initdb スクリプトが大きい場合は `start_period` を延ばす |
| 5 | **Prometheus ポート番号の誤り** | 現状の `prometheus.yml` のポート番号が 5/9 サービスで間違っている | メトリクスが収集されない | 本実装計画の Step 7 で修正する |
| 6 | **Azure Container Apps のポート** | Azure Container Apps の内部 Ingress はデフォルトポート 80 でリッスンする場合がある | ローカルと本番でポート番号が異なる可能性 | Azure Container Apps の Ingress 設定で `targetPort` を各サービスの `server.port` に合わせるか、ローカル同様にポート番号を統一する |
| 7 | **Kafka Binder の自動接続** | `spring-cloud-stream-binder-kafka` が classpath にあると、Spring Boot の HealthIndicator が Kafka ブローカーへの接続を試みる場合がある | Phase 1（LoggingEventPublisher）では Kafka 未使用だが、ヘルスチェックで `DOWN` が報告される可能性 | 本 Docker Compose 設計では常に Kafka を起動するため問題ない。IDE 開発時も `dev.sh infra` で Kafka が起動する。万一 Kafka なしで起動したい場合は `management.health.binders.enabled=false` で無効化可能 |

### 7.2 High な注意事項

| # | カテゴリ | 注意点 |
|---|---------|--------|
| 7 | **Docker ビルドコンテキスト** | 既存の Dockerfile はプロジェクトルート（`.`）をビルドコンテキストとして想定。`docker-compose.yml` の `build.context: .` と一致させること |
| 8 | **Maven マルチモジュールビルド** | 各 Dockerfile は `COPY pom.xml .` で親 pom をコピーし、全モジュールの pom.xml もコピーする。`docker compose build` 時にプロジェクトルートがコンテキストであることが必須 |
| 9 | **MongoDB 認証** | ローカルでは認証なし、本番では Cosmos DB の接続文字列に認証情報を含む。`MONGODB_URI` 環境変数で完全に切替可能なので問題なし |
| 10 | **Redis 無効化** | 現状 Redis は `spring.autoconfigure.exclude` で無効化されている。Docker Compose に Redis コンテナは含めない（将来の rate limiting 有効化時に追加） |
| 11 | **`docker compose build` の時間** | 9 サービスのビルドは初回 10〜20 分かかる。Maven キャッシュマウント（`--mount=type=cache,target=/root/.m2`）で 2 回目以降は短縮される |
| 12 | **シェルスクリプトの改行コード** | Windows 環境との互換性のため、シェルスクリプトは **LF 改行** を維持すること（`.gitattributes` での設定推奨） |
| 13 | **Kafka KRaft の CLUSTER_ID** | Docker Compose の Kafka 定義で `CLUSTER_ID` を固定値（`skishop-local-kafka-cluster-id`）にしている。`kafka-data` ボリューム削除後も同じ ID で再初期化されるが、ID を変更するとボリュームとの不整合でエラーになる |
| 14 | **Kafka データの永続化範囲** | `kafka-data` ボリュームにトピック・オフセット・メッセージが保存される。`dev.sh clean` で全ボリューム削除される。開発中のイベント履歴は消えるが、ローカル環境では問題ない |

### 7.3 本番固有の注意事項

| # | カテゴリ | 注意点 |
|---|---------|--------|
| 13 | **SSL 接続の強制** | Azure PostgreSQL Flexible Server はデフォルトで SSL を要求する。接続 URL に `?sslmode=require` を付与すること |
| 14 | **Azure Key Vault 参照** | Azure Container Apps は Key Vault の秘密情報をボリュームマウントまたは環境変数参照で取得できる。`DB_PASSWORD` や `JWT_SECRET` は Key Vault 経由で注入する |
| 15 | **Container App のスケーリング** | Azure Container Apps はレプリカ数を 0〜N で自動スケーリング可能。Gateway は最低 2 レプリカ以上を推奨（SPOF 解消の本質） |
| 16 | **Managed Identity** | Azure PostgreSQL への接続は可能であればパスワードレス認証（Managed Identity）を使用し、`DB_USERNAME` / `DB_PASSWORD` を不要にする |
| 17 | **Azure Event Hubs の SASL_SSL** | Azure Event Hubs は SASL_SSL プロトコルのみサポート。Phase 2 で本番デプロイ時に `security.protocol=SASL_SSL` + `sasl.mechanism=OAUTHBEARER` の設定が必須。Spring Profile（`application-azure.properties`）で環境別に設定する |
| 18 | **Event Hubs のトピック事前作成** | Azure Event Hubs は Kafka の `auto.create.topics` をサポートしない。Event Hub 名（= Kafka トピック名）は ARM テンプレートまたは Terraform で事前に作成する必要がある。§6.5 のトピックマッピング表を参照 |
| 19 | **Event Hubs のスループットユニット** | Standard SKU は 1 TU あたり 1MB/s 受信・2MB/s 送信。トラフィック増加時は Auto-inflate を有効化するか、Premium SKU へのアップグレードを検討する |

---

## 8. 実装計画

### Step 1: ディレクトリとスクリプトの準備

| # | 操作 | ファイル | 内容 |
|---|------|---------|------|
| 1-1 | **新規** | `docker/initdb/01_create_databases.sql` | 6 データベースの CREATE 文 |
| 1-2 | **新規** | `.env.example` | 環境変数テンプレート（ダミー値） |
| 1-3 | **確認** | `.gitignore` | `.env` が含まれていることを確認。なければ追加 |

### Step 2: Docker Compose ファイルの作成

| # | 操作 | ファイル | 内容 |
|---|------|---------|------|
| 2-1 | **新規** | `docker-compose.yml` | 全サービス定義（§4 の設計に基づく） |

### Step 3: シェルスクリプトの作成

| # | 操作 | ファイル | 内容 |
|---|------|---------|------|
| 3-1 | **新規** | `scripts/dev.sh` | メイン開発スクリプト（§5.1） |
| 3-2 | **新規** | `scripts/health-check.sh` | ヘルスチェックスクリプト（§5.2） |
| 3-3 | 実行権限付与 | `scripts/*.sh` | `chmod +x scripts/*.sh` |

### Step 4: Prometheus ポート番号の修正

| # | 操作 | ファイル | 変更内容 |
|---|------|---------|---------|
| 4-1 | **修正** | `monitoring/prometheus/prometheus.yml` | 5 サービスのポート番号を正しい値に修正 |

**修正内容**:
```yaml
# Before → After
api-gateway:          8080 → 8090
authentication:       8081 → 8080
user-management:      8082 → 8081
inventory-management: 8083 → 8082
sales-management:     8086 → 8083
```

### Step 5: 動作検証

| # | 検証内容 | コマンド | 期待結果 |
|---|---------|---------|---------|
| 5-1 | `.env` 作成 | `cp .env.example .env` | `.env` ファイルが作成される |
| 5-2 | インフラのみ起動 | `./scripts/dev.sh infra` | PostgreSQL + MongoDB + Kafka + Prometheus + Grafana が起動 |
| 5-3 | PostgreSQL DB 確認 | `docker compose exec postgres psql -U postgres -l` | 6 データベースが表示される |
| 5-4 | MongoDB 確認 | `docker compose exec mongo mongosh --eval "db.adminCommand('ping')"` | `{ ok: 1 }` |
| 5-5 | Kafka ブローカー確認 | `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list` | コマンドが成功すること（空リストでも OK） |
| 5-6 | IDE からの接続 | IDE でサービスを起動 | `localhost:5432` で PostgreSQL に接続できる |
| 5-7 | 全サービス起動 | `./scripts/dev.sh up` | 全 9 サービスが healthy |
| 5-8 | Gateway 経由のアクセス | `curl http://localhost:8090/actuator/health` | `{"status":"UP"}` |
| 5-9 | Prometheus 確認 | `http://localhost:9090/targets` | 全ターゲットが UP |
| 5-10 | Grafana 確認 | `http://localhost:3000` | ダッシュボードにメトリクスが表示 |
| 5-11 | 停止 | `./scripts/dev.sh down` | 全サービスが停止 |

### Step 6: `.gitignore` の確認と更新

`.env` が `.gitignore` に含まれていることを確認。その他 Docker 関連の除外パターンも追加:

```gitignore
# Docker
.env
postgres-data/
mongo-data/
kafka-data/
```

### 実装順序のまとめ

```
Step 1  ディレクトリ・initdb・.env.example 作成
  ↓
Step 2  docker-compose.yml 作成
  ↓
Step 3  シェルスクリプト作成 + chmod
  ↓
Step 4  Prometheus ポート修正
  ↓
Step 5  動作検証
  ↓
Step 6  .gitignore 確認
```

---

## 9. 作成・変更ファイル一覧

### 新規作成ファイル

| # | ファイルパス | 目的 |
|---|-----------|------|
| 1 | `docker-compose.yml` | 全サービスのローカル開発環境定義 |
| 2 | `docker/initdb/01_create_databases.sql` | PostgreSQL 複数 DB 初期化 |
| 3 | `.env.example` | 環境変数テンプレート |
| 4 | `scripts/dev.sh` | メイン開発シェルスクリプト |
| 5 | `scripts/health-check.sh` | ヘルスチェックスクリプト |

### 修正ファイル

| # | ファイルパス | 変更内容 |
|---|-----------|---------|
| 6 | `monitoring/prometheus/prometheus.yml` | 5 サービスのポート番号修正 |
| 7 | `.gitignore` | `.env` の追加確認 |

### 変更不要ファイル

| カテゴリ | 理由 |
|---------|------|
| `**/application.properties` | 環境変数で外部化済み。変更不要 |
| `**/*.java` | Java コード変更不要 |
| `**/Dockerfile` | 既存の Dockerfile は変更不要 |
| `**/pom.xml` | 依存関係の追加不要 |

---

## 10. ADR（Architecture Decision Record）

### ADR-003: Gateway SPOF 解消 — Docker Compose DNS + Azure Container Apps ネイティブ DNS

| 項目 | 内容 |
|------|------|
| **ステータス** | 承認 (Accepted) |
| **コンテキスト** | API Gateway がバックエンドサービスに静的 URL（環境変数）でルーティングしている。サービスディスカバリが存在せず、ローカル開発環境の Docker Compose もない。本番は Azure Container Apps を予定。|
| **決定** | サービスディスカバリ製品（Eureka / Consul 等）を導入せず、各デプロイ環境のネイティブ DNS を使用する。ローカルは Docker Compose の組込み DNS、本番は Azure Container Apps Environment の内部 DNS を使用。|
| **選択肢** | (A) **Docker Compose DNS + Azure Container Apps DNS（採用）** (B) Spring Cloud LoadBalancer + SimpleDiscoveryClient (C) Eureka Server (D) HashiCorp Consul |
| **選択理由** | (A) は Java コード変更ゼロ、追加依存ゼロ、既存の環境変数パターンをそのまま活用可能。Azure Container Apps は組込みのサービスディスカバリ・ロードバランサ・自動スケーリングを提供するため、Eureka/Consul は冗長。|
| **得たもの** | 運用コスト最小化、追加インフラ不要、シンプルな構成 |
| **犠牲にしたもの** | クライアントサイドロードバランシング（サービスメッシュ未導入）、サービスレジストリ UIでのサービス一覧確認 |
| **緩和策** | Azure Container Apps の組込みロードバランサーが L7 ロードバランシングを提供。Gateway のレプリカを 2 以上に設定して SPOF を解消。 |

### ADR-004: ローカル PostgreSQL 共有 + 本番 Flexible Server 分離

| 項目 | 内容 |
|------|------|
| **ステータス** | 承認 (Accepted) |
| **コンテキスト** | 6 サービスが PostgreSQL を使用。ローカル開発ではリソース節約、本番では障害分離・セキュリティ境界が必要。|
| **決定** | ローカルは PostgreSQL 1 台 + initdb で 6 DB 作成。本番はサービス毎に Azure PostgreSQL Flexible Server を 1 台ずつ用意する。|
| **選択理由** | ローカルで PostgreSQL 6 台はメモリ消費が大きく Mac で開発困難。本番では認証 DB と決済 DB のセキュリティ境界が必要。環境変数 `DB_URL` の値だけで切替可能な既存設計を活用。|
| **得たもの** | ローカル: メモリ節約（〜200MB）、起動高速。本番: 障害分離、独立スケーリング、セキュリティ境界 |
| **犠牲にしたもの** | ローカル環境と本番環境の DB 構成が異なる（1 台 vs 6 台）。ローカルでは DB 障害時の分離テストができない |

---

## 11. 検証チェックリスト

実装完了後に全て確認すること:

- [ ] `docker-compose.yml` が存在し、`docker compose config` でバリデーションが通ること
- [ ] `.env.example` が存在し、`cp .env.example .env` でローカル開発を開始できること
- [ ] `.env` が `.gitignore` に含まれていること
- [ ] `./scripts/dev.sh infra` で PostgreSQL + MongoDB + Kafka + Prometheus + Grafana が起動すること
- [ ] `docker compose exec postgres psql -U postgres -l` で 6 データベースが存在すること
- [ ] `docker compose exec mongo mongosh --eval "db.adminCommand('ping')"` が `{ ok: 1 }` を返すこと
- [ ] Kafka が起動し `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list` が成功すること
- [ ] IDE から `localhost:9092` で Kafka に接続可能であること（Phase 2 での IDE 開発時に使用）
- [ ] IDE から `localhost:5432` で PostgreSQL に接続できること
- [ ] IDE から `localhost:27017` で MongoDB に接続できること
- [ ] `./scripts/dev.sh up` で全 9 サービスが起動し、ヘルスチェックが全て ✅ であること
- [ ] `curl http://localhost:8090/actuator/health` が `{"status":"UP"}` を返すこと
- [ ] Docker 内の Gateway から `http://authentication-service:8080` でバックエンドに到達できること
- [ ] `http://localhost:9090/targets` で Prometheus の全ターゲットが UP であること
- [ ] `monitoring/prometheus/prometheus.yml` のポート番号が全サービスで正しいこと
- [ ] `./scripts/dev.sh down` で全サービスが正常に停止すること
- [ ] `./scripts/dev.sh clean` でボリュームを含む完全クリーンアップができること
- [ ] `./scripts/dev.sh db-reset` で DB の再初期化ができること
- [ ] `mvn clean test -Djacoco.skip=true -T 4` で既存テスト（316 テスト）が全て Pass すること（Java コード変更がないことの確認）
