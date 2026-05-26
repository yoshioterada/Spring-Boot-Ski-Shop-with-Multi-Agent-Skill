#!/usr/bin/env bash
# =============================================================================
# SkiShop Azure 全サービス停止スクリプト
#
# 停止対象リソース:
#   - AKS クラスター (skishop-aks)        ← VM 課金ゼロになる
#   - PostgreSQL Flexible Server × 7      ← コンピュート課金ゼロになる
#
# 停止不可リソース（常時最小課金）:
#   - Cosmos DB for MongoDB (Serverless)  ← ~$10-30/月
#   - Event Hubs Standard                 ← ~$10/月
#   - Container Registry Basic            ← ~$5/月
#   - Key Vault Standard                  ← ~$5/月
#
# 注意: PostgreSQL は Azure の仕様により停止から7日後に自動再起動されます
#
# 使用方法:
#   ./scripts/azure-stop.sh           # 確認プロンプトあり
#   ./scripts/azure-stop.sh --force   # 確認なしで即停止
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 設定ファイルを読み込む
# shellcheck source=azure-config.sh
source "${SCRIPT_DIR}/azure-config.sh"

# =============================================================================
# カラー定義
# =============================================================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# =============================================================================
# ユーティリティ関数
# =============================================================================
log_info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_success() { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }
log_step()    { echo -e "\n${BOLD}${CYAN}▶ $*${NC}"; }

# =============================================================================
# 前提チェック
# =============================================================================
preflight_check() {
  log_step "前提条件チェック"

  # az CLI の存在確認
  if ! command -v az &>/dev/null; then
    log_error "Azure CLI (az) がインストールされていません"
    log_error "インストール: https://docs.microsoft.com/cli/azure/install-azure-cli"
    exit 1
  fi

  # ログイン状態確認
  if ! az account show &>/dev/null; then
    log_error "Azure にログインしていません。'az login' を実行してください"
    exit 1
  fi

  # サブスクリプション設定
  if [[ -n "${AZURE_SUBSCRIPTION_ID}" ]]; then
    az account set --subscription "${AZURE_SUBSCRIPTION_ID}"
  fi

  local current_sub
  current_sub=$(az account show --query "name" -o tsv)
  local current_sub_id
  current_sub_id=$(az account show --query "id" -o tsv)

  log_success "Azure CLI ログイン済み"
  log_info   "  サブスクリプション: ${current_sub} (${current_sub_id})"
  log_info   "  リソースグループ  : ${AZURE_RESOURCE_GROUP}"
  log_info   "  AKS クラスター    : ${AKS_CLUSTER_NAME}"
  log_info   "  PostgreSQL 台数   : ${#POSTGRES_SERVERS[@]} 台"
}

# =============================================================================
# 現在の状態表示
# =============================================================================
show_current_status() {
  log_step "現在のリソース状態"

  # AKS
  local aks_state
  aks_state=$(az aks show \
    --name "${AKS_CLUSTER_NAME}" \
    --resource-group "${AZURE_RESOURCE_GROUP}" \
    --query "powerState.code" -o tsv 2>/dev/null || echo "Unknown")
  echo -e "  AKS クラスター  : ${BOLD}${aks_state}${NC}"

  # PostgreSQL
  echo "  PostgreSQL サーバー:"
  for server in "${POSTGRES_SERVERS[@]}"; do
    local state
    state=$(az postgres flexible-server show \
      --name "${server}" \
      --resource-group "${AZURE_RESOURCE_GROUP}" \
      --query "state" -o tsv 2>/dev/null || echo "Unknown")
    echo -e "    ${server}: ${BOLD}${state}${NC}"
  done
}

