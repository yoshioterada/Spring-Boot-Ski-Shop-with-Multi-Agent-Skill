# プロジェクト共通ルール

## 技術スタック

- **言語**: Java 25
- **フレームワーク**: Spring Boot 4.1, Spring AI 2.0
- **ビルドツール**: Maven
- **パッケージ構成**: `com.example.<プロジェクト名>` 配下に `controller`, `service`, `repository`, `model`, `config` を配置

## コーディング規約

- **命名規則**: クラス名は PascalCase、メソッド・変数は camelCase、定数は UPPER_SNAKE_CASE
- **パッケージ構成**: レイヤードアーキテクチャに従い、controller → service → repository の依存方向を厳守
- **Java 25 機能の活用**: レコードクラス、パターンマッチング、シールドクラス等を適切に利用

## セキュリティ最低基準

- **OWASP Top 10** を常に意識し、特に以下を徹底:
  - 入力検証: 全ての外部入力に対してバリデーションを実施（`@Valid`, `@NotNull`, `@Size` 等）
  - SQLインジェクション防止: パラメータバインドを必須とし、文字列結合による SQL 構築を禁止
  - XSS 防止: 出力時のエスケープを徹底
  - 認証・認可: 適切な `@PreAuthorize` / `@Secured` を設定
- **秘密情報の管理**: API キー、パスワード、トークン等のハードコードを禁止。環境変数または外部シークレット管理サービスを使用

## テストカバレッジ目標

- **分岐カバレッジ**: 80% 以上
- **全パブリックメソッド**: 単体テスト必須
- **異常系テスト**: 正常系と同等以上のテストケースを作成

## コミットメッセージ規約

- **形式**: `<type>(<scope>): <summary>` (Conventional Commits 準拠)
- **type**: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `ci`
- **summary**: 日本語も可、50 文字以内
- **例**: `feat(auth): ログイン機能を追加`, `fix(api): NullPointerException を修正`

## 禁止事項

- ハードコードされた秘密情報（API キー、パスワード、トークン、接続文字列）
- 未検証の外部入力をそのまま処理に使用すること
- `catch (Exception e) {}` のような例外の握りつぶし
- `System.out.println` によるログ出力（SLF4J/Logback を使用）
- SNAPSHOT バージョンの依存関係を本番ブランチに含めること
- テストなしでのコードマージ
