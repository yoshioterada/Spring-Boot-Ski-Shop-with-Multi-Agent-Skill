# Task: インフラ構成 (Docker Compose, DB スキーマ, 監視) の作成

## 概要
スキーショップの開発環境インフラを構成してください。
Docker Compose, DB 初期化スクリプト, 監視環境を整備します。

## 作成物

### 1. `docker-compose.yml`
以下のサービスを含む Docker Compose 構成:

#### インフラサービス
- `postgres`: PostgreSQL 16 (ポート 5432)
  - 各サービス用のデータベースを初期化スクリプトで作成
- `redis`: Redis 7.2 (ポート 6379)
- `kafka` + `zookeeper`: Apache Kafka (ポート 9092)

#### アプリケーションサービス
- `api-gateway`: ポート 8080
- `authentication-service`: ポート 8088
- `user-management-service`: ポート 8081
- `inventory-management-service`: ポート 8082
- `sales-management-service`: ポート 8083
- `payment-cart-service`: ポート 8084
- `point-service`: ポート 8085
- `coupon-service`: ポート 8086
- `ai-support-service`: ポート 8087

#### 監視
- `prometheus`: ポート 9090
- `grafana`: ポート 3100

### 2. `scripts/` ディレクトリ
#### DB 初期化スクリプト
- `scripts/init-databases.sql` - 各サービス用のデータベースとユーザーを作成
- `scripts/user-management-schema.sql` - User Management Service のテーブル定義
- `scripts/inventory-schema.sql` - Inventory Management Service のテーブル定義
- `scripts/sales-schema.sql` - Sales Management Service のテーブル定義
- `scripts/payment-schema.sql` - Payment & Cart Service のテーブル定義
- `scripts/point-schema.sql` - Point Service のテーブル定義
- `scripts/coupon-schema.sql` - Coupon Service のテーブル定義
- `scripts/auth-schema.sql` - Authentication Service のテーブル定義
- `scripts/ai-support-schema.sql` - AI Support Service のテーブル定義

### 3. `monitoring/` ディレクトリ
- `monitoring/prometheus/prometheus.yml` - Prometheus 設定
- `monitoring/grafana/provisioning/` - Grafana データソース・ダッシュボード設定

### 4. 各サービスの `Dockerfile`
- マルチステージビルド
- 非 root ユーザーで実行
- ヘルスチェック設定

## 品質要件
- `.github/instructions/dockerfile-infra.instructions.md` に準拠
- `.github/instructions/sql-schema-review.instructions.md` に準拠
- 秘密情報は環境変数で管理
- ヘルスチェック設定必須
- 適切なリソース制限
