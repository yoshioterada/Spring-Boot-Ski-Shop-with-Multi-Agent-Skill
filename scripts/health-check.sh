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
  "agent-runtime-monolith:8100"
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
