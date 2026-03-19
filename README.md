# Ski Shop E-Commerce Platform

スキー用品に特化したマイクロサービスベースの E-Commerce プラットフォーム。

| 項目 | 値 |
|------|-----|
| **Java** | 21 |
| **Spring Boot** | 3.5.0 |
| **Spring Cloud** | 2025.0.0 |
| **Spring AI** | 1.0.0 |
| **ビルドツール** | Maven（マルチモジュール） |

## 目次

- [プロジェクト概要](#プロジェクト概要)
- [アーキテクチャ](#アーキテクチャ)
- [サービス一覧](#サービス一覧)
- [プロジェクト構成](#プロジェクト構成)
- [ローカル開発環境](#ローカル開発環境)
  - [前提条件](#前提条件)
  - [セットアップ手順](#セットアップ手順)
  - [開発モード](#開発モード)
  - [動作確認](#動作確認)
- [本番環境（Azure）](#本番環境azure)
  - [Azure リソース構成](#azure-リソース構成)
  - [CI/CD パイプライン](#cicd-パイプライン)
  - [初回セットアップ手順](#初回セットアップ手順)
  - [デプロイ手順](#デプロイ手順)
  - [本番環境の確認方法](#本番環境の確認方法)
- [テスト](#テスト)
- [監視](#監視)
- [ディレクトリ構成](#ディレクトリ構成)

---

## プロジェクト概要

初心者からプロフェッショナルまで幅広いスキーヤーを対象としたオンラインショップ。主な機能:

- **商品カタログ** — カテゴリ・フィルター・AI 検索に対応した商品管理
- **認証・認可** — JWT ベースの認証、リフレッシュトークン、ロールベースアクセス制御
- **カート & 決済** — 複数決済手段、カート管理
- **注文管理** — 注文・配送・返品・分析
- **ポイント & クーポン** — ポイント付与/利用/失効、クーポン発行/適用
- **AI サポート** — OpenAI ベースのレコメンデーション・チャットボット
- **API Gateway** — リクエストルーティング、Circuit Breaker、CORS 制御

## アーキテクチャ

```
                        ┌─────────────────┐
                        │   クライアント    │
                        └────────┬────────┘
                                 │
                    ┌────────────▼────────────┐
                    │    API Gateway (:8090)   │
                    │  ルーティング / 認証 / CB  │
                    └────────────┬────────────┘
                                 │
        ┌──────────┬──────────┬──┴───┬──────────┬──────────┐
        ▼          ▼          ▼      ▼          ▼          ▼
   ┌────────┐ ┌────────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐
   │  Auth  │ │  User  │ │Inven-│ │Sales │ │Pay-  │ │Point │ ...
   │ :8080  │ │ :8081  │ │tory  │ │:8083 │ │ment  │ │:8085 │
   └───┬────┘ └───┬────┘ │:8082 │ └──┬───┘ │:8084 │ └──┬───┘
       │          │      └──┬───┘    │     └──┬───┘    │
       ▼          ▼         ▼        ▼        ▼        ▼
   PostgreSQL  PostgreSQL  MongoDB PostgreSQL        PostgreSQL
                                         │
                                     ┌───▼───┐
                                     │ Kafka │  ← Event Pub/Sub
                                     └───────┘
```

**通信方式**:
- **同期**: REST + JWT（Gateway 経由）
- **非同期**: Apache Kafka（Spring Cloud Stream）によるイベント駆動

**データストア**:
- **PostgreSQL** — 6 サービス（auth, user, sales, payment, point, coupon）
- **MongoDB** — 2 サービス（inventory, ai-support）

## サービス一覧

| # | サービス | ポート | データストア | 概要 |
|---|---------|--------|------------|------|
| 1 | `authentication-service` | 8080 | PostgreSQL | JWT 認証・リフレッシュトークン |
| 2 | `user-management-service` | 8081 | PostgreSQL | ユーザープロフィール・設定管理 |
| 3 | `inventory-management-service` | 8082 | MongoDB | 商品カタログ・在庫管理 |
| 4 | `sales-management-service` | 8083 | PostgreSQL | 注文・配送・返品管理 |
| 5 | `payment-cart-service` | 8084 | PostgreSQL | カート・決済処理 |
| 6 | `point-service` | 8085 | PostgreSQL | ポイント付与・利用・失効 |
| 7 | `ai-support-service` | 8087 | MongoDB | AI レコメンド・チャットボット |
| 8 | `coupon-service` | 8088 | PostgreSQL | クーポン発行・適用 |
| 9 | `api-gateway-service` | 8090 | — | ルーティング・認証・Circuit Breaker |
| — | `common-lib` | — | — | 共通ライブラリ（DTO, Event, 例外） |

## プロジェクト構成

```
spring-ai-sample/
├── docker-compose.yml              # ローカル開発用 Docker Compose
├── .env.example                    # 環境変数テンプレート
├── pom.xml                         # 親 POM（マルチモジュール）
│
├── common-lib/                     # 共通ライブラリ
├── authentication-service/         # 認証サービス
├── user-management-service/        # ユーザー管理サービス
├── inventory-management-service/   # 在庫管理サービス
├── sales-management-service/       # 販売管理サービス
├── payment-cart-service/           # 決済・カートサービス
├── point-service/                  # ポイントサービス
├── coupon-service/                 # クーポンサービス
├── ai-support-service/             # AI サポートサービス
├── api-gateway-service/            # API Gateway
│
├── scripts/
│   ├── dev.sh                      # 開発用メインスクリプト
│   └── health-check.sh             # ヘルスチェックスクリプト
│
├── docker/
│   └── initdb/
│       └── 01_create_databases.sql # PostgreSQL 複数 DB 初期化
│
├── monitoring/
│   ├── prometheus/prometheus.yml    # Prometheus 設定
│   └── grafana/                    # Grafana ダッシュボード・設定
│
├── infra/
│   └── terraform/main.tf           # Azure インフラ定義（Terraform）
│
├── load-tests/                     # k6 負荷テスト
│
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                  # CI: ビルド・テスト・Docker Push
│   │   ├── deploy.yml              # CD: Azure Container Apps デプロイ
│   │   ├── infra.yml               # Terraform インフラ管理
│   │   └── load-test.yml           # 負荷テスト実行
│   └── copilot-instructions.md     # Copilot 共通ルール
│
└── design-docs/                    # 設計ドキュメント
```

---

## ローカル開発環境

### 前提条件

| ソフトウェア | バージョン | 確認コマンド |
|------------|-----------|------------|
| **Java JDK** | 21 以上 | `java -version` |
| **Maven** | 3.9 以上 | `mvn -version` |
| **Docker** | 24 以上 | `docker --version` |
| **Docker Compose** | v2 （Docker 同梱） | `docker compose version` |

### セットアップ手順

```bash
# 1. リポジトリをクローン
git clone <repository-url>
cd spring-ai-sample

# 2. 環境変数ファイルを作成
cp .env.example .env
# 必要に応じて .env の値を編集（デフォルト値でローカル開発は可能）

# 3. Maven ビルド（初回のみ）
mvn clean package -DskipTests -Djacoco.skip=true -T 4

# 4. インフラを起動
./scripts/dev.sh infra
```

### 開発モード

3 つの開発モードを用途に応じて選択:

#### モード A: IDE ハイブリッド（推奨）

インフラ（DB, Kafka, 監視）のみ Docker で起動し、アプリケーションは IDE で起動・デバッグする。

```bash
# インフラのみ起動
./scripts/dev.sh infra

# IDE（IntelliJ / VS Code）で任意のサービスを起動
# → 環境変数不要（localhost デフォルト値で DB, Kafka に自動接続）
# → DB_PASSWORD と JWT_SECRET のみ IDE の Run Configuration に設定
```

| 接続先 | ホスト | ポート |
|--------|-------|--------|
| PostgreSQL | `localhost` | 5432 |
| MongoDB | `localhost` | 27017 |
| Kafka | `localhost` | 9092 |
| Prometheus | `localhost` | 9090 |
| Grafana | `localhost` | 3000 |

#### モード B: 全コンテナ起動（E2E テスト向け）

全 9 サービス + インフラを Docker で起動する。

```bash
# 全サービス起動（ビルド + 起動）
./scripts/dev.sh build   # Docker イメージ構築
./scripts/dev.sh up      # 全サービス起動 + ヘルスチェック
```

#### モード C: 部分起動

全サービスを Docker で起動し、特定のサービスだけ IDE に切り替える。

```bash
./scripts/dev.sh up

# 特定サービスを止めて IDE で起動
docker compose stop authentication-service
# → IDE で authentication-service を起動（localhost:8080）
```

### `dev.sh` コマンド一覧

| コマンド | 説明 |
|---------|------|
| `./scripts/dev.sh infra` | インフラのみ起動（PostgreSQL, MongoDB, Kafka, Prometheus, Grafana） |
| `./scripts/dev.sh up` | 全サービス起動 + ヘルスチェック |
| `./scripts/dev.sh down` | 全サービス停止 |
| `./scripts/dev.sh restart` | 全サービス再起動 |
| `./scripts/dev.sh logs [svc]` | ログ表示（サービス名省略で全体） |
| `./scripts/dev.sh ps` | サービス状態一覧 |
| `./scripts/dev.sh health` | 全サービスのヘルスチェック |
| `./scripts/dev.sh build` | Maven ビルド + Docker イメージ再構築 |
| `./scripts/dev.sh clean` | 全コンテナ・ボリューム削除（⚠️ データ消失） |
| `./scripts/dev.sh db-reset` | PostgreSQL + MongoDB のデータ再初期化（⚠️ データ消失） |

### 動作確認

```bash
# 1. インフラの確認
./scripts/dev.sh infra

# PostgreSQL — 6 データベースが作成されているか
docker compose exec postgres psql -U postgres -l

# MongoDB — 接続確認
docker compose exec mongo mongosh --eval "db.adminCommand('ping')"

# Kafka — ブローカー起動確認
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list

# 2. 全サービス起動後の確認
./scripts/dev.sh up

# Gateway ヘルスチェック
curl http://localhost:8090/actuator/health

# 各サービスの個別ヘルスチェック
curl http://localhost:8080/actuator/health   # auth
curl http://localhost:8081/actuator/health   # user
curl http://localhost:8082/actuator/health   # inventory
curl http://localhost:8083/actuator/health   # sales
curl http://localhost:8084/actuator/health   # payment
curl http://localhost:8085/actuator/health   # point
curl http://localhost:8087/actuator/health   # ai-support
curl http://localhost:8088/actuator/health   # coupon

# 3. 監視ダッシュボード
# Prometheus:  http://localhost:9090/targets  （全ターゲットが UP）
# Grafana:     http://localhost:3000          （admin / admin でログイン）

# 4. 停止
./scripts/dev.sh down
```

---

## 本番環境（Azure）

本番環境は **Azure Container Apps** にデプロイし、GitHub Actions で CI/CD を自動化する。

### Azure リソース構成

```
Azure Resource Group (skishop-rg)
│
├── Container Apps Environment (skishop-cae)
│   ├── gateway-svc       ← External Ingress (HTTPS)
│   ├── auth-svc          ← Internal Ingress
│   ├── user-svc          ← Internal Ingress
│   ├── inventory-svc     ← Internal Ingress
│   ├── sales-svc         ← Internal Ingress
│   ├── payment-svc       ← Internal Ingress
│   ├── point-svc         ← Internal Ingress
│   ├── coupon-svc        ← Internal Ingress
│   └── ai-svc            ← Internal Ingress
│
├── PostgreSQL Flexible Server × 6
│   ├── skishop-auth-db     (skishop_auth)
│   ├── skishop-user-db     (skishop_users)
│   ├── skishop-sales-db    (skishop_sales)
│   ├── skishop-payment-db  (skishop_payment)
│   ├── skishop-point-db    (point_db)
│   └── skishop-coupon-db   (coupon_db)
│
├── Cosmos DB for MongoDB vCore × 1
│   └── skishop-mongo  (skishop_inventory, skishop_ai)
│
├── Event Hubs (Kafka 互換) × 1
│   └── skishop-eventhub  (8 トピック)
│
├── Key Vault × 1            (秘密情報一元管理)
├── Container Registry × 1   (Docker イメージ)
└── Log Analytics Workspace   (ログ集約)
```

### CI/CD パイプライン

```
┌──────────┐     ┌───────────────────────────────┐     ┌──────────────────────┐
│ git push │────▶│ ci.yml                        │────▶│ deploy.yml           │
│ to main  │     │ ① Build & Test (mvn verify)   │     │ ① Azure Login (OIDC) │
└──────────┘     │ ② Docker Build & Push (GHCR)  │     │ ② Deploy to ACA      │
                 └───────────────────────────────┘     │ ③ Smoke Test         │
                                                       └──────────────────────┘
```

| Workflow | 説明 | トリガー |
|----------|------|---------|
| `ci.yml` | ビルド・テスト・Docker イメージ Push | `push` to main/develop, PR to main |
| `deploy.yml` | Azure Container Apps へデプロイ | CI 成功後自動 or 手動 (`workflow_dispatch`) |
| `infra.yml` | Terraform によるインフラ構築/変更/削除 | 手動 (`workflow_dispatch`) |
| `load-test.yml` | k6 負荷テスト | 手動 (`workflow_dispatch`) |

### 初回セットアップ手順

#### 1. GitHub リポジトリの設定

**Secrets**（Settings → Secrets and variables → Actions）:

| Secret | 説明 |
|--------|------|
| `AZURE_CLIENT_ID` | Azure AD アプリケーション（サービスプリンシパル）のクライアント ID |
| `AZURE_TENANT_ID` | Azure AD テナント ID |
| `AZURE_SUBSCRIPTION_ID` | Azure サブスクリプション ID |

**Variables**（Settings → Secrets and variables → Actions → Variables）:

| Variable | 説明 | 例 |
|----------|------|----|
| `AZURE_RESOURCE_GROUP` | リソースグループ名 | `skishop-rg` |
| `CONTAINER_APPS_ENVIRONMENT` | Container Apps 環境名 | `skishop-cae` |
| `AZURE_LOCATION` | Azure リージョン | `japaneast` |
| `GATEWAY_EXTERNAL_URL` | Gateway の外部 URL | `https://gateway-svc.xxx.azurecontainerapps.io` |

#### 2. Azure インフラの構築

```bash
# GitHub Actions → infra.yml を手動実行
# Action: plan → 確認 → apply
```

または Terraform をローカルで実行:

```bash
cd infra/terraform

# 変数を設定
export TF_VAR_subscription_id="<your-subscription-id>"
export TF_VAR_db_admin_password="<strong-password>"
export TF_VAR_jwt_secret="<jwt-secret-min-256-bits>"

terraform init
terraform plan
terraform apply
```

#### 3. 初回デプロイ

```bash
# GitHub Actions → deploy.yml を手動実行
# Services: all
# Environment: production
```

### デプロイ手順

**自動デプロイ（推奨）**:

`main` ブランチへの push → CI（ビルド・テスト）成功 → 自動デプロイ

**手動デプロイ**:

GitHub Actions → `deploy.yml` → Run workflow:
- `services`: `all` または `authentication-service,user-management-service`（カンマ区切り）
- `environment`: `production` または `staging`

### 本番環境の確認方法

```bash
# 1. Gateway のヘルスチェック
curl https://<gateway-url>/actuator/health

# 2. Azure CLI でサービス状態を確認
az containerapp list \
  --resource-group skishop-rg \
  --output table

# 3. 個別サービスのステータス
az containerapp show \
  --name gateway-svc \
  --resource-group skishop-rg \
  --query "properties.runningStatus" \
  --output tsv

# 4. ログの確認
az containerapp logs show \
  --name auth-svc \
  --resource-group skishop-rg \
  --follow

# 5. レプリカ数の確認
az containerapp revision list \
  --name gateway-svc \
  --resource-group skishop-rg \
  --output table
```

---

## テスト

```bash
# 全テスト実行
mvn test -Djacoco.skip=true -T 4

# 特定サービスのみ
mvn test -pl authentication-service -Djacoco.skip=true

# テスト + カバレッジレポート
mvn verify

# 負荷テスト（k6）
cd load-tests
k6 run --config config/smoke.json scripts/health-check.js
```

## 監視

| ツール | ローカル URL | 用途 |
|-------|------------|------|
| **Prometheus** | http://localhost:9090 | メトリクス収集・クエリ |
| **Grafana** | http://localhost:3000 | ダッシュボード可視化 |

各サービスは Spring Boot Actuator + Micrometer で `/actuator/prometheus` エンドポイントを公開し、Prometheus がスクレイプする。

本番環境では Azure Log Analytics Workspace にログとメトリクスが自動集約される。

---

## ディレクトリ構成

各マイクロサービスは統一されたレイヤードアーキテクチャに従う:

```
<service-name>/
├── Dockerfile                     # マルチステージビルド
├── pom.xml                        # サービス固有の依存関係
└── src/
    ├── main/
    │   ├── java/com/example/skishop/<service>/
    │   │   ├── controller/        # REST API エンドポイント
    │   │   ├── service/           # ビジネスロジック
    │   │   ├── repository/        # データアクセス
    │   │   ├── model/             # エンティティ・DTO
    │   │   └── config/            # 設定クラス
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/      # Flyway マイグレーション (PostgreSQL のみ)
    └── test/
        └── java/                  # 単体テスト・統合テスト
```

## Swagger

各マイクロサービスの Swagger UI URL:

| # | サービス | ポート | Swagger UI | OpenAPI JSON |
|---|---------|--------|------------|--------------|
| 1 | authentication-service | 8080 | http://localhost:8080/swagger-ui/index.html | http://localhost:8080/v3/api-docs |
| 2 | user-management-service | 8081 | http://localhost:8081/swagger-ui/index.html | http://localhost:8081/v3/api-docs |
| 3 | inventory-management-service | 8082 | http://localhost:8082/swagger-ui/index.html | http://localhost:8082/v3/api-docs |
| 4 | sales-management-service | 8083 | http://localhost:8083/swagger-ui/index.html | http://localhost:8083/v3/api-docs |
| 5 | payment-cart-service | 8084 | http://localhost:8084/swagger-ui/index.html | http://localhost:8084/v3/api-docs |
| 6 | point-service | 8085 | http://localhost:8085/swagger-ui/index.html | http://localhost:8085/v3/api-docs |
| 7 | ai-support-service | 8087 | http://localhost:8087/swagger-ui/index.html | http://localhost:8087/v3/api-docs |
| 8 | coupon-service | 8088 | http://localhost:8088/swagger-ui/index.html | http://localhost:8088/v3/api-docs |
| 9 | mailsend-service | 8089 | http://localhost:8089/swagger-ui.html | http://localhost:8089/v3/api-docs |
| 10 | api-gateway-service | 8090 | http://localhost:8090/swagger-ui/index.html | http://localhost:8090/v3/api-docs |


## デモ用アカウント作成

全サービス起動後、以下のコマンドで検証用アカウントを作成できます。

### 1. 一般ユーザーの作成

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"User1234!","firstName":"テスト","lastName":"ユーザー"}'
```

### 2. 管理者の作成

```bash
# ユーザー登録
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"Admin1234!","firstName":"管理者","lastName":"テスト"}'

# ロールを ADMIN に変更（PostgreSQL 直接更新）
docker compose exec postgres psql -U postgres -d skishop_auth \
  -c "UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';"
```

### 作成済みアカウント一覧

| 種別 | メールアドレス | パスワード | ロール |
|------|-------------|-----------|--------|
| 一般ユーザー | `user@example.com` | `User1234!` | USER |
| 管理者 | `admin@example.com` | `Admin1234!` | ADMIN |

> **注意**: 登録 API で作成されたアカウントはメール認証済み（`emailVerified=true`）の状態で即座にログイン可能です。管理者は `/admin/dashboard` にアクセスできます。


---

## ライセンス

このプロジェクトはプライベートリポジトリです。
