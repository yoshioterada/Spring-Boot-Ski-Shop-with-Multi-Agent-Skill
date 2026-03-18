# Task: Point Service の実装

## 概要
スキーショップのポイント管理マイクロサービスを実装してください。
ポイント付与・消費・有効期限管理・キャンペーン管理を提供します。

## 参照元
https://github.com/yoshioterada/GitHub-Copilot-Agent-Workshop-with-Enterprise-Microservices の `point-service` を参考に、Spring Boot 4.1 + Java 25 で再実装します。

## モジュール構成
`point-service/` ディレクトリ配下に Spring Boot アプリケーションを作成。
パッケージ: `com.example.skishop.point`

## 要件

### エンティティ (PostgreSQL)
- `PointAccount`: id, userId, balance, lifetimePoints, lastUpdatedAt
- `PointTransaction`: id, accountId, amount, type(EARN/REDEEM/EXPIRE/ADJUST), sourceType, sourceId, description, transactionDate, expiryDate
- `PointRule`: id, name, description, conversionRate, minimumAmount, applicableProducts, isActive
- `PointExpiry`: id, accountId, amount, earnedDate, expiryDate, status(ACTIVE/EXPIRED/USED)
- `PointCampaign`: id, name, description, multiplier, startDate, endDate, targetProducts, isActive

### REST API エンドポイント
#### 一般ユーザー向け
- `GET /api/v1/points/balance` - ポイント残高確認
- `GET /api/v1/points/history` - ポイント履歴取得 (ページング)
- `POST /api/v1/points/redeem` - ポイント使用
- `GET /api/v1/points/expiring` - 期限切れ間近のポイント確認

#### 管理者向け
- `POST /api/v1/points/earn` - ポイント付与 (注文完了時 Kafka 経由)
- `POST /api/v1/points/adjust` - ポイント調整
- `GET /api/admin/points/campaigns` - キャンペーン一覧
- `POST /api/admin/points/campaigns` - キャンペーン作成
- `PUT /api/admin/points/campaigns/{id}` - キャンペーン更新
- `GET /api/admin/points/rules` - ルール一覧
- `POST /api/admin/points/rules` - ルール作成

### ビジネスロジック
- ポイント付与: 注文金額 × 付与率 (デフォルト 1%)
- 会員ランクによる倍率: SILVER(1.5x), GOLD(2x), PLATINUM(3x)
- ポイント有効期限: 付与日から 1 年
- 有効期限切れポイントの自動失効 (バッチ処理)
- キャンペーン期間のボーナスポイント

### Kafka イベント (Consumer)
- `OrderCompleted` → ポイント自動付与
- `PaymentRefunded` → ポイント返還

### Kafka イベント (Producer)
- `PointsEarned` - ポイント付与通知
- `PointsRedeemed` - ポイント使用通知
- `PointsExpired` - ポイント失効通知

## 品質要件
- `.github/instructions/java-coding-standards.instructions.md` に準拠
- `.github/instructions/api-design.instructions.md` に準拠
- ポイント操作はトランザクション内で処理
- 全パブリックメソッドの単体テスト必須
