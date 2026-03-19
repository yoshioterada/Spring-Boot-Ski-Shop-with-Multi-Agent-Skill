# tech-lead レビューレポート — Gate 3 再審 #2

## サマリー
- **判定**: ⚠️ Warning (Conditional Pass)
- **指摘件数**: Critical: 0, High: 2, Medium: 3, Low: 2
- **レビュー対象**: IDOR 修正コード（JwtAuthenticationFilter, SecurityUtils, @PreAuthorize 適用 3 Controller, SecurityConfig ×3, IDOR テスト ×3）+ 前回指摘の残存確認
- **レビュー日時**: 2026-03-19
- **レビュー範囲**: IDOR 修正コードの品質評価（命名・構造・DRY・KISS・SOLID・テスト品質）+ 再審 #1 指摘 (C:0, H:2, M:3, L:2) の残存確認

### 前提条件の確認
- [x] ソースコードへのアクセス: OK
- [x] コーディング規約の把握: OK

---

## IDOR 修正の品質評価

### 総合評価

IDOR 修正は**高品質に実装されている**。共通ライブラリへの適切な配置、SpEL ベースの宣言的アクセス制御、Service 層での命令的チェック（OrderService.cancelOrder）の使い分けが合理的で、KISS 原則に沿った必要十分な実装。

#### 優れている点
1. **JwtAuthenticationFilter**: `OncePerRequestFilter` 継承は正しい設計。JWT 検証失敗時に SecurityContext を汚染しない（catch 内で filterChain 呼び出し前）。`UUID.fromString(userId)` による principal 型の一貫性確保
2. **SecurityUtils**: `final` クラス + private constructor でユーティリティクラスの誤用防止。`instanceof UUID uuid` パターンマッチング活用。sealed `ApplicationException` 体系の `AuthorizationDeniedException` を再利用
3. **@PreAuthorize SpEL**: `#id == authentication.principal` で UUID 型直接比較。9+5+1=15 エンドポイントを統一的に保護。SpEL 式が簡潔で可読性が高い
4. **SecurityConfig**: 3 サービスとも `@EnableMethodSecurity` + フィルタ登録が一貫したパターン。stateless セッション設定が適切
5. **テスト構造**: `@Nested` による IDOR シナリオごとのグルーピング。owner/other/admin の 3 パターン網羅。`should_...when_...` 命名規約準拠。AAA パターン遵守

---

## コード品質スコアカード

| # | 評価軸 | 評価 | 備考 |
|---|---|---|---|
| 1 | 命名規則 | ✅ | PascalCase/camelCase 準拠。`SecurityUtils`, `JwtAuthenticationFilter`, `verifyOwnershipOrAdmin` — 命名明確 |
| 2 | コード構造（DRY/KISS/長さ/複雑度） | ⚠️ | IDOR 修正自体は KISS 準拠。ただし前回 H-01 (EventPublishingService DRY 違反) 残存 |
| 3 | 例外処理 | ✅ | JWT 検証失敗は `log.debug` + SecurityContext 未設定で適切。`AuthorizationDeniedException` は sealed 例外階層に統合済み。GlobalExceptionHandler で 403 → ProblemDetail 変換 |
| 4 | ログ品質 | ✅ | SLF4J 使用。JWT 検証失敗は DEBUG レベル（適切 — 本番で無効化可能）。Authorization denied は GlobalExceptionHandler で WARN |
| 5 | 禁止事項の遵守 | ✅ | System.out.println なし。秘密情報ハードコードなし。JWT secret は `@Value("${jwt.secret}")` で外部化 |
| 6 | Java 25 機能活用 | ✅ | `instanceof UUID uuid` パターンマッチング使用。sealed `ApplicationException` 階層 |
| 7 | Spring Boot 規約 | ✅ | コンストラクタ DI 準拠。`@EnableMethodSecurity` + `@PreAuthorize` の標準的な Spring Security 活用 |
| 8 | Git 運用 | — | コミット履歴は本レビュースコープ外 |
| 9 | 保守性 | ⚠️ | IDOR コードの保守性は高い。テスタビリティも良好（MockMvc + authentication RequestPostProcessor）。ただし JJWT バージョン散在 (M-NEW-01) 要改善 |

