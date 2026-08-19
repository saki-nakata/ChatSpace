---
name: quality-check
description: プロジェクト全体の品質チェック（静的解析）を実行する。ESLint・TypeScript・Checkstyle・ビルド確認・Terraform検証・GitHub Actionsワークフロー検証、およびドキュメントと実装の差異確認を行い、決められた形式でレポートする。「品質チェック」「静的解析」「lint」「ビルド確認」を求められたときに使用する。
---

プロジェクト全体の品質チェック（静的解析）を実行してください。以下の手順を必ず守ること。

## 実行前の確認

プロジェクトのディレクトリ構成を確認し、存在するものだけを実行すること。

---

## 1. フロントエンド（JavaScript / TypeScript）

本プロジェクトのフロントエンドは `apps/web`（React + Vite）のみ。
`pnpm-workspace.yaml` もこの1パッケージだけを含む。

```bash
# ESLint チェック
pnpm lint

# TypeScript コンパイルチェック（tsconfig.json がある場合）
pnpm typecheck
# または
pnpm exec tsc --noEmit
```

`lint` スクリプトが未定義の場合は ESLint を直接実行する:
```bash
pnpm exec eslint .
```

---

## 2. バックエンド

### Java / Spring Boot（`apps/api/build.gradle.kts` が存在する場合。本プロジェクトの確定スタック）

Checkstyle/Maven は不採用（Spotless + ArchUnit を採用、計画書§1.1）。`./gradlew build` に
`spotlessCheck`（フォーマット）・ArchUnitテスト（3層アーキテクチャ制約）・単体/統合テストがすべて
内包されるため、これ1コマンドで完結する。

```bash
cd apps/api

# フォーマット・ArchUnit・テスト・ビルドを一括実行（checkタスクの依存経由でspotlessCheckも走る）
./gradlew build --console=plain

# フォーマット違反のみを自動修正したい場合
./gradlew spotlessApply
```

### Flyway マイグレーション検証（`apps/api/src/main/resources/db/migration/` が存在する場合）

```bash
cd apps/api
# 適用済みマイグレーションファイルが改変されていないかを検証する
./gradlew flywayValidate
```

`git diff` で `V{既存の番号}__*.sql` ファイルの内容そのものが変更されている場合は指摘すること
（変更が必要なら新しい番号のファイルを追加するのが正しい対応。DB設計書§1.3参照）。

### OpenAPI 生成物のドリフト検出（フェーズ8以降、`apps/api` に springdoc-openapi 導入後）

```bash
cd apps/api
./gradlew generateOpenApiDocs
git diff --exit-code -- openapi.json  # 差分があればコミット漏れ
```

### 認可クリティカルテストの確認（`apps/api/src/test/java/.../authorization/` が存在する場合）

上記の `./gradlew build` 実行結果のうち、`authorization` パッケージ配下のテストクラスの
成否は必ず個別に報告すること（静的解析より重要度が高い項目のため、レポートの「バックエンド」
節で他のテストとまとめず明示する）。テスト設計書.md の認可クリティカルテスト一覧と対応する。

### Python（pyproject.toml / setup.py が存在する場合）

```bash
# ruff（設定がある場合）
ruff check .

# または flake8
flake8 .
```

---

## 3. ビルド確認

### フロントエンド（package.json が存在する場合）

```bash
pnpm build
```

### バックエンド Java / Spring Boot（`apps/api`）

上記2節の `./gradlew build` に静的解析・テスト・ビルドがすべて含まれるため、ここで改めて
テスト抜きビルドを実行する必要はない（本プロジェクトでは「認可クリティカルテストは lint より
重要度が高い」という方針のため、テストをスキップしたビルド確認は行わない）。

### バックエンド Maven（pom.xml が存在する場合。本プロジェクトでは不使用）

```bash
mvn package -DskipTests
```

---

## 4. インフラ（Terraform）

`infra/terraform/` または `terraform/` ディレクトリが存在する場合のみ実行。

```bash
terraform fmt -check -recursive
terraform validate
```

---

## 5. GitHub Actions ワークフロー

`.github/workflows/` ディレクトリが存在する場合のみ実行。

actionlint が利用可能かを確認し、利用できる場合のみ実行する:

```bash
command -v actionlint && actionlint
```

actionlint が利用できない場合は、`.github/workflows/` 配下の全ファイルを読み込み、以下を目視で確認すること。

**確認観点：**

