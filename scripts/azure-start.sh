#!/usr/bin/env bash
# =============================================================================
# SkiShop Azure 全サービス起動スクリプト
#
# 起動順序:
#   1. PostgreSQL Flexible Server × 7 を並行起動し、全台 Ready になるまで待機
#   2. AKS クラスターを起動し、Running になるまで待機
#   3. kubeconfig を更新
#   4. 全 Pod が Ready になるまで待機
#   5. ヘルスチェックサマリー表示
#
# 使用方法:
#   ./scripts/azure-start.sh
#   ./scripts/azure-start.sh --skip-pod-wait   # Pod 起動待機をスキップ
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

  if ! command -v az &>/dev/null; then
    log_error "Azure CLI (az) がインストールされていません"
    exit 1
  fi

  if ! az account show &>/dev/null; then
    log_error "Azure にログインしていません。'az login' を実行してください"
    exit 1
  fi

  if ! command -v kubectl &>/dev/null; then
    log_error "kubectl がインストールされていません"
    log_error "インストール: brew install kubectl"
    exit 1
  fi

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

  local aks_state
  aks_state=$(az aks show \
    --name "${AKS_CLUSTER_NAME}" \
    --resource-group "${AZURE_RESOURCE_GROUP}" \
    --query "powerState.code" -o tsv 2>/dev/null || echo "Unknown")
  echo -e "  AKS クラスター  : ${BOLD}${aks_state}${NC}"

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
# PostgreSQL サーバーの起動（並行実行）
# =============================================================================
start_postgres_servers() {
  log_step "PostgreSQL Flexible Servers を起動"

  local servers_to_start=()

  # 既に起動済みのサーバーをチェック
  for server in "${POSTGRES_SERVERS[@]}"; do
    local state
    state=$(az postgres flexible-server show \
      --name "${server}" \
      --resource-group "${AZURE_RESOURCE_GROUP}" \
      --query "state" -o tsv 2>/dev/null || echo "Unknown")

    if [[ "${state}" == "Ready" ]]; then
      log_warn "  ${server}: すでに起動済み (スキップ)"
    else
      log_info "  ${server}: 起動リクエスト送信中 (状態: ${state})"
      servers_to_start+=("${server}")
    fi
  done

  if [[ ${#servers_to_start[@]} -eq 0 ]]; then
    log_success "全 PostgreSQL サーバーは起動済みです"
    return 0
  fi

  # 並行して起動リクエストを送信
  local pids=()
  for server in "${servers_to_start[@]}"; do
    (
      az postgres flexible-server start \
        --name "${server}" \
        --resource-group "${AZURE_RESOURCE_GROUP}" \
        --output none 2>/dev/null && echo "STARTED:${server}" || echo "FAILED:${server}"
    ) &
    pids+=($!)
  done

  log_info "  全サーバーの起動完了を待機中 (最大 ${POSTGRES_START_TIMEOUT}秒)..."

  # バックグラウンドジョブの完了を待つ
  for pid in "${pids[@]}"; do
    wait "${pid}" || true
  done

  # 全サーバーが Ready になるまでポーリング
  local elapsed=0
  while [[ $elapsed -lt $POSTGRES_START_TIMEOUT ]]; do
    local all_ready=true
    local not_ready_count=0

    for server in "${servers_to_start[@]}"; do
      local state
      state=$(az postgres flexible-server show \
        --name "${server}" \
        --resource-group "${AZURE_RESOURCE_GROUP}" \
        --query "state" -o tsv 2>/dev/null || echo "Unknown")

      if [[ "${state}" != "Ready" ]]; then
        all_ready=false
        not_ready_count=$((not_ready_count + 1))
      fi
    done

    if $all_ready; then
      break
    fi

    echo -ne "  待機中: ${not_ready_count}台が未起動 ... (${elapsed}秒経過)\r"
    sleep "${POLL_INTERVAL}"
    elapsed=$((elapsed + POLL_INTERVAL))
  done

  echo "" # 改行

  # 最終状態確認
  local success_count=0
  local fail_count=0
  for server in "${servers_to_start[@]}"; do
    local final_state
    final_state=$(az postgres flexible-server show \
      --name "${server}" \
      --resource-group "${AZURE_RESOURCE_GROUP}" \
      --query "state" -o tsv 2>/dev/null || echo "Unknown")

    if [[ "${final_state}" == "Ready" ]]; then
      log_success "  ${server}: Ready ✓"
      success_count=$((success_count + 1))
    else
      log_error "  ${server}: ${final_state} (起動失敗またはタイムアウト)"
      fail_count=$((fail_count + 1))
    fi
  done

  if [[ $fail_count -gt 0 ]]; then
    log_error "${fail_count} 台の PostgreSQL サーバーが起動できませんでした"
    log_error "手動確認: az postgres flexible-server list -g ${AZURE_RESOURCE_GROUP} --query '[].{name:name,state:state}' -o table"
    return 1
  fi

  log_success "全 PostgreSQL サーバー (${success_count}台) の起動完了"
}

# =============================================================================
# AKS クラスターの起動
# =============================================================================
start_aks_cluster() {
  log_step "AKS クラスターを起動"

  local current_state
  current_state=$(az aks show \
    --name "${AKS_CLUSTER_NAME}" \
    --resource-group "${AZURE_RESOURCE_GROUP}" \
    --query "powerState.code" -o tsv 2>/dev/null || echo "Unknown")

  if [[ "${current_state}" == "Running" ]]; then
    log_warn "AKS クラスターはすでに起動済みです (${AKS_CLUSTER_NAME})"
    return 0
  fi

  log_info "AKS クラスター起動を開始します: ${AKS_CLUSTER_NAME}"
  az aks start \
    --name "${AKS_CLUSTER_NAME}" \
    --resource-group "${AZURE_RESOURCE_GROUP}" \
    --no-wait

  log_info "AKS クラスターの起動完了を待機中 (最大 ${AKS_START_TIMEOUT}秒)..."
  local elapsed=0
  while [[ $elapsed -lt $AKS_START_TIMEOUT ]]; do
    local state
    state=$(az aks show \
      --name "${AKS_CLUSTER_NAME}" \
      --resource-group "${AZURE_RESOURCE_GROUP}" \
      --query "powerState.code" -o tsv 2>/dev/null || echo "Unknown")

    if [[ "${state}" == "Running" ]]; then
      echo "" # \r 上書き行をクリア
      log_success "AKS クラスター起動完了: ${AKS_CLUSTER_NAME}"
      return 0
    fi

    echo -ne "  状態: ${state} ... (${elapsed}秒経過)\r"
    sleep "${POLL_INTERVAL}"
    elapsed=$((elapsed + POLL_INTERVAL))
  done

  echo "" # \r 上書き行をクリア
  log_error "AKS クラスターの起動がタイムアウトしました"
  log_error "手動確認: az aks show -n ${AKS_CLUSTER_NAME} -g ${AZURE_RESOURCE_GROUP} --query powerState.code -o tsv"
  return 1
}

# =============================================================================
# kubeconfig の更新
# =============================================================================
update_kubeconfig() {
  log_step "kubeconfig を更新"

  az aks get-credentials \
    --name "${AKS_CLUSTER_NAME}" \
    --resource-group "${AZURE_RESOURCE_GROUP}" \
    --overwrite-existing \
    --output none

  log_success "kubeconfig 更新完了"
  log_info   "  コンテキスト: $(kubectl config current-context)"
}

# =============================================================================
# Pod の起動完了待機
# =============================================================================
wait_for_pods_ready() {
  log_step "Pod の起動完了を待機 (Namespace: ${AKS_NAMESPACE})"

  log_info "全 Pod が Running になるまで待機中 (最大 ${PODS_READY_TIMEOUT}秒)..."
  local elapsed=0

  while [[ $elapsed -lt $PODS_READY_TIMEOUT ]]; do
    # kubectl を1回だけ呼び出し結果を再利用（3回呼び出しによる不整合を防止）
    local pod_status
    pod_status=$(kubectl get pods -n "${AKS_NAMESPACE}" --no-headers 2>/dev/null || true)

    local total
    total=$(echo "${pod_status}" | grep -c '[^[:space:]]' || echo "0")
    local running
    running=$(echo "${pod_status}" | grep -c "Running" || echo "0")
    # Completed / Succeeded は正常終了のため「未起動」に含めない
    local not_ready
    not_ready=$(echo "${pod_status}" | grep -v "Running\|Completed\|Succeeded" | grep -c '[^[:space:]]' || echo "0")

    if [[ "${not_ready}" -eq 0 && "${total}" -gt 0 ]]; then
      echo "" # \r 上書き行をクリア
      log_success "全 ${total} Pod が起動済みです"
      return 0
    fi

    echo -ne "  Pod状態: ${running}/${total} Running, ${not_ready} 待機中 ... (${elapsed}秒)\r"
    sleep "${POLL_INTERVAL}"
    elapsed=$((elapsed + POLL_INTERVAL))
  done

  echo "" # \r 上書き行をクリア
  log_warn "一部の Pod がタイムアウトまでに Ready になりませんでした"
  log_warn "現在の Pod 状態を表示します:"
  kubectl get pods -n "${AKS_NAMESPACE}" 2>/dev/null || true
}

# =============================================================================
# Pod 状態サマリー表示
# =============================================================================
show_pod_summary() {
  log_step "Pod 状態サマリー"

  kubectl get pods -n "${AKS_NAMESPACE}" \
    --sort-by=.metadata.name \
    2>/dev/null || log_warn "kubectl で Pod 状態を取得できませんでした"

  echo ""
  local external_ip
  external_ip=$(kubectl get service ingress-nginx-controller \
    -n ingress-nginx \
    --template='{{range .status.loadBalancer.ingress}}{{.ip}}{{end}}' \
    2>/dev/null || echo "取得中...")
  if [[ -n "${external_ip}" && "${external_ip}" != "取得中..." ]]; then
    echo -e "  🌐 外部 IP (nginx Ingress): ${BOLD}${external_ip}${NC}"
  fi
}

# =============================================================================
# 起動完了サマリー
# =============================================================================
show_start_summary() {
  local elapsed_sec="$1"

  # Ingress の外部 IP を取得（show_pod_summary をスキップした場合にも表示するため）
  local external_ip
  external_ip=$(kubectl get service ingress-nginx-controller \
    -n ingress-nginx \
    --template='{{range .status.loadBalancer.ingress}}{{.ip}}{{end}}' \
    2>/dev/null || echo "")

  echo ""
  echo -e "${GREEN}╔══════════════════════════════════════════════════════════╗${NC}"
  echo -e "${GREEN}║              ✅ 全サービス起動完了                         ║${NC}"
  echo -e "${GREEN}╚══════════════════════════════════════════════════════════╝${NC}"
  echo ""
  echo "  起動済みリソース:"
  echo "    🐘 PostgreSQL × ${#POSTGRES_SERVERS[@]} サーバー"
  echo "    🔷 AKS クラスター: ${AKS_CLUSTER_NAME}"
  echo ""
  echo "  所要時間: ${elapsed_sec}秒"
  echo ""
  echo "  利用方法:"
  if [[ -n "${external_ip}" ]]; then
    echo -e "    ブラウザ: ${BOLD}http://${external_ip}/${NC}"
    echo -e "    API     : ${BOLD}http://${external_ip}/api/v1/${NC}"
  else
    echo "    ブラウザ: http://<外部IP>/"
    echo "    API     : http://<外部IP>/api/v1/"
    echo "    ※ IP確認: kubectl get svc ingress-nginx-controller -n ingress-nginx"
  fi
  echo ""
  echo "  停止する場合:"
  echo "    ./scripts/azure-stop.sh"
  echo ""
}

# =============================================================================
# メイン処理
# =============================================================================
main() {
  local skip_pod_wait=false
  for arg in "$@"; do
    if [[ "${arg}" == "--skip-pod-wait" ]]; then
      skip_pod_wait=true
    fi
  done

  echo ""
  echo -e "${BOLD}╔══════════════════════════════════════════════════════════╗${NC}"
  echo -e "${BOLD}║          🚀 SkiShop Azure 全サービス起動スクリプト          ║${NC}"
  echo -e "${BOLD}╚══════════════════════════════════════════════════════════╝${NC}"
  echo ""

  local start_time
  start_time=$(date +%s)

  preflight_check
  show_current_status

  # 1. PostgreSQL を先に起動（AKS Pod が DB 接続するため）
  start_postgres_servers

  # 2. AKS クラスターを起動
  start_aks_cluster

  # 3. kubeconfig を更新
  update_kubeconfig

  # 4. Pod の起動完了を待機
  if [[ "${skip_pod_wait}" != "true" ]]; then
    wait_for_pods_ready
    show_pod_summary
  fi

  local end_time
  end_time=$(date +%s)
  local elapsed=$((end_time - start_time))

  show_start_summary "${elapsed}"
}

main "$@"
