# Azure Kubernetes Service (AKS) デプロイ計画書
## Azure Ski Shop — AKS 本番環境構築

| 項目 | 値 |
|------|-----|
| 対象サブスクリプション | `<AZURE_SUBSCRIPTION_ID>` |
| 対象リソースグループ | `<RESOURCE_GROUP_NAME>` |
| テナント ID | `<AZURE_TENANT_ID>` |
| 対象リージョン | `japaneast` |
| 作成日 | 2026-04-20 |
| ステータス | ACTIVE |

> **CAE→AKS 切り替え理由**: `java` サブスクリプションにて Container Apps Environment のサブスク上限（5個/サブスク）に到達したため、AKS に移行。

---

## 既完了リソース（再利用）

| リソース名 | 種別 | 状態 |
|-----------|------|------|
| `<AUTH_DB_NAME>` 〜 `<DB_NAME>` | PostgreSQL Flexible Server × 7 | ✅ 作成済み |
| `<COSMOS_DB_NAME>` | Cosmos DB for MongoDB | ✅ 作成済み（`skishop_inventory`, `skishop_ai`）|
| `<EVENTHUB_NAMESPACE>` | Event Hubs Namespace + 8 トピック | ✅ 作成済み |
| `skishop-logs` | Log Analytics Workspace | ✅ 作成済み（ID: `<LOG_ANALYTICS_WORKSPACE_ID>`）|
| `<ACR_NAME>` | Azure Container Registry | ✅ 作成済み（`<ACR_LOGIN_SERVER>`）|
| `skishop-kv` | Key Vault | 未作成（Phase 5）|

---

## 目次

