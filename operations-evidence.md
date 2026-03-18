# Operations Evidence — AI 駆動マイクロサービス並列開発

> **実行日時**: 2026-03-18T22:15:00+09:00
> **リポジトリ**: https://github.com/yoshioterada/ski-shop-microservices
> **参照プロジェクト**: https://github.com/yoshioterada/GitHub-Copilot-Agent-Workshop-with-Enterprise-Microservices
> **技術スタック**: Java 25, Spring Boot 4.1, Spring AI 2.0, Maven

---

## 目次

1. [前提環境の確認](#1-前提環境の確認)
2. [.github AI フレームワークの概要](#2-github-ai-フレームワークの概要)
3. [プロジェクト初期化](#3-プロジェクト初期化)
4. [タスク設計と並列開発戦略](#4-タスク設計と並列開発戦略)
5. [Coding Agent タスクの並列実行](#5-coding-agent-タスクの並列実行)
6. [レビュー Agent の活用方法](#6-レビュー-agent-の活用方法)
7. [VS Code Agent Mode での追加活用](#7-vs-code-agent-mode-での追加活用)
8. [まとめと効果](#8-まとめと効果)

---

## 1. 前提環境の確認

### 1.1 GitHub CLI バージョン確認

```bash
$ which gh && gh --version
/opt/homebrew/bin/gh
gh version 2.83.1 (2025-11-13)
```

### 1.2 Copilot CLI 拡張のインストール

```bash
$ gh extension install github/gh-copilot
✓ Installed extension github/gh-copilot

$ gh copilot --version
version 1.2.0 (2025-10-30)
```

### 1.3 Agent Task コマンドの確認（Coding Agent）

```bash
$ gh agent-task --help
USAGE
  gh agent-task <command> [flags]

AVAILABLE COMMANDS
  create:        Create an agent task (preview)
  list:          List agent tasks (preview)
  view:          View an agent task session (preview)
```

**重要**: `gh agent-task create` コマンドは以下の機能を持つ:
- `--custom-agent <name>` — `.github/agents/<name>.agent.md` で定義したカスタムエージェントを使用
- `-F <file>` — タスク記述をファイルから読み込み
- `--follow` — エージェントのログをリアルタイムで追跡
- `--base <branch>` — PR のベースブランチを指定

### 1.4 認証状態の確認

```bash
$ gh auth status
github.com
  ✓ Logged in to github.com account yoshioterada (keyring)
  - Active account: true
  - Git operations protocol: https
  - Token scopes: 'gist', 'read:org', 'repo', 'workflow'
```

---

## 2. .github AI フレームワークの概要

### 2.1 ディレクトリ構成

```
.github/
├── copilot-instructions.md              # Layer 1: 全体共通ルール
├── instructions/                         # Layer 1: ファイルパターン別指示
│   ├── java-coding-standards.instructions.md    (applyTo: **/*.java)
│   ├── api-design.instructions.md               (applyTo: **/controller/**/*.java)
│   ├── security-coding.instructions.md          (applyTo: **/service/**/*.java, **/controller/**/*.java)
│   ├── test-standards.instructions.md           (applyTo: **/*Test.java)
│   ├── sql-schema-review.instructions.md        (applyTo: **/*.sql)
│   ├── pom-dependency.instructions.md           (applyTo: **/pom.xml)
│   ├── dockerfile-infra.instructions.md         (applyTo: Dockerfile/docker-compose)
│   └── spring-config.instructions.md            (applyTo: application*.yml)
├── agents/                               # Layer 2 + 3: ステークホルダー Agent
│   ├── orchestrator.agent.md             # Layer 3: ステージゲート・オーケストレーター
│   ├── architect.agent.md                # アーキテクチャ設計レビュー
│   ├── security-reviewer.agent.md        # セキュリティレビュー
│   ├── tech-lead.agent.md                # コード品質レビュー
│   ├── dba-reviewer.agent.md             # DB 設計レビュー
│   ├── qa-manager.agent.md               # テスト戦略レビュー
│   ├── compliance-reviewer.agent.md      # コンプライアンスチェック
│   ├── oss-reviewer.agent.md             # OSS ライセンス・脆弱性チェック
│   ├── performance-reviewer.agent.md     # パフォーマンスレビュー
│   ├── business-analyst.agent.md         # 要件分析
│   ├── release-manager.agent.md          # リリース判定
│   ├── infra-ops-reviewer.agent.md       # インフラ・運用性レビュー
│   ├── audit-reviewer.agent.md           # 監査レビュー
│   └── ux-accessibility-reviewer.agent.md # UX/アクセシビリティ
├── skills/                               # 複数ステップワークフロー
│   ├── stage-gate-review/SKILL.md        # ステージゲートレビュー
│   ├── security-audit/SKILL.md           # セキュリティ監査
│   ├── compliance-check/SKILL.md         # コンプライアンスチェック
│   ├── release-readiness/SKILL.md        # リリース準備評価
│   └── full-review-pipeline/SKILL.md     # 全 Agent フルレビュー
├── prompts/                              # 再利用可能なプロンプトテンプレート
│   ├── generate-test-plan.prompt.md
│   ├── generate-threat-model.prompt.md
│   ├── generate-api-spec.prompt.md
│   ├── generate-pia.prompt.md
│   ├── generate-release-notes.prompt.md
│   └── generate-runbook.prompt.md
└── hooks/                                # 自動チェック (pre/post tool use)
    ├── pre-tool-checks.json
    └── scripts/
        ├── post-edit-check.sh
        └── inject-project-context.sh
```

### 2.2 層構成と役割

| 層 | 用途 | Coding Agent 連携 |
|---|---|---|
| **Layer 1: Instructions** | 全ファイルに自動適用される品質ルール | `.github/copilot-instructions.md` が Coding Agent に自動読み込まれ、全コード生成に規約が反映される |
| **Layer 2: Agents** | ステークホルダー別の専門レビュー | `--custom-agent` で特定 Agent を使う Coding Agent タスクを作成可能 |
| **Layer 3: Orchestrator** | ステージゲートで全 Agent 統合レビュー | `--custom-agent orchestrator` で呼び出し |
| **Skills** | 複合ワークフロー | VS Code Agent Mode で `/` コマンドから利用 |
| **Prompts** | 定型タスクのテンプレート | VS Code Agent Mode で `/` コマンドから利用 |

---

## 3. プロジェクト初期化

### 3.1 Git リポジトリの初期化

```bash
$ cd /private/tmp/spring-ai-sample

$ git init
Initialized empty Git repository in /private/tmp/spring-ai-sample/.git/

$ git add -A

$ git commit -m "chore: initial commit with .github AI-driven development framework"
[main (root-commit) a1bd098] chore: initial commit with .github AI-driven development framework
 56 files changed, 12492 insertions(+)
```

### 3.2 GitHub リポジトリの作成とプッシュ

```bash
$ gh repo create ski-shop-microservices --private --source=. --push
✓ Created repository yoshioterada/ski-shop-microservices on github.com
  https://github.com/yoshioterada/ski-shop-microservices
✓ Added remote https://github.com/yoshioterada/ski-shop-microservices.git
✓ Pushed commits to https://github.com/yoshioterada/ski-shop-microservices.git
```

### 3.3 Git 認証の設定

```bash
$ gh auth setup-git
# gh CLI のトークンを git credential helper に設定
```

---

## 4. タスク設計と並列開発戦略

### 4.1 マイクロサービス一覧 (参照プロジェクトより)

参照プロジェクト (9 サービス + フロントエンド) を Spring Boot 4.1 + Java 25 + Spring AI 2.0 で再実装:

| # | サービス名 | 責務 | ポート |
|---|---|---|---|
| 1 | `shared-library` | 共通 DTO/例外/設定 | — |
| 2 | `authentication-service` | OAuth 2.0/JWT 認証 | 8088 |
| 3 | `user-management-service` | ユーザー管理 (RBAC) | 8081 |
| 4 | `inventory-management-service` | 商品・在庫管理 | 8082 |
| 5 | `sales-management-service` | 注文・販売管理 | 8083 |
| 6 | `payment-cart-service` | カート・支払い (Redis) | 8084 |
| 7 | `point-service` | ポイント管理 | 8085 |
| 8 | `coupon-service` | クーポン管理 | 8086 |
| 9 | `ai-support-service` | AI チャット (Spring AI 2.0) | 8087 |
| 10 | `api-gateway` | Spring Cloud Gateway | 8080 |
| 11 | `infrastructure` | Docker Compose, DB スキーマ, 監視 | — |

### 4.2 並列開発のウェーブ設計

```
Wave 1 (基盤):        [01] Parent POM + shared-library
                        ↓ (Coding Agent が独立に PR 作成)
Wave 2 (全サービス並列): [02] auth-service ──┐
                        [03] user-service ──┤
                        [04] inventory ─────┤
                        [05] sales ─────────┤ 全てを同時に Coding Agent で実行
                        [06] payment-cart ──┤
                        [07] point ─────────┤
                        [08] coupon ────────┤
                        [09] ai-support ────┤
                        [10] api-gateway ───┤
                        [11] infrastructure ┘
                        ↓
Wave 3 (レビュー):     Orchestrator / 各 Agent でステージゲートレビュー
                        ↓
Wave 4 (統合):         PR マージ → 統合テスト
```

**ポイント**: Coding Agent はそれぞれ独立したブランチで作業するため、全 11 タスクを同時並行で実行可能。

### 4.3 タスク記述ファイルの作成

各サービスの実装タスクを `tasks/` ディレクトリに Markdown ファイルとして作成:

```bash
$ mkdir -p tasks/

# 11 個のタスク記述ファイルを作成
tasks/
├── 01-parent-pom-and-shared.md       # Maven 親 POM + shared-library
├── 02-authentication-service.md       # 認証サービス
├── 03-user-management-service.md      # ユーザー管理
├── 04-inventory-management-service.md # 在庫管理
├── 05-sales-management-service.md     # 販売管理
├── 06-payment-cart-service.md         # 支払い・カート
├── 07-point-service.md                # ポイント
├── 08-coupon-service.md               # クーポン
├── 09-ai-support-service.md           # AI サポート
├── 10-api-gateway.md                  # API ゲートウェイ
└── 11-infrastructure.md               # インフラ構成
```

各タスク記述ファイルには以下を含む:
- **概要**: サービスの目的
- **参照元**: 元のリポジトリの対応ディレクトリ
- **エンティティ定義**: DB テーブル設計
- **REST API エンドポイント**: 全 API の仕様
- **Kafka イベント**: 非同期通信のイベント定義
- **ビジネスロジック**: 主要なビジネスルール
- **品質要件**: `.github/instructions/` の適用ルール

```bash
$ git add tasks/
$ git commit -m "chore: add agent task description files for parallel microservice development"
$ git push origin main
```

---

## 5. Coding Agent タスクの並列実行

### 5.1 全 11 タスクの一斉起動

各タスクを `gh agent-task create -F` で起動。Coding Agent はそれぞれ独立したブランチを作成し、PR を生成する。

```bash
# === Wave 1: 基盤 ===
$ gh agent-task create -F tasks/01-parent-pom-and-shared.md
# → PR #1: https://github.com/yoshioterada/ski-shop-microservices/pull/1
#   Branch: copilot/create-maven...
#   Session: 8ea334db-0fae-4836-a0ad-34cedfb18374

# === Wave 2: 全マイクロサービスを並列実行 ===
$ gh agent-task create -F tasks/02-authentication-service.md
# → PR #2: https://github.com/yoshioterada/ski-shop-microservices/pull/2
#   Branch: copilot/implement-au...
#   Session: 64c14e97-5865-49a7-af8e-272669ee3dcc

$ gh agent-task create -F tasks/03-user-management-service.md
# → PR #3: https://github.com/yoshioterada/ski-shop-microservices/pull/3
#   Branch: copilot/implement-us...
#   Session: 4ff8b407-56b4-4773-aaa1-ff6d21b71c32

$ gh agent-task create -F tasks/04-inventory-management-service.md
# → PR #4: https://github.com/yoshioterada/ski-shop-microservices/pull/4
#   Branch: copilot/implement-in...
#   Session: 9e2a46f1-0226-41fe-9677-4b284865c4fe

$ gh agent-task create -F tasks/05-sales-management-service.md
# → PR #5: https://github.com/yoshioterada/ski-shop-microservices/pull/5
#   Branch: copilot/implement-sa...
#   Session: 77ab1582-4252-437c-90a2-a21d687753a1

$ gh agent-task create -F tasks/06-payment-cart-service.md
# → PR #6: https://github.com/yoshioterada/ski-shop-microservices/pull/6
#   Branch: copilot/implement-pa...
#   Session: ea873b7b-64c4-4c97-90b2-4a529c79a8dd

$ gh agent-task create -F tasks/07-point-service.md
# → PR #7: https://github.com/yoshioterada/ski-shop-microservices/pull/7
#   Branch: copilot/implement-po...
#   Session: 6ddf5273-3b39-420b-948f-5574571ed9b7

$ gh agent-task create -F tasks/08-coupon-service.md
# → PR #8: https://github.com/yoshioterada/ski-shop-microservices/pull/8
#   Branch: copilot/implement-co...
#   Session: 4a4ec667-aca5-4e58-902f-52b44b88d3e6

$ gh agent-task create -F tasks/09-ai-support-service.md
# → PR #9: https://github.com/yoshioterada/ski-shop-microservices/pull/9
#   Branch: copilot/implement-ai...
#   Session: abca2661-3d2e-40cb-b3f9-5a610b67c043

$ gh agent-task create -F tasks/10-api-gateway.md
# → PR #10: https://github.com/yoshioterada/ski-shop-microservices/pull/10
#   Branch: copilot/implement-ap...
#   Session: 9692eeb2-3ea2-4df8-9aa7-eb6500d869b2

$ gh agent-task create -F tasks/11-infrastructure.md
# → PR #11: https://github.com/yoshioterada/ski-shop-microservices/pull/11
#   Branch: copilot/create-infra...
#   Session: fab0e192-d66f-49f1-9e8b-e2dae7bd53de
```

### 5.2 タスク一覧の確認

```bash
$ gh pr list --limit 15
Showing 11 of 11 open pull requests in yoshioterada/ski-shop-microservices

ID   TITLE                       BRANCH                   CREATED AT
#11  [WIP] Add infrastructur...  copilot/create-infra...  less than a minute ago
#10  [WIP] Add API Gateway i...  copilot/implement-ap...  less than a minute ago
#9   [WIP] Add AI support mi...  copilot/implement-ai...  about 1 minute ago
#8   [WIP] Add coupon manage...  copilot/implement-co...  about 1 minute ago
#7   [WIP] Add point managem...  copilot/implement-po...  about 1 minute ago
#6   [WIP] Implement payment...  copilot/implement-pa...  about 1 minute ago
#5   [WIP] Add sales managem...  copilot/implement-sa...  about 1 minute ago
#4   [WIP] Implement invento...  copilot/implement-in...  about 2 minutes ago
#3   [WIP] Implement user ma...  copilot/implement-us...  about 2 minutes ago
#2   [WIP] Implement authent...  copilot/implement-au...  about 2 minutes ago
#1   [WIP] Create Maven pare...  copilot/create-maven...  about 2 minutes ago
```

### 5.3 タスク進行のモニタリング

```bash
# 個別タスクのセッションログを追跡
$ gh agent-task view <session-id> --follow

# PR の状態を確認
$ gh pr view <PR番号> --json state,title,body

# 完了した PR の差分を確認
$ gh pr diff <PR番号>
```

### 5.4 カスタムエージェントを指定してのタスク実行

特定のレビュー観点を持つ Agent を指定してタスクを実行することも可能:

```bash
# セキュリティレビューア Agent で認証サービスの追加チェックを実行
$ gh agent-task create "authentication-service のセキュリティレビューを実施し、OWASP Top 10 の観点で脆弱性がないか確認してください" \
  --custom-agent security-reviewer

# アーキテクト Agent でサービス間連携の設計レビュー
$ gh agent-task create "全マイクロサービスのアーキテクチャを確認し、SOLID原則への準拠とサービス間の依存関係を分析してください" \
  --custom-agent architect

# DBA Agent で DB スキーマのレビュー
$ gh agent-task create "全データベーススキーマを確認し、正規化の適切性、インデックス設計、マイグレーションの可逆性を評価してください" \
  --custom-agent dba-reviewer
```

---

## 6. レビュー Agent の活用方法

### 6.1 ステージゲートレビュー (Gate 3: 実装完了)

VS Code Agent Mode で Orchestrator Agent を使用:

```
プロンプト例:
@orchestrator Gate 3 のステージゲートレビューを実行してください。
全マイクロサービスの実装品質を確認し、Go/No-Go 判定を出してください。
```

Orchestrator が自動で以下の Agent を呼び出す:
- `tech-lead` → コード品質チェック
- `security-reviewer` → セキュリティ脆弱性チェック
- `oss-reviewer` → OSS ライセンス・脆弱性
- `dba-reviewer` → DB 設計の適切性
- `compliance-reviewer` → 法規制準拠

### 6.2 フルレビューパイプライン

```
プロンプト例:
@orchestrator full レビューを実行してください。
全 13 Agent によるプロジェクト全体の品質評価を行ってください。
```

### 6.3 個別 Agent レビュー (CLI)

```bash
# Coding Agent を通じて特定の Agent 観点でレビュー
$ gh agent-task create "tech-lead Agent の観点で全サービスのコード品質をレビューしてください。命名規則、DRY/KISS 原則、コードの可読性を評価してください" \
  --custom-agent tech-lead

$ gh agent-task create "qa-manager Agent の観点でテストカバレッジとテスト品質を評価してください。テスト不足のメソッドを特定し、テスト追加の推奨を出してください" \
  --custom-agent qa-manager

$ gh agent-task create "performance-reviewer Agent の観点でパフォーマンスリスクを評価してください。N+1 問題、メモリリーク、ボトルネックを分析してください" \
  --custom-agent performance-reviewer
```

---

## 7. VS Code Agent Mode での追加活用

### 7.1 Prompt テンプレートの使用

VS Code の Agent Mode で以下のプロンプトをスラッシュコマンドで実行:

```
/generate-api-spec       → API 仕様書の自動生成 (architect Agent)
/generate-test-plan      → テスト計画の自動生成 (qa-manager Agent)
/generate-threat-model   → 脅威モデルの自動生成 (security-reviewer Agent)
/generate-pia            → プライバシー影響評価 (compliance-reviewer Agent)
/generate-release-notes  → リリースノートの自動生成 (release-manager Agent)
/generate-runbook        → 運用手順書の自動生成 (infra-ops-reviewer Agent)
```

### 7.2 Skill の使用

```
# セキュリティ監査 Skill
/security-audit プロジェクト全体のセキュリティ監査を実行してください

# コンプライアンスチェック Skill  
/compliance-check GDPR 準拠と個人情報取扱いの確認を実行してください

# リリース準備チェック Skill
/release-readiness リリース前の Go/No-Go チェックを実行してください
```

### 7.3 Instructions の自動適用

Agent Mode でコードを編集する際、以下のファイルパターンに応じて Instructions が自動適用される:

| ファイルパターン | 自動適用される Instructions | 効果 |
|---|---|---|
| `**/*.java` | `java-coding-standards` | 命名規則、パッケージ構成、Java 25 機能の活用を自動チェック |
| `**/controller/**/*.java` | `api-design` | REST 設計原則、エラーレスポンス形式を自動チェック |
| `**/service/**/*.java` | `security-coding` | 入力検証、SQLi/XSS 防止を自動チェック |
| `**/*Test.java` | `test-standards` | テスト命名、AAA パターン、カバレッジ基準を自動チェック |
| `**/*.sql` | `sql-schema-review` | 正規化、インデックス設計を自動チェック |
| `**/pom.xml` | `pom-dependency` | 依存関係の最小化、バージョン固定を自動チェック |
| `**/Dockerfile` | `dockerfile-infra` | マルチステージビルド、非 root 実行を自動チェック |
| `**/application*.yml` | `spring-config` | 秘密情報の外部化、プロファイル分離を自動チェック |

### 7.4 Hooks の自動実行

`pre-tool-checks.json` により、ファイル編集時に以下が自動チェックされる:

- **PreToolUse (edit_file/create_file)**: OWASP Top 10 確認、秘密情報のハードコード禁止、入力検証の必要性確認
- **PostToolUse (edit_file)**: 編集後のセキュリティチェック自動実行
- **SessionStart**: プロジェクトコンテキストの自動注入

---

## 8. まとめと効果

### 8.1 並列開発の実績

| 項目 | 値 |
|---|---|
| 同時実行タスク数 | **11 タスク** |
| 各タスクの生成 PR | 11 PR (各サービス独立ブランチ) |
| 品質ルール文書数 | 8 Instructions + 14 Agents + 5 Skills + 6 Prompts |
| 自動適用ルール | ファイルパターンベースで 8 つの Instructions が自動適用 |

### 8.2 AI フレームワークの効果

1. **品質の一貫性**: `.github/copilot-instructions.md` + File Instructions により、全 Coding Agent タスクが同一のコーディング規約に従う
2. **セキュリティの組込み**: `security-coding.instructions.md` + Hooks により、コード生成時点でセキュリティチェックが自動適用
3. **並列性の最大化**: 各サービスが独立モジュールのため、11 タスクを完全並列で実行可能
4. **多面的レビュー**: Orchestrator を通じて 13 の専門 Agent による包括的レビューが可能
5. **監査追跡**: 全レビュー結果が `.github/review-reports/` に永続化され、監査証跡として利用可能

### 8.3 推奨ワークフロー

```
Step 1: タスク記述ファイルを作成 (tasks/*.md)
Step 2: gh agent-task create -F で全タスクを並列実行
Step 3: PR 完了後、各 PR を確認・フィードバック
Step 4: Orchestrator で Gate 3 (実装完了) レビュー
Step 5: 修正 → 再レビュー → マージ
Step 6: 統合テスト → Gate 4 (テスト完了) レビュー
Step 7: Release-Readiness Skill で最終チェック
Step 8: Gate 5 (リリース承認) → デプロイ
```

### 8.4 各ターミナルで実行した全コマンドの時系列ログ

```
[22:10:00] which gh && gh --version
[22:10:05] gh extension install github/gh-copilot
[22:10:10] gh copilot --version
[22:10:15] gh agent-task --help
[22:10:20] gh agent-task create --help
[22:11:00] cd /private/tmp/spring-ai-sample && git init
[22:11:05] git add -A && git commit -m "chore: initial commit with .github AI-driven development framework"
[22:11:30] gh repo create ski-shop-microservices --private --source=. --push
[22:12:00] gh auth setup-git
[22:12:10] mkdir -p tasks/
[22:12:30] # 11 個のタスク記述ファイルを作成
[22:13:00] git add tasks/ && git commit -m "chore: add agent task description files"
[22:13:10] git push origin main
[22:14:00] gh agent-task create -F tasks/01-parent-pom-and-shared.md       → PR #1
[22:14:10] gh agent-task create -F tasks/02-authentication-service.md      → PR #2
[22:14:15] gh agent-task create -F tasks/03-user-management-service.md     → PR #3
[22:14:20] gh agent-task create -F tasks/04-inventory-management-service.md → PR #4
[22:14:25] gh agent-task create -F tasks/05-sales-management-service.md    → PR #5
[22:14:30] gh agent-task create -F tasks/06-payment-cart-service.md        → PR #6
[22:14:35] gh agent-task create -F tasks/07-point-service.md               → PR #7
[22:14:40] gh agent-task create -F tasks/08-coupon-service.md              → PR #8
[22:14:45] gh agent-task create -F tasks/09-ai-support-service.md          → PR #9
[22:14:50] gh agent-task create -F tasks/10-api-gateway.md                 → PR #10
[22:14:55] gh agent-task create -F tasks/11-infrastructure.md              → PR #11
[22:15:00] gh pr list --limit 15   # 全 11 PR が [WIP] 状態で確認
```

---

## 付録A: タスク記述ファイルのベストプラクティス

### 効果的なタスク記述のポイント

1. **明確な構造**: `## 概要`, `## エンティティ`, `## REST API`, `## 品質要件` の見出しで構造化
2. **具体的なエンティティ定義**: フィールド名と型を明確に記述
3. **API 仕様の詳細**: メソッド、パス、パラメータ、レスポンス形式を記述
4. **Kafka イベントの定義**: Producer/Consumer の双方を記述
5. **品質要件で .github/instructions/ を参照**: Instructions ファイルのパスを明示的に参照

### タスク記述のテンプレート

```markdown
# Task: {サービス名} の実装

## 概要
{目的と責務の説明}

## 参照元
https://github.com/yoshioterada/GitHub-Copilot-Agent-Workshop-with-Enterprise-Microservices の {ディレクトリ名}

## モジュール構成
{ディレクトリ名}/ 配下に Spring Boot アプリケーションを作成。
パッケージ: com.example.skishop.{パッケージ名}

## エンティティ (PostgreSQL)
- {エンティティ名}: {フィールド一覧}

## REST API エンドポイント
### 一般ユーザー向け
- {METHOD} {Path} - {説明}

### 管理者向け
- {METHOD} {Path} - {説明}

## Kafka イベント
- {イベント名} - {トリガー条件}

## 品質要件
- .github/instructions/{instructions名} に準拠
```

---

## 付録B: CLI コマンドリファレンス

| コマンド | 用途 | 例 |
|---|---|---|
| `gh agent-task create "<説明>"` | タスクをインライン指定で作成 | `gh agent-task create "Fix login bug"` |
| `gh agent-task create -F <file>` | タスクをファイルから作成 | `gh agent-task create -F tasks/01.md` |
| `gh agent-task create -F - < file` | タスクを stdin から作成 | `cat task.md \| gh agent-task create -F -` |
| `gh agent-task create --custom-agent <name>` | カスタム Agent でタスク作成 | `--custom-agent security-reviewer` |
| `gh agent-task create --follow` | ログをリアルタイム追跡 | `gh agent-task create "..." --follow` |
| `gh agent-task list` | タスク一覧 | `gh agent-task list` |
| `gh agent-task view <id>` | タスク詳細 | `gh agent-task view <session-id>` |
| `gh pr list` | PR 一覧 | `gh pr list --limit 20` |
| `gh pr view <num>` | PR 詳細 | `gh pr view 1 --json state,title` |
| `gh pr diff <num>` | PR の差分表示 | `gh pr diff 1` |
| `gh pr merge <num>` | PR のマージ | `gh pr merge 1 --squash` |
