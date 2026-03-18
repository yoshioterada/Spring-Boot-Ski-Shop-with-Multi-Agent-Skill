# Task: Maven Parent POM とプロジェクト基盤の作成

## 概要
スキーショップ (Ski Shop) マイクロサービスプロジェクトの Maven 親 POM と共有ライブラリモジュールを作成してください。

## 参照仕様
以下の URL のプロジェクトと同等の構成を Spring Boot 4.1 + Java 25 + Spring AI 2.0 で再実装します:
https://github.com/yoshioterada/GitHub-Copilot-Agent-Workshop-with-Enterprise-Microservices

## 技術スタック
- **Java**: 25
- **Spring Boot**: 4.1.x (最新)
- **Spring AI**: 2.0.x
- **ビルドツール**: Maven
- **DB**: PostgreSQL 16, Redis 7.2
- **メッセージング**: Apache Kafka

## 作成物

### 1. ルート `pom.xml` (Maven Parent POM)
マルチモジュールプロジェクトの親 POM を作成:
- groupId: `com.example.skishop`
- artifactId: `ski-shop-microservices`
- packaging: pom
- modules に以下を含む:
  - `shared-library`
  - `api-gateway`
  - `authentication-service`
  - `user-management-service`
  - `inventory-management-service`
  - `sales-management-service`
  - `payment-cart-service`
  - `point-service`
  - `coupon-service`
  - `ai-support-service`
- dependencyManagement で Spring Boot 4.1 BOM, Spring AI 2.0 BOM, Spring Cloud BOM を管理
- 共通プラグイン設定 (maven-compiler-plugin for Java 25, etc.)

### 2. `shared-library/` モジュール
全サービスで共有する基盤コード:
- `com.example.skishop.shared.dto`:
  - `ApiResponse<T>` レコード (status, code, message, data, meta)
  - `PageInfo` レコード (page, size, totalElements, totalPages)
  - `ErrorResponse` レコード (RFC 7807 Problem Details 互換)
- `com.example.skishop.shared.exception`:
  - `BusinessException` (ビジネスルール違反)
  - `ResourceNotFoundException` (404)
  - `GlobalExceptionHandler` (@RestControllerAdvice, RFC 7807)
- `com.example.skishop.shared.config`:
  - Kafka 共通設定
  - WebClient 共通設定

### 3. `.gitignore`
Java/Maven/Spring Boot 用の .gitignore を作成

## 品質要件
- `.github/copilot-instructions.md` のコーディング規約を遵守
- `.github/instructions/pom-dependency.instructions.md` の依存関係ルールを遵守
- `.github/instructions/java-coding-standards.instructions.md` に準拠
- Java 25 のレコードクラス、パターンマッチングを積極的に使用
- SLF4J/Logback でログ出力 (System.out.println 禁止)
- テストクラスも作成 (shared-library の単体テスト)
