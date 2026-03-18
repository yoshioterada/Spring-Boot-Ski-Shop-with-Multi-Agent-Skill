# Task: Sales Management Service の実装

## 概要
スキーショップの販売管理マイクロサービスを実装してください。
注文処理・出荷管理・返品処理・売上レポートを提供します。

## 参照元
https://github.com/yoshioterada/GitHub-Copilot-Agent-Workshop-with-Enterprise-Microservices の `sales-management-service` を参考に、Spring Boot 4.1 + Java 25 で再実装します。

## モジュール構成
`sales-management-service/` ディレクトリ配下に Spring Boot アプリケーションを作成。
パッケージ: `com.example.skishop.sales`

## 要件

### エンティティ (PostgreSQL)
- `Order`: id, userId, orderDate, status, subtotal, tax, shippingCost, discount, totalAmount, couponId, shippingAddressId, billingAddressId, paymentId, notes
- `OrderItem`: id, orderId, productId, productName, quantity, unitPrice, discount, tax, subtotal
- `Shipment`: id, orderId, trackingNumber, carrier, status, shippingDate, estimatedDeliveryDate, actualDeliveryDate
- `Return`: id, orderId, requestDate, status, reason, approvalDate, refundAmount
- `Invoice`: id, orderId, invoiceNumber, issuedDate, dueDate, paidDate, amount, status

### REST API エンドポイント
#### 一般ユーザー向け
- `POST /api/v1/orders` - 注文作成
- `GET /api/v1/orders` - 注文履歴取得 (ページング)
- `GET /api/v1/orders/{id}` - 注文詳細取得
- `POST /api/v1/orders/{id}/cancel` - 注文キャンセル
- `POST /api/v1/orders/{id}/returns` - 返品リクエスト
- `GET /api/v1/orders/{id}/shipment` - 出荷状況確認

#### 管理者向け
- `GET /api/admin/orders` - 全注文一覧 (フィルタ/ソート/ページング)
- `PUT /api/v1/orders/{orderId}/status` - 注文ステータス更新
- `GET /api/v1/orders/{id}/invoice` - 請求書取得
- `GET /api/v1/reports/sales` - 売上レポート (日別/週別/月別)
- `GET /api/v1/reports/sales/summary` - 売上サマリー
- `PUT /api/admin/returns/{id}/approve` - 返品承認
- `PUT /api/admin/returns/{id}/reject` - 返品却下

### Kafka イベント
- `OrderCreated` - 注文作成時 (在庫サービスに在庫引当を依頼)
- `OrderCancelled` - 注文キャンセル時
- `OrderStatusChanged` - ステータス変更時
- `ShipmentUpdated` - 出荷情報更新時

### ビジネスロジック
- 注文作成時: 在庫確認 → 支払い処理 → 注文確定 (Saga パターン)
- OrderStatus: PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED / CANCELLED / RETURNED

## 品質要件
- `.github/instructions/java-coding-standards.instructions.md` に準拠
- `.github/instructions/api-design.instructions.md` に準拠
- 注文ステータスの遷移はステートマシンパターンで管理
- 全パブリックメソッドの単体テスト必須
