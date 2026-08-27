## フェーズ14 — Renderデプロイ本体

**状態: ✅ 実装完了**(実機デプロイ・CD有効化は「Render初回セットアップ」節のDoDを参照)

Renderへのデプロイ方針確定(2026-08-22)に伴い新設したフェーズ。旧フェーズ14(パフォーマンステスト)は
[phase15.md](phase15.md)として繰り下げた。**フェーズ13(添付ファイルのオブジェクトストレージ化)の完了が
前提条件**(コードのマージだけでなく、R2実機疎通確認まで含めて完了していること。`phase13.md`のDoD参照)。
R2移行前にCDを有効化すると、再デプロイのたびに添付ファイルが消える。

### 実装対象

- [x] `.dockerignore`新設。`apps/web/.env`(`VITE_API_URL=http://localhost:8080`等をハードコード)が
      ビルドコンテキストに混入しないよう除外
- [x] マルチステージ`Dockerfile`新設(リポジトリルート): フロントエンドビルド(`node:22-slim`)→
      バックエンドビルド(`eclipse-temurin:21-jdk`、フロント成果物を`apps/api/src/main/resources/static/`へ同梱)→
      実行用(`eclipse-temurin:21-jre`)の3ステージ。フロントエンドビルド時は`VITE_API_URL=""`を明示設定し
      同一オリジン相対パスにする(`apps/web/.env`が万一混入しても`??`判定を確実に上書きする多層防御)
- [x] `apps/api/build.gradle.kts`の`bootJar`タスクに`archiveFileName.set("app.jar")`を追加(Dockerfileの
      COPY元ファイル名を固定するため)
- [x] `SpaFallbackController`新設: 実際のクライアント側ルート(`/login`・`/signup`・`/w/**`)のみを許可リストで
      `index.html`へフォワード。`/assets/**`は一切マッピングせずSpring Bootの既定静的リソース配信に委ねる
- [x] `SecurityConfig`の`permitAll`に`/`・`/index.html`・`/login`・`/signup`・`/assets/**`・`/w/**`を追加
      (未認証でもSPAシェル自体は読み込める必要があるため。実際のAPIは引き続き認証必須)
- [x] Swagger UI/OpenAPIの本番無効化(`springdoc.api-docs.enabled`/`springdoc.swagger-ui.enabled`を
      `SWAGGER_ENABLED`環境変数で切り替え、既定は有効)
- [x] `apps/web/src/realtime/stomp.ts`: `VITE_WS_URL`未設定時に`window.location`から`ws`/`wss`オリジンを
      導出するフォールバックを追加(ローカル開発は`apps/web/.env`が明示設定するため無改修で動く)
- [x] `.github/workflows/release-ci.yml`新設: `production`ブランチへのpush専用、pathsフィルタなし。
      バックエンドビルド+テスト・フロントエンドtypecheck/lint/build・`docker build`・コンテナ起動スモークテスト
      (`/health`・`/`・`/login`・`/workspaces`の応答確認)までを1本で行う(既存`backend-ci.yml`/`frontend-ci.yml`は
      pathsフィルタを持つため、Dockerfile等のみの変更ではCIが起動せずRenderの「After CI Checks Pass」が
      機能しない問題への対応)

### テスト

- [x] `SpaStaticAccessTest`(統合テスト): 未認証で`/login`・`/signup`・`/w/{id}`が401にならないこと、
      `/workspaces`・`/uploads/{key}`は引き続き401になること(既存の認可境界への回帰確認)
- [x] `release-ci.yml`のスモークテストで、実際のDockerイメージに対して`/`・`/login`が200、
      `/workspaces`(未認証)が401であることを確認

### Render初回セットアップ・環境変数チェックリスト

現行`application.yml`は`DATABASE_URL_JDBC`・`DATABASE_USERNAME`・`DATABASE_PASSWORD`を要求するが、
Renderが自動注入する`DATABASE_URL`(`postgresql://...`形式)とは非互換(変換コードは実装しない方針、
`docs/インフラ構成書.md` §5参照)。**Render PostgreSQLダッシュボードの個別接続情報から手動で組み立てて
個別環境変数として設定する**運用で対応する。