1. [ターゲット AKS アーキテクチャ](#1-ターゲット-aks-アーキテクチャ)
2. [必要な Azure リソース一覧](#2-必要な-azure-リソース一覧)
3. [構築ステップ詳細](#3-構築ステップ詳細)
   - [Phase 4: AKS クラスター構築](#phase-4-aks-クラスター構築)
   - [Phase 5: Key Vault・シークレット管理](#phase-5-key-vaultシークレット管理)
   - [Phase 6: CI/CD パイプライン設定](#phase-6-cicd-パイプライン設定)
   - [Phase 7: コンテナイメージのビルド・プッシュ](#phase-7-コンテナイメージのビルドプッシュ)
   - [Phase 8: AKS へのアプリケーションデプロイ](#phase-8-aks-へのアプリケーションデプロイ)
   - [Phase 9: データ初期化](#phase-9-データ初期化)
   - [Phase 10: フロントエンドデプロイ](#phase-10-フロントエンドデプロイ)
4. [Kubernetes マニフェスト設計](#4-kubernetes-マニフェスト設計)
5. [環境変数マッピング](#5-環境変数マッピング)
6. [ネットワーク設計](#6-ネットワーク設計)
7. [監視・アラート設定](#7-監視アラート設定)
8. [スモークテスト手順](#8-スモークテスト手順)
9. [ロールバック計画](#9-ロールバック計画)
10. [各フェーズ完了チェックリスト](#10-各フェーズ完了チェックリスト)
11. [サービス更新手順・トラブルシューティング](#11-サービス更新手順トラブルシューティング)

---

## 1. ターゲット AKS アーキテクチャ

```
                    ┌──────────────────────────────────┐
                    │  Azure DNS / Azure Front Door     │
                    └───────────────┬──────────────────┘
                                    │ HTTPS
                    ┌───────────────▼──────────────────┐
                    │  nginx-ingress (LoadBalancer)     │
                    │  Public IP: <自動割り当て>          │
                    └───────┬────────────┬─────────────┘
                            │            │
               /            │            │  /api/*
          ┌────▼──────┐     │     ┌──────▼──────────┐
          │ frontend  │     │     │  gateway-svc    │
          │ Next.js   │     │     │  (ClusterIP)    │
          │ :3000     │     │     │  :8090          │
          └───────────┘     │     └──┬──┬──┬──┬──┬──┘
                            │        │  │  │  │  │
        ┌───────────────────┘   auth user inv sales pay ...
        │                       svc  svc  svc svc  svc
        │  各バックエンドは ClusterIP のみ（外部非公開）
        │
        ▼
┌───────────────────────────────────────────────────────┐
│  AKS Cluster: skishop-aks  (japaneast)                │
│  Namespace: skishop                                   │
│                                                       │
│  Node Pool: system (Standard_B2s × 2)                 │
│  Node Pool: app    (Standard_B2s × 2, autoscale 2-5)  │
│                                                       │
│  Add-ons: Azure Monitor, Key Vault CSI Driver         │
└───────────────────────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────────────────────┐
│  Azure マネージドサービス（既存）                        │
│                                                       │
│  PostgreSQL × 7 (<DB_NAME>)                     │
│  Cosmos DB (<COSMOS_DB_NAME>)                          │
│  Event Hubs (<EVENTHUB_NAMESPACE>) ← Kafka互換          │
│  ACR (<ACR_LOGIN_SERVER>)                          │
│  Key Vault (skishop-kv)                              │
│  Log Analytics (skishop-logs)                        │
└───────────────────────────────────────────────────────┘
```

---

## 2. 必要な Azure リソース一覧

### 新規作成が必要なリソース

| リソース | 名前 | SKU/Tier | 目的 |
|---------|------|---------|------|
| AKS Cluster | `skishop-aks` | Free tier, Standard_B2s | 全コンテナを収容 |
| Public IP (nginx) | 自動割り当て | Standard | Ingress 用外部 IP |
| Key Vault | `skishop-kv` | Standard | シークレット一元管理 |

### コスト概算（月額）

| リソース | 概算コスト |
|---------|-----------|
| AKS ノード Standard_B2s × 4 | ~$35 × 4 = ~$140 |
| PostgreSQL Flexible Server × 7 (B1ms) | ~$30 × 7 = ~$210 |
| Cosmos DB for MongoDB (Serverless) | ~$10〜30 |
| Event Hubs Standard (1 TU) | ~$10 |
| Container Registry Basic | ~$5 |
| Key Vault Standard | ~$5 |
| Log Analytics (PerGB2018) | ~$10〜30 |
| **合計目安** | **~$390〜$430/月** |

---

## 3. 構築ステップ詳細

### Phase 4: AKS クラスター構築

#### 4-1. 必要ツールの確認・インストール

```bash
# kubectl
brew install kubectl

# Helm
brew install helm

# kubelogin (Azure AD 認証用)
brew install Azure/kubelogin/kubelogin

# バージョン確認
kubectl version --client
helm version
```

#### 4-2. AKS クラスターの作成

```bash
export SUB="<AZURE_SUBSCRIPTION_ID>"
export RG="<RESOURCE_GROUP_NAME>"
export LOCATION="japaneast"
export AKS_NAME="skishop-aks"
export ACR_NAME="<ACR_NAME>"
WORKSPACE_ID="<LOG_ANALYTICS_WORKSPACE_ID>"

az aks create \
  --name "$AKS_NAME" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --location "$LOCATION" \
  --node-count 2 \
  --node-vm-size "Standard_B2s" \
  --nodepool-name "system" \
  --enable-cluster-autoscaler \
  --min-count 2 \
  --max-count 4 \
  --network-plugin azure \
  --enable-addons monitoring \
  --workspace-resource-id "/subscriptions/${SUB}/resourceGroups/${RG}/providers/Microsoft.OperationalInsights/workspaces/skishop-logs" \
  --attach-acr "$ACR_NAME" \
  --enable-oidc-issuer \
  --enable-workload-identity \
  --tier free \
  --generate-ssh-keys \
  --query "{name:name, provisioningState:provisioningState, fqdn:fqdn}" \
  -o json 2>&1

echo "=== AKS created ==="
```

#### 4-3. Key Vault CSI Driver の有効化

```bash
# AKS クラスターに Key Vault Provider を追加
az aks enable-addons \
  --name "$AKS_NAME" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --addons azure-keyvault-secrets-provider \
  --query "{name:name, provisioningState:provisioningState}" \
  -o json 2>&1

echo "=== Key Vault CSI Driver enabled ==="
```

#### 4-4. kubeconfig の取得

```bash
az aks get-credentials \
  --name "$AKS_NAME" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --overwrite-existing

# 接続確認
kubectl get nodes
kubectl get namespaces
```

#### 4-5. Namespace と nginx-ingress の設定

```bash
# skishop 名前空間の作成
kubectl create namespace skishop

# Helm で nginx-ingress をインストール
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update

helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --create-namespace \
  --set controller.replicaCount=2 \
  --set controller.nodeSelector."kubernetes\.io/os"=linux \
  --set defaultBackend.nodeSelector."kubernetes\.io/os"=linux \
  --set controller.service.annotations."service\.beta\.kubernetes\.io/azure-load-balancer-health-probe-request-path"=/healthz

# External IP の取得（数分かかる）
kubectl get service ingress-nginx-controller -n ingress-nginx -w
# EXTERNAL-IP が割り当てられたら Ctrl+C
INGRESS_IP=$(kubectl get service ingress-nginx-controller \
  -n ingress-nginx \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
echo "Ingress IP: $INGRESS_IP"
```

#### 4-6. AKS の Workload Identity 設定（Key Vault アクセス用）

```bash
AKS_OIDC_ISSUER=$(az aks show \
  --name "$AKS_NAME" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --query "oidcIssuerProfile.issuerUrl" -o tsv)

# Managed Identity の作成
az identity create \
  --name "skishop-workload-identity" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --location "$LOCATION"

IDENTITY_CLIENT_ID=$(az identity show \
  --name "skishop-workload-identity" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --query "clientId" -o tsv)

IDENTITY_OBJ_ID=$(az identity show \
  --name "skishop-workload-identity" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --query "principalId" -o tsv)

echo "Identity Client ID: $IDENTITY_CLIENT_ID"

# Federated Credential の設定（Pod → Azure AD）
az identity federated-credential create \
  --name "skishop-aks-federated" \
  --identity-name "skishop-workload-identity" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --issuer "$AKS_OIDC_ISSUER" \
  --subject "system:serviceaccount:skishop:skishop-sa" \
  --audiences "api://AzureADTokenExchange"

# Kubernetes ServiceAccount の作成
kubectl create serviceaccount skishop-sa -n skishop
kubectl annotate serviceaccount skishop-sa -n skishop \
  azure.workload.identity/client-id="$IDENTITY_CLIENT_ID"
```

---

### Phase 5: Key Vault・シークレット管理

#### 5-1. Key Vault の作成

```bash
export SUB="<AZURE_SUBSCRIPTION_ID>"
export RG="<RESOURCE_GROUP_NAME>"
KV_NAME="skishop-kv"

az keyvault create \
  --name "$KV_NAME" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --location "japaneast" \
  --sku standard \
  --enable-rbac-authorization true \
  --soft-delete-retention-days 7

# Workload Identity に Key Vault Secrets User 権限付与
KV_ID=$(az keyvault show \
  --name "$KV_NAME" \
  --resource-group "$RG" \
  --query "id" -o tsv)

az role assignment create \
  --assignee-object-id "$IDENTITY_OBJ_ID" \
  --assignee-principal-type ServicePrincipal \
  --role "Key Vault Secrets User" \
  --scope "$KV_ID"

# GitHub Actions SP にも Secrets Officer 権限付与
SP_OBJ_ID=$(grep '^SP_OBJ_ID=' /tmp/skishop-java-secrets.env | cut -d= -f2-)
az role assignment create \
  --assignee-object-id "$SP_OBJ_ID" \
  --assignee-principal-type ServicePrincipal \
  --role "Key Vault Secrets Officer" \
  --scope "$KV_ID"
```

#### 5-2. シークレットの登録

```bash
KV_NAME="skishop-kv"

# 各値をファイルから読み込む（! が含まれるためファイル経由で取得）
DB_ADMIN_PASS=$(grep '^DB_ADMIN_PASS=' /tmp/skishop-java-secrets.env | cut -d= -f2-)
JWT_SECRET=$(grep '^JWT_SECRET=' /tmp/skishop-java-secrets.env | cut -d= -f2-)
INTERNAL_API_KEY=$(grep '^INTERNAL_API_KEY=' /tmp/skishop-java-secrets.env | cut -d= -f2-)
NEXTAUTH_SECRET=$(grep '^NEXTAUTH_SECRET=' /tmp/skishop-java-secrets.env | cut -d= -f2-)

az keyvault secret set --vault-name "$KV_NAME" --name "db-admin-password"    --value "$DB_ADMIN_PASS"
az keyvault secret set --vault-name "$KV_NAME" --name "jwt-secret"           --value "$JWT_SECRET"
az keyvault secret set --vault-name "$KV_NAME" --name "internal-api-key"     --value "$INTERNAL_API_KEY"
az keyvault secret set --vault-name "$KV_NAME" --name "nextauth-secret"      --value "$NEXTAUTH_SECRET"

# Event Hubs 接続文字列
EVENTHUB_CS=$(grep '^EVENTHUB_CS=' /tmp/skishop-java-secrets.env | cut -d= -f2-)
az keyvault secret set --vault-name "$KV_NAME" --name "eventhub-connection-string" --value "$EVENTHUB_CS"

# Cosmos DB 接続文字列（取得）
MONGO_CONN=$(az cosmosdb keys list \
  --name "<COSMOS_DB_NAME>" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --type connection-strings \
  --query "connectionStrings[0].connectionString" -o tsv)
az keyvault secret set --vault-name "$KV_NAME" --name "mongo-connection-string" --value "$MONGO_CONN"

# Azure OpenAI API Key（手動入力）
az keyvault secret set --vault-name "$KV_NAME" --name "azure-openai-api-key" \
  --value "<AZURE_OPENAI_API_KEY>"

# ACS 接続文字列（CA Global Demos RG のものを再利用）
# az keyvault secret set --vault-name "$KV_NAME" --name "acs-connection-string" --value "<ACS_CONN>"
# az keyvault secret set --vault-name "$KV_NAME" --name "acs-sender-address"    --value "<ACS_SENDER>"

echo "=== All secrets registered ==="
```

#### 5-3. SecretProviderClass マニフェストの作成

```yaml
# k8s/secret-provider-class.yaml
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: skishop-secrets
  namespace: skishop
spec:
  provider: azure
  parameters:
    usePodIdentity: "false"
    clientID: "<IDENTITY_CLIENT_ID>"   # 5-4-6 で取得した値
    keyvaultName: "skishop-kv"
    tenantId: "<AZURE_TENANT_ID>"
    objects: |
      array:
        - |
          objectName: db-admin-password
          objectType: secret
        - |
          objectName: jwt-secret
          objectType: secret
        - |
          objectName: internal-api-key
          objectType: secret
        - |
          objectName: nextauth-secret
          objectType: secret
        - |
          objectName: eventhub-connection-string
          objectType: secret
        - |
          objectName: mongo-connection-string
          objectType: secret
        - |
          objectName: azure-openai-api-key
          objectType: secret
        - |
          objectName: acs-connection-string
          objectType: secret
        - |
          objectName: acs-sender-address
          objectType: secret
  secretObjects:
    - secretName: skishop-secrets
      type: Opaque
      data:
        - objectName: db-admin-password
          key: DB_ADMIN_PASSWORD
        - objectName: jwt-secret
          key: JWT_SECRET
        - objectName: internal-api-key
          key: INTERNAL_API_KEY
        - objectName: nextauth-secret
          key: NEXTAUTH_SECRET
        - objectName: eventhub-connection-string
          key: EVENTHUB_CONNECTION_STRING
        - objectName: mongo-connection-string
          key: MONGO_CONNECTION_STRING
        - objectName: azure-openai-api-key
          key: AZURE_OPENAI_API_KEY
        - objectName: acs-connection-string
          key: ACS_CONNECTION_STRING
        - objectName: acs-sender-address
          key: ACS_SENDER_ADDRESS
```

---

### Phase 6: CI/CD パイプライン設定

#### 6-1. GitHub Secrets の追加登録

既存の `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` に加えて:

```bash
AKS_NAME="skishop-aks"
ACR_NAME="<ACR_NAME>"

# GitHub Variables に追加
gh variable set AZURE_RESOURCE_GROUP --body "<RESOURCE_GROUP_NAME>"
gh variable set AKS_CLUSTER_NAME     --body "$AKS_NAME"
gh variable set ACR_NAME             --body "$ACR_NAME"
gh variable set K8S_NAMESPACE        --body "skishop"
```

#### 6-2. deploy.yml の AKS 対応修正

```yaml
# .github/workflows/deploy.yml
name: Deploy to AKS

on:
  push:
    branches: [main]

env:
  ACR_NAME: <ACR_NAME>
  AKS_CLUSTER_NAME: skishop-aks
  RESOURCE_GROUP: <RESOURCE_GROUP_NAME>
  NAMESPACE: skishop

jobs:
  deploy:
    runs-on: ubuntu-latest
    permissions:
      id-token: write
      contents: read
    steps:
      - uses: actions/checkout@v4

      - name: Azure Login (OIDC)
        uses: azure/login@v2
        with:
          client-id: ${{ secrets.AZURE_CLIENT_ID }}
          tenant-id: ${{ secrets.AZURE_TENANT_ID }}
          subscription-id: ${{ secrets.AZURE_SUBSCRIPTION_ID }}

      - name: Login to ACR
        run: az acr login --name ${{ env.ACR_NAME }}

      - name: Get AKS credentials
        run: |
          az aks get-credentials \
            --name ${{ env.AKS_CLUSTER_NAME }} \
            --resource-group ${{ env.RESOURCE_GROUP }} \
            --overwrite-existing

      - name: Build and push image
        uses: docker/build-push-action@v6
        with:
          context: .
          file: ${{ matrix.service }}/Dockerfile
          push: true
          tags: |
            ${{ env.ACR_NAME }}.azurecr.io/${{ matrix.service }}:${{ github.sha }}
            ${{ env.ACR_NAME }}.azurecr.io/${{ matrix.service }}:latest

      - name: Deploy to AKS
        run: |
          kubectl set image deployment/${{ matrix.service }} \
            ${{ matrix.service }}=${{ env.ACR_NAME }}.azurecr.io/${{ matrix.service }}:${{ github.sha }} \
            -n ${{ env.NAMESPACE }}
          kubectl rollout status deployment/${{ matrix.service }} \
            -n ${{ env.NAMESPACE }} \
            --timeout=300s
```

---

### Phase 7: コンテナイメージのビルド・プッシュ

> ⚠️ **Docker ビルドキャッシュに注意**: Dockerfile に `--mount=type=cache` によるキャッシュ設定がある場合、
> Maven のローカルキャッシュから古い JAR が再利用され、**コードを変更しても変更が反映されない**ことがあります。
> 確実に最新のコードでビルドするには、`docker buildx build --no-cache` を使用するか、
> 下記のように JAR をローカルでビルドしてからコピーする方式を推奨します。

```bash
export SUB="<AZURE_SUBSCRIPTION_ID>"
export RG="<RESOURCE_GROUP_NAME>"
ACR_NAME="<ACR_NAME>"
ACR_LOGIN_SERVER="${ACR_NAME}.azurecr.io"
TAG=$(git rev-parse --short HEAD)

# Maven ビルド（並列）
# --no-transfer-progress で進捗ログを削減、-T 4 で並列ビルド
mvn clean package -DskipTests -Djacoco.skip=true -T 4 --no-transfer-progress

# ACR へログイン
az acr login --name "$ACR_NAME"

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
)

for SVC in "${SERVICES[@]}"; do
  echo "=== Building: $SVC ==="
  # --no-cache でキャッシュをバイパスして確実に最新コードをビルド
  docker buildx build --platform linux/amd64 --no-cache \
    -t "${ACR_LOGIN_SERVER}/${SVC}:${TAG}" \
    -t "${ACR_LOGIN_SERVER}/${SVC}:latest" \
    -f "${SVC}/Dockerfile" \
    --push \
    .
  echo "[DONE] ${SVC}"
done

# agent-runtime は別ディレクトリ
echo "=== Building: agent-runtime-monolith ==="
docker buildx build --platform linux/amd64 --no-cache \
  -t "${ACR_LOGIN_SERVER}/agent-runtime-monolith:${TAG}" \
  -t "${ACR_LOGIN_SERVER}/agent-runtime-monolith:latest" \
  -f "ai-agent-services/agent-runtime-monolith/Dockerfile" \
  --push \
  .

echo "=== All images pushed. TAG=$TAG ==="
```

> **💡 ビルド高速化のヒント（キャッシュ問題が発生した場合の代替手順）**
> Docker キャッシュの影響が疑われる場合、以下のように JAR を先にビルドして直接コピーする
> シンプルな Dockerfile を使う方法が確実です:
>
> ```bash
> # 1. JAR をローカルでビルド
> mvn clean package -DskipTests -pl <service-name> -am
>
> # 2. キャッシュなしのシンプル Dockerfile（ビルド済み JAR をコピーするだけ）
> cat > /tmp/Dockerfile.simple << 'EOF'
> FROM eclipse-temurin:21-jre
> WORKDIR /app
> RUN groupadd -r appgroup && useradd -r -g appgroup -d /app -s /sbin/nologin appuser
> COPY <service-name>/target/<service-name>-1.0.0.jar app.jar
> RUN chown appuser:appgroup app.jar
> USER appuser
> EXPOSE 8080
> ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC"
> ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
> EOF
>
> # 3. ビルド & プッシュ
> docker buildx build --platform linux/amd64 \
>   -t "${ACR_LOGIN_SERVER}/<service-name>:latest" --push \
>   . -f /tmp/Dockerfile.simple
> ```

---

### Phase 8: AKS へのアプリケーションデプロイ

#### 8-1. ConfigMap の作成（非機密設定値）

```bash
PG_PREFIX="<DB_PREFIX>"
EVENTHUB_NS="<EVENTHUB_NAMESPACE>"

# PostgreSQL FQDN 取得
AUTH_DB_FQDN=$(az postgres flexible-server show \
  --name "${PG_PREFIX}-auth-db" --resource-group "$RG" --subscription "$SUB" \
  --query fullyQualifiedDomainName -o tsv)
USER_DB_FQDN=$(az postgres flexible-server show \
  --name "${PG_PREFIX}-user-db" --resource-group "$RG" --subscription "$SUB" \
  --query fullyQualifiedDomainName -o tsv)
SALES_DB_FQDN=$(az postgres flexible-server show \
  --name "${PG_PREFIX}-sales-db" --resource-group "$RG" --subscription "$SUB" \
  --query fullyQualifiedDomainName -o tsv)
PAYMENT_DB_FQDN=$(az postgres flexible-server show \
  --name "${PG_PREFIX}-payment-db" --resource-group "$RG" --subscription "$SUB" \
  --query fullyQualifiedDomainName -o tsv)
POINT_DB_FQDN=$(az postgres flexible-server show \
  --name "${PG_PREFIX}-point-db" --resource-group "$RG" --subscription "$SUB" \
  --query fullyQualifiedDomainName -o tsv)
COUPON_DB_FQDN=$(az postgres flexible-server show \
  --name "${PG_PREFIX}-coupon-db" --resource-group "$RG" --subscription "$SUB" \
  --query fullyQualifiedDomainName -o tsv)
MAIL_DB_FQDN=$(az postgres flexible-server show \
  --name "${PG_PREFIX}-mail-db" --resource-group "$RG" --subscription "$SUB" \
  --query fullyQualifiedDomainName -o tsv)

kubectl create configmap skishop-config \
  --namespace skishop \
  --from-literal=AUTH_DB_URL="jdbc:postgresql://${AUTH_DB_FQDN}:5432/skishop_auth?sslmode=require" \
  --from-literal=USER_DB_URL="jdbc:postgresql://${USER_DB_FQDN}:5432/skishop_users?sslmode=require" \
  --from-literal=SALES_DB_URL="jdbc:postgresql://${SALES_DB_FQDN}:5432/skishop_sales?sslmode=require" \
  --from-literal=PAYMENT_DB_URL="jdbc:postgresql://${PAYMENT_DB_FQDN}:5432/skishop_payment?sslmode=require" \
  --from-literal=POINT_DB_URL="jdbc:postgresql://${POINT_DB_FQDN}:5432/point_db?sslmode=require" \
  --from-literal=COUPON_DB_URL="jdbc:postgresql://${COUPON_DB_FQDN}:5432/coupon_db?sslmode=require" \
  --from-literal=MAIL_DB_URL="jdbc:postgresql://${MAIL_DB_FQDN}:5432/skishop_mailsend?sslmode=require" \
  --from-literal=DB_USERNAME="<DB_ADMIN_USER>" \
  --from-literal=KAFKA_BROKERS="${EVENTHUB_NS}.servicebus.windows.net:9093" \
  --from-literal=KAFKA_REPLICATION_FACTOR="1" \
  --from-literal=SPRING_PROFILES_ACTIVE="kafka" \
  --from-literal=SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL="SASL_SSL" \
  --from-literal=SPRING_KAFKA_PROPERTIES_SASL_MECHANISM="PLAIN" \
  --from-literal=AZURE_OPENAI_ENDPOINT="https://<AZURE_OPENAI_ENDPOINT_HOST>" \
  --from-literal=AZURE_OPENAI_DEPLOYMENT="gpt-5.4-nano" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "=== ConfigMap created ==="
```

#### 8-2. SecretProviderClass の適用

```bash
# IDENTITY_CLIENT_ID を取得して書き換え
IDENTITY_CLIENT_ID=$(az identity show \
  --name "skishop-workload-identity" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --query "clientId" -o tsv)

sed "s/<IDENTITY_CLIENT_ID>/${IDENTITY_CLIENT_ID}/g" \
  k8s/secret-provider-class.yaml | kubectl apply -f -

echo "=== SecretProviderClass applied ==="
```

#### 8-3. 各サービスの Deployment + Service マニフェスト適用

```bash
ACR_LOGIN_SERVER="<ACR_LOGIN_SERVER>"
TAG=$(az acr repository show-tags \
  --name "<ACR_NAME>" --repository "authentication-service" \
  --orderby time_desc --query "[0]" -o tsv)

# マニフェストのタグを現在のイメージタグに置換して一括適用
find k8s/deployments -name "*.yaml" | while read f; do
  sed "s|IMAGE_TAG|${TAG}|g; s|ACR_SERVER|${ACR_LOGIN_SERVER}|g" "$f" \
    | kubectl apply -f -
done

# デプロイ状況確認
kubectl get pods -n skishop -w
```

> ⚠️ **`latest` タグ使用時の注意**: `imagePullPolicy: Always` が設定されていない場合、
> 既存の Pod は新しいイメージを自動的に取得しません。
> 新しいイメージを反映させるには必ず以下のコマンドで Pod を再起動してください:
>
> ```bash
> # 全バックエンドサービスを一括再起動
> for SVC in authentication-service user-management-service inventory-management-service \
>            sales-management-service payment-cart-service point-service \
>            coupon-service ai-support-service mailsend-service api-gateway-service \
>            agent-runtime-monolith; do
>   kubectl rollout restart deployment/${SVC} -n skishop
> done
>
> # 全 Pod のロールアウト完了を確認
> kubectl rollout status deployment --timeout=300s -n skishop
> ```

#### 8-3-1. Deployment マニフェストへの `imagePullPolicy` 設定

`latest` タグを使う場合は、各 Deployment の `containers` セクションに以下を必ず追加してください:

```yaml
containers:
  - name: <service-name>
    image: <ACR_LOGIN_SERVER>/<service-name>:latest
    imagePullPolicy: Always   # ← 追加必須（latest タグ使用時）
```

これにより `kubectl rollout restart` を実行した際に常に最新イメージが ACR から取得されます。

#### 8-4. API Gateway の Ingress 設定

```bash
# Gateway と Frontend の Ingress を適用
INGRESS_IP=$(kubectl get service ingress-nginx-controller \
  -n ingress-nginx \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

echo "Ingress IP: $INGRESS_IP"
echo "Gateway URL: http://${INGRESS_IP}  (DNS設定後: https://api.skishop.example.com)"

kubectl apply -f k8s/ingress.yaml
```

---

### Phase 9: データ初期化

#### 9-1. MongoDB シードデータの投入

```bash
# Cosmos DB 接続文字列取得
MONGO_CONN=$(az cosmosdb keys list \
  --name "<COSMOS_DB_NAME>" \
  --resource-group "$RG" \
  --subscription "$SUB" \
  --type connection-strings \
  --query "connectionStrings[0].connectionString" -o tsv)

# ローカルの mongosh でシードを実行
for SEED_FILE in docker/initdb-mongo/*.js; do
  echo "Executing: $SEED_FILE"
  mongosh "$MONGO_CONN" --file "$SEED_FILE"
done

echo "=== MongoDB seed completed ==="
```

#### 9-2. PostgreSQL の Flyway マイグレーション確認

各 Spring Boot サービス起動時に自動実行されます。Pod ログで確認:

```bash
# 全サービスのマイグレーション状況確認
for SVC in auth-svc user-svc sales-svc payment-svc point-svc coupon-svc mail-svc; do
  echo "=== $SVC ==="
  kubectl logs -n skishop \
    -l app="$SVC" \
    --tail=20 | grep -E "Flyway|Successfully applied|ERROR" || true
done
```

---

### Phase 10: フロントエンドデプロイ

> ⚠️ **ブートストラップ問題**: フロントエンドの FQDN（Ingress IP / DNS 名）が確定してからイメージをビルドする必要があります。
> `NEXT_PUBLIC_` プレフィックスの環境変数は **ビルド時に HTML/JS に静的に埋め込まれる**ため、
> Ingress IP が取得できていない状態でビルドしても正しく動作しません。

```bash
ACR_LOGIN_SERVER="<ACR_LOGIN_SERVER>"
TAG=$(git rev-parse --short HEAD)

# Step 1: Ingress IP を確認（割り当て済みであること）
INGRESS_IP=$(kubectl get service ingress-nginx-controller \
  -n ingress-nginx \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

if [ -z "$INGRESS_IP" ]; then
  echo "ERROR: Ingress IP がまだ割り当てられていません。しばらく待ってから再実行してください。"
  exit 1
fi
echo "Ingress IP: ${INGRESS_IP}"

FRONTEND_URL="http://${INGRESS_IP}"   # DNS 設定後は https://skishop.example.com

# Step 2: フロントエンドイメージビルド（確定 URL を NEXT_PUBLIC_ に埋め込む）
# --no-cache で確実に最新コードをビルド
docker buildx build --platform linux/amd64 --no-cache \
  -t "${ACR_LOGIN_SERVER}/frontend:${TAG}" \
  -t "${ACR_LOGIN_SERVER}/frontend:latest" \
  --build-arg "NEXT_PUBLIC_APP_URL=${FRONTEND_URL}" \
  --build-arg "NEXT_PUBLIC_APP_NAME=Azure SkiShop" \
  --push \
  ./frontend

# Step 3: フロントエンド Deployment を適用・再起動
kubectl apply -f k8s/deployments/frontend.yaml -n skishop
kubectl set image deployment/frontend \
  frontend="${ACR_LOGIN_SERVER}/frontend:${TAG}" \
  -n skishop
kubectl rollout restart deployment/frontend -n skishop
kubectl rollout status deployment/frontend -n skishop --timeout=120s

echo "Frontend URL: ${FRONTEND_URL}"
```

> **✅ ビルド確認**: フロントエンドイメージに環境変数が正しく埋め込まれているか確認するには:
>
> ```bash
> # ビルド済みイメージから SSR チャンク内の環境変数を検索
> docker run --rm "${ACR_LOGIN_SERVER}/frontend:${TAG}" \
>   grep -r "NEXT_PUBLIC_APP_URL" /app/.next/standalone/.next/server/ | head -3
> ```

---

## 4. Kubernetes マニフェスト設計

`k8s/` ディレクトリ構成:

```
k8s/
├── secret-provider-class.yaml     # Key Vault CSI 設定
├── ingress.yaml                   # nginx-ingress ルーティング
├── deployments/
│   ├── auth-svc.yaml
│   ├── user-svc.yaml
│   ├── inventory-svc.yaml
│   ├── sales-svc.yaml
│   ├── payment-svc.yaml
│   ├── point-svc.yaml
│   ├── coupon-svc.yaml
│   ├── ai-svc.yaml
│   ├── mail-svc.yaml
│   ├── agent-runtime.yaml
│   ├── gateway-svc.yaml
│   └── frontend.yaml
```

### Deployment テンプレート例（auth-svc）

```yaml
# k8s/deployments/auth-svc.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-svc
  namespace: skishop
spec:
  replicas: 2
  selector:
    matchLabels:
      app: auth-svc
  template:
    metadata:
      labels:
        app: auth-svc
        azure.workload.identity/use: "true"
    spec:
      serviceAccountName: skishop-sa
      containers:
        - name: auth-svc
          image: ACR_SERVER/authentication-service:IMAGE_TAG
          ports:
            - containerPort: 8080
          resources:
            requests:
              cpu: "250m"
              memory: "512Mi"
            limits:
              cpu: "500m"
              memory: "1Gi"
          envFrom:
            - configMapRef:
                name: skishop-config
          env:
            - name: DB_URL
              valueFrom:
                configMapKeyRef:
                  name: skishop-config
                  key: AUTH_DB_URL
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: skishop-secrets
                  key: DB_ADMIN_PASSWORD
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: skishop-secrets
                  key: JWT_SECRET
            - name: INTERNAL_API_KEY
              valueFrom:
                secretKeyRef:
                  name: skishop-secrets
                  key: INTERNAL_API_KEY
            - name: SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG
              value: >-
                org.apache.kafka.common.security.plain.PlainLoginModule
                required username="$ConnectionString"
                password="$(EVENTHUB_CONNECTION_STRING)";
          volumeMounts:
            - name: secrets-store
              mountPath: "/mnt/secrets-store"
              readOnly: true
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
      volumes:
        - name: secrets-store
          csi:
            driver: secrets-store.csi.k8s.io
            readOnly: true
            volumeAttributes:
              secretProviderClass: skishop-secrets
---
apiVersion: v1
kind: Service
metadata:
  name: auth-svc
  namespace: skishop
spec:
  selector:
    app: auth-svc
  ports:
    - port: 8080
      targetPort: 8080
  type: ClusterIP
```

### Ingress マニフェスト

```yaml
# k8s/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: skishop-ingress
  namespace: skishop
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /$2
    nginx.ingress.kubernetes.io/proxy-body-size: "50m"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "300"
spec:
  ingressClassName: nginx
  rules:
    - http:
        paths:
          # API Gateway → バックエンド全て
          - path: /api(/|$)(.*)
            pathType: ImplementationSpecific
            backend:
              service:
                name: gateway-svc
                port:
                  number: 8090
          # フロントエンド
          - path: /()(.*)
            pathType: ImplementationSpecific
            backend:
              service:
                name: frontend
                port:
                  number: 3000
```

---

## 5. 環境変数マッピング

### Docker Compose → AKS 変換表

| ローカル値 | AKS 本番値 | 設定方法 |
|-----------|-----------|---------|
| `postgres:5432` | `<AUTH_DB_HOST>:5432` | ConfigMap |
| `mongo:27017/skishop_inventory` | Cosmos DB 接続文字列 | Key Vault Secret |
| `kafka:9092` | `<EVENTHUB_NAMESPACE>.servicebus.windows.net:9093` | ConfigMap |
| `http://auth-service:8080` | `http://auth-svc.skishop.svc.cluster.local:8080` | ConfigMap |
| `SPRING_PROFILES_ACTIVE=kafka` | `SPRING_PROFILES_ACTIVE=kafka` | ConfigMap（変更なし）|

### サービス間内部通信 URL

Kubernetes では Service 名で `<service>.<namespace>.svc.cluster.local` と解決されます:

| サービス | 内部 URL |
|---------|---------|
| auth-svc | `http://auth-svc.skishop.svc.cluster.local:8080` |
| user-svc | `http://user-svc.skishop.svc.cluster.local:8081` |
| inventory-svc | `http://inventory-svc.skishop.svc.cluster.local:8082` |
| sales-svc | `http://sales-svc.skishop.svc.cluster.local:8083` |
| payment-svc | `http://payment-svc.skishop.svc.cluster.local:8084` |
| point-svc | `http://point-svc.skishop.svc.cluster.local:8085` |
| ai-svc | `http://ai-svc.skishop.svc.cluster.local:8087` |
| coupon-svc | `http://coupon-svc.skishop.svc.cluster.local:8088` |
| mail-svc | `http://mail-svc.skishop.svc.cluster.local:8089` |
| gateway-svc | `http://gateway-svc.skishop.svc.cluster.local:8090` |
| agent-runtime | `http://agent-runtime.skishop.svc.cluster.local:8100` |

---

## 6. ネットワーク設計

### イングレス構成（AKS + nginx-ingress）

```
インターネット
     │ HTTPS/HTTP
     ▼
nginx-ingress (LoadBalancer, Public IP)
     │
     ├── /api/*  → gateway-svc:8090  (ClusterIP)
     └── /*      → frontend:3000     (ClusterIP)
                       │
             ┌─────────┼─────────┐
           auth-svc  user-svc  ...  (ClusterIP, 外部非公開)
```

### セキュリティ境界

| コンポーネント | Service Type | 外部公開 |
|------------|-------------|--------|
| frontend | ClusterIP | Ingress 経由のみ |
| gateway-svc | ClusterIP | Ingress 経由のみ |
| 全バックエンド | ClusterIP | 不可（クラスター内のみ）|

---

## 7. 監視・アラート設定

#### Container Insights（AKS + Log Analytics）

AKS 作成時に `--enable-addons monitoring` で自動有効化。以下のクエリで確認:

```kusto
// Pod の再起動回数（異常検知）
KubePodInventory
| where TimeGenerated >= ago(1h)
| where Namespace == "skishop"
| summarize restarts=max(PodRestartCount) by Name, Namespace
| where restarts > 3
| order by restarts desc

// コンテナの CPU 使用率
Perf
| where TimeGenerated >= ago(30m)
| where ObjectName == "K8SContainer"
| where CounterName == "cpuUsageNanoCores"
| where InstanceName has "skishop"
| summarize avg(CounterValue/1000000) by InstanceName, bin(TimeGenerated, 5m)
| render timechart
```

---

## 8. スモークテスト手順

```bash
INGRESS_IP=$(kubectl get service ingress-nginx-controller \
  -n ingress-nginx \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
GATEWAY="http://${INGRESS_IP}"

echo "=== [1] Pod 稼働確認 ==="
kubectl get pods -n skishop

echo ""
echo "=== [2] ヘルスチェック ==="
curl -sf "${GATEWAY}/api/actuator/health" | python3 -m json.tool

echo ""
echo "=== [3] 認証テスト ==="
TOKEN=$(curl -sf -X POST "${GATEWAY}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"Admin1234!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
echo "Token: ${TOKEN:0:30}..."

echo ""
echo "=== [4] 商品一覧取得 ==="
curl -sf "${GATEWAY}/api/v1/products?size=3" \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('Products:', d.get('totalElements','?'))"

echo ""
echo "=== [5] フロントエンド疎通 ==="
curl -sf -o /dev/null -w "HTTP:%{http_code}\n" "http://${INGRESS_IP}/"
```

---

## 9. ロールバック計画

### AKS でのロールバック

```bash
# Deployment のロールアウト履歴確認
kubectl rollout history deployment/gateway-svc -n skishop

# 直前のリビジョンに即時ロールバック
kubectl rollout undo deployment/gateway-svc -n skishop

# 特定リビジョンへのロールバック
kubectl rollout undo deployment/gateway-svc -n skishop --to-revision=2

# ロールバック状況確認
kubectl rollout status deployment/gateway-svc -n skishop
```

### ロールバック手順

| 手順 | 操作 | 目安時間 |
|------|------|---------|
| 1 | 障害検知（Container Insights アラート受信） | 0〜5 分 |
| 2 | `kubectl rollout undo` 実行 | 1 分 |
| 3 | Pod 起動確認（`kubectl get pods -n skishop`） | 2〜3 分 |
| 4 | スモークテスト実行 | 3 分 |
| 5 | 根本原因分析・修正 | 担当者判断 |

---

## 10. 各フェーズ完了チェックリスト

### Phase 4: AKS クラスター構築

- [ ] `az aks create` 完了（`skishop-aks`）
- [ ] Key Vault CSI Driver 有効化
- [ ] `kubectl get nodes` で Ready 確認
- [ ] `skishop` 名前空間作成
- [ ] nginx-ingress インストール + External IP 割り当て確認
- [ ] Workload Identity 設定完了（OIDC Issuer, Managed Identity, Federated Credential）
- [ ] `skishop-sa` ServiceAccount 作成・アノテーション設定

### Phase 5: Key Vault・シークレット

- [ ] `skishop-kv` 作成
- [ ] Workload Identity に `Key Vault Secrets User` 権限付与
- [ ] 全シークレット登録（9 件）
- [ ] `SecretProviderClass` マニフェスト適用

### Phase 6-7: CI/CD + イメージビルド

- [ ] GitHub Secrets/Variables 更新
- [ ] `deploy.yml` を AKS 対応に修正
- [ ] 全サービスイメージビルド・ACR プッシュ完了（11 サービス）

### Phase 8: AKS デプロイ

- [ ] `skishop-config` ConfigMap 作成
- [ ] 全 Deployment マニフェスト適用（11 サービス）
- [ ] 全 Pod が `Running` 状態
- [ ] Ingress 設定適用

### Phase 9-10: データ初期化 + フロントエンド

- [ ] MongoDB シードデータ投入
- [ ] 全サービスの Flyway マイグレーション完了ログ確認
- [ ] フロントエンドイメージビルド・デプロイ
- [ ] スモークテスト全項目グリーン

---

## 11. サービス更新手順・トラブルシューティング

### 11-1. 既存クラスターへのサービス単体更新手順

AKS クラスター構築後に個別のサービスを更新（再ビルド・再デプロイ）する際の手順です。

```bash
ACR_LOGIN_SERVER="<ACR_LOGIN_SERVER>"
ACR_NAME="<ACR_NAME>"
SVC="<サービス名>"  # 例: user-management-service

# Step 1: JAR ビルド（プロジェクトルートで実行）
mvn clean package -DskipTests -pl ${SVC} -am --no-transfer-progress

# Step 2: Docker イメージビルド & ACR プッシュ（--no-cache 推奨）
docker buildx build --platform linux/amd64 --no-cache \
  -t "${ACR_LOGIN_SERVER}/${SVC}:latest" \
  -f "${SVC}/Dockerfile" \
  --push \
  .

# Step 3: Pod 再起動（新イメージ取得）
kubectl rollout restart deployment/${SVC} -n skishop
kubectl rollout status deployment/${SVC} -n skishop --timeout=120s

# Step 4: ログ確認
kubectl logs -n skishop -l app=${SVC} --tail=50 -f
```

### 11-2. ConfigMap / Secret 更新後の反映

ConfigMap や Kubernetes Secret を変更しても、既存の Pod には**自動的に反映されません**。
変更後は必ず Pod を再起動してください:

```bash
# ConfigMap を更新した場合
kubectl create configmap skishop-config \
  --namespace skishop \
  --from-literal=KEY="NEW_VALUE" \
  ... \
  --dry-run=client -o yaml | kubectl apply -f -

# 変更を適用する Pod を再起動（全サービス or 特定サービス）
kubectl rollout restart deployment -n skishop          # 全サービス
kubectl rollout restart deployment/user-management-service -n skishop  # 特定サービス
```

### 11-3. サービス個別デバッグ（port-forward）

Ingressを経由せず、特定のサービスに直接アクセスしてデバッグする方法です。

```bash
# 構文: kubectl port-forward -n skishop deployment/<service> <local-port>:<container-port>

# サービスポート一覧
kubectl port-forward -n skishop deployment/authentication-service       18080:8080 &
kubectl port-forward -n skishop deployment/user-management-service      18081:8081 &
kubectl port-forward -n skishop deployment/inventory-management-service 18082:8082 &
kubectl port-forward -n skishop deployment/sales-management-service     18083:8083 &
kubectl port-forward -n skishop deployment/payment-cart-service         18084:8084 &
kubectl port-forward -n skishop deployment/point-service                18085:8085 &
kubectl port-forward -n skishop deployment/ai-support-service           18087:8087 &
kubectl port-forward -n skishop deployment/coupon-service               18088:8088 &
kubectl port-forward -n skishop deployment/mailsend-service             18089:8089 &
kubectl port-forward -n skishop deployment/api-gateway-service          18090:8090 &

# 使用例: user-management-service のユーザー情報を直接取得
TOKEN=$(curl -s -X POST http://<EXTERNAL_IP>/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"Admin1234!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

curl -s http://localhost:18081/api/v1/users/me \
  -H "Authorization: Bearer ${TOKEN}" | python3 -m json.tool

# port-forward を全て停止
jobs -l
kill %1 %2 %3   # ジョブ番号を指定
```

### 11-4. Pod 障害時の調査手順

```bash
# Pod 一覧と状態確認
kubectl get pods -n skishop

# Pod が起動しない場合 → イベントを確認
kubectl describe pod -n skishop <pod-name>

# コンテナログ確認（最新100行）
kubectl logs -n skishop <pod-name> --tail=100

# Pod が再起動を繰り返す場合 → 直前のコンテナログを確認
kubectl logs -n skishop <pod-name> --previous

# アクティブな全 Pod のログを横断監視
kubectl logs -n skishop -l app=<service-name> -f --max-log-requests=5

# イメージが正しく取得できているか確認
kubectl get deployment -n skishop <service-name> \
  -o jsonpath='{.spec.template.spec.containers[0].image}'
```

### 11-5. よくある問題と対処法

| 症状 | 原因 | 対処 |
|------|------|------|
| Pod が `ImagePullBackOff` になる | ACR 認証失敗 または イメージが存在しない | `az acr login` 後に再ビルド・プッシュ。`kubectl describe pod` でエラー詳細確認 |
| コードを変更したのに反映されない | Docker ビルドキャッシュが古い JAR を使用 | `docker buildx build --no-cache` でリビルド |
| Pod 再起動後も古いイメージのまま | `imagePullPolicy: Always` が未設定 | Deployment に `imagePullPolicy: Always` を追加し `kubectl apply` |
| サービス起動直後に `CrashLoopBackOff` | DB 接続失敗 / Secret 未設定 | `kubectl logs --previous` で起動エラー確認。ConfigMap・Secret の値を検証 |
| ConfigMap 更新が反映されない | Pod が古い ConfigMap をキャッシュ | `kubectl rollout restart deployment -n skishop` で全 Pod 再起動 |
| フロントエンドで API URL が間違っている | ビルド時に `NEXT_PUBLIC_APP_URL` が未設定 | Ingress IP 確認後、`--no-cache` かつ `--build-arg` を指定して再ビルド |
| Flyway マイグレーションエラー | DB スキーマが不整合 | `kubectl logs` でエラー確認。必要に応じ PostgreSQL に直接接続して手動修正 |
