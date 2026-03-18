# Task: Inventory Management Service の実装

## 概要
スキーショップの在庫管理マイクロサービスを実装してください。
商品カタログ・在庫管理・カテゴリ管理・価格管理を提供します。

## 参照元
https://github.com/yoshioterada/GitHub-Copilot-Agent-Workshop-with-Enterprise-Microservices の `inventory-management-service` を参考に、Spring Boot 4.1 + Java 25 で再実装します。

## モジュール構成
`inventory-management-service/` ディレクトリ配下に Spring Boot アプリケーションを作成。
パッケージ: `com.example.skishop.inventory`

## 要件

### エンティティ (PostgreSQL)
- `Product`: id, sku, name, description, brand, categoryId, price, cost, weight, dimensions, isActive, createdAt, updatedAt
- `Category`: id, name, description, parentId, level, path, imageUrl, isActive
- `Inventory`: id, productId, stockQuantity, reservedQuantity, availableQuantity, warehouseId, reorderLevel, updatedAt
- `Supplier`: id, name, contactPerson, email, phone, address, rating
- `PriceHistory`: id, productId, price, effectiveFrom, effectiveTo, promotionId
- `ProductAttribute`: id, productId, attributeName, attributeValue, isFilterable, isSortable
- `ProductImage`: id, productId, imageUrl, altText, sortOrder, isPrimary

### REST API エンドポイント
#### 一般向け（商品閲覧）
- `GET /api/v1/products` - 商品一覧取得 (フィルタ/ソート/ページング)
- `GET /api/v1/products/{id}` - 商品詳細取得
- `GET /api/v1/products/search` - 商品検索 (キーワード, カテゴリ, 価格帯)
- `GET /api/v1/products/{id}/inventory` - 在庫状況確認
- `GET /api/v1/categories` - カテゴリ一覧取得
- `GET /api/v1/categories/{id}` - カテゴリ詳細取得
- `GET /api/v1/categories/{id}/products` - カテゴリ別商品一覧

#### 管理者向け
- `POST /api/v1/products` - 商品登録
- `PUT /api/v1/products/{id}` - 商品更新
- `DELETE /api/v1/products/{id}` - 商品削除
- `PUT /api/v1/products/{id}/inventory` - 在庫更新
- `POST /api/v1/categories` - カテゴリ登録
- `PUT /api/v1/categories/{id}` - カテゴリ更新
- `GET /api/v1/inventory/low-stock` - 在庫不足商品一覧
- `POST /api/v1/inventory/bulk-update` - 一括在庫更新

### Kafka イベント
- `InventoryUpdated` - 在庫変更時
- `ProductCreated` - 商品作成時
- `ProductUpdated` - 商品更新時
- `LowStockAlert` - 在庫が閾値以下になった時

## 品質要件
- `.github/instructions/java-coding-standards.instructions.md` に準拠
- `.github/instructions/api-design.instructions.md` に準拠
- @Valid による入力バリデーション必須
- JPA Specification パターンを使用した柔軟な検索
- 在庫更新は楽観的ロック (@Version) を使用
- 全パブリックメソッドの単体テスト必須