---

## 指摘事項

### 新規指摘（IDOR 修正コードに関するもの）

| # | 重要度 | カテゴリ | 対象ファイル | 行番号 | 指摘内容 | 修正前 | 修正後 |
|---|--------|---------|-------------|--------|----------|--------|--------|
| M‑NEW‑01 | Medium | 依存管理 | [common-lib/pom.xml](common-lib/pom.xml#L37), [authentication-service/pom.xml](authentication-service/pom.xml#L66) | — | JJWT 0.12.6 が common-lib と authentication-service の両 pom.xml にハードコード (3 箇所×2=6 箇所)。親 BOM の `<dependencyManagement>` でバージョンを一元管理すべき。バージョン乖離リスクあり | `<version>0.12.6</version>` を各 pom.xml に直接記述 | 親 pom.xml の `<dependencyManagement>` に `<jjwt.version>0.12.6</jjwt.version>` プロパティ + JJWT 依存を追加し、子 pom.xml では `<version>` を省略 |
| L‑NEW‑01 | Low | IDOR 保護範囲 | [OrderController.java](sales-management-service/src/main/java/com/example/skishop/sales/controller/OrderController.java#L42-L44) | L42-44 | `GET /orders/{orderId}`, `GET /orders/number/{orderNumber}` に `@PreAuthorize` なし。注文 ID/番号を知っていれば他者の注文詳細を参照可能。cancelOrder は Service 層で保護済みだが、getOrder は未保護。現状は管理系 API として許容できるが、顧客向け運用では保護が必要 | `@PreAuthorize` なし | Service 層で `SecurityUtils.verifyOwnershipOrAdmin(order.getCustomerId())` を追加（cancelOrder と同パターン）|

### 前回指摘（再審 #1）の残存確認

| # | 重要度 | 再審#1 ID | 状態 | 詳細 |
|---|--------|----------|------|------|
| H‑01 | High | H-01 | **残存** | EventPublishingService が 7 サービスで重複（DRY 違反）。IDOR 修正スコープ外のため変更なし。architect 管轄の指摘と重複 → architect 優先 |
| H‑02 | High | H-02 | **残存** | `CouponService.bulkGenerateCoupons()` 内の `existsByCode()` ループ。CouponRepository に `existsByCode` は Spring Data JPA メソッド名クエリとして存在するため SQL インジェクションリスクはないが、大量生成時の N+1 DB ラウンドトリップは非効率。`existsByCodeIn(Set<String>)` バッチチェックへの改善を推奨 |
| M‑01 | Medium | M-01 | **残存** | エラーメッセージスタイルの不統一。IDOR 修正でも日本語ハードコード (`"このリソースへのアクセス権がありません"`) を使用しており、既存パターンとの一貫性は維持されているが、メッセージキー化は未対応 |
| M‑02 | Medium | M-02 | **該当なし** | `InventoryService.bulkUpdateStock()` はコードベースに存在しない（設計ドキュメントのみ）。未実装のため指摘取下げ |
| M‑03 | Medium | M-03 | **残存** | AdminUserController テストカバレッジの薄さ。IDOR 修正スコープ外 |
| L‑01 | Low | L-01 | **残存** | ドメインモデルとエンティティの命名混在。IDOR 修正スコープ外 |
| L‑02 | Low | L-02 | **残存** | 一部 Controller の長メソッド。UserController は PreAuthorize 追加のみで構造変更なし |

---

## 統合指摘一覧（現時点）

| # | 重要度 | カテゴリ | 対象 | 状態 |
|---|--------|---------|------|------|
| 1 | High | DRY | EventPublishingService 7 サービス重複 | 残存 (architect 優先管轄) |
| 2 | High | 性能/構造 | bulkGenerateCoupons の existsByCode N+1 | 残存 |
| 3 | Medium | 依存管理 | JJWT バージョン散在 (BOM 未登録) | **新規** |
| 4 | Medium | 一貫性 | エラーメッセージスタイル不統一 | 残存 |
| 5 | Medium | テスト | AdminUserController テスト薄い | 残存 |
| 6 | Low | IDOR 範囲 | OrderController getOrder/getOrderByNumber 未保護 | **新規** |
| 7 | Low | 命名 | ドメイン vs エンティティ混在 | 残存 |

---

## テスト品質詳細評価

| 観点 | 評価 | 詳細 |
|------|------|------|
| テスト数 | ✅ | UserControllerIdorTest: 14 件、CartControllerIdorTest: 8 件、OrderControllerIdorTest: 5+1 件。Owner/Other/Admin/Unauthenticated パターン網羅 |
| 命名規約 | ✅ | `should_...when_...` パターン準拠。`@DisplayName` 日本語併記 |
| AAA パターン | ✅ | Arrange (mock setup) → Act (mockMvc.perform) → Assert (status/jsonPath) の 3 段階が明確 |
| テスト分離 | ✅ | `@Nested` でエンドポイント/シナリオごとに論理グループ化。テスト間の依存なし |
| テスト環境 | ✅ | `@WebMvcTest` + `@Import(SecurityConfig.class)` で Controller + Security 層のスライステスト。`@TestPropertySource` で JWT secret を安全にモック |
| 境界ケース | ⚠️ | 未認証テストは各テストクラスに 1 件のみ存在。期限切れ JWT やマルフォーム JWT のテストは Controller レベルでは未実装（Filter 単体テストとして別途実施すべき） |
| OrderService 層テスト | ✅ | `OrderServiceTest` に `should_throwAuthorizationDenied_when_nonOwnerCancels` が追加済み。`setupSecurityContext` ヘルパーで SecurityContext をセットアップ。`@AfterEach` で `SecurityContextHolder.clearContext()` しておりテスト分離も適切 |

---

## 再審比較

| 項目 | 再審 #1 | 再審 #2 | 変化 |
|------|---------|---------|------|
| 判定 | ⚠️ Conditional Go (IDOR ブロッカー) | ⚠️ Warning (Conditional Pass) | IDOR ブロッカー解消 |
| Critical | 0 (C:39→0) | 0 | — |
| High | 2 | 2 | — (同一指摘が残存) |
| Medium | 3 | 3 | M-02 取下げ、M-NEW-01 追加 (±0) |
| Low | 2 | 2 | L-02 統合して L-NEW-01 追加 (±0) |
| IDOR 対策 | ❌ 未実装 | ✅ 実装完了 | **解消** |
| テスト数 | 284 | 284 + 27 IDOR = **311** | +27 |

---

## エスカレーション事項

| # | 区分 | 内容 | エスカレーション理由 |
|---|------|------|---------------------|
| 1 | EventPublishingService 共通化 | 7 サービス重複は architect が設計判断すべき | tech-lead スコープ外。architect への委譲が適切 |

## 競合フラグ

- ⚡ **architect**: H-01 (EventPublishingService DRY 違反) は architect が構造設計として判断すべき。tech-lead としては DRY 違反の事実を記録するが、共通化の方式は architect に委ねる
- ⚡ **security-reviewer**: L-NEW-01 (OrderController getOrder 未保護) はセキュリティ観点で security-reviewer が重要度を再評価すべき可能性あり

---

## 推奨事項

### IDOR 修正に関して（追加改善）
1. **JwtAuthenticationFilter の単体テスト追加**: 期限切れ JWT、不正署名、マルフォーム JWT トークンに対するフィルタの挙動テストが未確認。common-lib にテストモジュールを追加して検証すべき
2. **JJWT バージョン一元管理**: 親 pom.xml の `<dependencyManagement>` に `jjwt.version` プロパティを追加し、common-lib と authentication-service の 6 箇所のハードコードバージョンを除去

### 前回指摘の対応優先度
1. **H-02** (bulkGenerateCoupons N+1): `couponRepository.existsByCodeIn(Set<String>)` バッチクエリを追加し、生成済みコードを SET でメモリ保持 → DB ラウンドトリップを 1 回に削減
2. **M-01** (エラーメッセージ不統一): 次スプリントでメッセージキー化 + `messages.properties` 統一を計画
3. **M-03** (AdminUserController テスト): 管理者 CRUD の異常系テスト（権限不足、不正入力、競合）を追加
