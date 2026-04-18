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
  - [Azure Communication Services（メール送信）のセットアップ](#azure-communication-servicesメール送信のセットアップ)
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

### Azure Communication Services（メール送信）のセットアップ

`mailsend-service` は Azure Communication Services (ACS) Email を利用して通知メールを送信します。
ACS リソースを未作成のまま `docker compose --profile app up` を実行すると、以下の WARN が表示され、メール送信機能が動作しません。

```text
WARN: The "AZURE_COMMUNICATION_ENDPOINT" variable is not set. Defaulting to a blank string.
WARN: The "AZURE_COMMUNICATION_CONNECTION_STRING" variable is not set. Defaulting to a blank string.
WARN: The "MAIL_SENDER_ADDRESS" variable is not set. Defaulting to a blank string.
```

以下の手順で Azure 上に必要なリソースを作成し、`.env` に接続情報を設定します。

#### 前提

- Azure CLI（`az`）がインストール済みで `az login` 済み
- Azure サブスクリプションへのリソース作成権限（Contributor 以上）
- 作成先のリソースグループが存在すること（無い場合は `az group create` で作成）

#### 1. Azure CLI 拡張機能のインストール

```bash
# communication 拡張機能を追加（既にインストール済みなら警告のみ）
az extension add --name communication --yes
az extension show --name communication --query version -o tsv
```

#### 2. リソースプロバイダーの登録

サブスクリプションで初めて ACS を利用する場合、`Microsoft.Communication` プロバイダーの登録が必須です（数分かかります）。

```bash
SUB=<your-subscription-id>

az provider register \
  --namespace Microsoft.Communication \
  --subscription "$SUB" \
  --wait

# Registered になっていることを確認
az provider show -n Microsoft.Communication --subscription "$SUB" \
  --query registrationState -o tsv
```

#### 3. ACS リソースの作成

ACS は次の 3 階層で構成されます。順番に作成してください。

| 順序 | リソース種別 | 役割 |
|------|------------|------|
| ① | `Microsoft.Communication/emailServices` | メールサービス（データ保管リージョンを保持） |
| ② | `.../emailServices/domains` | 送信元ドメイン（Azure Managed Domain or Customer Managed Domain） |
| ③ | `Microsoft.Communication/communicationServices` | ACS 本体（Email Domain にリンクして送信機能を有効化） |

```bash
# 変数定義
SUB=<your-subscription-id>
RG=rg-yoshio-test                    # 利用するリソースグループ名
EMAIL_SVC=skishop-email-comm         # ① Email Service 名
ACS_NAME=skishop-acs                 # ③ Communication Service 名
DATA_LOC=japan                       # データ保管リージョン（japan / unitedstates / europe など）

# ① Email Communication Service を作成
az communication email create \
  --name "$EMAIL_SVC" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --location global \
  --data-location "$DATA_LOC"

# ② Azure Managed Domain を作成（無料・即時 Verified、1 日 100 通の制限あり）
az communication email domain create \
  --domain-name AzureManagedDomain \
  --email-service-name "$EMAIL_SVC" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --location global \
  --domain-management AzureManaged

# 送信元ドメイン（fromSenderDomain）を取得
DOMAIN_ID=$(az communication email domain show \
  --domain-name AzureManagedDomain \
  --email-service-name "$EMAIL_SVC" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --query id -o tsv)

SENDER_DOMAIN=$(az communication email domain show \
  --domain-name AzureManagedDomain \
  --email-service-name "$EMAIL_SVC" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --query fromSenderDomain -o tsv)

# ③ Communication Service を作成し、Email Domain をリンク
az communication create \
  --name "$ACS_NAME" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --location global \
  --data-location "$DATA_LOC" \
  --linked-domains "$DOMAIN_ID"
```

> **本番環境向け**: 独自ドメインを使う場合は `--domain-management CustomerManaged` を指定し、DNS に TXT/CNAME レコード（SPF・DKIM・DMARC）を登録して `az communication email domain initiate-verification` で検証してください。

#### 4. 接続情報の取得

```bash
# エンドポイント
ENDPOINT=$(az communication show \
  --name "$ACS_NAME" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --query "hostName" -o tsv)
ENDPOINT="https://${ENDPOINT}/"

# 接続文字列（プライマリ）
CONNECTION_STRING=$(az communication list-key \
  --name "$ACS_NAME" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --query primaryConnectionString -o tsv)

# 送信元アドレス（Azure Managed Domain の場合）
SENDER_ADDRESS="DoNotReply@${SENDER_DOMAIN}"

echo "AZURE_COMMUNICATION_ENDPOINT=${ENDPOINT}"
echo "AZURE_COMMUNICATION_CONNECTION_STRING=${CONNECTION_STRING}"
echo "MAIL_SENDER_ADDRESS=${SENDER_ADDRESS}"
```

#### 5. `.env` への設定

取得した値を `.env` に追記します。

```bash
cat >> .env << EOF

# Azure Communication Services (ACS)
AZURE_COMMUNICATION_ENDPOINT=${ENDPOINT}
AZURE_COMMUNICATION_CONNECTION_STRING=${CONNECTION_STRING}
MAIL_SENDER_ADDRESS=${SENDER_ADDRESS}
EOF
```

`.env` で参照される環境変数と利用箇所:

| 環境変数 | 参照ファイル | 用途 |
|---------|------------|------|
| `AZURE_COMMUNICATION_ENDPOINT` | `mailsend-service/src/main/resources/application.properties` | ACS REST エンドポイント URL |
| `AZURE_COMMUNICATION_CONNECTION_STRING` | 同上 | ACS 接続文字列（`accesskey` を含む） |
| `MAIL_SENDER_ADDRESS` | 同上 | 送信元メールアドレス（`From`） |

#### 6. 動作確認

```bash
# 環境変数を読み込んで Docker Compose を起動（WARN が消えれば成功）
set -a && source .env && set +a
docker compose --profile app up -d mail
docker logs -f skishop-mail
```

#### セキュリティ上の注意

- ⚠ **`.env` は絶対にコミットしないでください**（`.gitignore` に登録済みであることを確認）。
- 本番環境では接続文字列を **Azure Key Vault** に格納し、`@Value("${...}")` 経由でアプリへ注入してください。
- Azure Managed Domain は開発・検証用途向けです（1 日 100 通、レート制限あり）。本番では Customer Managed Domain を利用してください。
- アクセスキーをローテーションする場合は `az communication regenerate-key --key-type primary` を実行し、`.env` を更新してください。

#### クリーンアップ（不要になった場合）

```bash
az communication delete --name "$ACS_NAME" --resource-group "$RG" --subscription "$SUB" --yes
az communication email domain delete --domain-name AzureManagedDomain \
  --email-service-name "$EMAIL_SVC" --resource-group "$RG" --subscription "$SUB" --yes
az communication email delete --name "$EMAIL_SVC" --resource-group "$RG" --subscription "$SUB" --yes
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

#### モード B: 全コンテナ起動（E2E テスト・統合検証向け）

全 11 サービス（マイクロサービス 9 + API Gateway + Agent Runtime）+ インフラを Docker Compose で起動する。

##### 1. 事前準備

`.env` ファイルが用意され、以下の値が設定されていること:

- `DB_PASSWORD`, `JWT_SECRET`（必須）
- `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_DEPLOYMENT_NAME`（AI 機能を使う場合）
- `AZURE_COMMUNICATION_ENDPOINT`, `AZURE_COMMUNICATION_CONNECTION_STRING`, `MAIL_SENDER_ADDRESS`（メール送信を使う場合 — 上の節を参照）

> ⚠ **`.env` の値に `;` `#` `空白` などのシェルメタ文字を含む場合は必ずダブルクオートで囲んでください。**
> 例: `AZURE_COMMUNICATION_CONNECTION_STRING="endpoint=https://...;accesskey=..."`
> （囲まないと `source .env` 時に `;` 以降が切り捨てられ、`'key' cannot be null` 等の起動失敗の原因になります）

##### 2. ホスト側のポート競合チェック

下表のポートがホスト側で他プロセスにより使用されていないことを確認します。

| ポート | サービス |
|------|---------|
| 5432 | PostgreSQL |
| 27017 | MongoDB |
| 9092 | Kafka |
| 9090 | Prometheus |
| 3000 | Grafana |
| 8080 | authentication-service |
| 8081 | user-management-service |
| 8082 | inventory-management-service |
| 8083 | sales-management-service |
| 8084 | payment-cart-service |
| 8085 | point-service |
| 8087 | ai-support-service |
| 8088 | coupon-service |
| 8089 | mailsend-service |
| 8090 | api-gateway-service |
| 8100 | agent-runtime-monolith |

```bash
# 競合確認（出力があれば該当 PID を停止）
for p in 5432 27017 9092 9090 3000 8080 8081 8082 8083 8084 8085 8087 8088 8089 8090 8100; do
  lsof -nP -iTCP:"$p" -sTCP:LISTEN 2>/dev/null | tail -n +2
done
```

##### 3. ビルド

```bash
# Maven マルチモジュールを一括ビルド（初回 / コード変更後）
mvn clean package -DskipTests -Djacoco.skip=true -T 4

# Docker イメージをビルド
set -a && source .env && set +a
docker compose --profile app build
```

##### 4. 全サービス起動

```bash
set -a && source .env && set +a
docker compose --profile app up -d

# 起動完了まで待機（おおよそ 60〜90 秒）
sleep 90

# 全コンテナの状態を確認（全て healthy であること）
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep skishop
```

期待される出力（16 コンテナ全て healthy / Up）:

```text
skishop-gateway         Up (healthy)   0.0.0.0:8090->8090/tcp
skishop-agent-runtime   Up (healthy)   0.0.0.0:8100->8100/tcp
skishop-auth            Up (healthy)   0.0.0.0:8080->8080/tcp
skishop-user            Up (healthy)   0.0.0.0:8081->8081/tcp
skishop-inventory       Up (healthy)   0.0.0.0:8082->8082/tcp
skishop-sales           Up (healthy)   0.0.0.0:8083->8083/tcp
skishop-payment         Up (healthy)   0.0.0.0:8084->8084/tcp
skishop-point           Up (healthy)   0.0.0.0:8085->8085/tcp
skishop-ai              Up (healthy)   0.0.0.0:8087->8087/tcp
skishop-coupon          Up (healthy)   0.0.0.0:8088->8088/tcp
skishop-mail            Up (healthy)   0.0.0.0:8089->8089/tcp
skishop-postgres        Up (healthy)   0.0.0.0:5432->5432/tcp
skishop-mongo           Up (healthy)   0.0.0.0:27017->27017/tcp
skishop-kafka           Up (healthy)   0.0.0.0:9092->9092/tcp
skishop-prometheus      Up             0.0.0.0:9090->9090/tcp
skishop-grafana         Up             0.0.0.0:3000->3000/tcp
```

##### 5. 個別サービスの再起動

特定サービスだけビルドして反映:

```bash
# サービス名は docker-compose.yml の service キー（末尾 -service 含む）
set -a && source .env && set +a
docker compose --profile app build mailsend-service
docker compose --profile app up -d mailsend-service

# ログ確認
docker logs --tail 50 -f skishop-mail
```

| サービスキー（`docker compose` 用） | コンテナ名 |
|-----------------------------------|-----------|
| `authentication-service` | `skishop-auth` |
| `user-management-service` | `skishop-user` |
| `inventory-management-service` | `skishop-inventory` |
| `sales-management-service` | `skishop-sales` |
| `payment-cart-service` | `skishop-payment` |
| `point-service` | `skishop-point` |
| `coupon-service` | `skishop-coupon` |
| `ai-support-service` | `skishop-ai` |
| `mailsend-service` | `skishop-mail` |
| `api-gateway-service` | `skishop-gateway` |
| `agent-runtime-monolith` | `skishop-agent-runtime` |

##### 6. 停止 / クリーンアップ

```bash
# 停止（コンテナ削除、ボリュームは保持）
docker compose --profile app down

# データも含めて完全削除（⚠ DB データ消失）
docker compose --profile app down -v
```

##### よくある失敗と対処

| 症状 | 原因 | 対処 |
|------|------|------|
| `Bind for 0.0.0.0:<port> failed: port is already allocated` | ホスト側で別プロセスが該当ポートを LISTEN | `lsof -nP -iTCP:<port> -sTCP:LISTEN` で特定し停止／コンテナなら `docker rm -f <name>` |
| `skishop-mail` が `Restarting`、ログに `'key' cannot be null` | `.env` の接続文字列がクオートされておらず `;` で truncate | `AZURE_COMMUNICATION_CONNECTION_STRING="..."` のようにダブルクオートで囲む |
| `agent-runtime-monolith` 起動時に Azure OpenAI 認証エラー | `AZURE_OPENAI_*` 環境変数未設定 / 値誤り | `.env` の値を再確認し `docker compose up -d agent-runtime-monolith` で再起動 |
| `docker-compose` が `no such service: <name>` を返す | サービスキー名は末尾 `-service` を含む | 上記の対応表を参照（例: `mail` ではなく `mailsend-service`） |
| 古い `welcome-to-docker` 等のサンプルコンテナがポートを占有 | Docker Desktop の初回起動コンテナが残存 | `docker rm -f welcome-to-docker` |

#### モード C: 部分起動

全サービスを Docker で起動し、特定のサービスだけ IDE に切り替える。

```bash
set -a && source .env && set +a
docker compose --profile app up -d

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
curl http://localhost:8089/actuator/health   # mailsend
curl http://localhost:8100/actuator/health   # agent-runtime-monolith

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