- YAML構文が正しいか
- `permissions` が最小権限の原則に従っているか（不要な `write` 権限が付いていないか）
- シークレットが `run:` 内やログに露出していないか（`echo` などでの標準出力への出力、成果物やアーティファクトへの書き出し）
- **スクリプトインジェクション**が起こり得ないか
  - PRタイトル・Issue本文・コメント・ブランチ名など、外部から自由に書き換えられる入力を `run:` 内で `${{ }}` により直接展開していないか
  - 該当する場合、`env:` で一度変数に受けてから `"$VAR"` として参照する形になっているか
  - 例（危険）: `run: echo "${{ github.event.pull_request.title }}"`
  - 例（安全）: `env: { TITLE: "${{ github.event.pull_request.title }}" }` として `run: echo "$TITLE"`
- アクションの参照方式が一貫しているか。本プロジェクトの方針: `actions/checkout` や `anthropics/claude-code-action` など公式・信頼できる publisher のアクションはタグ参照（`@v6` 等）で可とし、SHA固定は必須としない（学習用途のプロジェクトでは運用コストに見合わないため）。ただし出所の不明確なサードパーティアクションを新たに追加する場合は SHA 固定を検討する
- `pull_request_target` を使用している場合、PRのコードをチェックアウトして実行していないか
- フォークからのPRでシークレットを要するジョブが実行されないようガードされているか
- `timeout-minutes` が設定されているか（ジョブの暴走防止）
- `concurrency` 設定により、不要な多重実行が発生しないようになっているか

---

## 6. 仕様書・要件定義書との差異確認

`docs/` ディレクトリまたは `README.md` が存在する場合のみ実行すること。

**`docs/` は要件定義書・DB設計書・画面設計書・画面遷移図・シーケンス図・インフラ構成書・
テスト設計書・ログ運用設計書・機能定義書（11ファイル）の計19ファイルと分量が大きいため、
毎回全ファイルを読み込むのではなく、`git diff` で変更されたコードパッケージ・機能から
対応するドキュメントを特定し、その範囲のみを対象とする**（例: `apps/api/src/main/java/com/chatspace/api/dm/` 配下の変更なら `docs/機能定義書/DM機能定義書.md` と `docs/要件定義書.md`・`docs/DB設計書.md` の関連箇所のみ確認すれば足りる）。差異確認の対象を絞れない大きな変更（新機能追加等）の場合のみ、関連する複数ドキュメントを横断的に確認する。`README.md` は毎回対象に含めてよい。

**確認観点：**

- ドキュメントに記載された機能・仕様のうち、未実装のものはないか
- 実装済み機能がドキュメントの記載と異なっていないか（API・DB・画面・ビジネスロジック）
- `README.md` に記載されたセットアップ手順・機能説明が現状と一致しているか

---

## 結果のレポート

チェックが完了したら、以下の形式でまとめて報告すること。

```
## 品質チェック結果

### フロントエンド（ESLint）
- 結果: PASS / FAIL
- エラー数: X 件
- 警告数: X 件
- 問題があれば内容を列挙

### フロントエンド（TypeScript）
- 結果: PASS / FAIL
- エラーがあれば内容を列挙

### バックエンド（Spotless + ArchUnit + テスト + ビルド、`./gradlew build`）
- 結果: PASS / FAIL / 対象外
- Spotlessフォーマット違反: X 件
- ArchUnitアーキテクチャ制約違反: X 件（層飛ばし・Service へのHTTP概念混入・エンティティ直接返却）
- **認可クリティカルテスト(`authorization`パッケージ)**: PASS / FAIL（個別に明示。件数と失敗テスト名を列挙)
- その他テスト失敗: X 件
- 問題があれば内容を列挙

### バックエンド（Flyway マイグレーション検証）
- 結果: PASS / FAIL / 対象外
- 適用済みファイルの改変: あり / なし

### フロントエンド（ビルド）
- 結果: PASS / FAIL / 対象外
- エラーがあれば内容を列挙

### Terraform（fmt / validate）
- 結果: PASS / FAIL
- 問題があれば内容を列挙

### GitHub Actions（ワークフロー）
- 結果: PASS / FAIL / 対象外
- チェック方法: actionlint / 目視確認
- 問題があればファイル名と内容を列挙

### ドキュメント（docs/）との差異
- 対象外（docs/ が存在しない場合）
- 差異なし / 差異あり
- 差異がある場合はドキュメント名と内容を列挙

### README との差異
- 対象外（README.md が存在しない場合）
- 差異なし / 差異あり
- 差異がある場合は内容を列挙
```

問題が見つかった場合は、修正方法の提案も合わせて提示すること。
存在しないスタックのセクションは「対象外」と記載すること。
