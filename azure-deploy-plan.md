# Azure Container Apps デプロイ計画書
## Azure Ski Shop — ミッションクリティカル本番環境構築

| 項目 | 値 |
|------|-----|
| 対象サブスクリプション | `<AZURE_SUBSCRIPTION_ID>` |
| 対象リソースグループ | `<RESOURCE_GROUP_NAME>` |
| テナント | `caglobaldemos2605.onmicrosoft.com` |
| 対象リージョン | `japaneast` |
| 作成日 | 2026-04-20 |
| ステータス | DRAFT |

---

## 目次

1. [現状アーキテクチャ](#1-現状アーキテクチャ)
2. [ターゲット Azure アーキテクチャ](#2-ターゲット-azure-アーキテクチャ)
3. [既存リソースの確認と再利用方針](#3-既存リソースの確認と再利用方針)
4. [必要な Azure リソース一覧](#4-必要な-azure-リソース一覧)
5. [前提条件・事前準備](#5-前提条件事前準備)
6. [構築ステップ詳細](#6-構築ステップ詳細)
   - [Phase 1: IAM・権限設定](#phase-1-iam権限設定)
   - [Phase 2: データ基盤構築](#phase-2-データ基盤構築)
   - [Phase 3: メッセージング基盤構築](#phase-3-メッセージング基盤構築)
   - [Phase 4: コンテナ基盤構築](#phase-4-コンテナ基盤構築)
   - [Phase 5: シークレット管理](#phase-5-シークレット管理)
   - [Phase 6: CI/CD パイプライン設定](#phase-6-cicd-パイプライン設定)
   - [Phase 7: コンテナイメージのビルド・プッシュ](#phase-7-コンテナイメージのビルドプッシュ)
   - [Phase 8: Container Apps デプロイ](#phase-8-container-apps-デプロイ)
   - [Phase 9: データ初期化](#phase-9-データ初期化)
   - [Phase 10: フロントエンドデプロイ](#phase-10-フロントエンドデプロイ)
7. [環境変数マッピング](#7-環境変数マッピング)
8. [ネットワーク設計](#8-ネットワーク設計)
9. [監視・アラート設定](#9-監視アラート設定)
10. [スモークテスト手順](#10-スモークテスト手順)
11. [ロールバック計画](#11-ロールバック計画)
12. [運用・保守考慮事項](#12-運用保守考慮事項)
13. [作業スケジュール目安](#13-作業スケジュール目安)
14. [各フェーズ完了チェックリスト](#14-各フェーズ完了チェックリスト)

---

## 1. 現状アーキテクチャ

### Docker Compose 構成（16 コンテナ）

```
┌───────────────────────────────────────────────────────────┐
│ Docker Compose (ローカル / 開発環境)                         │
│                                                           │
│  ┌─────────────┐  ┌──────────────────────────────────┐   │
│  │  Frontend   │  │         API Gateway :8090         │   │
│  │  Next.js    │  │  (Spring Cloud Gateway)           │   │
│  │  :3001      │  └──┬───┬───┬───┬───┬───┬───┬───┬───┘   │
│  └─────────────┘     │   │   │   │   │   │   │   │       │
│                   8080│8081│8082│8083│8084│8085│8087│8088│8089│8100
│  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐       │
│  │auth  │  │user  │  │inven-│  │sales │  │pay-  │  ...  │
│  │:8080 │  │:8081 │  │tory  │  │:8083 │  │ment  │       │
│  └──┬───┘  └──┬───┘  │:8082 │  └──┬───┘  │:8084 │       │
│     │         │      └──┬───┘     │      └──────┘       │
│     ▼         ▼         ▼         ▼                      │
│  PostgreSQL(共有)     MongoDB     Kafka(KRaft)            │
│  :5432               :27017       :9092                  │
└───────────────────────────────────────────────────────────┘
```

### サービス一覧

| サービス名 | コンテナ名 | ポート | データストア |
|-----------|-----------|--------|------------|
| authentication-service | skishop-auth | 8080 | PostgreSQL: skishop_auth |
| user-management-service | skishop-user | 8081 | PostgreSQL: skishop_users |
| inventory-management-service | skishop-inventory | 8082 | MongoDB: skishop_inventory |
| sales-management-service | skishop-sales | 8083 | PostgreSQL: skishop_sales |
| payment-cart-service | skishop-payment | 8084 | PostgreSQL: skishop_payment |
| point-service | skishop-point | 8085 | PostgreSQL: point_db |
| ai-support-service | skishop-ai | 8087 | MongoDB: skishop_ai |
| coupon-service | skishop-coupon | 8088 | PostgreSQL: coupon_db |
| mailsend-service | skishop-mail | 8089 | PostgreSQL: skishop_mailsend |
| api-gateway-service | skishop-gateway | 8090 | — |
| agent-runtime-monolith | skishop-agent-runtime | 8100 | — |
| frontend (Next.js) | skishop-frontend | 3001 | — |

---

## 2. ターゲット Azure アーキテクチャ

```
                    ┌──────────────────────────────────┐
                    │  Azure DNS / Front Door (任意)    │
                    └───────────────┬──────────────────┘
                                    │ HTTPS
                    ┌───────────────▼──────────────────┐
                    │  Container App: frontend-svc      │
                    │  Next.js  (External Ingress)      │
                    └───────────────┬──────────────────┘
                                    │
                    ┌───────────────▼──────────────────┐
                    │  Container App: gateway-svc       │
                    │  API Gateway  (External Ingress)  │
                    └──┬─┬─┬─┬─┬─┬─┬─┬─┬──────────────┘
                       │ │ │ │ │ │ │ │ │
        ┌──────────────┘ │ │ │ │ │ │ │ └──────────────────┐
        │    ┌───────────┘ │ │ │ │ │ └──────────┐         │
        │    │    ┌────────┘ │ │ │ └────────┐   │         │
        ▼    ▼    ▼          ▼ ▼ ▼          ▼   ▼         ▼
     auth  user  inven    sales pay  point  ai coupon  agent
     svc   svc   tory-svc svc  svc  svc   svc  svc   runtime
    (Int) (Int)  (Int)   (Int)(Int)(Int) (Int)(Int)   (Int)
                                              │
                                              ▼
                                          mail-svc (Int)
     │     │      │        │    │    │    │    │        │
     ▼     ▼      ▼        ▼    ▼    ▼    ▼    ▼        ▼
┌─────────────────────────────────────────────────────────┐
│             Azure マネージドサービス                       │
│                                                         │
│  PostgreSQL Flexible Server × 7        Cosmos DB (Mongo)│
│  ┌──────┐┌──────┐┌──────┐┌──────┐     ┌──────────────┐ │
│  │auth  ││users ││sales ││pay   │     │skishop_inven-│ │
│  │_auth ││_users││_sales││_ment │     │tory          │ │
│  └──────┘└──────┘└──────┘└──────┘     │skishop_ai    │ │
│  ┌──────┐┌──────┐                     └──────────────┘ │
│  │point ││coupon│                                       │
│  │_db   ││_db   │   Event Hubs Namespace (Kafka互換)    │
│  └──────┘└──────┘   ┌─────────────────────────────┐    │
│                     │ 8 topics (auth/user/sales/  │    │
│                     │ payment/inventory/point/    │    │
│                     │ coupon/ai events)           │    │
│                     └─────────────────────────────┘    │
│                                                         │
│  Key Vault          Container Registry   Log Analytics  │
│  Azure OpenAI       Azure Communication Services (既存) │
│  Azure AI Foundry (既存)   Azure Search (既存)           │
└─────────────────────────────────────────────────────────┘

  ※ (Int) = Internal Ingress のみ（VNet 内部からのみアクセス可）
  ※ (Ext) = External Ingress（インターネットからアクセス可）
```

---

## 3. 既存リソースの確認と再利用方針

`<RESOURCE_GROUP_NAME>` に既にデプロイ済みのリソース:

| リソース | 種別 | 再利用方針 |
|---------|------|-----------|
| Azure AI Foundry | AI Hub / Project | `agent-runtime-monolith` から接続（既存エンドポイント利用） |
| Azure Cognitive Search | Search Service | `ai-support-service` の検索機能強化に接続 |
| Azure Communication Services (`skishop-acs`) | ACS Email | `mailsend-service` がそのまま利用（`.env` 設定済み） |
| Azure OpenAI (`yoshio-test`) | Azure OpenAI | `ai-support-service`, `agent-runtime-monolith` から利用 |

### 確認コマンド

```bash
SUB="<AZURE_SUBSCRIPTION_ID>"
RG="<RESOURCE_GROUP_NAME>"

# 既存リソースの一覧確認
az resource list \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --output table

# ACS エンドポイント確認
az communication show \
  --name "skishop-acs" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --query "{endpoint:hostName, location:location}" \
  --output json
```

---

## 4. 必要な Azure リソース一覧

### 新規作成が必要なリソース

| リソース | 名前(案) | SKU/Tier | 目的 |
|---------|---------|---------|------|
| Container Apps Environment | `skishop-cae` | Consumption | 全 Container Apps を収容する環境 |
| Log Analytics Workspace | `skishop-logs` | PerGB2018 | Container Apps のログ集約 |
| Container Registry | `<ACR_NAME>` | Standard | Docker イメージの格納 |
| Key Vault | `skishop-kv` | Standard | シークレット一元管理 |
| PostgreSQL Flexible Server (auth) | `skishop-auth-db` | B_Standard_B1ms | 認証 DB |
| PostgreSQL Flexible Server (user) | `skishop-user-db` | B_Standard_B1ms | ユーザー管理 DB |
| PostgreSQL Flexible Server (sales) | `skishop-sales-db` | B_Standard_B1ms | 販売管理 DB |
| PostgreSQL Flexible Server (payment) | `skishop-payment-db` | B_Standard_B1ms | 決済 DB |
| PostgreSQL Flexible Server (point) | `skishop-point-db` | B_Standard_B1ms | ポイント DB |
| PostgreSQL Flexible Server (coupon) | `skishop-coupon-db` | B_Standard_B1ms | クーポン DB |
| Cosmos DB for MongoDB | `skishop-mongo` | Serverless | 在庫・AI サポート DB |
| Event Hubs Namespace | `skishop-eventhub` | Standard | Kafka 互換メッセージング |
| Event Hub (8 topics) | skishop-*-events | — | 各サービスのイベントトピック |
| Container App: auth-svc | `auth-svc` | — | authentication-service |
| Container App: user-svc | `user-svc` | — | user-management-service |
| Container App: inventory-svc | `inventory-svc` | — | inventory-management-service |
| Container App: sales-svc | `sales-svc` | — | sales-management-service |
| Container App: payment-svc | `payment-svc` | — | payment-cart-service |
| Container App: point-svc | `point-svc` | — | point-service |
| Container App: coupon-svc | `coupon-svc` | — | coupon-service |
| Container App: ai-svc | `ai-svc` | — | ai-support-service |
| Container App: mail-svc | `mail-svc` | — | mailsend-service |
| Container App: agent-runtime | `agent-runtime` | — | agent-runtime-monolith |
| Container App: gateway-svc | `gateway-svc` | — | api-gateway-service (External) |
| Container App: frontend-svc | `frontend-svc` | — | Next.js フロントエンド (External) |

### コスト概算（月額）

| リソース | 概算コスト |
|---------|-----------|
| Container Apps (Consumption) | ~$50〜150（アクセス量依存） |
| PostgreSQL Flexible Server × 7 (B1ms) | ~$30 × 7 = ~$210 |
| Cosmos DB for MongoDB (Serverless) | ~$10〜30 |
| Event Hubs Standard (1 TU) | ~$10 |
| Container Registry Standard | ~$20 |
| Key Vault Standard | ~$5 |
| Log Analytics (PerGB2018) | ~$10〜30 |
| **合計目安** | **~$315〜$455/月** |

> ⚠️ 本番トラフィック・レプリカ数・ストレージ量により大きく変動します。高可用性要件に応じて PostgreSQL のティアアップ（Standard_D2s_v3 等）を検討してください。

---

## 5. 前提条件・事前準備

### 必要なツール

```bash
# バージョン確認
az --version          # Azure CLI 2.60 以上
terraform --version   # 1.9 以上
docker --version      # 24 以上
mvn --version         # 3.9 以上
java -version         # 21 以上
mongosh --version     # 2.0 以上（Phase 9 の MongoDB シード投入に必須）
gh --version          # GitHub CLI（任意）
```

### Azure CLI 拡張機能のインストール

```bash
az extension add --name containerapp --upgrade --yes
az extension add --name communication --upgrade --yes
az provider register --namespace Microsoft.App --wait
az provider register --namespace Microsoft.OperationalInsights --wait
az provider register --namespace Microsoft.ContainerRegistry --wait
az provider register --namespace Microsoft.EventHub --wait
az provider register --namespace Microsoft.KeyVault --wait
az provider register --namespace Microsoft.DBforPostgreSQL --wait
az provider register --namespace Microsoft.DocumentDB --wait
```

### 環境変数の設定（作業セッション共通）

```bash
export SUB="<AZURE_SUBSCRIPTION_ID>"
export RG="<RESOURCE_GROUP_NAME>"
export LOCATION="japaneast"
export PREFIX="skishop"
export ACR_NAME="<ACR_NAME>"
export CAE_NAME="skishop-cae"
export KV_NAME="skishop-kv"
export EVENTHUB_NS="${PREFIX}-eventhub"

az account set --subscription "$SUB"
```

---

## 6. 構築ステップ詳細

### Phase 1: IAM・権限設定

#### 1-1. Service Principal の作成（GitHub Actions OIDC 用）

```bash
# アプリケーション登録
APP_NAME="skishop-github-actions"
APP_ID=$(az ad app create --display-name "$APP_NAME" --query appId -o tsv)
SP_OBJ_ID=$(az ad sp create --id "$APP_ID" --query id -o tsv)

echo "APP_ID: $APP_ID"
echo "SP_OBJ_ID: $SP_OBJ_ID"

# サブスクリプションへの Contributor 権限付与
az role assignment create \
  --assignee-object-id "$SP_OBJ_ID" \
  --assignee-principal-type ServicePrincipal \
  --role "Contributor" \
  --scope "/subscriptions/$SUB/resourceGroups/$RG"

# AcrPush 権限（Container Registry へのプッシュ用）
# ※ ACR 作成後に実行
az role assignment create \
  --assignee-object-id "$SP_OBJ_ID" \
  --assignee-principal-type ServicePrincipal \
  --role "AcrPush" \
  --scope "/subscriptions/$SUB/resourceGroups/$RG/providers/Microsoft.ContainerRegistry/registries/$ACR_NAME"
```

#### 1-2. Federated Credentials の設定（OIDC）

```bash
GITHUB_ORG="<your-github-org>"
GITHUB_REPO="<your-github-repo>"

# main ブランチ用
az ad app federated-credential create \
  --id "$APP_ID" \
  --parameters "{
    \"name\": \"github-main\",
    \"issuer\": \"https://token.actions.githubusercontent.com\",
    \"subject\": \"repo:${GITHUB_ORG}/${GITHUB_REPO}:ref:refs/heads/main\",
    \"audiences\": [\"api://AzureADTokenExchange\"]
  }"

# environment: production 用
az ad app federated-credential create \
  --id "$APP_ID" \
  --parameters "{
    \"name\": \"github-env-production\",
    \"issuer\": \"https://token.actions.githubusercontent.com\",
    \"subject\": \"repo:${GITHUB_ORG}/${GITHUB_REPO}:environment:production\",
    \"audiences\": [\"api://AzureADTokenExchange\"]
  }"

TENANT_ID=$(az account show --query tenantId -o tsv)
echo "=== GitHub Actions Secrets ==="
echo "AZURE_CLIENT_ID: $APP_ID"
echo "AZURE_TENANT_ID: $TENANT_ID"
echo "AZURE_SUBSCRIPTION_ID: $SUB"
```

#### 1-3. GitHub Secrets / Variables の登録

GitHub リポジトリの **Settings → Secrets and variables → Actions** に以下を登録:

**Secrets（機密情報）:**

| Secret 名 | 値 | 用途 |
|----------|---|------|
| `AZURE_CLIENT_ID` | Phase 1-2 で取得した `APP_ID` | OIDC 認証 |
| `AZURE_TENANT_ID` | `caglobaldemos2605.onmicrosoft.com` のテナント ID | OIDC 認証 |
| `AZURE_SUBSCRIPTION_ID` | `<AZURE_SUBSCRIPTION_ID>` | デプロイ先 |

**Variables（非機密設定値）:**

| Variable 名 | 値 |
|------------|---|
| `AZURE_RESOURCE_GROUP` | `<RESOURCE_GROUP_NAME>` |
| `CONTAINER_APPS_ENVIRONMENT` | `skishop-cae` |
| `AZURE_LOCATION` | `japaneast` |
| `GATEWAY_EXTERNAL_URL` | Phase 8 完了後に設定 |

---

### Phase 2: データ基盤構築

#### 2-1. PostgreSQL Flexible Server × 6

```bash
# 共通パラメータ
DB_ADMIN_USER="<DB_ADMIN_USER>"
DB_ADMIN_PASS="<Strong-Pass-12chars!>"  # 英大文字・小文字・数字・記号を含む12文字以上

# 6 サービス分を一括作成（並列実行可）
declare -A DBS=(
  ["auth"]="skishop_auth"
  ["user"]="skishop_users"
  ["sales"]="skishop_sales"
  ["payment"]="skishop_payment"
  ["point"]="point_db"
  ["coupon"]="coupon_db"
)

for SVC in "${!DBS[@]}"; do
  DB_NAME="${DBS[$SVC]}"
  SERVER_NAME="${PREFIX}-${SVC}-db"

  echo "Creating PostgreSQL server: $SERVER_NAME ..."
  az postgres flexible-server create \
    --name "$SERVER_NAME" \
    --resource-group "$RG" \
    --subscription "$SUB" \
    --location "$LOCATION" \
    --admin-user "$DB_ADMIN_USER" \
    --admin-password "$DB_ADMIN_PASS" \
    --sku-name "Standard_B1ms" \
    --tier "Burstable" \
    --storage-size 32 \
    --version 16 \
    --backup-retention 7 \
    --public-access "0.0.0.0" \
    --yes

  # データベース作成
  az postgres flexible-server db create \
    --resource-group "$RG" \
    --server-name "$SERVER_NAME" \
    --database-name "$DB_NAME" \
    --subscription "$SUB"

  echo "Created: $SERVER_NAME / $DB_NAME"
done
```

> ⚠️ **セキュリティ注意**: `--public-access 0.0.0.0` は Container Apps 環境からの接続許可のため。本番環境では VNet 統合または Private Endpoint への変更を強く推奨します。

> ⚠️ **PostgreSQL SSL**: Azure PostgreSQL Flexible Server は SSL/TLS 接続が必須です。Spring Boot の `DB_URL` には `?sslmode=require` を必ず付加してください。
> 例: `DB_URL=jdbc:postgresql://${AUTH_DB_FQDN}:5432/skishop_auth?sslmode=require`

#### 2-2. Cosmos DB for MongoDB（skishop_inventory, skishop_ai 用）

```bash
COSMOS_ACCOUNT="${PREFIX}-mongo"

az cosmosdb create \
  --name "$COSMOS_ACCOUNT" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --kind MongoDB \
  --server-version "7.0" \
  --capabilities EnableMongo EnableServerless \
  --default-consistency-level "Session" \
  --locations regionName="$LOCATION" failoverPriority=0 isZoneRedundant=false

# データベース作成
az cosmosdb mongodb database create \
  --account-name "$COSMOS_ACCOUNT" \
  --resource-group "$RG" \
  --name "skishop_inventory"

az cosmosdb mongodb database create \
  --account-name "$COSMOS_ACCOUNT" \
  --resource-group "$RG" \
  --name "skishop_ai"

# 接続文字列の取得
MONGO_CONN=$(az cosmosdb keys list \
  --name "$COSMOS_ACCOUNT" \
  --resource-group "$RG" \
  --type connection-strings \
  --query "connectionStrings[0].connectionString" \
  -o tsv)

echo "MongoDB Connection String (保存してください): $MONGO_CONN"
```

#### 2-3. mailsend 用 PostgreSQL

```bash
az postgres flexible-server create \
  --name "${PREFIX}-mail-db" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --location "$LOCATION" \
  --admin-user "$DB_ADMIN_USER" \
  --admin-password "$DB_ADMIN_PASS" \
  --sku-name "Standard_B1ms" \
  --tier "Burstable" \
  --storage-size 32 \
  --version 16 \
  --backup-retention 7 \
  --public-access "0.0.0.0" \
  --yes

az postgres flexible-server db create \
  --resource-group "$RG" \
  --server-name "${PREFIX}-mail-db" \
  --database-name "skishop_mailsend"
```

---

### Phase 3: メッセージング基盤構築

#### 3-1. Event Hubs Namespace（Kafka 互換）

```bash
az eventhubs namespace create \
  --name "$EVENTHUB_NS" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --location "$LOCATION" \
  --sku Standard \
  --capacity 1 \
  --enable-auto-inflate true \
  --maximum-throughput-units 4 \
  --enable-kafka true

# Kafka 互換エンドポイントの確認
az eventhubs namespace show \
  --name "$EVENTHUB_NS" \
  --resource-group "$RG" \
  --query "kafkaEnabled"
```

#### 3-2. Event Hub Topics（8 トピック）

```bash
TOPICS=(
  "skishop-auth-events"
  "skishop-user-events"
  "skishop-sales-events"
  "skishop-payment-events"
  "skishop-inventory-events"
  "skishop-point-events"
  "skishop-coupon-events"
  "skishop-ai-events"
)

for TOPIC in "${TOPICS[@]}"; do
  az eventhubs eventhub create \
    --name "$TOPIC" \
    --namespace-name "$EVENTHUB_NS" \
    --resource-group "$RG" \
    --partition-count 4 \
    --message-retention 7  # メッセージ保持期間: 7日間（Standard tier の最大値、単位: 日）
  echo "Created topic: $TOPIC"
done

# 接続文字列の取得
EVENTHUB_CONN=$(az eventhubs namespace authorization-rule keys list \
  --resource-group "$RG" \
  --namespace-name "$EVENTHUB_NS" \
  --name RootManageSharedAccessKey \
  --query primaryConnectionString \
  -o tsv)

echo "Event Hubs Connection String: $EVENTHUB_CONN"
```

> **重要**: Event Hubs の Kafka ブートストラップアドレスは `${EVENTHUB_NS}.servicebus.windows.net:9093` です。Spring Boot の `KAFKA_BROKERS` をこの値に変更してください（ローカルの `kafka:9092` から変更）。

---

### Phase 4: コンテナ基盤構築

#### 4-1. Log Analytics Workspace

```bash
WORKSPACE_NAME="${PREFIX}-logs"

az monitor log-analytics workspace create \
  --workspace-name "$WORKSPACE_NAME" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --location "$LOCATION" \
  --retention-time 30

WORKSPACE_ID=$(az monitor log-analytics workspace show \
  --workspace-name "$WORKSPACE_NAME" \
  --resource-group "$RG" \
  --query customerId -o tsv)

WORKSPACE_KEY=$(az monitor log-analytics workspace get-shared-keys \
  --workspace-name "$WORKSPACE_NAME" \
  --resource-group "$RG" \
  --query primarySharedKey -o tsv)
```

#### 4-2. Container Apps Environment

```bash
az containerapp env create \
  --name "$CAE_NAME" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --location "$LOCATION" \
  --logs-workspace-id "$WORKSPACE_ID" \
  --logs-workspace-key "$WORKSPACE_KEY"

echo "Container Apps Environment created: $CAE_NAME"

# CAE の defaultDomain を取得（内部サービス間通信の FQDN 構築に使用）
ENV_DOMAIN=$(az containerapp env show \
  --name "$CAE_NAME" \
  --resource-group "$RG" \
  --query "properties.defaultDomain" -o tsv)
echo "CAE Default Domain (ENV_DOMAIN): $ENV_DOMAIN"
# 内部 FQDN の形式: https://<app-name>.${ENV_DOMAIN}
# 例: https://auth-svc.blue-field-abc12345.japaneast.azurecontainerapps.io
```

#### 4-3. Azure Container Registry

```bash
az acr create \
  --name "$ACR_NAME" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --location "$LOCATION" \
  --sku Standard \
  --admin-enabled false

ACR_LOGIN_SERVER=$(az acr show \
  --name "$ACR_NAME" \
  --resource-group "$RG" \
  --query loginServer -o tsv)

echo "ACR Login Server: $ACR_LOGIN_SERVER"

# Container Apps 環境に ACR へのアクセス権限付与
CAE_ID=$(az containerapp env show \
  --name "$CAE_NAME" \
  --resource-group "$RG" \
  --query id -o tsv)

az role assignment create \
  --assignee-object-id "$SP_OBJ_ID" \
  --assignee-principal-type ServicePrincipal \
  --role "AcrPull" \
  --scope "/subscriptions/$SUB/resourceGroups/$RG/providers/Microsoft.ContainerRegistry/registries/$ACR_NAME"
```

---

### Phase 5: シークレット管理

#### 5-1. Key Vault の作成

```bash
az keyvault create \
  --name "$KV_NAME" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --location "$LOCATION" \
  --sku standard \
  --enable-rbac-authorization true \    # RBAC モードを有効化（ロール割り当てに必須）
  --soft-delete-retention-days 7

# Service Principal に Key Vault Secret User 権限付与
az role assignment create \
  --assignee-object-id "$SP_OBJ_ID" \
  --assignee-principal-type ServicePrincipal \
  --role "Key Vault Secrets Officer" \
  --scope "/subscriptions/$SUB/resourceGroups/$RG/providers/Microsoft.KeyVault/vaults/$KV_NAME"
```

#### 5-2. シークレットの登録

```bash
# JWT シークレット（本番用に新規生成すること）
JWT_SECRET_PROD=$(openssl rand -base64 48)

# DB パスワード（Phase 2 で設定したもの）
az keyvault secret set --vault-name "$KV_NAME" --name "db-admin-password" --value "$DB_ADMIN_PASS"
az keyvault secret set --vault-name "$KV_NAME" --name "jwt-secret" --value "$JWT_SECRET_PROD"
az keyvault secret set --vault-name "$KV_NAME" --name "internal-api-key" --value "$(openssl rand -hex 32)"
az keyvault secret set --vault-name "$KV_NAME" --name "azure-openai-api-key" \
  --value "<your-azure-openai-api-key>"    # .env の AZURE_OPENAI_API_KEY の値を使用（ハードコード禁止）
az keyvault secret set --vault-name "$KV_NAME" --name "mongo-connection-string" --value "$MONGO_CONN"
az keyvault secret set --vault-name "$KV_NAME" --name "eventhub-connection-string" --value "$EVENTHUB_CONN"
az keyvault secret set --vault-name "$KV_NAME" --name "acs-connection-string" \
  --value "<your-acs-connection-string>"  # .env の AZURE_COMMUNICATION_CONNECTION_STRING の値を使用
az keyvault secret set --vault-name "$KV_NAME" --name "acs-sender-address" \
  --value "<your-sender-address>"         # メール送信元アドレス（例: DoNotReply@xxx.azurecomm.net）
az keyvault secret set --vault-name "$KV_NAME" --name "nextauth-secret" \
  --value "$(openssl rand -base64 32)"    # NextAuth.js 署名用シークレット（本番用に新規生成）
```

---

### Phase 6: CI/CD パイプライン設定

既存の `.github/workflows/ci.yml` と `.github/workflows/deploy.yml` を本番 ACR 向けに修正します。

#### 6-1. ci.yml の修正（GHCR → ACR への変更）

現在 ci.yml は `ghcr.io` にプッシュしていますが、ACR に変更する場合は以下のように修正します（任意：GHCR のままでも動作可）:

```yaml
# .github/workflows/ci.yml の docker-build ジョブ内
- name: Login to Azure Container Registry
  uses: azure/login@v2
  with:
    client-id: ${{ secrets.AZURE_CLIENT_ID }}
    tenant-id: ${{ secrets.AZURE_TENANT_ID }}
    subscription-id: ${{ secrets.AZURE_SUBSCRIPTION_ID }}

- name: Login to ACR
  run: az acr login --name <ACR_NAME>

- name: Build and push to ACR
  uses: docker/build-push-action@v6
  with:
    context: .
    file: ${{ matrix.service }}/Dockerfile
    push: true
    tags: <ACR_LOGIN_SERVER>/${{ matrix.service }}:${{ github.sha }}
```

#### 6-2. deploy.yml の修正（ACR 利用時）

```yaml
env:
  CONTAINER_REGISTRY: <ACR_LOGIN_SERVER>  # GHCR から ACR に変更

# デプロイステップでの registryUrl 変更
- name: Deploy to Azure Container Apps
  uses: azure/container-apps-deploy-action@v2
  with:
    containerAppName: ${{ steps.app.outputs.name }}
    resourceGroup: ${{ env.AZURE_RESOURCE_GROUP }}
    containerAppEnvironment: ${{ env.CONTAINER_APPS_ENVIRONMENT }}
    imageToDeploy: ${{ steps.image.outputs.full }}
    registryUrl: <ACR_LOGIN_SERVER>
    registryUsername: ""  # マネージド ID 利用時は不要
    registryPassword: ""  # マネージド ID 利用時は不要
```

---

### Phase 7: コンテナイメージのビルド・プッシュ

#### 7-1. ローカルからのビルドとプッシュ（初回）

```bash
# Maven ビルド
mvn clean package -DskipTests -Djacoco.skip=true -T 4

# ACR へログイン
az acr login --name "$ACR_NAME"

# 全サービスのイメージビルド・プッシュ
SERVICES=(
  "authentication-service"
  "user-management-service"
  "inventory-management-service"
  "sales-management-service"
  "payment-cart-service"
  "point-service"
  "coupon-service"
  "ai-support-service"
  "mailsend-service"
  "api-gateway-service"
  "agent-runtime-monolith:ai-agent-services/agent-runtime-monolith"
)

TAG=$(git rev-parse --short HEAD)

for SVC_INFO in "${SERVICES[@]}"; do
  SVC_NAME="${SVC_INFO%%:*}"
  SVC_DIR="${SVC_INFO#*:}"
  [[ "$SVC_INFO" != *:* ]] && SVC_DIR="$SVC_NAME"

  echo "Building: $SVC_NAME ..."
  docker build \
    -t "${ACR_LOGIN_SERVER}/${SVC_NAME}:${TAG}" \
    -t "${ACR_LOGIN_SERVER}/${SVC_NAME}:latest" \
    -f "${SVC_DIR}/Dockerfile" \
    .
  docker push "${ACR_LOGIN_SERVER}/${SVC_NAME}:${TAG}"
  docker push "${ACR_LOGIN_SERVER}/${SVC_NAME}:latest"
  echo "Pushed: ${ACR_LOGIN_SERVER}/${SVC_NAME}:${TAG}"
done

# フロントエンドは個別ビルド（ビルド ARG が必要）
GATEWAY_URL="https://<gateway-svc-fqdn>"  # Phase 8 後に設定
docker build \
  -t "${ACR_LOGIN_SERVER}/frontend:${TAG}" \
  -t "${ACR_LOGIN_SERVER}/frontend:latest" \
  --build-arg "NEXT_PUBLIC_APP_URL=https://<frontend-fqdn>" \
  --build-arg "NEXT_PUBLIC_APP_NAME=Azure SkiShop" \
  ./frontend
docker push "${ACR_LOGIN_SERVER}/frontend:${TAG}"
```

---

### Phase 8: Container Apps デプロイ

#### 8-1. 各サービス用の環境変数マップ作成

各サービスのデプロイ前に、接続先の FQDN を取得しておきます。

```bash
# PostgreSQL FQDN 一覧取得
for SVC in auth user sales payment point coupon mail; do
  FQDN=$(az postgres flexible-server show \
    --name "${PREFIX}-${SVC}-db" \
    --resource-group "$RG" \
    --query fullyQualifiedDomainName -o tsv 2>/dev/null)
  echo "${SVC}: $FQDN"
done

# Event Hubs ブートストラップ
KAFKA_BROKERS="${EVENTHUB_NS}.servicebus.windows.net:9093"
KAFKA_SASL_CONFIG="org.apache.kafka.common.security.plain.PlainLoginModule required username=\"\$ConnectionString\" password=\"${EVENTHUB_CONN}\";"

# Container Apps Environment の defaultDomain 取得（内部サービス URL 構築用）
# この変数は Phase 4-2 で設定済みだが、安全のため再取得
ENV_DOMAIN=$(az containerapp env show \
  --name "$CAE_NAME" \
  --resource-group "$RG" \
  --query "properties.defaultDomain" -o tsv)
echo "ENV_DOMAIN: $ENV_DOMAIN"
# 内部サービス URL の形式: https://<app-name>.${ENV_DOMAIN}
# 注意: Container Apps の内部通信は '.internal.<location>' 形式ではなく、上記の ENV_DOMAIN を使用すること
```

#### 8-2. バックエンドサービスのデプロイ（依存関係順）

**Step 1: データ層に依存するサービスから順にデプロイ**

```bash
TAG=$(az acr repository show-tags \
  --name "$ACR_NAME" \
  --repository "authentication-service" \
  --orderby time_desc \
  --query "[0]" -o tsv)

AUTH_DB_FQDN=$(az postgres flexible-server show \
  --name "${PREFIX}-auth-db" --resource-group "$RG" --query fullyQualifiedDomainName -o tsv)

# authentication-service
az containerapp create \
  --name "auth-svc" \
  --resource-group "$RG" \
  --environment "$CAE_NAME" \
  --image "${ACR_LOGIN_SERVER}/authentication-service:${TAG}" \
  --registry-server "$ACR_LOGIN_SERVER" \
  --min-replicas 1 --max-replicas 3 \
  --cpu 0.5 --memory 1Gi \
  --ingress internal --target-port 8080 \
  --env-vars \
    "DB_URL=jdbc:postgresql://${AUTH_DB_FQDN}:5432/skishop_auth?sslmode=require" \
    "DB_USERNAME=${DB_ADMIN_USER}" \
    "DB_PASSWORD=secretref:db-admin-password" \
    "JWT_SECRET=secretref:jwt-secret" \
    "INTERNAL_API_KEY=secretref:internal-api-key" \
    "KAFKA_BROKERS=${KAFKA_BROKERS}" \
    "KAFKA_REPLICATION_FACTOR=1" \
    "SPRING_PROFILES_ACTIVE=kafka" \
    "SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL=SASL_SSL" \
    "SPRING_KAFKA_PROPERTIES_SASL_MECHANISM=PLAIN" \
    "SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG=${KAFKA_SASL_CONFIG}" \
  --secrets \
    "db-admin-password=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/db-admin-password" \
    "jwt-secret=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/jwt-secret" \
    "internal-api-key=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/internal-api-key"
```

**user-management-service**

```bash
USER_DB_FQDN=$(az postgres flexible-server show \
  --name "${PREFIX}-user-db" --resource-group "$RG" --query fullyQualifiedDomainName -o tsv)

az containerapp create \
  --name "user-svc" \
  --resource-group "$RG" \
  --environment "$CAE_NAME" \
  --image "${ACR_LOGIN_SERVER}/user-management-service:${TAG}" \
  --registry-server "$ACR_LOGIN_SERVER" \
  --min-replicas 1 --max-replicas 3 \
  --cpu 0.5 --memory 1Gi \
  --ingress internal --target-port 8081 \
  --env-vars \
    "DB_URL=jdbc:postgresql://${USER_DB_FQDN}:5432/skishop_users?sslmode=require" \
    "DB_USERNAME=${DB_ADMIN_USER}" \
    "DB_PASSWORD=secretref:db-admin-password" \
    "JWT_SECRET=secretref:jwt-secret" \
    "INTERNAL_API_KEY=secretref:internal-api-key" \
    "KAFKA_BROKERS=${KAFKA_BROKERS}" \
    "KAFKA_REPLICATION_FACTOR=1" \
    "SPRING_PROFILES_ACTIVE=kafka" \
    "SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL=SASL_SSL" \
    "SPRING_KAFKA_PROPERTIES_SASL_MECHANISM=PLAIN" \
    "SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG=${KAFKA_SASL_CONFIG}" \
  --secrets \
    "db-admin-password=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/db-admin-password" \
    "jwt-secret=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/jwt-secret" \
    "internal-api-key=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/internal-api-key"
```

**inventory-management-service (MongoDB)**

```bash
az containerapp create \
  --name "inventory-svc" \
  --resource-group "$RG" \
  --environment "$CAE_NAME" \
  --image "${ACR_LOGIN_SERVER}/inventory-management-service:${TAG}" \
  --registry-server "$ACR_LOGIN_SERVER" \
  --min-replicas 1 --max-replicas 3 \
  --cpu 0.5 --memory 1Gi \
  --ingress internal --target-port 8082 \
  --env-vars \
    "MONGODB_URI=secretref:mongo-connection-string" \
    "JWT_SECRET=secretref:jwt-secret" \
    "INTERNAL_API_KEY=secretref:internal-api-key" \
    "KAFKA_BROKERS=${KAFKA_BROKERS}" \
    "KAFKA_REPLICATION_FACTOR=1" \
    "SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL=SASL_SSL" \
    "SPRING_KAFKA_PROPERTIES_SASL_MECHANISM=PLAIN" \
    "SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG=${KAFKA_SASL_CONFIG}" \
  --secrets \
    "mongo-connection-string=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/mongo-connection-string" \
    "jwt-secret=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/jwt-secret" \
    "internal-api-key=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/internal-api-key"
```

**sales-management-service**

```bash
SALES_DB_FQDN=$(az postgres flexible-server show \
  --name "${PREFIX}-sales-db" --resource-group "$RG" --query fullyQualifiedDomainName -o tsv)

az containerapp create \
  --name "sales-svc" \
  --resource-group "$RG" \
  --environment "$CAE_NAME" \
  --image "${ACR_LOGIN_SERVER}/sales-management-service:${TAG}" \
  --registry-server "$ACR_LOGIN_SERVER" \
  --min-replicas 1 --max-replicas 3 \
  --cpu 0.5 --memory 1Gi \
  --ingress internal --target-port 8083 \
  --env-vars \
    "DB_URL=jdbc:postgresql://${SALES_DB_FQDN}:5432/skishop_sales?sslmode=require" \
    "DB_USERNAME=${DB_ADMIN_USER}" \
    "DB_PASSWORD=secretref:db-admin-password" \
    "JWT_SECRET=secretref:jwt-secret" \
    "INTERNAL_API_KEY=secretref:internal-api-key" \
    "KAFKA_BROKERS=${KAFKA_BROKERS}" \
    "KAFKA_REPLICATION_FACTOR=1" \
    "SPRING_PROFILES_ACTIVE=kafka" \
    "SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL=SASL_SSL" \
    "SPRING_KAFKA_PROPERTIES_SASL_MECHANISM=PLAIN" \
    "SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG=${KAFKA_SASL_CONFIG}" \
  --secrets \
    "db-admin-password=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/db-admin-password" \
    "jwt-secret=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/jwt-secret" \
    "internal-api-key=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/internal-api-key"
```

**payment-cart-service**

```bash
PAYMENT_DB_FQDN=$(az postgres flexible-server show \
  --name "${PREFIX}-payment-db" --resource-group "$RG" --query fullyQualifiedDomainName -o tsv)

az containerapp create \
  --name "payment-svc" \
  --resource-group "$RG" \
  --environment "$CAE_NAME" \
  --image "${ACR_LOGIN_SERVER}/payment-cart-service:${TAG}" \
  --registry-server "$ACR_LOGIN_SERVER" \
  --min-replicas 1 --max-replicas 3 \
  --cpu 0.5 --memory 1Gi \
  --ingress internal --target-port 8084 \
  --env-vars \
    "DB_URL=jdbc:postgresql://${PAYMENT_DB_FQDN}:5432/skishop_payment?sslmode=require" \
    "DB_USERNAME=${DB_ADMIN_USER}" \
    "DB_PASSWORD=secretref:db-admin-password" \
    "JWT_SECRET=secretref:jwt-secret" \
    "INTERNAL_API_KEY=secretref:internal-api-key" \
    "KAFKA_BROKERS=${KAFKA_BROKERS}" \
    "KAFKA_REPLICATION_FACTOR=1" \
    "SPRING_PROFILES_ACTIVE=kafka" \
    "SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL=SASL_SSL" \
    "SPRING_KAFKA_PROPERTIES_SASL_MECHANISM=PLAIN" \
    "SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG=${KAFKA_SASL_CONFIG}" \
  --secrets \
    "db-admin-password=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/db-admin-password" \
    "jwt-secret=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/jwt-secret" \
    "internal-api-key=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/internal-api-key"
```

**point-service**

```bash
POINT_DB_FQDN=$(az postgres flexible-server show \
  --name "${PREFIX}-point-db" --resource-group "$RG" --query fullyQualifiedDomainName -o tsv)

az containerapp create \
  --name "point-svc" \
  --resource-group "$RG" \
  --environment "$CAE_NAME" \
  --image "${ACR_LOGIN_SERVER}/point-service:${TAG}" \
  --registry-server "$ACR_LOGIN_SERVER" \
  --min-replicas 1 --max-replicas 3 \
  --cpu 0.5 --memory 1Gi \
  --ingress internal --target-port 8085 \
  --env-vars \
    "DB_URL=jdbc:postgresql://${POINT_DB_FQDN}:5432/point_db?sslmode=require" \
    "DB_USERNAME=${DB_ADMIN_USER}" \
    "DB_PASSWORD=secretref:db-admin-password" \
    "JWT_SECRET=secretref:jwt-secret" \
    "INTERNAL_API_KEY=secretref:internal-api-key" \
    "KAFKA_BROKERS=${KAFKA_BROKERS}" \
    "KAFKA_REPLICATION_FACTOR=1" \
    "SPRING_PROFILES_ACTIVE=kafka" \
    "SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL=SASL_SSL" \
    "SPRING_KAFKA_PROPERTIES_SASL_MECHANISM=PLAIN" \
    "SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG=${KAFKA_SASL_CONFIG}" \
  --secrets \
    "db-admin-password=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/db-admin-password" \
    "jwt-secret=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/jwt-secret" \
    "internal-api-key=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/internal-api-key"
```

**coupon-service**

```bash
COUPON_DB_FQDN=$(az postgres flexible-server show \
  --name "${PREFIX}-coupon-db" --resource-group "$RG" --query fullyQualifiedDomainName -o tsv)

az containerapp create \
  --name "coupon-svc" \
  --resource-group "$RG" \
  --environment "$CAE_NAME" \
  --image "${ACR_LOGIN_SERVER}/coupon-service:${TAG}" \
  --registry-server "$ACR_LOGIN_SERVER" \
  --min-replicas 1 --max-replicas 3 \
  --cpu 0.5 --memory 1Gi \
  --ingress internal --target-port 8088 \
  --env-vars \
    "DB_URL=jdbc:postgresql://${COUPON_DB_FQDN}:5432/coupon_db?sslmode=require" \
    "DB_USERNAME=${DB_ADMIN_USER}" \
    "DB_PASSWORD=secretref:db-admin-password" \
    "JWT_SECRET=secretref:jwt-secret" \
    "INTERNAL_API_KEY=secretref:internal-api-key" \
    "KAFKA_BROKERS=${KAFKA_BROKERS}" \
    "KAFKA_REPLICATION_FACTOR=1" \
    "SPRING_PROFILES_ACTIVE=kafka" \
    "SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL=SASL_SSL" \
    "SPRING_KAFKA_PROPERTIES_SASL_MECHANISM=PLAIN" \
    "SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG=${KAFKA_SASL_CONFIG}" \
  --secrets \
    "db-admin-password=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/db-admin-password" \
    "jwt-secret=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/jwt-secret" \
    "internal-api-key=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/internal-api-key"
```

**ai-support-service (MongoDB + Azure OpenAI)**

```bash
az containerapp create \
  --name "ai-svc" \
  --resource-group "$RG" \
  --environment "$CAE_NAME" \
  --image "${ACR_LOGIN_SERVER}/ai-support-service:${TAG}" \
  --registry-server "$ACR_LOGIN_SERVER" \
  --min-replicas 1 --max-replicas 3 \
  --cpu 0.75 --memory 1.5Gi \
  --ingress internal --target-port 8087 \
  --env-vars \
    "MONGODB_URI=secretref:mongo-connection-string" \
    "JWT_SECRET=secretref:jwt-secret" \
    "INTERNAL_API_KEY=secretref:internal-api-key" \
    "AZURE_OPENAI_ENDPOINT=https://<AZURE_OPENAI_ENDPOINT_HOST>" \
    "AZURE_OPENAI_DEPLOYMENT=gpt-5.4-nano" \
    "AZURE_OPENAI_API_KEY=secretref:azure-openai-api-key" \
    "KAFKA_BROKERS=${KAFKA_BROKERS}" \
    "KAFKA_REPLICATION_FACTOR=1" \
    "SPRING_PROFILES_ACTIVE=kafka" \
    "SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL=SASL_SSL" \
    "SPRING_KAFKA_PROPERTIES_SASL_MECHANISM=PLAIN" \
    "SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG=${KAFKA_SASL_CONFIG}" \
  --secrets \
    "mongo-connection-string=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/mongo-connection-string" \
    "jwt-secret=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/jwt-secret" \
    "internal-api-key=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/internal-api-key" \
    "azure-openai-api-key=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/azure-openai-api-key"
```

**mailsend-service**

```bash
MAIL_DB_FQDN=$(az postgres flexible-server show \
  --name "${PREFIX}-mail-db" --resource-group "$RG" --query fullyQualifiedDomainName -o tsv)

az containerapp create \
  --name "mail-svc" \
  --resource-group "$RG" \
  --environment "$CAE_NAME" \
  --image "${ACR_LOGIN_SERVER}/mailsend-service:${TAG}" \
  --registry-server "$ACR_LOGIN_SERVER" \
  --min-replicas 1 --max-replicas 2 \
  --cpu 0.5 --memory 1Gi \
  --ingress internal --target-port 8089 \
  --env-vars \
    "DB_URL=jdbc:postgresql://${MAIL_DB_FQDN}:5432/skishop_mailsend?sslmode=require" \
    "DB_USERNAME=${DB_ADMIN_USER}" \
    "DB_PASSWORD=secretref:db-admin-password" \
    "JWT_SECRET=secretref:jwt-secret" \
    "INTERNAL_API_KEY=secretref:internal-api-key" \
    "AZURE_COMMUNICATION_CONNECTION_STRING=secretref:acs-connection-string" \
    "MAIL_SENDER_ADDRESS=secretref:acs-sender-address" \
    "KAFKA_BROKERS=${KAFKA_BROKERS}" \
    "KAFKA_REPLICATION_FACTOR=1" \
    "SPRING_PROFILES_ACTIVE=kafka" \
    "SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL=SASL_SSL" \
    "SPRING_KAFKA_PROPERTIES_SASL_MECHANISM=PLAIN" \
    "SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG=${KAFKA_SASL_CONFIG}" \
  --secrets \
    "db-admin-password=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/db-admin-password" \
    "jwt-secret=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/jwt-secret" \
    "internal-api-key=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/internal-api-key" \
    "acs-connection-string=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/acs-connection-string" \
    "acs-sender-address=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/acs-sender-address"
```

**agent-runtime-monolith (Azure OpenAI 接続)**

```bash
az containerapp create \
  --name "agent-runtime" \
  --resource-group "$RG" \
  --environment "$CAE_NAME" \
  --image "${ACR_LOGIN_SERVER}/agent-runtime-monolith:${TAG}" \
  --registry-server "$ACR_LOGIN_SERVER" \
  --min-replicas 1 --max-replicas 2 \
  --cpu 1.0 --memory 2Gi \
  --ingress internal --target-port 8100 \
  --env-vars \
    "AGENTS_DEPLOYMENT_MODE=monolith" \
    "AGENTS_WEB_ENABLED=true" \
    "AZURE_OPENAI_ENDPOINT=https://<AZURE_OPENAI_ENDPOINT_HOST>" \
    "AZURE_OPENAI_DEPLOYMENT=gpt-5.4-nano" \
    "AZURE_OPENAI_API_KEY=secretref:azure-openai-api-key" \
    "JWT_SECRET=secretref:jwt-secret" \
    "INTERNAL_API_KEY=secretref:internal-api-key" \
    "USER_MANAGEMENT_SERVICE_URL=https://user-svc.${ENV_DOMAIN}" \
    "INVENTORY_MANAGEMENT_SERVICE_URL=https://inventory-svc.${ENV_DOMAIN}" \
    "SALES_MANAGEMENT_SERVICE_URL=https://sales-svc.${ENV_DOMAIN}" \
    "PAYMENT_CART_SERVICE_URL=https://payment-svc.${ENV_DOMAIN}" \
    "POINT_SERVICE_URL=https://point-svc.${ENV_DOMAIN}" \
    "COUPON_SERVICE_URL=https://coupon-svc.${ENV_DOMAIN}" \
    "AI_SERVICE_URL=https://ai-svc.${ENV_DOMAIN}" \
  --secrets \
    "azure-openai-api-key=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/azure-openai-api-key" \
    "jwt-secret=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/jwt-secret" \
    "internal-api-key=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/internal-api-key"
```

#### 8-3. API Gateway のデプロイ（External Ingress）

```bash
az containerapp create \
  --name "gateway-svc" \
  --resource-group "$RG" \
  --environment "$CAE_NAME" \
  --image "${ACR_LOGIN_SERVER}/api-gateway-service:${TAG}" \
  --registry-server "$ACR_LOGIN_SERVER" \
  --min-replicas 2 --max-replicas 5 \
  --cpu 0.75 --memory 1.5Gi \
  --ingress external --target-port 8090 \
  --env-vars \
    "AUTH_SERVICE_URL=https://auth-svc.${ENV_DOMAIN}" \
    "USER_SERVICE_URL=https://user-svc.${ENV_DOMAIN}" \
    "INVENTORY_SERVICE_URL=https://inventory-svc.${ENV_DOMAIN}" \
    "SALES_SERVICE_URL=https://sales-svc.${ENV_DOMAIN}" \
    "PAYMENT_SERVICE_URL=https://payment-svc.${ENV_DOMAIN}" \
    "POINT_SERVICE_URL=https://point-svc.${ENV_DOMAIN}" \
    "COUPON_SERVICE_URL=https://coupon-svc.${ENV_DOMAIN}" \
    "AI_SERVICE_URL=https://ai-svc.${ENV_DOMAIN}" \
    "MAIL_SERVICE_URL=https://mail-svc.${ENV_DOMAIN}" \
    "ORCHESTRATOR_SERVICE_URL=https://agent-runtime.${ENV_DOMAIN}" \
    "JWT_SECRET=secretref:jwt-secret" \
    "INTERNAL_API_KEY=secretref:internal-api-key" \
  --secrets \
    "jwt-secret=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/jwt-secret" \
    "internal-api-key=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/internal-api-key"

# Gateway の FQDN 取得
GATEWAY_FQDN=$(az containerapp show \
  --name "gateway-svc" \
  --resource-group "$RG" \
  --query "properties.configuration.ingress.fqdn" -o tsv)

echo "Gateway URL: https://${GATEWAY_FQDN}"
```

---

### Phase 9: データ初期化

#### 9-1. MongoDB シードデータの投入

```bash
# Cosmos DB for MongoDB に対してローカルの mongosh でシードを実行
# mongosh の接続文字列は Cosmos DB の接続文字列を利用
MONGO_CONN="<Cosmos DB Connection String from Phase 2-2>"

# 各シードファイルを順番に実行
for SEED_FILE in docker/initdb-mongo/*.js; do
  echo "Executing seed: $SEED_FILE"
  mongosh "$MONGO_CONN" --file "$SEED_FILE"
done
```

#### 9-2. PostgreSQL の Flyway マイグレーション確認

各 Spring Boot サービスは起動時に Flyway マイグレーションを自動実行します。
Container Apps ログで `Successfully applied X migration(s)` を確認してください。

```bash
# ログ確認コマンド
az containerapp logs show \
  --name "auth-svc" \
  --resource-group "$RG" \
  --follow \
  --tail 50 | grep -E "Flyway|Successfully|ERROR"
```

---

### Phase 10: フロントエンドデプロイ

> ⚠️ **ブートストラップ問題**: Next.js の `NEXT_PUBLIC_APP_URL` と `NEXTAUTH_URL` にはフロントエンド自身の FQDN が必要ですが、初回デプロイ前は FQDN が未確定です。そのため **2 ステップ** で行います。
>
> - **Step A**: プレースホルダ URL でデプロイし、実際の FQDN を確認する
> - **Step B**: 確定した FQDN でイメージをリビルドし、Container App を更新する

```bash
# Step A: FQDN 確定のための仮デプロイ
GATEWAY_FQDN=$(az containerapp show \
  --name "gateway-svc" \
  --resource-group "$RG" \
  --query "properties.configuration.ingress.fqdn" -o tsv)

# 仮デプロイ（PLACEHOLDER URL でビルド）
docker build \
  -t "${ACR_LOGIN_SERVER}/frontend:${TAG}-pre" \
  --build-arg "NEXT_PUBLIC_APP_URL=https://placeholder.example.com" \
  --build-arg "NEXT_PUBLIC_APP_NAME=Azure SkiShop" \
  ./frontend
docker push "${ACR_LOGIN_SERVER}/frontend:${TAG}-pre"

az containerapp create \
  --name "frontend-svc" \
  --resource-group "$RG" \
  --environment "$CAE_NAME" \
  --image "${ACR_LOGIN_SERVER}/frontend:${TAG}-pre" \
  --registry-server "$ACR_LOGIN_SERVER" \
  --min-replicas 1 --max-replicas 3 \
  --cpu 0.5 --memory 1Gi \
  --ingress external --target-port 3000 \
  --env-vars \
    "NODE_ENV=production" \
    "PORT=3000" \
    "HOSTNAME=0.0.0.0" \
    "NEXT_PUBLIC_APP_NAME=Azure SkiShop" \
    "API_GATEWAY_URL=https://${GATEWAY_FQDN}" \
    "NEXTAUTH_URL=https://placeholder.example.com" \
    "NEXTAUTH_SECRET=secretref:nextauth-secret" \
    "JWT_SECRET=secretref:jwt-secret" \
  --secrets \
    "jwt-secret=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/jwt-secret" \
    "nextauth-secret=keyvaultref:https://${KV_NAME}.vault.azure.net/secrets/nextauth-secret"

# Step A 完了: 実際の FQDN を取得
FRONTEND_FQDN=$(az containerapp show \
  --name "frontend-svc" \
  --resource-group "$RG" \
  --query "properties.configuration.ingress.fqdn" -o tsv)
echo "Frontend FQDN confirmed: https://${FRONTEND_FQDN}"

# Step B: 確定した FQDN で本番イメージをリビルド・再デプロイ
docker build \
  -t "${ACR_LOGIN_SERVER}/frontend:${TAG}" \
  -t "${ACR_LOGIN_SERVER}/frontend:latest" \
  --build-arg "NEXT_PUBLIC_APP_URL=https://${FRONTEND_FQDN}" \
  --build-arg "NEXT_PUBLIC_APP_NAME=Azure SkiShop" \
  ./frontend
docker push "${ACR_LOGIN_SERVER}/frontend:${TAG}"
docker push "${ACR_LOGIN_SERVER}/frontend:latest"

# Container App を正式イメージで更新し、NEXTAUTH_URL も確定 URL に修正
az containerapp update \
  --name "frontend-svc" \
  --resource-group "$RG" \
  --image "${ACR_LOGIN_SERVER}/frontend:${TAG}" \
  --set-env-vars \
    "NEXTAUTH_URL=https://${FRONTEND_FQDN}" \
    "NEXT_PUBLIC_APP_URL=https://${FRONTEND_FQDN}"

echo "Frontend URL (final): https://${FRONTEND_FQDN}"
```

---

## 7. 環境変数マッピング

### Docker Compose → Azure Container Apps 変換表

| ローカル値 | Azure 本番値 | 備考 |
|-----------|------------|------|
| `postgres:5432` | `${PREFIX}-auth-db.postgres.database.azure.com:5432` | サービスごとに個別サーバー |
| `mongo:27017/skishop_inventory` | Cosmos DB 接続文字列 | Key Vault 参照 |
| `kafka:9092` | `${EVENTHUB_NS}.servicebus.windows.net:9093` | SASL_SSL 設定必要 |
| `KAFKA_REPLICATION_FACTOR=1` | `KAFKA_REPLICATION_FACTOR=1` | Event Hubs では 1 で可 |
| `http://auth-service:8080` | `https://auth-svc.${ENV_DOMAIN}` | Container Apps 内部 FQDN（ENV_DOMAIN 変数を使用） |
| `SPRING_PROFILES_ACTIVE=kafka` | `SPRING_PROFILES_ACTIVE=kafka` | 変更不要 |

### Kafka 接続設定（Event Hubs 用）

Event Hubs を Kafka プロトコルで使用する際、以下の追加環境変数が必要です:

```properties
SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL=SASL_SSL
SPRING_KAFKA_PROPERTIES_SASL_MECHANISM=PLAIN
SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG=org.apache.kafka.common.security.plain.PlainLoginModule required username="$ConnectionString" password="<Event Hubs Connection String>";
```

---

## 8. ネットワーク設計

### Container Apps のイングレス設定

| サービス | Ingress | 理由 |
|---------|---------|------|
| `frontend-svc` | **External** | エンドユーザーがアクセス |
| `gateway-svc` | **External** | フロントエンドからの API 呼び出し |
| `auth-svc` | **Internal** | Gateway 経由のみ許可 |
| `user-svc` | **Internal** | Gateway 経由のみ許可 |
| `inventory-svc` | **Internal** | Gateway 経由のみ許可 |
| `sales-svc` | **Internal** | Gateway 経由のみ許可 |
| `payment-svc` | **Internal** | Gateway 経由のみ許可 |
| `point-svc` | **Internal** | Gateway 経由のみ許可 |
| `coupon-svc` | **Internal** | Gateway 経由のみ許可 |
| `ai-svc` | **Internal** | Gateway 経由のみ許可 |
| `mail-svc` | **Internal** | Kafka 経由のみ呼び出し |
| `agent-runtime` | **Internal** | Gateway 経由のみ許可 |

### セキュリティ境界

```
インターネット
     │
     ▼ HTTPS (443)
┌────────────────────────────────────────────┐
│  External Ingress                           │
│  frontend-svc  ←→  gateway-svc             │
└──────────────────────┬─────────────────────┘
                       │ Internal HTTPS のみ
┌──────────────────────▼─────────────────────┐
│  Internal Services（外部からアクセス不可）    │
│  auth/user/inventory/sales/payment/         │
│  point/coupon/ai/mail/agent-runtime         │
└────────────────────────────────────────────┘
```

---

## 9. 監視・アラート設定

#### Azure Monitor アラートの設定

```bash
# Gateway の 5xx エラー率アラート
az monitor metrics alert create \
  --name "gateway-5xx-alert" \
  --resource-group "$RG" \
  --scopes "/subscriptions/$SUB/resourceGroups/$RG/providers/Microsoft.App/containerApps/gateway-svc" \
  --condition "avg Requests > 0 where StatusCodeClass includes '5xx'" \
  --window-size 5m \
  --evaluation-frequency 1m \
  --severity 2 \
  --action "<action-group-id>"

# レプリカ数の急増アラート（スケールアウト検知）
az monitor metrics alert create \
  --name "replica-count-alert" \
  --resource-group "$RG" \
  --scopes "/subscriptions/$SUB/resourceGroups/$RG/providers/Microsoft.App/containerApps/gateway-svc" \
  --condition "avg Replicas > 4" \
  --window-size 5m \
  --evaluation-frequency 1m \
  --severity 3
```

#### Log Analytics クエリ（Kusto）

```kusto
// 直近 1 時間のエラーログ一覧
ContainerAppConsoleLogs_CL
| where TimeGenerated >= ago(1h)
| where Log_s contains "ERROR"
| project TimeGenerated, ContainerAppName_s, RevisionName_s, Log_s
| order by TimeGenerated desc
| limit 100

// サービスごとのリクエスト数集計
ContainerAppSystemLogs_CL
| where TimeGenerated >= ago(24h)
| summarize count() by ContainerAppName_s, bin(TimeGenerated, 1h)
| render timechart
```

---

## 10. スモークテスト手順

デプロイ完了後、以下の順序でエンドツーエンドの動作確認を行います。

```bash
GATEWAY="https://<gateway-svc-fqdn>"
FRONTEND="https://<frontend-svc-fqdn>"

# 1. ヘルスチェック
echo "=== Health Checks ==="
curl -sf "${GATEWAY}/actuator/health" | python3 -m json.tool

# 2. 認証テスト（管理者ログイン）
echo "=== Auth Test ==="
TOKEN=$(curl -sf -X POST "${GATEWAY}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"Admin1234!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
echo "Token acquired: ${TOKEN:0:20}..."

# 3. 商品一覧取得（inventory-service 疎通確認）
echo "=== Inventory Test ==="
curl -sf "${GATEWAY}/api/v1/products?size=5" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json; d=json.load(sys.stdin)
print('Products:', d.get('totalElements','?'), 'total')
"

# 4. 初心者向けスキー板検索（AIエージェント + 商品検索）
echo "=== Equipment Search Test ==="
curl -sf "${GATEWAY}/api/v1/internal/products/search?category=cat-ski&skillLevel=BEGINNER" \
  -H "X-Internal-Api-Key: <INTERNAL_API_KEY>" | python3 -c "
import sys,json; d=json.load(sys.stdin)
print('BEGINNER ski results:', len(d))
"

# 5. アナリティクス疎通確認
echo "=== Analytics Test ==="
curl -sf -o /dev/null -w "HTTP:%{http_code}" \
  "${GATEWAY}/api/v1/admin/analytics/dashboard" \
  -H "Authorization: Bearer $TOKEN"

# 6. フロントエンド疎通確認
echo ""
echo "=== Frontend Test ==="
curl -sf -o /dev/null -w "HTTP:%{http_code}" "${FRONTEND}/"

echo ""
echo "=== All smoke tests completed ==="
```

---

## 11. ロールバック計画

### Container Apps のリビジョン管理

Container Apps はリビジョン（Revision）単位でロールバックが可能です。

```bash
# 現在のリビジョン一覧確認
az containerapp revision list \
  --name "gateway-svc" \
  --resource-group "$RG" \
  --output table

# 旧リビジョンへのトラフィック切り替え（即時ロールバック）
az containerapp revision set-mode \
  --name "gateway-svc" \
  --resource-group "$RG" \
  --mode multiple  # 複数リビジョンへのルーティングを有効化

az containerapp ingress traffic set \
  --name "gateway-svc" \
  --resource-group "$RG" \
  --revision-weight <old-revision-name>=100 <new-revision-name>=0
```

### ロールバック手順（障害時）

| 手順 | 操作 | 目安時間 |
|------|------|---------|
| 1 | 障害検知（アラート受信） | 0〜5 分 |
| 2 | 影響範囲の特定（Log Analytics 確認） | 5 分 |
| 3 | 旧リビジョンへのトラフィック切り替え | 2 分 |
| 4 | DB マイグレーションロールバック（必要な場合） | 15〜30 分 |
| 5 | 根本原因分析・修正 | 担当者判断 |

---

## 12. 運用・保守考慮事項

### 高可用性設定（本番推奨）

```bash
# API Gateway: 最小 2 レプリカ（ゾーン冗長）
az containerapp update \
  --name "gateway-svc" \
  --resource-group "$RG" \
  --min-replicas 2 \
  --scale-rule-name "http-scale" \
  --scale-rule-type http \
  --scale-rule-http-concurrency 100

# PostgreSQL: ゾーン冗長 + 自動バックアップ
az postgres flexible-server update \
  --name "${PREFIX}-auth-db" \
  --resource-group "$RG" \
  --backup-retention 30 \
  --geo-redundant-backup Enabled
```

### 証明書管理

Container Apps のカスタムドメイン設定時:

```bash
# カスタムドメインの追加（任意）
az containerapp hostname add \
  --name "gateway-svc" \
  --resource-group "$RG" \
  --hostname "api.yourdomain.com"

# マネージド証明書の作成（無料 TLS）
# 注意: az containerapp ssl upload は非推奨。正しいコマンドは hostname bind --certificate-type managed
az containerapp hostname bind \
  --name "gateway-svc" \
  --resource-group "$RG" \
  --hostname "api.yourdomain.com" \
  --certificate-type managed
```

### バックアップ戦略

| データ | バックアップ方法 | RPO | RTO |
|-------|--------------|-----|-----|
| PostgreSQL | Azure 自動バックアップ (7〜35 日) | 5 分 | 30 分 |
| Cosmos DB | Azure 自動バックアップ (継続) | 1 時間 | 4 時間 |
| Container Images | ACR Geo-replication | N/A | 5 分 |

### セキュリティ強化（本番必須）

1. **Key Vault 参照の全面利用**: 全シークレットを `secretref:` ではなく Key Vault 参照に統一
2. **Managed Identity の使用**: Service Principal の代わりに Container Apps のシステム割り当てマネージド ID を使用して Key Vault/ACR にアクセス
3. **VNet 統合**: Container Apps 環境を VNet に統合し、PostgreSQL/Cosmos DB に Private Endpoint を使用
4. **CORS の厳格化**: Gateway の CORS 設定を本番フロントエンド URL のみに制限
5. **レート制限**: Azure API Management または Gateway レベルでの DDoS 保護設定

```bash
# Managed Identity の有効化と Key Vault アクセス権付与
az containerapp identity assign \
  --name "gateway-svc" \
  --resource-group "$RG" \
  --system-assigned

GATEWAY_IDENTITY=$(az containerapp show \
  --name "gateway-svc" \
  --resource-group "$RG" \
  --query "identity.principalId" -o tsv)

az role assignment create \
  --assignee-object-id "$GATEWAY_IDENTITY" \
  --role "Key Vault Secrets User" \
  --scope "/subscriptions/$SUB/resourceGroups/$RG/providers/Microsoft.KeyVault/vaults/$KV_NAME"
```

---

## 13. 作業スケジュール目安

| Phase | 作業内容 | 目安時間 | 担当 |
|-------|---------|---------|------|
| 1 | IAM・権限設定（SP、OIDC、GitHub Secrets） | 2 時間 | インフラ担当 |
| 2 | PostgreSQL × 7 + Cosmos DB 作成 | 3 時間（Azure プロビジョニング含む） | インフラ担当 |
| 3 | Event Hubs 作成・トピック設定 | 1 時間 | インフラ担当 |
| 4 | Container Apps 環境・ACR 作成 | 1 時間 | インフラ担当 |
| 5 | Key Vault 設定・シークレット登録 | 1 時間 | セキュリティ担当 |
| 6 | CI/CD パイプライン修正・確認 | 2 時間 | アプリ担当 |
| 7 | コンテナイメージビルド・プッシュ（12 イメージ） | 2 時間 | アプリ担当 |
| 8 | Container Apps デプロイ（12 サービス） | 3 時間 | インフラ/アプリ担当 |
| 9 | データ初期化（MongoDB シード + Flyway 確認） | 1 時間 | アプリ担当 |
| 10 | フロントエンドデプロイ・URL 確定 | 1 時間 | フロント担当 |
| — | スモークテスト・動作確認 | 2 時間 | 全担当 |
| — | 監視・アラート設定 | 1 時間 | インフラ担当 |
| **合計** | | **約 2 日間** | |

> ⚠️ Azure リソースのプロビジョニングは並列実行可能ですが、依存関係（Container Apps 環境 → Container Apps 作成等）に注意してください。

---

## 14. 各フェーズ完了チェックリスト

各フェーズ作業完了後、以下のチェックリストで確認してから次フェーズに進んでください。全項目が ✅ になるまで次フェーズに進まないこと。

---

### Phase 1 完了チェック：IAM・権限設定

#### 確認コマンド

```bash
# SP 作成確認
az ad sp show --id "$APP_ID" --query "{displayName:displayName,appId:appId}" -o json

# ロール割り当て確認（Contributor が含まれること）
az role assignment list \
  --assignee "$SP_OBJ_ID" \
  --resource-group "$RG" \
  --output table

# Federated Credentials 確認（2件: github-main / github-env-production）
az ad app federated-credential list --id "$APP_ID" --output table

# ACR AcrPush ロール確認（Phase 4 の ACR 作成後に実行）
az role assignment list \
  --assignee "$SP_OBJ_ID" \
  --scope "/subscriptions/$SUB/resourceGroups/$RG/providers/Microsoft.ContainerRegistry/registries/$ACR_NAME" \
  --output table
```

#### ✅ チェック項目

- [ ] `az ad sp show` で Service Principal が存在し、APP_ID が取得できている
- [ ] `<RESOURCE_GROUP_NAME>` に対して **Contributor** ロールが付与されている
- [ ] Federated Credential が **2件** 存在する（`github-main`、`github-env-production`）
- [ ] GitHub Secrets に **3件** 登録済み
  - [ ] `AZURE_CLIENT_ID`（SP の APP_ID）
  - [ ] `AZURE_TENANT_ID`（テナント ID）
  - [ ] `AZURE_SUBSCRIPTION_ID`（`<AZURE_SUBSCRIPTION_ID>`）
- [ ] GitHub Variables に **3件** 登録済み
  - [ ] `AZURE_RESOURCE_GROUP`（`<RESOURCE_GROUP_NAME>`）
  - [ ] `CONTAINER_APPS_ENVIRONMENT`（`skishop-cae`）
  - [ ] `AZURE_LOCATION`（`japaneast`）
- [ ] （Phase 4 完了後）SP に **AcrPush** ロールが ACR スコープで付与されている

---

### Phase 2 完了チェック：データ基盤構築

#### 確認コマンド

```bash
# PostgreSQL サーバー一覧確認（7サーバーが "Ready" 状態）
az postgres flexible-server list \
  --resource-group "$RG" \
  --query "[].{name:name, state:state, version:version}" \
  --output table

# 各サーバーの状態確認
for SVC in auth user sales payment point coupon mail; do
  STATE=$(az postgres flexible-server show \
    --name "${PREFIX}-${SVC}-db" \
    --resource-group "$RG" \
    --query "state" -o tsv 2>/dev/null)
  echo "${PREFIX}-${SVC}-db: ${STATE:-NOT FOUND}"
done

# Cosmos DB 確認
az cosmosdb show \
  --name "${PREFIX}-mongo" \
  --resource-group "$RG" \
  --query "{state:provisioningState, endpoint:documentEndpoint}" -o json

# MongoDB データベース一覧（2件: skishop_inventory / skishop_ai）
az cosmosdb mongodb database list \
  --account-name "${PREFIX}-mongo" \
  --resource-group "$RG" \
  --query "[].name" -o tsv

# MongoDB 接続文字列の存在確認（文字数のみ表示）
MONGO_TEST=$(az cosmosdb keys list \
  --name "${PREFIX}-mongo" \
  --resource-group "$RG" \
  --type connection-strings \
  --query "connectionStrings[0].connectionString" -o tsv)
echo "MongoDB connection string length: ${#MONGO_TEST} chars"
```

#### ✅ チェック項目

- [ ] PostgreSQL Flexible Server が **7サーバー** 存在する（auth/user/sales/payment/point/coupon/mail）
- [ ] 全 PostgreSQL サーバーの状態が **"Ready"**
- [ ] PostgreSQL のバージョンが **16**
- [ ] 各サーバーに対応するデータベースが作成されている
  - [ ] `skishop_auth`（auth-db）
  - [ ] `skishop_users`（user-db）
  - [ ] `skishop_sales`（sales-db）
  - [ ] `skishop_payment`（payment-db）
  - [ ] `point_db`（point-db）
  - [ ] `coupon_db`（coupon-db）
  - [ ] `skishop_mailsend`（mail-db）
- [ ] Cosmos DB アカウント `skishop-mongo` が **"Succeeded"** 状態
- [ ] MongoDB データベースが **2件** 存在する（`skishop_inventory`、`skishop_ai`）
- [ ] MongoDB 接続文字列が取得できている（`MONGO_CONN` 変数）
- [ ] 全 PostgreSQL サーバーのファイアウォールに Azure サービスからの接続が許可されている

---

### Phase 3 完了チェック：メッセージング基盤構築

#### 確認コマンド

```bash
# Event Hubs Namespace 確認
az eventhubs namespace show \
  --name "$EVENTHUB_NS" \
  --resource-group "$RG" \
  --query "{status:status, kafkaEnabled:kafkaEnabled, sku:sku.name}" -o json

# Event Hub トピック一覧（8件存在すること）
az eventhubs eventhub list \
  --namespace-name "$EVENTHUB_NS" \
  --resource-group "$RG" \
  --query "[].{name:name, partitionCount:partitionCount, status:status}" \
  -o table

# 接続文字列の取得確認（先頭 60 文字のみ表示）
CONN_TEST=$(az eventhubs namespace authorization-rule keys list \
  --resource-group "$RG" \
  --namespace-name "$EVENTHUB_NS" \
  --name RootManageSharedAccessKey \
  --query primaryConnectionString -o tsv)
echo "Event Hubs connection string: ${CONN_TEST:0:60}..."
```

#### ✅ チェック項目

- [ ] Event Hubs Namespace `skishop-eventhub` が **"Active"** 状態
- [ ] `kafkaEnabled` が **true**
- [ ] SKU が **"Standard"**（Kafka プロトコルは Standard 以上が必須）
- [ ] **8トピック**が全て存在し、ステータスが **"Active"**
  - [ ] `skishop-auth-events`（パーティション数: 4）
  - [ ] `skishop-user-events`（パーティション数: 4）
  - [ ] `skishop-sales-events`（パーティション数: 4）
  - [ ] `skishop-payment-events`（パーティション数: 4）
  - [ ] `skishop-inventory-events`（パーティション数: 4）
  - [ ] `skishop-point-events`（パーティション数: 4）
  - [ ] `skishop-coupon-events`（パーティション数: 4）
  - [ ] `skishop-ai-events`（パーティション数: 4）
- [ ] Event Hubs 接続文字列が取得できている（`EVENTHUB_CONN` 変数）
- [ ] Kafka ブートストラップアドレスを確認: `${EVENTHUB_NS}.servicebus.windows.net:9093`

---

### Phase 4 完了チェック：コンテナ基盤構築

#### 確認コマンド

```bash
# Log Analytics Workspace 確認
az monitor log-analytics workspace show \
  --workspace-name "${PREFIX}-logs" \
  --resource-group "$RG" \
  --query "{customerId:customerId, provisioningState:provisioningState, retentionInDays:retentionInDays}" -o json

# Container Apps Environment 確認
az containerapp env show \
  --name "$CAE_NAME" \
  --resource-group "$RG" \
  --query "{provisioningState:properties.provisioningState, defaultDomain:properties.defaultDomain, location:location}" -o json

# ACR 確認
az acr show \
  --name "$ACR_NAME" \
  --resource-group "$RG" \
  --query "{loginServer:loginServer, provisioningState:provisioningState, sku:sku.name}" -o json

# SP への AcrPull ロール確認
az role assignment list \
  --assignee "$SP_OBJ_ID" \
  --scope "/subscriptions/$SUB/resourceGroups/$RG/providers/Microsoft.ContainerRegistry/registries/$ACR_NAME" \
  --query "[].{role:roleDefinitionName}" \
  -o table
```

#### ✅ チェック項目

- [ ] Log Analytics Workspace `skishop-logs` が **"Succeeded"** 状態
- [ ] `WORKSPACE_ID`（customerId）変数が設定されている
- [ ] `WORKSPACE_KEY`（primarySharedKey）変数が設定されている
- [ ] Container Apps Environment `skishop-cae` が **"Succeeded"** 状態
- [ ] `ENV_DOMAIN`（defaultDomain）変数が設定されている
  - 形式例: `blue-field-abc12345.japaneast.azurecontainerapps.io`
- [ ] Container Registry `<ACR_NAME>` が **"Succeeded"** 状態、SKU が **"Standard"**
- [ ] `ACR_LOGIN_SERVER` 変数が `<ACR_LOGIN_SERVER>` を指している
- [ ] SP に **AcrPull** ロールが ACR スコープで付与されている

---

### Phase 5 完了チェック：シークレット管理

#### 確認コマンド

```bash
# Key Vault 状態確認
az keyvault show \
  --name "$KV_NAME" \
  --resource-group "$RG" \
  --query "{provisioningState:properties.provisioningState, vaultUri:properties.vaultUri}" -o json

# 必須シークレットの存在確認（9件全て enabled=true）
REQUIRED_SECRETS=(
  "db-admin-password"
  "jwt-secret"
  "internal-api-key"
  "azure-openai-api-key"
  "mongo-connection-string"
  "eventhub-connection-string"
  "acs-connection-string"
  "acs-sender-address"
  "nextauth-secret"
)
ALL_OK=true
for SECRET in "${REQUIRED_SECRETS[@]}"; do
  STATUS=$(az keyvault secret show \
    --vault-name "$KV_NAME" \
    --name "$SECRET" \
    --query "attributes.enabled" -o tsv 2>/dev/null)
  if [ "$STATUS" = "true" ]; then
    echo "✅ $SECRET: OK"
  else
    echo "❌ $SECRET: NOT FOUND or DISABLED"
    ALL_OK=false
  fi
done
echo "Overall: $ALL_OK"

# SP への Key Vault アクセス権確認
az role assignment list \
  --assignee "$SP_OBJ_ID" \
  --scope "/subscriptions/$SUB/resourceGroups/$RG/providers/Microsoft.KeyVault/vaults/$KV_NAME" \
  --query "[].roleDefinitionName" -o tsv
```

#### ✅ チェック項目

- [ ] Key Vault `skishop-kv` が **"Succeeded"** 状態
- [ ] 以下 **9件**のシークレットが全て `enabled=true` で登録されている
  - [ ] `db-admin-password`
  - [ ] `jwt-secret`（本番用に新規生成した値、ローカル開発用と**異なる**こと）
  - [ ] `internal-api-key`
  - [ ] `azure-openai-api-key`（プレースホルダではなく実際の値）
  - [ ] `mongo-connection-string`（`MONGO_CONN` 変数から設定）
  - [ ] `eventhub-connection-string`（`EVENTHUB_CONN` 変数から設定）
  - [ ] `acs-connection-string`（プレースホルダではなく実際の値）
  - [ ] `acs-sender-address`（プレースホルダではなく実際の値）
  - [ ] `nextauth-secret`
- [ ] SP に **"Key Vault Secrets Officer"** ロールが付与されている
- [ ] シークレット値が全てプレースホルダ（`<your-...>`）のままになっていない

---

### Phase 6 完了チェック：CI/CD パイプライン設定

#### 確認コマンド

```bash
# GitHub CLI でワークフロー確認（インストール済みの場合）
gh workflow list 2>/dev/null || echo "gh CLI not configured - manual check required"

# ワークフローファイルの構文確認
python3 -c "
import yaml, sys
for f in ['ci.yml', 'deploy.yml']:
    try:
        with open(f'.github/workflows/{f}') as fh:
            yaml.safe_load(fh)
        print(f'✅ {f}: YAML syntax OK')
    except Exception as e:
        print(f'❌ {f}: {e}')
"
```

#### ✅ チェック項目

- [ ] `.github/workflows/ci.yml` の YAML 構文エラーがない
- [ ] `.github/workflows/deploy.yml` の YAML 構文エラーがない
- [ ] `ci.yml` が ACR（または GHCR）への認証と `docker push` を正しく設定している
- [ ] `deploy.yml` の `CONTAINER_REGISTRY` 環境変数が正しいレジストリを指している
- [ ] `deploy.yml` のサービスマトリクスに **12サービス全て** が含まれている
  - [ ] `agent-runtime-monolith`（従来未含みの場合は追加）
  - [ ] `mailsend-service`（従来未含みの場合は追加）
  - [ ] `frontend`（従来未含みの場合は追加）
- [ ] `main` ブランチへのテスト push で CI ビルドが成功することを確認
- [ ] `workflow_dispatch` でのデプロイ手動実行が可能なことを確認

---

### Phase 7 完了チェック：コンテナイメージのビルド・プッシュ

#### 確認コマンド

```bash
# ACR リポジトリ一覧（12件存在すること）
az acr repository list \
  --name "$ACR_NAME" \
  --output tsv | sort

# 各イメージの最新タグ確認
echo "=== Image tags in ACR ==="
for SVC in authentication-service user-management-service inventory-management-service \
           sales-management-service payment-cart-service point-service coupon-service \
           ai-support-service mailsend-service api-gateway-service agent-runtime-monolith frontend; do
  LATEST=$(az acr repository show-tags \
    --name "$ACR_NAME" \
    --repository "$SVC" \
    --orderby time_desc \
    --query "[0]" -o tsv 2>/dev/null)
  echo "${SVC}: ${LATEST:-❌ NO IMAGE}"
done
```

#### ✅ チェック項目

- [ ] ACR に **12リポジトリ**が存在する
  - [ ] `authentication-service`、`user-management-service`、`inventory-management-service`
  - [ ] `sales-management-service`、`payment-cart-service`、`point-service`
  - [ ] `coupon-service`、`ai-support-service`、`mailsend-service`
  - [ ] `api-gateway-service`、`agent-runtime-monolith`、`frontend`
- [ ] 各リポジトリに `latest` タグが存在する
- [ ] 各リポジトリにコミット SHA タグ（`$TAG` 変数）が存在する
- [ ] `TAG` 変数（コミット SHA）が設定されている
- [ ] `ACR_LOGIN_SERVER` 変数（`<ACR_LOGIN_SERVER>`）が設定されている

---

### Phase 8 完了チェック：Container Apps デプロイ

#### 確認コマンド

```bash
# 全 Container Apps の状態確認（12件が "Succeeded"）
az containerapp list \
  --resource-group "$RG" \
  --query "[].{name:name, state:properties.provisioningState, minR:properties.template.scale.minReplicas}" \
  --output table

# 各サービスの状態と FQDN
for SVC in auth-svc user-svc inventory-svc sales-svc payment-svc point-svc \
           coupon-svc ai-svc mail-svc agent-runtime gateway-svc frontend-svc; do
  STATE=$(az containerapp show \
    --name "$SVC" \
    --resource-group "$RG" \
    --query "properties.provisioningState" -o tsv 2>/dev/null)
  FQDN=$(az containerapp show \
    --name "$SVC" \
    --resource-group "$RG" \
    --query "properties.configuration.ingress.fqdn" -o tsv 2>/dev/null)
  echo "${SVC}: ${STATE:-NOT FOUND} | ${FQDN:-no-external-ingress}"
done

# Gateway ヘルスチェック
GATEWAY_FQDN=$(az containerapp show \
  --name "gateway-svc" \
  --resource-group "$RG" \
  --query "properties.configuration.ingress.fqdn" -o tsv)
curl -sf "https://${GATEWAY_FQDN}/actuator/health" | python3 -m json.tool

# 各サービスの起動ログにエラーがないことを確認
for SVC in auth-svc inventory-svc agent-runtime gateway-svc; do
  ERR=$(az containerapp logs show \
    --name "$SVC" \
    --resource-group "$RG" \
    --tail 50 2>/dev/null | grep -c "ERROR")
  echo "${SVC}: ERROR count = $ERR"
done
```

#### ✅ チェック項目

- [ ] **12サービス全て**の `provisioningState` が **"Succeeded"**
- [ ] **Internal Ingress（10サービス）**が外部 URL から直接アクセス不可であることを確認
  - auth-svc / user-svc / inventory-svc / sales-svc / payment-svc
  - point-svc / coupon-svc / ai-svc / mail-svc / agent-runtime
- [ ] **External Ingress（2サービス）**の FQDN が取得できる
  - [ ] `gateway-svc`: `/actuator/health` が `{"status":"UP"}` を返す
  - [ ] `frontend-svc`: `https://<fqdn>/` が HTTP 200 を返す
- [ ] `GATEWAY_FQDN` 変数に正しい FQDN が格納されている
- [ ] GitHub Variables の `GATEWAY_EXTERNAL_URL` を実際の値に更新済み
- [ ] 各サービスのログに起動時 `ERROR` がない（特に DB 接続エラー、Kafka 接続エラー）
- [ ] Flyway マイグレーションが成功している（PostgreSQL サービスのログで確認）
- [ ] Kafka（Event Hubs）への接続が成功している（SASL_SSL 設定が正しい）
- [ ] `agent-runtime` が Azure OpenAI に接続できている（ログ確認）
- [ ] `mail-svc` が ACS に接続できている（ログ確認）

---

### Phase 9 完了チェック：データ初期化

#### 確認コマンド

```bash
MONGO_CONN="<Cosmos DB Connection String from Phase 2-2>"

# MongoDB シードデータ確認
mongosh "$MONGO_CONN" --eval "
use skishop_inventory;
print('products count:', db.products.countDocuments());
print('categories count:', db.categories.countDocuments());
print('search_logs count:', db.search_logs.countDocuments());
print('BEGINNER ski (cat-ski):', db.products.countDocuments({
  categoryId: 'cat-ski',
  tags: {'\$in': ['初心者', '初中級者']}
}));
"

# Flyway マイグレーション確認
for SVC in auth-svc user-svc sales-svc payment-svc point-svc coupon-svc mail-svc; do
  echo "=== $SVC Flyway ==="
  az containerapp logs show \
    --name "$SVC" \
    --resource-group "$RG" \
    --tail 100 2>/dev/null \
    | grep -E "Successfully applied|No migration needed|ERROR" | tail -3
done
```

#### ✅ チェック項目

- [ ] `skishop_inventory.products` に **90件以上**の商品データが存在する
- [ ] `skishop_inventory.categories` にカテゴリデータが存在する
- [ ] `skishop_inventory.search_logs` にシードデータが存在する
- [ ] BEGINNER 向けスキー板（`cat-ski` + `初心者/初中級者`タグ）が **3件以上**存在する
- [ ] BEGINNER 向けブーツ（`cat-boots` + `初心者/初中級者`タグ）が **4件以上**存在する
- [ ] 各 PostgreSQL サービスのログに `"Successfully applied X migration(s)"` が表示される
- [ ] Flyway エラーがない（`ERROR` が含まれない）
- [ ] デモ用管理者アカウント（`admin@example.com` / `Admin1234!`）でログインできる

---

### Phase 10 完了チェック：フロントエンドデプロイ

#### 確認コマンド

```bash
FRONTEND_FQDN=$(az containerapp show \
  --name "frontend-svc" \
  --resource-group "$RG" \
  --query "properties.configuration.ingress.fqdn" -o tsv)

echo "Frontend URL: https://${FRONTEND_FQDN}"

# 各ページの HTTP ステータス確認
for PATH_CHECK in "/" "/login" "/api/health"; do
  STATUS=$(curl -sf -o /dev/null -w "%{http_code}" \
    "https://${FRONTEND_FQDN}${PATH_CHECK}" 2>/dev/null || echo "ERROR")
  echo "${PATH_CHECK}: HTTP ${STATUS}"
done

# フロントエンドから API Gateway への接続確認
GATEWAY_FQDN=$(az containerapp show \
  --name "gateway-svc" \
  --resource-group "$RG" \
  --query "properties.configuration.ingress.fqdn" -o tsv)
curl -sf -o /dev/null -w "Gateway health from outside: %{http_code}\n" \
  "https://${GATEWAY_FQDN}/actuator/health"
```

#### ✅ チェック項目

- [ ] `frontend-svc` の `provisioningState` が **"Succeeded"**
- [ ] `https://<frontend-fqdn>/` が **HTTP 200** を返す
- [ ] `/login` ページが正常に表示される
- [ ] 管理者ログイン（`admin@example.com` / `Admin1234!`）が成功する
- [ ] ログイン後に `/admin/dashboard` が表示される
- [ ] フロントエンドから API Gateway への接続で CORS エラーが発生しない
- [ ] `NEXTAUTH_URL` がデプロイされたフロントエンドの FQDN と一致している
- [ ] ビルド時の `NEXT_PUBLIC_APP_URL` が正しいフロントエンド FQDN に設定されている
- [ ] AI アドバイザー機能から商品検索が動作する（エンドツーエンド）
- [ ] フロントエンドイメージが **Step B** で正式 FQDN を使ったビルドに更新されていることを確認
- [ ] ブラウザの開発者コンソールに重大なエラーがない

---

### 全体完了チェック（エンドツーエンド スモークテスト）

全フェーズ完了後、以下を実行して本番環境が正常に動作していることを確認します。

```bash
GATEWAY="https://${GATEWAY_FQDN}"
FRONTEND="https://${FRONTEND_FQDN}"

echo "===== E2E Smoke Tests ====="

# 1. 全サービス ヘルスチェック
echo -e "\n[1] Gateway Health Check"
curl -sf "${GATEWAY}/actuator/health" | python3 -m json.tool

# 2. 認証（ログイン → JWT トークン取得）
echo -e "\n[2] Auth Login"
TOKEN=$(curl -sf -X POST "${GATEWAY}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"Admin1234!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])" 2>/dev/null)
[ -n "$TOKEN" ] && echo "✅ Login OK (token length: ${#TOKEN})" || echo "❌ Login FAILED"

# 3. 商品一覧（inventory-service 疎通）
echo -e "\n[3] Product List"
PROD_COUNT=$(curl -sf "${GATEWAY}/api/v1/products?size=1" \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalElements','error'))")
echo "Products total: $PROD_COUNT (expected: >= 90)"

# 4. BEGINNER スキー板検索（AI エージェント連携確認）
echo -e "\n[4] BEGINNER Ski Equipment Search"
INTERNAL_KEY=$(az keyvault secret show \
  --vault-name "$KV_NAME" \
  --name "internal-api-key" \
  --query "value" -o tsv 2>/dev/null || echo "KEY_NOT_FOUND")
SKI_COUNT=$(curl -sf "${GATEWAY}/api/v1/internal/products/search?category=cat-ski&skillLevel=BEGINNER" \
  -H "X-Internal-Api-Key: $INTERNAL_KEY" \
  | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null)
echo "BEGINNER ski: $SKI_COUNT results (expected: >= 3)"

# 5. アナリティクス（管理者機能）
echo -e "\n[5] Analytics Dashboard"
ANALYTICS=$(curl -sf -o /dev/null -w "%{http_code}" \
  "${GATEWAY}/api/v1/admin/analytics/dashboard" \
  -H "Authorization: Bearer $TOKEN")
echo "Analytics: HTTP $ANALYTICS (expected: 200)"

# 6. フロントエンド疎通
echo -e "\n[6] Frontend"
FE_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" "${FRONTEND}/")
echo "Frontend: HTTP $FE_STATUS (expected: 200)"

# 7. Event Hubs メッセージング確認（手動）
echo -e "\n[7] Kafka/Event Hubs - Manual Check Required"
echo "Azure Portal で受信メッセージを確認:"
echo "https://portal.azure.com/#@caglobaldemos2605.onmicrosoft.com/resource/subscriptions/${SUB}/resourceGroups/${RG}/providers/Microsoft.EventHub/namespaces/${EVENTHUB_NS}/overview"

echo -e "\n===== Smoke Tests Complete ====="
```

#### ✅ 全体完了チェック項目

- [ ] 全 12 Container Apps が稼働中
- [ ] Gateway `/actuator/health` が `{"status":"UP"}` を返す
- [ ] 管理者ログインが成功し JWT トークンが取得できる
- [ ] 商品一覧の `totalElements` が 90 以上
- [ ] BEGINNER 向けスキー板が 3件以上返ってくる
- [ ] アナリティクスダッシュボードにアクセスできる（HTTP 200）
- [ ] フロントエンドが HTTP 200 を返す
- [ ] Event Hubs でメッセージが受信されている（ポータル確認）
- [ ] フロントエンドでエンドツーエンドの購入フロー（商品選択 → カート → 決済）が動作する
- [ ] メール送信機能が動作する（ACS 経由で送信確認）
- [ ] AI アドバイザーが推薦商品を返す
- [ ] ポイント残高・クーポン一覧が正常に表示される

---

## Appendix: Terraform による IaC 自動化

既存の `infra/terraform/main.tf` を `<RESOURCE_GROUP_NAME>` 向けに利用する場合:

```bash
cd infra/terraform

# terraform.tfvars の作成（.gitignore 登録済みであることを確認）
cat > terraform.tfvars << EOF
subscription_id     = "<AZURE_SUBSCRIPTION_ID>"
resource_group_name = "<RESOURCE_GROUP_NAME>"
location            = "japaneast"
environment         = "prod"
db_admin_username   = "<DB_ADMIN_USER>"
db_admin_password   = "<Strong-Password!>"
jwt_secret          = "$(openssl rand -base64 48)"
EOF

# 既存リソースグループを Terraform の管理下に追加
terraform import \
  azurerm_resource_group.main \
  "/subscriptions/<AZURE_SUBSCRIPTION_ID>/resourceGroups/<RESOURCE_GROUP_NAME>"

terraform init
terraform plan -out=tfplan
terraform apply tfplan
```

> **注意**: 既存リソース（AI Foundry、Search Service、ACS）は `terraform import` で Terraform の管理下に追加するか、`data` ブロックで参照のみにするかを決定してください。既存リソースに `terraform destroy` が適用されないよう、`lifecycle { prevent_destroy = true }` を設定することを推奨します。
