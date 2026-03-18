# Task: User Management Service の実装

## 概要
スキーショップのユーザー管理マイクロサービスを実装してください。
RBAC (Role-Based Access Control) を持つユーザー管理サービスです。

## 参照元
https://github.com/yoshioterada/GitHub-Copilot-Agent-Workshop-with-Enterprise-Microservices の `user-management-service` を参考に、Spring Boot 4.1 + Java 25 で再実装します。

## モジュール構成
`user-management-service/` ディレクトリ配下に Spring Boot アプリケーションを作成。
パッケージ: `com.example.skishop.usermanagement`

## 要件

### エンティティ (PostgreSQL)
- `User`: id, email, passwordHash, firstName, lastName, phoneNumber, birthDate, createdAt, updatedAt, lastLoginAt, status
- `Role`: id, name, description, permissions
- `Permission`: id, name, description, resource, action
- `UserPreference`: id, userId, language, currency, notificationPreferences, displayPreferences
- `Address`: id, userId, addressType(SHIPPING/BILLING), recipient, zipCode, prefecture, city, streetAddress, building, phoneNumber, isDefault
- `UserActivity`: id, userId, activityType, timestamp, details, ipAddress, deviceInfo

### REST API エンドポイント
#### 一般ユーザー向け
- `GET /api/v1/users/me` - 自分のプロフィール取得
- `PUT /api/v1/users/me` - プロフィール更新
- `GET /api/v1/users/me/addresses` - 自分の住所一覧取得
- `POST /api/v1/users/me/addresses` - 住所追加
- `PUT /api/v1/users/me/addresses/{id}` - 住所更新
- `DELETE /api/v1/users/me/addresses/{id}` - 住所削除
- `GET /api/v1/users/me/preferences` - 設定取得
- `PUT /api/v1/users/me/preferences` - 設定更新

#### 管理者向け
- `GET /api/admin/users` - ユーザー一覧取得 (ページング/フィルタ)
- `GET /api/admin/users/{id}` - ユーザー詳細取得
- `PUT /api/admin/users/{id}` - ユーザー更新
- `DELETE /api/admin/users/{id}` - ユーザー削除
- `POST /api/admin/users/{id}/roles` - ロール付与
- `DELETE /api/admin/users/{id}/roles/{roleId}` - ロール剥奪
- `GET /api/admin/users/{id}/activities` - アクティビティ履歴

### RBAC ロール
- `CUSTOMER` - 一般顧客
- `PREMIUM_CUSTOMER` - プレミアム会員
- `STORE_ADMIN` - 店舗管理者
- `INVENTORY_MANAGER` - 在庫管理者
- `SALES_MANAGER` - 販売管理者
- `SYSTEM_ADMIN` - システム管理者

### Kafka イベント
- `UserCreated` - ユーザー作成時
- `UserUpdated` - ユーザー更新時
- `UserDeleted` - ユーザー削除時

## 品質要件
- `.github/instructions/java-coding-standards.instructions.md` に準拠
- `.github/instructions/api-design.instructions.md` に準拠
- `.github/instructions/security-coding.instructions.md` を厳守
- 全パブリックメソッドの単体テスト必須
- @Valid による入力バリデーション必須
- DTO にレコードクラスを使用