# =============================================================================
# 確認プロンプト
# =============================================================================
confirm_stop() {
  echo ""
  echo -e "${YELLOW}╔══════════════════════════════════════════════════════════╗${NC}"
  echo -e "${YELLOW}║            ⚠️  SkiShop 全サービス停止                     ║${NC}"
  echo -e "${YELLOW}╚══════════════════════════════════════════════════════════╝${NC}"
  echo ""
  echo "  停止対象:"
  echo "    🔷 AKS クラスター: ${AKS_CLUSTER_NAME}"
  echo "    🐘 PostgreSQL × ${#POSTGRES_SERVERS[@]} サーバー"
  echo ""
  echo "  停止後の推定コスト削減:"
  echo -e "    AKS ノード VM    : ${GREEN}-\$${COST_AKS_MONTHLY}/月${NC}  (B2s×2 + D4as_v5×3)"
  echo -e "    PostgreSQL 計算  : ${GREEN}-\$${COST_POSTGRES_MONTHLY}/月${NC}"
  echo -e "    -------------------------"
  local total=$((COST_AKS_MONTHLY + COST_POSTGRES_MONTHLY))
  echo -e "    削減合計         : ${GREEN}約 -\$${total}/月${NC}"
  echo ""
  echo "  ⚠️  注意事項:"
  echo "    - 全てのユーザーリクエストが失敗します"
  echo "    - PostgreSQL は7日後に Azure が自動再起動します"
  echo "    - 再起動には azure-start.sh を実行してください"
  echo ""
  read -r -p "  停止を実行しますか？ (yes/N): " answer
  if [[ "${answer}" != "yes" ]]; then
    echo "キャンセルしました。"
    exit 0
  fi
}

# =============================================================================
# AKS クラスターの停止
# =============================================================================
stop_aks_cluster() {
  log_step "AKS クラスターを停止"

  local current_state
  current_state=$(az aks show \
    --name "${AKS_CLUSTER_NAME}" \
    --resource-group "${AZURE_RESOURCE_GROUP}" \
    --query "powerState.code" -o tsv 2>/dev/null || echo "Unknown")

  if [[ "${current_state}" == "Stopped" ]]; then
    log_warn "AKS クラスターはすでに停止済みです (${AKS_CLUSTER_NAME})"
    return 0
  fi

  log_info "AKS クラスター停止を開始します: ${AKS_CLUSTER_NAME}"
  az aks stop \
    --name "${AKS_CLUSTER_NAME}" \
    --resource-group "${AZURE_RESOURCE_GROUP}" \
    --no-wait

  log_info "AKS クラスターの停止完了を待機中 (最大 ${AKS_STOP_TIMEOUT}秒)..."
  local elapsed=0
  while [[ $elapsed -lt $AKS_STOP_TIMEOUT ]]; do
    local state
    state=$(az aks show \
      --name "${AKS_CLUSTER_NAME}" \
      --resource-group "${AZURE_RESOURCE_GROUP}" \
      --query "powerState.code" -o tsv 2>/dev/null || echo "Unknown")

    if [[ "${state}" == "Stopped" ]]; then
      echo "" # \r 上書き行をクリア
      log_success "AKS クラスター停止完了: ${AKS_CLUSTER_NAME}"
      return 0
    fi

    echo -ne "  状態: ${state} ... (${elapsed}秒経過)\r"
    sleep "${POLL_INTERVAL}"
    elapsed=$((elapsed + POLL_INTERVAL))
  done

  echo "" # \r 上書き行をクリア
  log_warn "AKS クラスターの停止がタイムアウトしました（バックグラウンドで継続中の可能性があります）"
  log_warn "確認: az aks show -n ${AKS_CLUSTER_NAME} -g ${AZURE_RESOURCE_GROUP} --query powerState.code -o tsv"
}

