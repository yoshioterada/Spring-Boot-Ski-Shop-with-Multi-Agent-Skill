# Task: AI Support Service の実装

## 概要
スキーショップの AI サポートマイクロサービスを実装してください。
Spring AI 2.0 を使用した AI チャットボット・商品レコメンデーション機能を提供します。

## 参照元
https://github.com/yoshioterada/GitHub-Copilot-Agent-Workshop-with-Enterprise-Microservices の `ai-support-service` を参考に、Spring Boot 4.1 + Java 25 + **Spring AI 2.0** で再実装します。

## モジュール構成
`ai-support-service/` ディレクトリ配下に Spring Boot アプリケーションを作成。
パッケージ: `com.example.skishop.aisupport`

## 要件

### エンティティ (PostgreSQL)
- `ChatSession`: id, userId, startTime, endTime, sessionSummary, feedbackRating
- `ChatMessage`: id, sessionId, timestamp, sender(USER/BOT), content, messageType
- `ProductRecommendation`: id, userId, productId, recommendationSource, timestamp, wasClicked, confidence
- `SearchQuery`: id, userId, query, timestamp, resultCount, filterParameters

### REST API エンドポイント
#### AI チャット
- `POST /api/v1/ai/chat` - チャットメッセージ送信
- `POST /api/v1/ai/chat/stream` - ストリーミングチャット (SSE)
- `GET /api/v1/ai/chat/sessions` - チャットセッション一覧
- `GET /api/v1/ai/chat/sessions/{id}` - チャットセッション詳細
- `POST /api/v1/ai/chat/sessions/{id}/feedback` - セッション評価

#### レコメンデーション
- `GET /api/v1/recommendations/{userId}` - ユーザー向けレコメンデーション
- `GET /api/v1/recommendations/products/{productId}/similar` - 類似商品レコメンデーション
- `GET /api/v1/recommendations/trending` - トレンド商品

#### FAQ・ナレッジベース
- `GET /api/v1/ai/faq` - よくある質問検索
- `POST /api/v1/ai/product-advice` - 商品選びアドバイス

### Spring AI 2.0 の活用
- `ChatClient` を使用した AI チャット実装
- ストリーミングレスポンス (Flux<String>)
- Function Calling で在庫確認・注文状況を AI が呼び出し可能に
- 商品データを RAG (Retrieval-Augmented Generation) パターンで活用
- プロンプトテンプレートの外部管理

### 設定
- AI モデル接続情報は環境変数で管理
- スキーに関するナレッジベースデータを初期ロード

## 品質要件
- `.github/instructions/java-coding-standards.instructions.md` に準拠
- `.github/instructions/api-design.instructions.md` に準拠
- AI API キーのハードコード禁止
- ストリーミングレスポンスの適切な実装
- 全パブリックメソッドの単体テスト必須