> **この節の位置づけ**: 初回セットアップ時に「何を設定すべきか」を列挙した**手順メモ**であり、実施記録ではない。
> **実機デプロイ自体は2026-08-23に完了している**(公開URL: https://chatspace-ydxk.onrender.com 、稼働中)。
> 下のチェックボックスは、**実機で個別に検証が取れた項目にのみ**印を付けている(未チェック=未設定、ではない)。
> 実際のデプロイ実績の記録は`.plans/render-deploy-handoff.md` §7(Git管理対象外)を参照。
>
> **「動いているから設定済み」と推論してよい項目とそうでない項目がある**(レビュー指摘対応)。DB接続・
> `JWT_SECRET`・`WEB_ORIGIN`・R2関連は、未設定ならアプリの起動やログイン・添付ファイル・WebSocketが
> そもそも失敗するため、稼働している事実から設定済みと判断できる。一方**`COOKIE_SECURE`と`SWAGGER_ENABLED`は
> 既定値がそれぞれ`false`・`true`という「安全側でない」値で、未設定のままでもアプリは正常に動作してしまう**
> (`application.yml`参照)。この2項目は稼働状況からは判断できないため、実機のレスポンスを直接検証した。

Render Web Serviceへ初回設定するチェックリスト:

- [x] ビルド方式: Docker(リポジトリルートの`Dockerfile`)を選択(2026-08-26、Renderダッシュボードのサービス画面に`Docker`表示を確認)
- [ ] `DATABASE_URL_JDBC` / `DATABASE_USERNAME` / `DATABASE_PASSWORD`(手動組み立て)
- [ ] `JWT_SECRET`(Renderのシークレット管理)
- [ ] `WEB_ORIGIN=https://<実際のRenderサービスURL>`
- [x] `COOKIE_SECURE=true`(2026-08-26、本番`/auth/login`のレスポンスヘッダが
      `Set-Cookie: chatspace_token=...; Path=/; Max-Age=604800; Secure; HttpOnly; SameSite=Lax`
      であることを確認。**既定値が`false`(Secure属性なし)であり、未設定でもアプリは正常動作してしまうため、
      稼働している事実からは設定の有無を判断できない項目**。実機のレスポンスヘッダで直接検証した)
- [ ] `STORAGE_TYPE=s3` + R2関連5項目(`STORAGE_S3_BUCKET`/`STORAGE_S3_ENDPOINT`/`STORAGE_S3_REGION`/
      `STORAGE_S3_ACCESS_KEY_ID`/`STORAGE_S3_SECRET_ACCESS_KEY`。R2はバケット限定の最小権限トークンを発行して使う)
- [x] `SWAGGER_ENABLED=false`(2026-08-26、本番の`/swagger-ui.html`・`/swagger-ui/index.html`・
      `/v3/api-docs`がいずれも404を返すことを確認。**既定値が`true`(公開)であり、未設定でもアプリは
      正常動作してしまうため、稼働している事実からは設定の有無を判断できない項目**。実機への
      HTTPリクエストで直接検証した)
- [x] `LOG_STRUCTURED_FORMAT=logstash`(2026-08-26、実機`chatspace`サービスで設定済み・JSON出力を確認。詳細は`docs/ログ運用設計書.md` §4)
- [x] Health Check Path: `/health`(2026-08-26、転送先のNew Relic上で`{"path":"/health","status":200}`のアクセスログを確認)
- [ ] 初回は手動デプロイでビルド所要時間を計測する(Gradle+pnpmの重いビルドがRender無料枠のビルド時間内に
      収まるか確認。収まらない場合は別途対応を検討)

### CDの有効化(R2移行完了が前提条件)

上記の初回手動デプロイで動作確認できたら、GitHub Actions(`release-ci.yml`)によるCDを有効化する。

1. Renderの Auto-Deploy を「After CI Checks Pass」・対象ブランチを`production`に設定する
2. `main`→`production`へのPRマージが唯一のリリース経路とする(`production`への直接pushはしない)
3. `release-ci.yml`が`production`ブランチへのpushで実際に起動し、スモークテストが通ることを確認する

### リリース・ロールバック運用(軽量版)

数日限定・単独運用の学校提出用デプロイという前提のため、タグ付きリリース/`hotfix/*`ブランチ/リバートPRと
いった本格的なリリース運用は今回のスコープに含めない(必要になった場合は別途相談)。

- **リリース**: `feature/*` → `main`(CIのみ)→ `production`へのPRをマージ、が唯一のリリース経路
- **ロールバック**: Renderダッシュボードから直前の成功デプロイに戻す、または`production`ブランチで
  問題のコミットをrevertしてpush

### 撤収手順チェックリスト(スクール公開後)

- [ ] Render Web Serviceを停止する
- [ ] Render PostgreSQLを削除する(必要ならエクスポート後に)
- [ ] R2バケットを空にしてから削除する
- [ ] R2 APIトークンを失効させる
- [ ] `production`ブランチへのpushを止める(CDの実質的な無効化として十分)

### 確認方法

```bash
# ローカルでのDockerビルド確認(このリポジトリの開発環境にDockerが必要)
docker build -t chatspace:local .
docker run -p 8080:8080 \
  -e DATABASE_URL_JDBC="jdbc:postgresql://host.docker.internal:5432/chatspace" \
  -e DATABASE_USERNAME=chatspace -e DATABASE_PASSWORD=chatspace \
  -e JWT_SECRET="local-only-secret-must-be-at-least-32-bytes-long" \
  chatspace:local
```

ブラウザで `http://localhost:8080/` にアクセスしSPAが表示されること、`/assets/*.js`・`*.css`が
`text/html`ではない正しい`Content-Type`で返っていること、未認証で`/login`が200で読み込めること、
`/w/xxx`のような直接URLアクセスでも401にならずSPAシェルが返ること、`/uploads/存在しないkey`はJSON 404が
返ること、`/swagger-ui.html`が404になること(`SWAGGER_ENABLED=false`時)を確認する。

## 関連ドキュメント

- [`docs/インフラ構成書.md`](../docs/インフラ構成書.md)
- [phase13.md](phase13.md)(前フェーズ、添付ファイルのオブジェクトストレージ化。完了が前提条件)
- [phase15.md](phase15.md)(次フェーズ、任意。パフォーマンステスト)
- [README.md](README.md)(全体目次)