# =============================================================================
# PostgreSQL サーバーの停止（並行実行）
# =============================================================================
stop_postgres_servers() {
  log_step "PostgreSQL Flexible Servers を停止"

  local pids=()
  local stopped_servers=()
  local already_stopped=()

  for server in "${POSTGRES_SERVERS[@]}"; do
    local state
    state=$(az postgres flexible-server show \
      --name "${server}" \
      --resource-group "${AZURE_RESOURCE_GROUP}" \
      --query "state" -o tsv 2>/dev/null || echo "Unknown")

    if [[ "${state}" == "Stopped" ]]; then
      log_warn "  ${server}: すでに停止済み (スキップ)"
      already_stopped+=("${server}")
      continue
    fi

    log_info "  ${server}: 停止リクエスト送信 (バックグラウンド)"
    (
      if az postgres flexible-server stop \
        --name "${server}" \
        --resource-group "${AZURE_RESOURCE_GROUP}" \
        --output none 2>/dev/null; then
        echo "OK:${server}"
      else
        echo "FAIL:${server}"
      fi
    ) &
    pids+=($!)
    stopped_servers+=("${server}")
  done

  # 全バックグラウンドジョブの完了を待機
  if [[ ${#pids[@]} -gt 0 ]]; then
    log_info "  全サーバーの停止完了を待機中..."
    for pid in "${pids[@]}"; do
      wait "${pid}" || true
    done
  fi

  # 停止結果の確認
  log_info "  停止結果を確認中..."
  local all_ok=true
  for server in "${stopped_servers[@]}"; do
    local final_state
    final_state=$(az postgres flexible-server show \
      --name "${server}" \
      --resource-group "${AZURE_RESOURCE_GROUP}" \
      --query "state" -o tsv 2>/dev/null || echo "Unknown")

    if [[ "${final_state}" == "Stopped" ]]; then
      log_success "  ${server}: 停止完了"
    else
      log_warn "  ${server}: 状態 = ${final_state} (停止処理中の場合があります)"
      all_ok=false
    fi
  done

  if ! $all_ok; then
    log_warn "  一部のサーバーがまだ停止処理中です。確認コマンド:"
    log_warn "  az postgres flexible-server list -g ${AZURE_RESOURCE_GROUP} --query '[].{name:name,state:state}' -o table"
  fi

  if [[ ${#already_stopped[@]} -gt 0 ]]; then
    log_info "  停止済みでスキップしたサーバー: ${#already_stopped[@]} 台"
  fi
}

# =============================================================================
# 停止完了サマリー
# =============================================================================
show_stop_summary() {
  echo ""
  echo -e "${GREEN}╔══════════════════════════════════════════════════════════╗${NC}"
  echo -e "${GREEN}║              ✅ 全サービス停止完了                         ║${NC}"
  echo -e "${GREEN}╚══════════════════════════════════════════════════════════╝${NC}"
  echo ""
  echo "  停止済みリソース:"
  echo "    🔷 AKS クラスター: ${AKS_CLUSTER_NAME}"
  echo "    🐘 PostgreSQL × ${#POSTGRES_SERVERS[@]} サーバー"
  echo ""
  echo "  常時稼働中のリソース（停止不可）:"
  echo "    🌐 Cosmos DB     : ${COSMOS_DB_ACCOUNT}"
  echo "    📨 Event Hubs    : ${EVENTHUB_NAMESPACE}"
  echo ""
  echo "  再起動する場合:"
  echo "    ./scripts/azure-start.sh"
  echo ""
  echo -e "  ⚠️  ${YELLOW}PostgreSQL は7日後に Azure が自動再起動します（Azure の仕様）${NC}"
  echo ""
}

# =============================================================================
# メイン処理
# =============================================================================
main() {
  local force=false
  for arg in "$@"; do
    if [[ "${arg}" == "--force" || "${arg}" == "-f" ]]; then
      force=true
    fi
  done

  echo ""
  echo -e "${BOLD}╔══════════════════════════════════════════════════════════╗${NC}"
  echo -e "${BOLD}║          🛑 SkiShop Azure 全サービス停止スクリプト          ║${NC}"
  echo -e "${BOLD}╚══════════════════════════════════════════════════════════╝${NC}"
  echo ""

  preflight_check
  show_current_status

  if [[ "${force}" != "true" ]]; then
    confirm_stop
  else
    log_warn "--force フラグが指定されました。確認なしで停止を実行します"
  fi

  local start_time
  start_time=$(date +%s)

  # 1. AKS を先に停止（Pod が DB 接続を切断）
  stop_aks_cluster

  # 2. PostgreSQL サーバーを停止（並行実行）
  stop_postgres_servers

  local end_time
  end_time=$(date +%s)
  local elapsed=$((end_time - start_time))

  show_stop_summary
  log_info "所要時間: ${elapsed}秒"
}

main "$@"
