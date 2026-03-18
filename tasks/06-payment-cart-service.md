# Task: Payment & Cart Service の実装

## 概要
スキーショップの支払い・カート管理マイクロサービスを実装してください。
ショッピングカート管理と支払い処理を提供します。

## 参照元
https://github.com/yoshioterada/GitHub-Copilot-Agent-Workshop-with-Enterprise-Microservices の `payment-cart-service` を参考に、Spring Boot 4.1 + Java 25 で再実装します。

## モジュール構成
`payment-cart-service/` ディレクトリ配下に Spring Boot アプリケーションを作成。
パッケージ: `com.example.skishop.paymentcart`

## 要件

### エンティティ (PostgreSQL + Redis)
- `Cart` (Redis): id, userId, sessionId, items, createdAt, updatedAt, expiredAt, status
- `CartItem` (Redis): id, cartId, productId, productName, quantity, unitPrice, selectedAttributes
- `Payment` (PostgreSQL): id, orderId, amount, currency, method, status, gatewayReference, transactionDate
- `PaymentMethod` (PostgreSQL): id, userId, type, provider, accountReference, isDefault, expiryDate
- `Transaction` (PostgreSQL): id, paymentId, type, amount, status, gatewayResponse, createdAt

### REST API エンドポイント
#### カート操作
- `GET /api/v1/cart` - カート内容取得
- `POST /api/v1/cart/items` - カートに商品追加
- `PUT /api/v1/cart/items/{itemId}` - カート内商品数量更新
- `DELETE /api/v1/cart/items/{itemId}` - カートから商品削除
- `DELETE /api/v1/cart` - カートクリア
- `GET /api/v1/cart/summary` - カートサマリー (小計, 税, 送料計算)

#### 支払い操作
- `POST /api/v1/payments` - 支払い処理実行
- `GET /api/v1/payments/{id}` - 支払い詳細取得
- `POST /api/v1/payments/{id}/refund` - 返金処理
- `GET /api/v1/payments/methods` - 支払い方法一覧取得
- `POST /api/v1/payments/methods` - 支払い方法追加

### カート管理
- Redis でカート情報を管理 (TTL: 7日)
- カート→注文変換時にアトミックな操作を保証
- 価格計算: 小計 + 税金 + 送料 - クーポン割引

### Kafka イベント
- `PaymentCompleted` - 支払い完了時 (Sales Service に通知)
- `PaymentFailed` - 支払い失敗時
- `CartAbandoned` - カート放棄検知時

## 品質要件
- `.github/instructions/java-coding-standards.instructions.md` に準拠
- `.github/instructions/api-design.instructions.md` に準拠
- `.github/instructions/security-coding.instructions.md` を厳守 (PCI DSS を意識)
- Redis セッション管理の適切な実装
- 支払いに関する機密データのマスキング
- 全パブリックメソッドの単体テスト必須
