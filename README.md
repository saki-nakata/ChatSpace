# ChatSpace

Slack風のチャットアプリケーション。

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

## 再設計について(進行中)

上記のプロトタイプ(Node.js/Hono/Socket.IO/Prisma/SQLite)を土台に、**Java/Spring Boot + STOMP + PostgreSQL へのゼロからの再設計**を `feature/java-spring-boot-redesign` ブランチで進めている。

- 設計ドキュメント一式(要件定義書・DB設計書・画面設計書・画面遷移図・シーケンス図・インフラ構成書・テスト設計書・ログ運用設計書・機能定義書11本)は [`docs/`](docs/) を参照
- 実装計画(フェーズ単位の手順)は [`実装計画書/`](実装計画書/) を参照
- 旧実装(`apps/api`, `apps/web`)は、新実装が機能同等性チェックリストを満たすまで並行して残す(削除しない)。現時点(フェーズ7完了)では新バックエンド `apps/api-java` に認証・ワークスペース/チャンネル/DM CRUD・メッセージング(編集/削除/スレッド/リアクション)・STOMPによるリアルタイム通信・メンション/通知(スコープ再チェック含む)・検索(pg_trgm)・添付ファイル(マジックバイト判定・ライブ権限再チェック)まで実装済み。OpenAPI生成・フロントエンド(`apps/web-next`)は未着手

## 開発

### 技術スタック

#### 現行実装(プロトタイプ、`apps/api` / `apps/web`)

pnpm workspace によるモノレポ構成。

| 分類 | 技術 |
| --- | --- |
| フロントエンド | React + Vite + TypeScript, React Router, Zustand, Tailwind CSS, Socket.IO Client, marked + DOMPurify(Markdown描画・XSS対策) |
| バックエンド | Hono + @hono/node-server, Socket.IO, Prisma + SQLite, jose(JWT), bcryptjs |
| 共有 | `packages/shared`(zod スキーマ・DTO型・Socket.IOイベント名を front/back で共有) |

```
apps/
  api/    Hono製REST API + Socket.IOサーバー(ポート 4000)
  web/    React製フロントエンド(ポート 5173)
packages/
  shared/ zodスキーマ・DTO型・Socket.IOイベント名の共有パッケージ
```

#### 再設計中(`apps/api-java` / `apps/web-next`、進行中)

| 分類 | 技術 |
| --- | --- |
| バックエンド | Java 21 + Spring Boot 4.1.0(STOMP over WebSocket, Spring Data JPA, Spring Security), PostgreSQL + Flyway, Nimbus JOSE+JWT |
| 静的解析 | Spotless(google-java-format)、ArchUnit(3層アーキテクチャ制約の自動テスト) |
| フロントエンド | React + Vite + TypeScript(ゼロから再構築、フェーズ9で着手) |

```
apps/
  api-java/  Spring Boot製REST API + STOMPサーバー(ポート 8080、移行完了後 apps/api にリネーム)
  web-next/  React製フロントエンド(移行完了後 apps/web にリネーム、未着手)
docker-compose.yml   ローカル開発用 PostgreSQL
docs/                設計ドキュメント一式
実装計画書/           フェーズ別の実装手順書
```

### パッケージマネージャ

**pnpm** を使用する（npm / yarn は使用しない）。

### セットアップ

```bash
pnpm install

# apps/api/.env と apps/web/.env を用意する(.env.sample を参照)
cp .env.sample apps/api/.env    # DATABASE_URL / JWT_SECRET などを編集
cp .env.sample apps/web/.env    # VITE_API_URL を編集

# DBマイグレーション + 初期データ投入
pnpm --filter @chatspace/api run db:migrate
pnpm run db:seed
```

シード投入後、以下のアカウントでログインできる(パスワードは共通で `password123`)。

| userId | 表示名 | 備考 |
| --- | --- | --- |
| alice | Alice | Sample Workspace のオーナー |
| bob | Bob | メンバー |
| carol | Carol | メンバー |

### 起動

```bash
pnpm run dev        # API(:4000) と Web(:5173) を同時起動
# もしくは個別に
pnpm run dev:api
pnpm run dev:web
```

起動後 http://localhost:5173 にアクセスする。

### ビルド・型チェック・Lint・テスト

```bash
pnpm run build       # shared -> api -> web の順にビルド
pnpm run typecheck   # 各パッケージの tsc --noEmit
pnpm run lint        # ESLint(apps/api, apps/web。警告0件が必須)
pnpm run test        # apps/api の認可テスト(vitest)。専用DB(prisma/test.db)へ自動でマイグレーションを適用する
```

### 再設計中バックエンド(`apps/api-java`)のセットアップ・起動

現時点ではリポジトリ雛形のみ(認証・API等の機能は未実装)。ビルド・マイグレーション適用の確認用。

```bash
# ローカル用 PostgreSQL を起動(リポジトリルートで実行)
docker compose up -d postgres

# ビルド(Spotless + ArchUnit + テスト + jar作成)
cd apps/api-java
./gradlew build

# 起動(dev プロファイル、docker-compose.yml のPostgresへ自動接続)
./gradlew bootRun --args='--spring.profiles.active=dev'
```

起動時に Flyway が `src/main/resources/db/migration/` 配下のマイグレーションを自動適用する。

### コードレビュー

プルリクエストの作成時、および PR ブランチへのプッシュ時に、
GitHub Actions 上で Claude Code による自動レビューが実行される。

- ドラフト PR はレビュー対象外（Ready for review にした時点で実行される）
- 設定は [.github/workflows/claude-code-review.yml](.github/workflows/claude-code-review.yml)

### 品質チェック

Claude Code のセッションで `/quality-check` を実行すると、
静的解析・ビルド確認・ドキュメントとの差異確認がまとめて実行される。
