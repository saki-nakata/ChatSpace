---
name: quality-check
description: プロジェクト全体の品質チェック（静的解析）を実行する。ESLint・TypeScript・Checkstyle・ビルド確認・Terraform検証・GitHub Actionsワークフロー検証、およびドキュメントと実装の差異確認を行い、決められた形式でレポートする。「品質チェック」「静的解析」「lint」「ビルド確認」を求められたときに使用する。
---

プロジェクト全体の品質チェック（静的解析）を実行してください。以下の手順を必ず守ること。

## 実行前の確認

プロジェクトのディレクトリ構成を確認し、存在するものだけを実行すること。

---

## 1. フロントエンド（JavaScript / TypeScript）

`package.json` が存在するディレクトリを対象とする。
`web/`, `frontend/`, `client/` など、プロジェクトによってディレクトリ名が異なる場合は適宜読み替えること。

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

### Java / Spring Boot（build.gradle または pom.xml が存在する場合）

```bash
# Checkstyle（build.gradle に checkstyle プラグインが設定されている場合）
./gradlew checkstyleMain checkstyleTest

# または Maven
mvn checkstyle:check
```

### Python（pyproject.toml / setup.py が存在する場合）

```bash
# ruff（設定がある場合）
ruff check .

# または flake8
flake8 .
```

### Node.js / TypeScript（バックエンドが独立している場合）

フロントエンドと同様に ESLint + TypeScript チェックを実行。

---

## 3. ビルド確認

### フロントエンド（package.json が存在する場合）

```bash
pnpm build
```

### バックエンド Java / Spring Boot（build.gradle が存在する場合）

```bash
# テストをスキップしてビルドのみ実行
./gradlew build -x test
```

### バックエンド Maven（pom.xml が存在する場合）

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

actionlint が利用可能な場合は実行する:

```bash
actionlint
```

actionlint が利用できない場合は、`.github/workflows/` 配下の全ファイルを読み込み、以下を目視で確認すること。

**確認観点：**

- YAML構文が正しいか
- `permissions` が最小権限の原則に従っているか（不要な `write` 権限が付いていないか）
- シークレットが `run:` 内やログに露出していないか（`echo` などでの出力、`env:` 経由でのスクリプトへの露出）
- サードパーティ製アクションのバージョンが固定されているか（タグ参照より SHA 固定が望ましい）
- `pull_request_target` を使用している場合、PRのコードをチェックアウトして実行していないか
- フォークからのPRでシークレットを要するジョブが実行されないようガードされているか
- `timeout-minutes` が設定されているか（ジョブの暴走防止）
- `concurrency` 設定により、不要な多重実行が発生しないようになっているか

---

## 6. 仕様書・要件定義書との差異確認

`docs/` ディレクトリまたは `README.md` が存在する場合のみ実行すること。

`docs/` 配下の全ファイルと `README.md` を読み込み、現在のコード実装との差異を確認すること。

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

### バックエンド（Checkstyle）
- 結果: PASS / FAIL / 対象外
- 違反数: X 件
- 問題があれば内容を列挙

### フロントエンド（ビルド）
- 結果: PASS / FAIL / 対象外
- エラーがあれば内容を列挙

### バックエンド（ビルド）
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
