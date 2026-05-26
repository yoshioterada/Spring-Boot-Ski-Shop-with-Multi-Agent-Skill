#!/usr/bin/env bash
# =============================================================================
# SkiShop Azure リソース設定ファイル
# azure-stop.sh / azure-start.sh から source して使用します
# =============================================================================

# -----------------------------------------------------------------------------
# Azure 基本設定（環境に合わせて変更してください）
# -----------------------------------------------------------------------------
AZURE_RESOURCE_GROUP="${AZURE_RESOURCE_GROUP:-rg-yoshioterada-ski}"
AZURE_SUBSCRIPTION_ID="${AZURE_SUBSCRIPTION_ID:-}"   # 空の場合は az CLI のデフォルト使用
AKS_CLUSTER_NAME="${AKS_CLUSTER_NAME:-skishop-aks}"
AKS_NAMESPACE="${AKS_NAMESPACE:-skishop}"

# -----------------------------------------------------------------------------
# PostgreSQL Flexible Server 一覧
# 停止/起動の対象サーバーを列挙してください
# -----------------------------------------------------------------------------
POSTGRES_SERVERS=(
  "yoshi-ski-auth-db"
  "yoshi-ski-user-db"
  "yoshi-ski-sales-db"
  "yoshi-ski-payment-db"
  "yoshi-ski-point-db"
  "yoshi-ski-coupon-db"
  "yoshi-ski-mail-db"
)

# -----------------------------------------------------------------------------
# 情報表示のみ（停止/起動不可のリソース）
# -----------------------------------------------------------------------------
COSMOS_DB_ACCOUNT="yoshi-ski-mongo"       # Serverless → 停止不可、常時最小課金
EVENTHUB_NAMESPACE="yoshi-ski-eventhub"   # Standard → 停止不可、~$10/月

# -----------------------------------------------------------------------------
# タイムアウト設定（秒）
# -----------------------------------------------------------------------------
POSTGRES_START_TIMEOUT=300   # PostgreSQL 起動待機最大時間（5分）
AKS_START_TIMEOUT=600        # AKS 起動待機最大時間（10分）
AKS_STOP_TIMEOUT=600         # AKS 停止待機最大時間（10分）
PODS_READY_TIMEOUT=300       # Pod 起動完了待機最大時間（5分）
POLL_INTERVAL=15             # ポーリング間隔（秒）

# -----------------------------------------------------------------------------
# 月額コスト概算（参考）
# -----------------------------------------------------------------------------
# 実ノードプール構成:
#   system: Standard_B2s × 2  (~$35/月 × 2 = ~$70)
#   user  : Standard_D4as_v5 × 3  (~$108/月 × 3 = ~$324)
COST_AKS_MONTHLY=394         # AKS ノード合計 5台 (B2s×2 + D4as_v5×3)
COST_POSTGRES_MONTHLY=210    # PostgreSQL Flexible Server × 7 台
COST_ALWAYS_ON_MONTHLY=60    # Cosmos DB + Event Hubs + ACR + Key Vault 等
