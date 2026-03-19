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
