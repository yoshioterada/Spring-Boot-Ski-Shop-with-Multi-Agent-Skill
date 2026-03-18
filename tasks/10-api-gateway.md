# Task: API Gateway の実装

## 概要
スキーショップの API ゲートウェイを実装してください。
全マイクロサービスへの統一エントリーポイントを提供します。

## 参照元
https://github.com/yoshioterada/GitHub-Copilot-Agent-Workshop-with-Enterprise-Microservices の `api-gateway` を参考に、Spring Boot 4.1 + Java 25 で再実装します。

## モジュール構成
`api-gateway/` ディレクトリ配下に Spring Boot アプリケーションを作成。
パッケージ: `com.example.skishop.gateway`

## 要件

### ルーティング設定
- `user-management-service`: `/api/v1/users/**`, `/api/admin/users/**` → `http://user-management-service:8081`
- `inventory-management-service`: `/api/v1/products/**`, `/api/v1/categories/**`, `/api/v1/inventory/**` → `http://inventory-management-service:8082`
- `sales-management-service`: `/api/v1/orders/**`, `/api/v1/reports/**`, `/api/admin/orders/**` → `http://sales-management-service:8083`
- `payment-cart-service`: `/api/v1/cart/**`, `/api/v1/payments/**` → `http://payment-cart-service:8084`
- `point-service`: `/api/v1/points/**`, `/api/admin/points/**` → `http://point-service:8085`
- `coupon-service`: `/api/v1/coupons/**`, `/api/v1/campaigns/**` → `http://coupon-service:8086`
- `ai-support-service`: `/api/v1/ai/**`, `/api/v1/recommendations/**` → `http://ai-support-service:8087`
- `authentication-service`: `/api/v1/auth/**` → `http://authentication-service:8088`

### 機能
- Spring Cloud Gateway を使用
- JWT トークン検証フィルタ (認証サービスと連携)
- レート制限 (Redis ベース): 一般ユーザー 60 req/min, API キーユーザー 300 req/min
- CORS 設定
- リクエスト/レスポンスのログ記録
- サーキットブレーカー (Resilience4j)
- ヘルスチェックエンドポイント (/actuator/health)
- リクエストID の生成と伝播 (X-Request-ID)

### セキュリティ
- 管理者 API (`/api/admin/**`) は ADMIN ロール必須
- 認証不要エンドポイント: `/api/v1/auth/login`, `/api/v1/auth/register`, `/api/v1/products` (GET), `/api/v1/categories` (GET)
- セキュリティヘッダーの付加 (X-Content-Type-Options, X-Frame-Options, etc.)

### 設定ファイル
- `application.yml` にルーティング設定
- dev/prod プロファイル分離

## 品質要件
- `.github/instructions/spring-config.instructions.md` に準拠
- `.github/instructions/security-coding.instructions.md` を厳守
- 全パブリックメソッドの単体テスト必須
