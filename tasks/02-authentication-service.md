# Task: Authentication Service の実装

## 概要
スキーショップのマイクロサービス群における認証サービスを実装してください。
OAuth 2.0 / JWT ベースの認証・認可を提供するサービスです。

## 参照元
https://github.com/yoshioterada/GitHub-Copilot-Agent-Workshop-with-Enterprise-Microservices の `authentication-service` を参考に、Spring Boot 4.1 + Java 25 で再実装します。

## モジュール構成
`authentication-service/` ディレクトリ配下に Spring Boot アプリケーションを作成。
パッケージ: `com.example.skishop.authentication`

## 要件

### エンティティ (PostgreSQL)
- `OAuthClient`: id, clientId, clientSecret, name, description, redirectUris, allowedGrantTypes, scopes
- `OAuthToken`: id, accessToken, refreshToken, clientId, userId, scopes, issuedAt, expiresAt
- `OAuthScope`: id, name, description, isDefault
- `LoginAttempt`: id, userId, timestamp, ipAddress, userAgent, isSuccess, failureReason

### REST API エンドポイント
- `POST /api/v1/auth/login` - ユーザーログイン（JWT トークン発行）
- `POST /api/v1/auth/register` - 新規ユーザー登録
- `POST /api/v1/auth/refresh` - トークンリフレッシュ
- `POST /api/v1/auth/logout` - ログアウト（トークン無効化）
- `GET /api/v1/auth/validate` - トークン検証
- `GET /api/v1/auth/me` - 現在のユーザー情報取得

### セキュリティ要件
- JWT (RS256) によるトークン生成・検証
- リフレッシュトークンの安全な管理 (Redis)
- パスワードは BCrypt でハッシュ化
- ログイン失敗回数の追跡とアカウントロック
- OWASP Top 10 を意識した実装

### 設定ファイル
- `application.yml` (dev/prod プロファイル分離)
- JWT 秘密鍵は環境変数で管理（ハードコード禁止）

## 品質要件
- `.github/instructions/security-coding.instructions.md` を厳守
- `.github/instructions/api-design.instructions.md` に準拠
- 全パブリックメソッドの単体テスト必須
- 分岐カバレッジ 80% 以上目標
- DTO にはレコードクラスを使用
