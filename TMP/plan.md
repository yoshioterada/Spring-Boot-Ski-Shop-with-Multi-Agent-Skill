# 実装計画書 — AI Agent Skills による開発プロセス自動化

> **作成日**: 2026-03-18
> **参照元**: [definition.md](definition.md)
> **目的**: definition.md に記載された全成果物を漏れなく作成するための具体的手順と、完成後の検証チェックリストを定義する

---

## 目次

1. [成果物の全体一覧（52 項目）](#1-成果物の全体一覧52-項目)
2. [Phase 別の実装手順](#2-phase-別の実装手順)
3. [各成果物の作成仕様と手順](#3-各成果物の作成仕様と手順)
4. [検証チェックリスト](#4-検証チェックリスト)
5. [依存関係と作業順序の制約](#5-依存関係と作業順序の制約)

---

## 1. 成果物の全体一覧（52 項目）

definition.md から抽出した全成果物を種別ごとに整理する。

### 1.1 ディレクトリ構造（19 エントリ / 実ディレクトリ 25）

以下のディレクトリを `.github/` 配下に作成する。

> **注意**: テーブルには 19 エントリを記載しているが、D15 に含まれる 5 つのサブディレクトリと
> `mkdir -p` により暗黙的に作成される `.github/skills/` を含めると、実際のディレクトリ数は **25** となる。

| # | ディレクトリパス | 用途 |
|---|---|---|
| D1 | `.github/` | ルートディレクトリ |
| D2 | `.github/instructions/` | File Instructions 格納 |
| D3 | `.github/agents/` | Custom Agents 格納 |
| D4 | `.github/skills/stage-gate-review/` | ステージゲートレビュー Skill |
| D5 | `.github/skills/stage-gate-review/references/` | ゲート基準の参照ドキュメント |
| D6 | `.github/skills/security-audit/` | セキュリティ監査 Skill |
| D7 | `.github/skills/security-audit/scripts/` | 監査用スクリプト |
| D8 | `.github/skills/security-audit/references/` | OWASP 等の参照ドキュメント |
| D9 | `.github/skills/compliance-check/` | コンプライアンスチェック Skill |
| D10 | `.github/skills/compliance-check/references/` | GDPR 等の参照ドキュメント |
| D11 | `.github/skills/release-readiness/` | リリース準備評価 Skill |
| D12 | `.github/skills/release-readiness/references/` | Go/No-Go 基準の参照ドキュメント |
| D13 | `.github/skills/full-review-pipeline/` | フルレビューパイプライン Skill |
| D14 | `.github/skills/full-review-pipeline/references/` | パイプラインフロー参照 |
| D15 | `.github/test-fixtures/` | テスト検証用サンプル（5 サブディレクトリ） |
| D16 | `.github/review-reports/` | 監査証跡の保存先 |
| D17 | `.github/prompts/` | Prompt ファイル格納 |
| D18 | `.github/hooks/` | Hook 定義・スクリプト格納 |
| D19 | `.github/hooks/scripts/` | Hook 用シェルスクリプト |

### 1.2 ファイル一覧（52 ファイル）

#### A. Layer 1: Workspace Instructions（1 ファイル）

| # | ファイルパス | definition.md 参照セクション |
|---|---|---|
| F01 | `.github/copilot-instructions.md` | §3.1 |

#### B. Layer 1: File Instructions（8 ファイル）

| # | ファイルパス | applyTo | definition.md 参照 |
|---|---|---|---|
| F02 | `.github/instructions/java-coding-standards.instructions.md` | `**/*.java` | §3.2 |
| F03 | `.github/instructions/sql-schema-review.instructions.md` | `**/*.sql` | §3.2 |
| F04 | `.github/instructions/api-design.instructions.md` | `**/controller/**/*.java` | §3.2 |
| F05 | `.github/instructions/test-standards.instructions.md` | `**/*Test.java`, `**/*Tests.java` | §3.2 |
| F06 | `.github/instructions/security-coding.instructions.md` | `**/service/**/*.java`, `**/controller/**/*.java` | §3.2 |
| F07 | `.github/instructions/pom-dependency.instructions.md` | `**/pom.xml` | §3.2 |
| F08 | `.github/instructions/dockerfile-infra.instructions.md` | `**/Dockerfile`, `**/docker-compose*.yml` | §3.2 |
| F09 | `.github/instructions/spring-config.instructions.md` | `**/application*.properties`, `**/application*.yml` | §3.2 |

#### C. Layer 2: Stakeholder Agents（13 ファイル）

| # | ファイルパス | user-invocable | definition.md 参照 |
|---|---|---|---|
| F10 | `.github/agents/business-analyst.agent.md` | false | §4.3 Agent 1 |
| F11 | `.github/agents/architect.agent.md` | true | §4.3 Agent 2 |
| F12 | `.github/agents/security-reviewer.agent.md` | true | §4.3 Agent 3 |
| F13 | `.github/agents/tech-lead.agent.md` | true | §4.3 Agent 6 |
| F14 | `.github/agents/dba-reviewer.agent.md` | false | §4.3 Agent 7 |
| F15 | `.github/agents/compliance-reviewer.agent.md` | false | §4.3 Agent 4 |
| F16 | `.github/agents/oss-reviewer.agent.md` | true | §4.3 Agent 5 |
| F17 | `.github/agents/ux-accessibility-reviewer.agent.md` | false | §4.3 Agent 8b |
| F18 | `.github/agents/qa-manager.agent.md` | true | §4.3 Agent 8 |
| F19 | `.github/agents/performance-reviewer.agent.md` | false | §4.3 Agent 9 |
| F20 | `.github/agents/release-manager.agent.md` | true | §4.3 Agent 10 |
| F21 | `.github/agents/infra-ops-reviewer.agent.md` | false | §4.3 Agent 11 |
| F22 | `.github/agents/audit-reviewer.agent.md` | false | §4.3 Agent 12 |

#### D. Layer 3: Orchestrator Agent（1 ファイル）

| # | ファイルパス | definition.md 参照 |
|---|---|---|
| F23 | `.github/agents/orchestrator.agent.md` | §5.1–§5.5 |

#### E. Skills — SKILL.md（5 ファイル）

| # | ファイルパス | definition.md 参照 |
|---|---|---|
| F24 | `.github/skills/stage-gate-review/SKILL.md` | §6.1 |
| F25 | `.github/skills/security-audit/SKILL.md` | §6.2 |
| F26 | `.github/skills/compliance-check/SKILL.md` | §6.3 |
| F27 | `.github/skills/release-readiness/SKILL.md` | §6.4 |
| F28 | `.github/skills/full-review-pipeline/SKILL.md` | §6.5 |

#### F. Skills — 参照ドキュメント（8 ファイル）

| # | ファイルパス | definition.md 参照 |
|---|---|---|
| F29 | `.github/skills/stage-gate-review/references/gate-criteria.md` | §5.2, §5.3 |
| F30 | `.github/skills/stage-gate-review/references/checklist-templates.md` | §5.2 |
| F31 | `.github/skills/security-audit/references/owasp-top10.md` | §6.2 |
| F32 | `.github/skills/security-audit/references/secure-coding-guide.md` | §6.2 |
| F33 | `.github/skills/compliance-check/references/gdpr-checklist.md` | §6.3 |
| F34 | `.github/skills/compliance-check/references/license-policy.md` | §6.3 |
| F35 | `.github/skills/release-readiness/references/go-nogo-criteria.md` | §6.4 |
| F36 | `.github/skills/full-review-pipeline/references/pipeline-flow.md` | §6.5 |

#### G. Skills — スクリプト（1 ファイル）

| # | ファイルパス | definition.md 参照 |
|---|---|---|
| F37 | `.github/skills/security-audit/scripts/dependency-check.sh` | §6.2 |

#### H. Prompts（6 ファイル）

| # | ファイルパス | agent フィールド | definition.md 参照 |
|---|---|---|---|
| F38 | `.github/prompts/generate-test-plan.prompt.md` | qa-manager | §7 |
| F39 | `.github/prompts/generate-threat-model.prompt.md` | security-reviewer | §7 |
| F40 | `.github/prompts/generate-pia.prompt.md` | compliance-reviewer | §7 |
| F41 | `.github/prompts/generate-release-notes.prompt.md` | release-manager | §7 |
| F42 | `.github/prompts/generate-api-spec.prompt.md` | architect | §7 |
| F43 | `.github/prompts/generate-runbook.prompt.md` | infra-ops-reviewer | §7 |

#### I. Hooks（3 ファイル）

| # | ファイルパス | definition.md 参照 |
|---|---|---|
| F44 | `.github/hooks/pre-tool-checks.json` | §3.3 |
| F45 | `.github/hooks/scripts/post-edit-check.sh` | §3.3 |
| F46 | `.github/hooks/scripts/inject-project-context.sh` | §3.3 |

#### J. 監査証跡プレースホルダー（1 ファイル）

| # | ファイルパス | definition.md 参照 |
|---|---|---|
| F47 | `.github/review-reports/.gitkeep` | §9.3 |

#### K. テスト検証用フィクスチャ（5 ディレクトリ + プレースホルダー）

| # | ファイルパス | 用途 | definition.md 参照 |
|---|---|---|---|
| F48 | `.github/test-fixtures/vulnerable-code/.gitkeep` | OWASP Top 10 違反サンプル | §12.2 |
| F49 | `.github/test-fixtures/bad-architecture/.gitkeep` | 循環依存・過度な結合サンプル | §12.2 |
| F50 | `.github/test-fixtures/non-compliant-data/.gitkeep` | 個人情報不適切取扱いサンプル | §12.2 |
| F51 | `.github/test-fixtures/poor-tests/.gitkeep` | カバレッジ不足テストサンプル | §12.2 |
| F52 | `.github/test-fixtures/golden-reports/.gitkeep` | 期待出力レポート（回帰テスト用） | §12.2 |

---

## 2. Phase 別の実装手順

definition.md §8 の実装フェーズ計画に基づき、各 Phase を以下の手順で実施する。

### 2.0 事前準備: ディレクトリ構造の作成

**目的**: 全 Phase で必要となるディレクトリ構造を先に作成する。

| 手順 | 作業内容 | 対象 |
|---|---|---|
| 0-1 | `.github/` 配下の全ディレクトリを一括作成 | D1–D19 |
| 0-2 | `.github/review-reports/.gitkeep` を作成 | F47 |
| 0-3 | `.github/test-fixtures/` 配下の 5 サブディレクトリに `.gitkeep` を配置 | F48–F52 |

**実行コマンド**:
```bash
mkdir -p .github/{instructions,agents,prompts,hooks/scripts,review-reports}
mkdir -p .github/skills/{stage-gate-review/references,security-audit/{scripts,references},compliance-check/references,release-readiness/references,full-review-pipeline/references}
mkdir -p .github/test-fixtures/{vulnerable-code,bad-architecture,non-compliant-data,poor-tests,golden-reports}
touch .github/review-reports/.gitkeep
touch .github/test-fixtures/{vulnerable-code,bad-architecture,non-compliant-data,poor-tests,golden-reports}/.gitkeep
```

---

### 2.1 Phase 1: 基盤構築（5 ファイル）

**目標**: 日常的なコーディング作業に最も効果が高いものから着手。

| 手順 | 対象ファイル | 作業内容 | 前提条件 |
|---|---|---|---|
| 1-1 | F01: `copilot-instructions.md` | §3.1 に基づき全体共通ルールを記述 | なし |
| 1-2 | F02: `java-coding-standards.instructions.md` | YAML frontmatter に `applyTo: "**/*.java"` を設定。§3.2 のチェック観点（命名規則、例外処理、ログ出力、Null Safety、エラーハンドリング）を記述 | F01 の完了 |
| 1-3 | F06: `security-coding.instructions.md` | YAML frontmatter に `applyTo` を `**/service/**/*.java`, `**/controller/**/*.java` で設定。§3.2 のチェック観点（入力検証、SQLi/XSS 防止、認証・認可）を記述 | F01 の完了 |
| 1-4 | F13: `tech-lead.agent.md` | §4.3 Agent 6 の仕様に基づき作成。ペルソナ・チェック観点・ツール（read, search, edit）・統一レポートフォーマット（§9.2）を組み込む | F01, F02 の完了 |
| 1-5 | F12: `security-reviewer.agent.md` | §4.3 Agent 3 の仕様に基づき作成。高精度モデル指定。ツール（read, search, execute）。統一レポートフォーマットを組み込む | F01, F06 の完了 |

**Phase 1 完了時の検証**: → §4.1 参照

---

### 2.2 Phase 2: レビュー体制拡充（13 ファイル）

**目標**: 設計・実装フェーズの主要チェック観点を網羅。

| 手順 | 対象ファイル | 作業内容 | 前提条件 |
|---|---|---|---|
| 2-1 | F11: `architect.agent.md` | §4.3 Agent 2 の仕様。ツール: read, search, web | Phase 1 完了 |
| 2-2 | F16: `oss-reviewer.agent.md` | §4.3 Agent 5 の仕様。ツール: read, search, execute, web | Phase 1 完了 |
| 2-3 | F15: `compliance-reviewer.agent.md` | §4.3 Agent 4 の仕様。高精度モデル指定 | Phase 1 完了 |
| 2-4 | F14: `dba-reviewer.agent.md` | §4.3 Agent 7 の仕様。ツール: read, search | Phase 1 完了 |
| 2-5 | F18: `qa-manager.agent.md` | §4.3 Agent 8 の仕様。UAT チェック項目含む（§4.2） | Phase 1 完了 |
| 2-6 | F17: `ux-accessibility-reviewer.agent.md` | §4.3 Agent 8b の仕様 | Phase 1 完了 |
| 2-7 | F07: `pom-dependency.instructions.md` | `applyTo: "**/pom.xml"` | Phase 1 完了 |
| 2-8 | F05: `test-standards.instructions.md` | `applyTo` に `**/*Test.java` と `**/*Tests.java` を設定 | Phase 1 完了 |
| 2-9 | F09: `spring-config.instructions.md` | `applyTo` に `**/application*.properties` と `**/application*.yml` を設定 | Phase 1 完了 |
| 2-10 | F25: `security-audit/SKILL.md` + 参照ファイル群 | §6.2 の手順を SKILL.md に記述。F31, F32, F37 も同時に作成 | F12 (security-reviewer) の完了 |

**Phase 2 で同時作成する参照ファイル・スクリプト**:
- F31: `.github/skills/security-audit/references/owasp-top10.md`
- F32: `.github/skills/security-audit/references/secure-coding-guide.md`
- F37: `.github/skills/security-audit/scripts/dependency-check.sh`

**Phase 2 完了時の検証**: → §4.2 参照

---

### 2.3 Phase 3: オーケストレーション構築（13 ファイル）

**目標**: 全 Agent を統合し、ステージゲートによる自動レビューパイプラインを完成。

| 手順 | 対象ファイル | 作業内容 | 前提条件 |
|---|---|---|---|
| 3-1 | F19: `performance-reviewer.agent.md` | §4.3 Agent 9 の仕様 | Phase 2 完了 |
| 3-2 | F20: `release-manager.agent.md` | §4.3 Agent 10 の仕様 | Phase 2 完了 |
| 3-3 | F21: `infra-ops-reviewer.agent.md` | §4.3 Agent 11 の仕様 | Phase 2 完了 |
| 3-4 | F22: `audit-reviewer.agent.md` | §4.3 Agent 12 の仕様。高精度モデル指定 | Phase 2 完了 |
| 3-5 | F10: `business-analyst.agent.md` | §4.3 Agent 1 の仕様 | Phase 2 完了 |
| 3-6 | F23: `orchestrator.agent.md` | §5.1–§5.5 の全仕様。agents フィールドに全 13 Agent を列挙。ゲート判定ルール（§5.3）、競合解決プロトコル（§5.4）を本文に記述 | 全 13 Agent (F10–F22) の完了 |
| 3-7 | F24: `stage-gate-review/SKILL.md` + 参照ファイル群 | §6.1 の手順。F29, F30 も同時に作成 | F23 (orchestrator) の完了 |
| 3-8 | F28: `full-review-pipeline/SKILL.md` + 参照ファイル | §6.5 の手順。F36 も同時に作成 | F23 の完了 |
| 3-9 | F27: `release-readiness/SKILL.md` + 参照ファイル | §6.4 の手順。F35 も同時に作成。Agent 化しないステークホルダーのチェック項目（§4.2）を組み込む | F20, F23 の完了 |

**Phase 3 で同時作成する参照ファイル**:
- F29: `.github/skills/stage-gate-review/references/gate-criteria.md`
- F30: `.github/skills/stage-gate-review/references/checklist-templates.md`
- F35: `.github/skills/release-readiness/references/go-nogo-criteria.md`
- F36: `.github/skills/full-review-pipeline/references/pipeline-flow.md`

**Phase 3 完了時の検証**: → §4.3 参照

---

### 2.4 Phase 4: 高度な自動化（15 ファイル）

**目標**: Hooks による強制チェックと Prompt による生成タスクの追加。

| 手順 | 対象ファイル | 作業内容 | 前提条件 |
|---|---|---|---|
| 4-1 | F44: `pre-tool-checks.json` | §3.3 の JSON 定義をそのまま作成 | Phase 3 完了 |
| 4-2 | F45: `post-edit-check.sh` | PostToolUse で呼び出される静的解析スクリプトを作成。実行権限付与 | F44 の完了 |
| 4-3 | F46: `inject-project-context.sh` | SessionStart で呼び出されるコンテキスト注入スクリプトを作成。実行権限付与 | F44 の完了 |
| 4-4 | F03: `sql-schema-review.instructions.md` | `applyTo: "**/*.sql"` | Phase 1 完了 |
| 4-5 | F04: `api-design.instructions.md` | `applyTo: "**/controller/**/*.java"` | Phase 1 完了 |
| 4-6 | F08: `dockerfile-infra.instructions.md` | `applyTo` に `**/Dockerfile` と `**/docker-compose*.yml` を設定 | Phase 1 完了 |
| 4-7 | F38: `generate-test-plan.prompt.md` | agent: qa-manager, argument-hint: 「対象機能名」 | F18 の完了 |
| 4-8 | F39: `generate-threat-model.prompt.md` | agent: security-reviewer, argument-hint: 「対象コンポーネント名」 | F12 の完了 |
| 4-9 | F40: `generate-pia.prompt.md` | agent: compliance-reviewer, argument-hint: 「対象データ種別」 | F15 の完了 |
| 4-10 | F41: `generate-release-notes.prompt.md` | agent: release-manager, argument-hint: 「バージョン番号」 | F20 の完了 |
| 4-11 | F42: `generate-api-spec.prompt.md` | agent: architect, argument-hint: 「対象 API エンドポイント」 | F11 の完了 |
| 4-12 | F43: `generate-runbook.prompt.md` | agent: infra-ops-reviewer, argument-hint: 「対象サービス名」 | F21 の完了 |
| 4-13 | F26: `compliance-check/SKILL.md` + 参照ファイル群 | §6.3 の手順。F33, F34 も同時に作成 | F15 の完了 |

**Phase 4 で同時作成する参照ファイル**:
- F33: `.github/skills/compliance-check/references/gdpr-checklist.md`
- F34: `.github/skills/compliance-check/references/license-policy.md`

**Phase 4 完了時の検証**: → §4.4 参照

---

## 3. 各成果物の作成仕様と手順

### 3.1 Workspace Instructions（F01）

**ファイル**: `.github/copilot-instructions.md`

**記述すべき内容**（definition.md §3.1 より）:
1. プロジェクトの技術スタック: Java 25, Spring Boot 4.1, Spring AI 2.0
2. コーディング規約の要点: 命名規則、パッケージ構成
3. セキュリティの最低基準: OWASP Top 10 の意識、入力検証必須
4. テストカバレッジの目標値
5. コミットメッセージ規約
6. 禁止事項: ハードコードされた秘密情報、未検証の外部入力等

**作成手順**:
1. 上記 6 項目を Markdown で構造化して記述
2. 簡潔に箇条書きで記述し、コンテキスト消費を最小化
3. 各 Agent が参照する共通ルールであるため、曖昧な表現を避ける

---

### 3.2 File Instructions（F02–F09）

**共通フォーマット**:
```markdown
---
applyTo: "<glob pattern>"
---

# <ファイル名> Instructions

## チェック観点
- ...
```

**各ファイルの個別仕様**:

| ファイル ID | applyTo | 記述すべきチェック観点 |
|---|---|---|
| F02 | `**/*.java` | 命名規則（クラス名 PascalCase、メソッド名 camelCase）、例外処理（検査例外 vs 非検査例外の使い分け）、ログ出力（適切なレベル分け）、Null Safety（Optional 活用）、エラーハンドリング（握りつぶし禁止） |
| F03 | `**/*.sql` | 正規化（第 3 正規形以上）、インデックス設計（WHERE 句・JOIN 条件のカラム）、マイグレーション可逆性（UP/DOWN の両方を定義） |
| F04 | `**/controller/**/*.java` | REST 設計原則（リソース指向 URI）、エラーレスポンス形式（RFC 7807 Problem Details）、バージョニング（URI or ヘッダー） |
| F05 | `**/*Test.java`, `**/*Tests.java` | テスト命名（should_期待結果_when_条件）、AAA パターン（Arrange-Act-Assert）、カバレッジ基準（分岐カバレッジ 80% 以上） |
| F06 | `**/service/**/*.java`, `**/controller/**/*.java` | 入力検証（@Valid, @NotNull 等）、SQLi 防止（パラメータバインド必須）、XSS 防止（出力エスケープ）、認証・認可チェック（@PreAuthorize 等） |
| F07 | `**/pom.xml` | 依存関係の最小化（不要な依存を排除）、バージョン固定（SNAPSHOT 禁止）、既知脆弱性（CVE の確認） |
| F08 | `**/Dockerfile`, `**/docker-compose*.yml` | マルチステージビルド、非 root 実行（USER 指定）、ヘルスチェック（HEALTHCHECK 定義） |
| F09 | `**/application*.properties`, `**/application*.yml` | 秘密情報の外部化（環境変数 or Vault）、プロファイル分離（dev/staging/prod）、アクチュエータ設定（公開エンドポイントの制限）、ログレベル（本番は WARN 以上） |

**作成手順**（各ファイル共通）:
1. YAML frontmatter に `applyTo` を正確に記述
2. チェック観点を箇条書きで明確に記述
3. 悪い例と良い例をコードブロックで示す（可能な範囲）
4. definition.md §3.2 の注意事項（java-coding-standards と security-coding の分離意図）を遵守

---

### 3.3 Custom Agents（F10–F22）

**共通フォーマット**:
```markdown
---
description: "<Use when... DO NOT use when...>"
tools: [<tool list>]
model: "<省略 or 高精度モデル指定>"
user-invocable: <true/false>
---

# <Agent名>

## ペルソナ
...

## チェック観点
...

## 入力
...

## 出力フォーマット
（§9.2 統一レポートフォーマット準拠）
...

## 競合解決ルール（該当する場合）
（§5.4 の競合解決プロトコルに基づく自分の優先度）
...
```

**各 Agent の個別仕様**:

| ファイル ID | Agent 名 | tools | model | user-invocable | 特記事項 |
|---|---|---|---|---|---|
| F10 | business-analyst | read, search | デフォルト | false | 要件網羅性、ユーザーストーリー品質、ROI、ガバナンス |
| F11 | architect | read, search, web | デフォルト | true | SOLID 原則、レイヤー構成、API 設計、技術戦略 |
| F12 | security-reviewer | read, search, execute | **高精度モデル** | true | OWASP Top 10、脅威モデル、暗号化、秘密情報管理 |
| F13 | tech-lead | read, search, edit | デフォルト | true | DRY/KISS、Git 運用、ブランチ戦略 |
| F14 | dba-reviewer | read, search | デフォルト | false | N+1 問題、インデックス最適化、マイグレーション安全性 |
| F15 | compliance-reviewer | read, search | **高精度モデル** | false | GDPR、個人情報保護法、SLA 条件（§4.2 ベンダー/SIer） |
| F16 | oss-reviewer | read, search, execute, web | デフォルト | true | ライセンス互換性、CVE、メンテナンス状態 |
| F17 | ux-accessibility-reviewer | read, search | デフォルト | false | WCAG 2.1、i18n、レスポンシブ |
| F18 | qa-manager | read, search, execute | デフォルト | true | UAT 計画チェック（§4.2）を含む |
| F19 | performance-reviewer | read, search | デフォルト | false | 計算量、メモリ、キャッシュ、非同期 |
| F20 | release-manager | read, search | デフォルト | true | Go/No-Go 判定、ロールバック計画 |
| F21 | infra-ops-reviewer | read, search | デフォルト | false | DR 対応、BCP、監視、ヘルスチェック |
| F22 | audit-reviewer | read, search | **高精度モデル** | false | プロセス準拠、トレーサビリティ、承認記録 |

**作成手順**（各 Agent 共通）:
1. definition.md §4.3 の該当 Agent 仕様表から全項目を転記
2. YAML frontmatter を構成（description, tools, model, user-invocable）
3. `description` に「Use when:」と「DO NOT use when:」の両方を記述（§9.1 ルール 6）
4. 出力セクションに §9.2 統一レポートフォーマットをテンプレートとして組み込む
5. 該当する場合、§5.4 競合解決プロトコルの自 Agent の優先度ルールを記述
6. ツールは必要最小限のみ付与（§9.1 ルール 2）

---

### 3.4 Orchestrator Agent（F23）

**ファイル**: `.github/agents/orchestrator.agent.md`

**記述すべき内容**（definition.md §5.1–§5.5 より）:
1. YAML frontmatter:
   - `description`: フェーズゲートレビュー、全体品質チェック等のトリガーワード
   - `argument-hint`: 「Gate番号（1-5）またはfullを指定」
   - `tools`: read, search, agent, todo
   - `agents`: 全 13 Agent のリスト
   - `user-invocable`: true
2. ステージゲート別サブエージェント呼び出しマトリクス（§5.2）
3. ゲート判定ルール（§5.3）: Go / No-Go / Conditional Go / Go with Notes
4. 競合解決プロトコル（§5.4）: 4 つの競合パターンと解決ルール
5. 実行フロー（§5.5）: 並行レビュー → 集約 → 判定の流れ
6. Agent 化しないステークホルダーの扱い（§4.2）: チェックリスト項目として組み込み

**作成手順**:
1. 全 13 Agent が存在することを確認してから作成開始
2. §5.2 のゲート別マトリクスを正確に転記
3. §5.3 の判定ルールを if-then 形式で明確に記述
4. §5.4 の競合解決を優先度リストとして記述
5. Fail-Safe 原則（§1.3 ルール 6）を明示

---

### 3.5 Skills — SKILL.md（F24–F28）

**共通フォーマット**:
```markdown
---
name: "<skill-name>"
description: "<Use when...>"
argument-hint: "<引数のヒント>"
---

# <Skill名>

## 目的
...

## 手順
1. ...
2. ...

## 参照ドキュメント
- [ドキュメント名](./references/xxx.md)
```

**各 SKILL.md の個別仕様**:

| ファイル ID | Skill 名 | 手順数 | 参照ファイル数 | 特記事項 |
|---|---|---|---|---|
| F24 | stage-gate-review | 8 手順 | 2 (gate-criteria.md, checklist-templates.md) | Orchestrator 経由で Agent を呼び出す |
| F25 | security-audit | 7 手順 | 2 (owasp-top10.md, secure-coding-guide.md) + 1 script | dependency-check.sh を execute で呼び出す |
| F26 | compliance-check | 5 手順 | 2 (gdpr-checklist.md, license-policy.md) | — |
| F27 | release-readiness | 10 手順 | 1 (go-nogo-criteria.md) | §4.2 の Agent 化しないステークホルダー項目を含む |
| F28 | full-review-pipeline | 7 手順 | 1 (pipeline-flow.md) | 全 13 Agent を呼び出す |

**作成手順**（各 Skill 共通）:
1. YAML frontmatter を definition.md §6 から正確に転記
2. 手順を番号付きリストで記述（definition.md の手順をそのまま採用）
3. 参照ファイルへのリンクを相対パスで記述
4. 500 行以内に収める（§9.1 ルール 4）
5. 参照ドキュメントを同時に作成

---

### 3.6 Skills — 参照ドキュメント（F29–F36）

| ファイル ID | ファイルパス | 記述すべき内容 |
|---|---|---|
| F29 | `gate-criteria.md` | §5.2 のゲート別 Agent セットと§5.3 の判定基準を表形式で記述 |
| F30 | `checklist-templates.md` | Gate 1〜5 各ゲートのチェックリストテンプレート。§4.2 の Agent 化しないステークホルダー項目を含む |
| F31 | `owasp-top10.md` | OWASP Top 10 (2021) の各項目と、Java/Spring での具体的チェックポイント |
| F32 | `secure-coding-guide.md` | Java/Spring Boot でのセキュアコーディングガイドライン。入力検証、認証・認可、暗号化、秘密情報管理 |
| F33 | `gdpr-checklist.md` | GDPR / 個人情報保護法のチェックリスト。データ分類、同意管理、保持期間、削除権 |
| F34 | `license-policy.md` | OSS ライセンスポリシー。許可/禁止ライセンス一覧、互換性マトリクス |
| F35 | `go-nogo-criteria.md` | リリース Go/No-Go の判定基準。§6.4 の 10 手順に対応するチェック項目 |
| F36 | `pipeline-flow.md` | フルレビューパイプラインのフロー図。13 Agent の実行順序・並行度・集約方法 |

---

### 3.7 Skills — スクリプト（F37）

**ファイル**: `.github/skills/security-audit/scripts/dependency-check.sh`

**記述すべき内容**:
1. `mvn dependency:tree` の実行と結果パース
2. 既知脆弱性データベースとの照合（OWASP Dependency-Check または同等のツール呼び出し）
3. 結果を標準出力に JSON or Markdown 形式で出力
4. 実行権限 (`chmod +x`) の付与

---

### 3.8 Prompts（F38–F43）

**共通フォーマット**:
```markdown
---
agent: "<agent-name>"
argument-hint: "<引数のヒント>"
description: "<用途の説明>"
---

# <Prompt名>

<プロンプト本文>
```

**各 Prompt の個別仕様**:

| ファイル ID | Prompt 名 | agent | argument-hint | プロンプト内容の概要 |
|---|---|---|---|---|
| F38 | generate-test-plan | qa-manager | 「対象機能名」 | 対象機能のテスト計画書雛形を生成。テスト範囲、テスト種別、テストケース概要、合格基準 |
| F39 | generate-threat-model | security-reviewer | 「対象コンポーネント名」 | STRIDE モデルに基づく脅威モデルを生成。脅威の識別、リスク評価、対策案 |
| F40 | generate-pia | compliance-reviewer | 「対象データ種別」 | プライバシー影響評価を生成。データフロー、リスク評価、緩和策 |
| F41 | generate-release-notes | release-manager | 「バージョン番号」 | リリースノートを自動生成。変更点、修正バグ、既知の問題、アップグレード手順 |
| F42 | generate-api-spec | architect | 「対象 API エンドポイント」 | API 仕様書雛形を生成。エンドポイント定義、リクエスト/レスポンス形式、エラーハンドリング |
| F43 | generate-runbook | infra-ops-reviewer | 「対象サービス名」 | 運用手順書雛形を生成。起動/停止手順、障害対応、監視項目、エスカレーション |

---

### 3.9 Hooks（F44–F46）

#### F44: `pre-tool-checks.json`

- definition.md §3.3 の JSON をそのまま使用
- 3 つのフックイベント: PreToolUse, PostToolUse, SessionStart
- スクリプトパスが正しいことを確認

#### F45: `post-edit-check.sh`

**記述すべき内容**:
1. 編集されたファイルの拡張子を判定
2. Java ファイルの場合: コンパイルチェック（`mvn compile`）
3. 秘密情報のパターン検出（API キー、パスワードの正規表現マッチ）
4. 結果を JSON 形式の `systemMessage` として標準出力
5. タイムアウト 30 秒以内で完了すること
6. 実行権限の付与

#### F46: `inject-project-context.sh`

**記述すべき内容**:
1. 現在の Git ブランチ名の取得
2. 未コミット変更の有無
3. pom.xml からプロジェクトバージョンの取得
4. 結果を JSON 形式の `systemMessage` として標準出力
5. タイムアウト 10 秒以内で完了すること
6. 実行権限の付与

---

## 4. 検証チェックリスト

### 4.0 全体構造チェック（Phase 0 完了後） — ✅ 完了 (2026-03-18)

| # | チェック項目 | 確認方法 | 合格基準 | 結果 |
|---|---|---|---|---|
| C00-1 | 全ディレクトリが存在する（D1–D19 + 暗黙の `.github/skills/` + test-fixtures サブディレクトリ 5 つ） | `find .github -type d \| sort` | 25 ディレクトリが全て存在 | ✅ 25 確認 |
| C00-2 | `.gitkeep` ファイルが所定の場所に存在する | `find .github -name ".gitkeep"` | 6 ファイル（review-reports + test-fixtures 5 つ） | ✅ 6 確認 |
| C00-3 | definition.md §2 のディレクトリツリーと一致する | 目視比較 | 完全一致 | ✅ 一致 |

---

### 4.1 Phase 1 完了チェック（基盤構築） — ✅ 完了 (2026-03-18)

| # | チェック項目 | 確認方法 | 合格基準 | 結果 |
|---|---|---|---|---|
| C01-1 | F01〜F02, F06, F12〜F13 の 5 ファイルが存在する | `ls -la` | 全 5 ファイル存在 | ✅ 5 確認 |
| C01-2 | `copilot-instructions.md` に技術スタック（Java 25, Spring Boot 4.1, Spring AI 2.0）が明記されている | `grep` で検索 | 3 つのキーワード全てヒット | ✅ 全ヒット |
| C01-3 | `copilot-instructions.md` に §3.1 の 6 項目が全て含まれている | 目視確認 | 6 項目全て記載 | ✅ 6 項目確認 |
| C01-4 | `java-coding-standards.instructions.md` の `applyTo` が `**/*.java` である | YAML frontmatter を確認 | 完全一致 | ✅ 一致 |
| C01-5 | `security-coding.instructions.md` の `applyTo` に `**/service/**/*.java` と `**/controller/**/*.java` が含まれる | YAML frontmatter を確認 | 両方のパターンが存在 | ✅ 両方存在 |
| C01-6 | `tech-lead.agent.md` のツールが `read, search, edit` のみである | YAML frontmatter を確認 | 余分なツールがない | ✅ 正確 |
| C01-7 | `security-reviewer.agent.md` に高精度モデルが指定されている | YAML frontmatter を確認 | model フィールドが設定済み | ✅ o4-mini |
| C01-8 | 全 Agent の出力セクションが §9.2 統一レポートフォーマットに準拠している | テンプレート比較 | サマリー・指摘事項テーブル・競合フラグの構造が一致 | ✅ 準拠 |
| C01-9 | 全 Agent の `description` に「Use when:」と「DO NOT use when:」が含まれる | `grep` で検索 | 両方のパターンがヒット | ✅ 全ヒット |

---

### 4.2 Phase 2 完了チェック（レビュー体制拡充） — ✅ 完了 (2026-03-18)

| # | チェック項目 | 確認方法 | 合格基準 | 結果 |
|---|---|---|---|---|
| C02-1 | Phase 2 対象の 13 ファイルが全て存在する | `ls` で個別確認 | 13 ファイル全て存在 | ✅ 13 確認 |
| C02-2 | Agent ファイル 6 つ（F11, F14–F18）の YAML frontmatter が正しい | 各ファイルの tools, model, user-invocable を確認 | definition.md §4.3 と一致 | ✅ 一致 |
| C02-3 | `compliance-reviewer.agent.md` に高精度モデルが指定されている | YAML frontmatter を確認 | model フィールドが設定済み | ✅ o4-mini |
| C02-4 | `compliance-reviewer.agent.md` に SLA・契約条件チェックが含まれている | 本文を確認 | 記載あり | ✅ 2箇所 |
| C02-5 | `qa-manager.agent.md` に UAT 計画チェック項目が含まれている | 本文を確認 | 記載あり | ✅ 2箇所 |
| C02-6 | File Instructions 3 ファイル（F05, F07, F09）の `applyTo` が正しい | YAML frontmatter を確認 | definition.md §3.2 と一致 | ✅ 一致 |
| C02-7 | `security-audit/SKILL.md` が 500 行以内である | `wc -l` | 500 行以下 | ✅ 29行 |
| C02-8 | `security-audit/SKILL.md` が参照ファイルを正しくリンクしている | リンク先ファイルの存在確認 | 3 ファイル全て存在 | ✅ 3ファイル |
| C02-9 | `dependency-check.sh` に実行権限がある | `ls -la` | `-rwxr-xr-x` | ✅ -rwxr-xr-x |
| C02-10 | `security-audit/SKILL.md` の手順数が definition.md §6.2 と一致する | 手順番号を数える | 7 手順 | ✅ 7手順 |

---

### 4.3 Phase 3 完了チェック（オーケストレーション構築） — ✅ 完了 (2026-03-18)

| # | チェック項目 | 確認方法 | 合格基準 | 結果 |
|---|---|---|---|---|
| C03-1 | 全 14 Agent ファイルが存在する | `find` | 14 | ✅ 14 |
| C03-2 | `orchestrator.agent.md` の `agents` に全 13 Agent が列挙されている | YAML 確認 | 13 エントリ | ✅ 13 確認 |
| C03-3 | ゲート別マトリクス（Gate 1〜5 + 全体レビュー）が記述されている | 本文確認 | 6 つのゲート定義 | ✅ 6 定義 |
| C03-4 | ゲート判定ルール（Go / No-Go / Conditional Go / Go with Notes）が記述されている | 本文確認 | 4 つの条件-判定ペア | ✅ 4 ペア |
| C03-5 | 競合解決プロトコル（4 パターン）が記述されている | 本文確認 | 4 パターン | ✅ 4 パターン |
| C03-6 | `audit-reviewer.agent.md` に高精度モデルが指定されている | YAML 確認 | model 設定済み | ✅ o4-mini |
| C03-7 | Skill 3 つの手順数が一致する | 手順数カウント | stage-gate: 8, release-readiness: 10, full-review: 7 | ✅ 全一致 |
| C03-8 | `release-readiness/SKILL.md` に Agent 化しないステークホルダー 4 項目が含まれている | 本文確認 | 4 項目 | ✅ 4 項目確認 |
| C03-9 | 参照ファイル 4 つが全て存在する | `find` | 4 ファイル | ✅ 4 ファイル |

---

### 4.4 Phase 4 完了チェック（高度な自動化） — ✅ 完了 (2026-03-18)

| # | チェック項目 | 確認方法 | 合格基準 | 結果 |
|---|---|---|---|---|
| C04-1 | Hook 定義（F44）の JSON が正しい構文である | `python3 -m json.tool` | エラーなし | ✅ PASS |
| C04-2 | Hook 定義に 3 つのイベントが定義されている | JSON 内容を確認 | 3 イベント全て存在 | ✅ 3 イベント |
| C04-3 | Hook スクリプト 2 つに実行権限がある | `ls -la` | `-rwxr-xr-x` | ✅ -rwxr-xr-x |
| C04-4 | Hook スクリプトのパスが JSON 内の `command` と一致する | 相互参照 | パスが一致 | ✅ 一致 |
| C04-5 | 残り File Instructions 3 つが存在し `applyTo` が正しい | YAML frontmatter を確認 | definition.md §3.2 と一致 | ✅ 一致 |
| C04-6 | Prompt 6 ファイルが全て存在する | `find` | 6 | ✅ 6 |
| C04-7 | 各 Prompt の `agent` フィールドが正しい Agent を参照している | YAML frontmatter を確認 | §7 の対応表と一致 | ✅ 全一致 |
| C04-8 | 各 Prompt の `agent` 参照先 Agent ファイルが存在する | 相互参照 | 6 つ全て存在 | ✅ 6/6 OK |
| C04-9 | `compliance-check/SKILL.md` + 参照ファイル 2 つが存在する | `find` で確認 | 3 ファイル全て存在 | ✅ 3ファイル |
| C04-10 | `compliance-check/SKILL.md` の手順数が一致する | 手順番号を数える | 5 手順 | ✅ 5手順 |

---

### 4.5 最終統合チェック（全 Phase 完了後） — ✅ 完了 (2026-03-18)

#### A. ファイル数チェック

| # | チェック項目 | 確認コマンド | 期待値 | 結果 |
|---|---|---|---|---|
| C05-1 | Workspace Instructions | `find .github -maxdepth 1 -name "copilot-instructions.md" \| wc -l` | 1 | ✅ 1 |
| C05-2 | File Instructions の総数 | `find .github/instructions -name "*.instructions.md" \| wc -l` | 8 | ✅ 8 |
| C05-3 | Agent ファイルの総数 | `find .github/agents -name "*.agent.md" \| wc -l` | 14 | ✅ 14 |
| C05-4 | SKILL.md の総数 | `find .github/skills -name "SKILL.md" \| wc -l` | 5 | ✅ 5 |
| C05-5 | 参照ドキュメントの総数 | `find .github/skills -path "*/references/*" -type f \| wc -l` | 8 | ✅ 8 |
| C05-6 | スクリプトの総数（skills + hooks） | `find .github -name "*.sh" -type f \| wc -l` | 3 | ✅ 3 |
| C05-7 | Prompt ファイルの総数 | `find .github/prompts -name "*.prompt.md" \| wc -l` | 6 | ✅ 6 |
| C05-8 | Hook 定義ファイルの総数 | `find .github/hooks -name "*.json" \| wc -l` | 1 | ✅ 1 |
| C05-9 | 全ファイル総数（.gitkeep 除く） | `find .github -type f ! -name ".gitkeep" \| wc -l` | 46 | ✅ 46 |
| C05-10 | 全ファイル総数（.gitkeep 含む） | `find .github -type f \| wc -l` | 52 | ✅ 52 |

#### B. 相互参照整合性チェック

| # | チェック項目 | 確認方法 | 合格基準 | 結果 |
|---|---|---|---|---|
| C06-1 | `orchestrator.agent.md` の `agents` リストと実在 Agent ファイルの一致 | agents リスト内の名前と `.github/agents/` のファイル名を比較 | 13 Agent 全てファイルが存在 | ✅ 13/13 OK |
| C06-2 | 各 Prompt の `agent` フィールドが実在する Agent を参照している | 6 Prompt 全ての agent 値を確認 | 全て対応するファイルが存在 | ✅ 6/6 OK |
| C06-3 | `pre-tool-checks.json` のスクリプトパスが実在する | `command` フィールドのパスを確認 | 2 スクリプト全て存在 | ✅ 2/2 OK |
| C06-4 | 各 SKILL.md の参照パスが実在する | 相対パスを解決してファイル存在確認 | 全リンク先が存在 | ✅ 全存在 |
| C06-5 | `security-audit/SKILL.md` の `dependency-check.sh` パスが実在する | 相対パスを解決してファイル存在確認 | ファイル存在 | ✅ 存在 |

#### C. 品質基準チェック（§9.1 準拠）

| # | チェック項目 | 確認方法 | 合格基準 | 結果 |
|---|---|---|---|---|
| C07-1 | 全 Agent の `description` に「Use when:」が含まれる | `grep -l "Use when"` | 14 | ✅ 14 |
| C07-2 | 全 Agent の `description` に「DO NOT use when:」が含まれる | `grep -l "DO NOT use when"` | 14 | ✅ 14 |
| C07-3 | 高精度モデル指定が 3 Agent のみ | `grep -l "model:"` | security-reviewer, compliance-reviewer, audit-reviewer | ✅ 3 のみ |
| C07-4 | 全 SKILL.md が 500 行以内 | `wc -l` | 全て 500 行以下 | ✅ 最大 33行 |
| C07-5 | 全 Agent の出力に統一レポートフォーマットが含まれる | 「サマリー」「指摘事項」セクション確認 | 全 13 Agent で存在 | ✅ 全存在 |
| C07-6 | 全シェルスクリプトに実行権限がある | `find -perm +111` | 3 | ✅ 3 |
| C07-7 | Hook のタイムアウトが適切 | JSON の timeout 値を確認 | PreToolUse: 5, PostToolUse: 30, SessionStart: 10 | ✅ 正確 |

#### D. 内容正確性チェック

| # | チェック項目 | 確認方法 | 合格基準 | 結果 |
|---|---|---|---|---|
| C08-1 | ゲート別 Agent セットが definition.md と一致する | orchestrator.agent.md を definition.md と比較 | Gate 1〒5 + 全体レビューの Agent リストが完全一致 | ✅ 完全一致 |
| C08-2 | 競合解決プロトコルの 4 パターンが正確 | orchestrator.agent.md を definition.md と比較 | 4 パターン全てが正確 | ✅ 4パターン正確 |
| C08-3 | Agent 化しないステークホルダー 5 項目が適切な場所に組み込まれている | 各ファイルを確認 | 5 項目全て適切な場所に記載 | ✅ 5項目確認 |
| C08-4 | `user-invocable` の値が definition.md と一致する | 各 Agent の YAML を確認 | true: 6, false: 7, orchestrator: true | ✅ 全一致 |
| C08-5 | 各 File Instructions の `applyTo` パターンが一致する | 8 ファイル全ての YAML を確認 | 全パターンが完全一致 | ✅ 完全一致 |

#### E. ディレクトリ構造チェック

| # | チェック項目 | 確認コマンド | 合格基準 | 結果 |
|---|---|---|---|---|
| C09-1 | `.github/` 配下のディレクトリ構造が definition.md §2 と一致する | `find .github -type d \| sort` | 完全一致 | ✅ 25 ディレクトリ一致 |
| C09-2 | test-fixtures のサブディレクトリが 5 つ存在する | `ls .github/test-fixtures/` | 5 ディレクトリ | ✅ 5 確認 |
| C09-3 | review-reports ディレクトリが存在し `.gitkeep` がある | `ls -la .github/review-reports/` | .gitkeep が存在 | ✅ 存在 |

---

## 5. 依存関係と作業順序の制約

### 5.1 依存関係グラフ

```
Phase 0（ディレクトリ構造）
     │
     ▼
Phase 1（基盤）
     │  F01: copilot-instructions.md ← 全ての基盤
     │  F02: java-coding-standards   ← F01 に依存
     │  F06: security-coding         ← F01 に依存
     │  F13: tech-lead               ← F01, F02 に依存
     │  F12: security-reviewer       ← F01, F06 に依存
     │
     ▼
Phase 2（レビュー体制拡充）
     │  F11, F14–F18: 各 Agent       ← Phase 1 に依存
     │  F05, F07, F09: Instructions ← F01 に依存
     │  F25: security-audit Skill   ← F12 に依存
     │  F31, F32, F37: 参照+スクリプト ← F25 と同時作成
     │
     ▼
Phase 3（オーケストレーション）
     │  F19–F22, F10: 残り Agent     ← Phase 2 に依存
     │  F23: orchestrator            ← 全 13 Agent (F10–F22) に依存 ★最重要依存
     │  F24: stage-gate-review Skill ← F23 に依存
     │  F27, F28: Skill              ← F23 に依存
     │  F29, F30, F35, F36: 参照     ← 対応 Skill と同時作成
     │
     ▼
Phase 4（高度な自動化）
     │  F44: pre-tool-checks.json    ← Phase 3 に依存
     │  F45, F46: Hook スクリプト    ← F44 に依存
     │  F03, F04, F08: Instructions ← F01 に依存
     │  F38–F43: Prompts            ← 対応 Agent の完了に依存
     │  F26: compliance-check Skill ← F15 に依存
     │  F33, F34: 参照              ← F26 と同時作成
```

### 5.2 クリティカルパス

最長の依存チェーン:

```
F01 → F06 → F12 → F25(+F31,F32,F37) → Phase 2 完了
  → F19–F22,F10 → F23(orchestrator) → F24(+F29,F30) → Phase 3 完了
  → F44 → F45,F46 → Phase 4 完了
```

### 5.3 並行作業可能なタスク

以下のタスクは同一 Phase 内で並行して作業可能:

| Phase | 並行可能なグループ |
|---|---|
| Phase 1 | F02 と F06（互いに独立） |
| Phase 2 | F11, F14–F18（各 Agent は互いに独立）/ F05, F07, F09（各 Instructions は互いに独立） |
| Phase 3 | F19–F22, F10（orchestrator 以外の Agent は互いに独立） |
| Phase 4 | F03, F04, F08（互いに独立）/ F38–F43（互いに独立）/ F26 は独立 |

---

## 付録 A: 最終確認用コマンド集

全 Phase 完了後に以下のコマンドを順次実行し、全チェックを一括で実施する。

```bash
# === ファイル数チェック ===
echo "=== File Count Check ==="
echo "Workspace Instructions: $(find .github -maxdepth 1 -name 'copilot-instructions.md' | wc -l) (expected: 1)"
echo "File Instructions: $(find .github/instructions -name '*.instructions.md' | wc -l) (expected: 8)"
echo "Agents: $(find .github/agents -name '*.agent.md' | wc -l) (expected: 14)"
echo "SKILL.md: $(find .github/skills -name 'SKILL.md' | wc -l) (expected: 5)"
echo "References: $(find .github/skills -path '*/references/*' -type f | wc -l) (expected: 8)"
echo "Scripts: $(find .github -name '*.sh' -type f | wc -l) (expected: 3)"
echo "Prompts: $(find .github/prompts -name '*.prompt.md' | wc -l) (expected: 6)"
echo "Hooks JSON: $(find .github/hooks -name '*.json' | wc -l) (expected: 1)"
echo "Total (excl .gitkeep): $(find .github -type f ! -name '.gitkeep' | wc -l) (expected: 46)"
echo "Total (incl .gitkeep): $(find .github -type f | wc -l) (expected: 52)"

# === YAML frontmatter チェック ===
echo ""
echo "=== Agent Description Check ==="
for f in .github/agents/*.agent.md; do
  name=$(basename "$f")
  has_use=$(grep -c "Use when" "$f" 2>/dev/null || echo 0)
  has_donot=$(grep -c "DO NOT use when" "$f" 2>/dev/null || echo 0)
  echo "$name: Use when=$has_use, DO NOT use when=$has_donot"
done

# === 高精度モデルチェック ===
echo ""
echo "=== High-Accuracy Model Check ==="
grep -l "model:" .github/agents/*.agent.md 2>/dev/null || echo "No model specifications found"

# === スクリプト実行権限チェック ===
echo ""
echo "=== Script Permission Check ==="
find .github -name "*.sh" -exec ls -la {} \;

# === Hook JSON 構文チェック ===
echo ""
echo "=== Hook JSON Syntax Check ==="
python3 -m json.tool .github/hooks/pre-tool-checks.json > /dev/null 2>&1 && echo "PASS" || echo "FAIL"

# === SKILL.md 行数チェック ===
echo ""
echo "=== SKILL.md Line Count Check (max 500) ==="
wc -l .github/skills/*/SKILL.md

# === 相互参照チェック ===
echo ""
echo "=== Cross-Reference Check ==="
echo "Checking orchestrator agents list..."
for agent_name in business-analyst architect security-reviewer compliance-reviewer oss-reviewer tech-lead dba-reviewer qa-manager ux-accessibility-reviewer performance-reviewer release-manager infra-ops-reviewer audit-reviewer; do
  if [ -f ".github/agents/${agent_name}.agent.md" ]; then
    echo "  orchestrator -> ${agent_name}.agent.md: OK"
  else
    echo "  orchestrator -> ${agent_name}.agent.md: MISSING!"
  fi
done
echo "Checking prompt agent references..."
for f in .github/prompts/*.prompt.md; do
  agent=$(grep "^agent:" "$f" | sed 's/agent: *//' | tr -d '"' | tr -d "'")
  if [ -f ".github/agents/${agent}.agent.md" ]; then
    echo "  $(basename $f) -> ${agent}.agent.md: OK"
  else
    echo "  $(basename $f) -> ${agent}.agent.md: MISSING!"
  fi
done

# === ディレクトリ数チェック ===
echo ""
echo "=== Directory Count Check ==="
echo "Directories: $(find .github -type d | wc -l) (expected: 25)"

# === SKILL.md 手順数チェック ===
echo ""
echo "=== SKILL.md Step Count Check ==="
for skill_dir in stage-gate-review security-audit compliance-check release-readiness full-review-pipeline; do
  count=$(grep -c "^[0-9]\+\." ".github/skills/${skill_dir}/SKILL.md" 2>/dev/null || echo 0)
  echo "  ${skill_dir}: ${count} steps"
done

# === ディレクトリ構造チェック ===
echo ""
echo "=== Directory Structure ==="
find .github -type d | sort
```

---

## 付録 B: 成果物 × definition.md セクション トレーサビリティマトリクス

| definition.md セクション | 対応する成果物 | ファイル ID |
|---|---|---|
| §2 ディレクトリ構成 | 全ディレクトリ | D1–D19 |
| §3.1 Workspace Instructions | copilot-instructions.md | F01 |
| §3.2 File Instructions | 8 つの .instructions.md | F02–F09 |
| §3.3 Hooks | pre-tool-checks.json + 2 スクリプト | F44–F46 |
| §4.3 Agent 1 | business-analyst.agent.md | F10 |
| §4.3 Agent 2 | architect.agent.md | F11 |
| §4.3 Agent 3 | security-reviewer.agent.md | F12 |
| §4.3 Agent 4 | compliance-reviewer.agent.md | F15 |
| §4.3 Agent 5 | oss-reviewer.agent.md | F16 |
| §4.3 Agent 6 | tech-lead.agent.md | F13 |
| §4.3 Agent 7 | dba-reviewer.agent.md | F14 |
| §4.3 Agent 8 | qa-manager.agent.md | F18 |
| §4.3 Agent 8b | ux-accessibility-reviewer.agent.md | F17 |
| §4.3 Agent 9 | performance-reviewer.agent.md | F19 |
| §4.3 Agent 10 | release-manager.agent.md | F20 |
| §4.3 Agent 11 | infra-ops-reviewer.agent.md | F21 |
| §4.3 Agent 12 | audit-reviewer.agent.md | F22 |
| §5.1–§5.5 Orchestrator | orchestrator.agent.md | F23 |
| §6.1 stage-gate-review | SKILL.md + references 2 | F24, F29, F30 |
| §6.2 security-audit | SKILL.md + references 2 + script 1 | F25, F31, F32, F37 |
| §6.3 compliance-check | SKILL.md + references 2 | F26, F33, F34 |
| §6.4 release-readiness | SKILL.md + references 1 | F27, F35 |
| §6.5 full-review-pipeline | SKILL.md + references 1 | F28, F36 |
| §7 Prompts | 6 つの .prompt.md | F38–F43 |
| §9.2 統一レポートフォーマット | 全 Agent の出力セクションに組み込み | F10–F22 |
| §9.3 監査証跡 | review-reports/.gitkeep | F47 |
| §12.2 テスト検証用サンプル | test-fixtures 5 ディレクトリ | F48–F52 |
| §4.2 Agent 化しないステークホルダー | orchestrator, release-readiness, qa-manager, compliance-reviewer に分散組み込み | F23, F27, F18, F15 |
