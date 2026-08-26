# ChatSpace

Slack風のチャットアプリケーション。

**公開デモ**: [chatspace-ydxk.onrender.com](https://chatspace-ydxk.onrender.com)(誰でも新規登録可能。無料インスタンスのため、しばらくアクセスが無いとスリープし初回アクセス時に起動まで1分ほどかかる場合がある。添付ファイルのウイルススキャンは未実装のため、本物の個人情報は登録しないこと)

ワークスペース単位でチャンネルやDMを管理し、スレッド返信・絵文字リアクション・
メンション・メッセージ検索といったコミュニケーション機能を提供する。

## 主な機能

| 分類 | 機能 |
| --- | --- |
| 認証・ユーザー管理 | 新規登録、ログイン / ログアウト、プロフィール設定（アバター画像・表示名・ステータス） |
| メッセージング | チャンネル内チャット、DM、編集・削除、スレッド返信、マークダウン、画像・動画添付、絵文字リアクション、メンション、検索 |
| ワークスペース・チャンネル | ワークスペース作成、パブリック / プライベートチャンネル作成、友人の招待 |
| 管理者権限（オーナー） | ユーザーの招待・キック、チャンネルの追加・削除 |
| 通知 | 未読メッセージ通知、メンション通知 |

ボイスチャンネルは対象外とする。

上記の要件に加えて、実際の使用感を確認しながら以下も実装している。

- メンションの入力補完(`@`入力で候補表示)
- 未読区切り線、過去ログの遡り読み込み(上スクロール)
- 検索結果から該当メッセージへのジャンプ・ハイライト(スレッド返信もジャンプ対象)
- タイピングインジケーター、オンライン/オフラインのプレゼンス表示
- ブラウザ通知・未読件数のタブタイトル表示
- スレッド返信の通知、ワークスペースからの自主退出

詳細な要件および設計上の注意点は [CLAUDE.md](CLAUDE.md) を参照。

## 設計ドキュメント

- 設計ドキュメント一式(要件定義書・DB設計書・画面設計書・画面遷移図・シーケンス図・インフラ構成書・テスト設計書・ログ運用設計書・機能定義書11本)は [`docs/`](docs/) を参照
- 実装計画(フェーズ単位の手順)は [`実装計画書/`](実装計画書/) を参照

## 開発

### 技術スタック

| 分類 | 技術 |
| --- | --- |
| バックエンド | Java 21 + Spring Boot 4.1.0(STOMP over WebSocket, Spring Data JPA, Spring Security), PostgreSQL + Flyway, Nimbus JOSE+JWT |
| 静的解析(バックエンド) | Spotless(google-java-format)、ArchUnit(3層アーキテクチャ制約の自動テスト) |
| フロントエンド | React 18 + Vite + TypeScript、React Router、Zustand、Tailwind CSS、`@stomp/stompjs`、marked + DOMPurify(Markdown描画・XSS対策)、`openapi-typescript`によるAPI型生成 |

```
apps/
  api/    Spring Boot製REST API + STOMPサーバー(ポート 8080)
  web/    React製フロントエンド(ポート 5173)
docker-compose.yml   ローカル開発用 PostgreSQL
docs/                設計ドキュメント一式
実装計画書/           フェーズ別の実装手順書
```

### パッケージマネージャ

**pnpm** を使用する（npm / yarn は使用しない）。

### セットアップ

```bash
# 依存関係(フロントエンド)
pnpm install

# 環境変数(.env.sample を参照。ローカル開発では dev プロファイルが既定値を持つため、
# apps/api/.env は無くても docker-compose.yml の PostgreSQL に接続できる)
cp .env.sample apps/web/.env

# ローカル用 PostgreSQL を起動(リポジトリルートで実行)
docker compose up -d postgres
```

### 起動

バックエンド(:8080)。dev プロファイルで Flyway マイグレーションが自動適用される。
seed プロファイルを併記すると alice/bob/carol のシードデータも投入される。

```bash
cd apps/api
./gradlew bootRun --args='--spring.profiles.active=dev,seed'
```

フロントエンド(:5173)。**別ターミナル**をリポジトリルートで開いて実行する。

```bash
pnpm run dev
```

起動後 http://localhost:5173 にアクセスする。Swagger UI は http://localhost:8080/swagger-ui/index.html 。

シード投入後、以下のアカウントでログインできる(パスワードは共通で `password123`)。

| userId | 表示名 | 備考 |
| --- | --- | --- |
| alice | Alice | Sample Workspace のオーナー |
| bob | Bob | メンバー |
| carol | Carol | メンバー |

### ビルド・型チェック・Lint・テスト

バックエンド(`apps/api` で実行)。Spotless + ArchUnit + 全テスト + jar作成が1コマンドに含まれる。

```bash
cd apps/api
./gradlew build

# STOMP宛先の契約テスト(AUTH-N18)が突き合わせるJSONを書き出す
./gradlew exportStompDestinations
```

フロントエンド(リポジトリルートで実行)。契約テストは上記の `exportStompDestinations` を先に済ませておくこと。

```bash
pnpm run typecheck   # tsc --noEmit
pnpm run lint        # ESLint(警告0件が必須)
pnpm run build       # tsc --noEmit && vite build
pnpm run test        # STOMP宛先の契約テスト(vitest)

# OpenAPIスキーマ変更時のみ: apps/api/openapi.json からフロントエンドのAPI型を再生成する
pnpm run generate:api-types
```

### コードレビュー

プルリクエストの作成時、および PR ブランチへのプッシュ時に、
GitHub Actions 上で Claude Code による自動レビューが実行される。

- ドラフト PR はレビュー対象外（Ready for review にした時点で実行される）
- 設定は [.github/workflows/claude-code-review.yml](.github/workflows/claude-code-review.yml)

### 品質チェック

Claude Code のセッションで `/quality-check` を実行すると、
静的解析・ビルド確認・ドキュメントとの差異確認がまとめて実行される。
