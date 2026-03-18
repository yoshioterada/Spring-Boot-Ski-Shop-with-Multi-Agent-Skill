# Task: Coupon Service の実装

## 概要
スキーショップのクーポン管理マイクロサービスを実装してください。
クーポン発行・検証・キャンペーン管理を提供します。

## 参照元
https://github.com/yoshioterada/GitHub-Copilot-Agent-Workshop-with-Enterprise-Microservices の `coupon-service` を参考に、Spring Boot 4.1 + Java 25 で再実装します。

## モジュール構成
`coupon-service/` ディレクトリ配下に Spring Boot アプリケーションを作成。
パッケージ: `com.example.skishop.coupon`

## 要件

### エンティティ (PostgreSQL)
- `Coupon`: id, code, description, discountType(PERCENTAGE/FIXED), discountValue, minOrderAmount, maxDiscountAmount, startDate, endDate, usageLimit, usageCount, isActive
- `CouponType`: id, name, description, usageLimitationType
- `CouponUsage`: id, couponId, userId, orderId, usedAt, discountAmount
- `Campaign`: id, name, description, startDate, endDate, status, budget, targetAudience
- `CouponRestriction`: id, couponId, restrictionType(PRODUCT/CATEGORY/USER), restrictionValue, isExclusion

### REST API エンドポイント
#### 一般ユーザー向け
- `POST /api/v1/coupons/validate` - クーポンコード検証
- `POST /api/v1/coupons/apply` - クーポン適用
- `GET /api/v1/coupons/available` - 利用可能クーポン一覧

#### 管理者向け
- `POST /api/v1/coupons` - クーポン作成
- `GET /api/v1/coupons` - クーポン一覧取得 (フィルタ/ページング)
- `GET /api/v1/coupons/{id}` - クーポン詳細取得
- `PUT /api/v1/coupons/{id}` - クーポン更新
- `DELETE /api/v1/coupons/{id}` - クーポン無効化
- `POST /api/v1/campaigns` - キャンペーン作成
- `GET /api/v1/campaigns` - キャンペーン一覧
- `PUT /api/v1/campaigns/{id}` - キャンペーン更新
- `GET /api/v1/coupons/{id}/usage` - クーポン使用統計

### ビジネスロジック
- 検証ルールのチェーン: 有効期間確認 → 使用回数確認 → 最低注文金額確認 → 商品/カテゴリ制限確認
- 割引タイプ: PERCENTAGE (最大割引額あり), FIXED (固定額割引)
- クーポンコードはユニークで推測困難な文字列を自動生成

### Kafka イベント (Consumer)
- `OrderCompleted` → クーポン使用確定

### Kafka イベント (Producer)
- `CouponApplied` - クーポン適用時
- `CouponExpired` - クーポン期限切れ時

## 品質要件
- `.github/instructions/java-coding-standards.instructions.md` に準拠
- `.github/instructions/api-design.instructions.md` に準拠
- バリデーションはルールエンジンパターンで拡張可能に設計
- 全パブリックメソッドの単体テスト必須
