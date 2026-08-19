## フェーズ11 — 機能同等性チェックリストの最終確認・旧実装削除とリネーム

**状態: ✅ 完了**

新実装が旧実装(プロトタイプ)と機能的に同等であることを確認したうえで、旧実装を削除・リネームした。

### 機能同等性チェックリスト(全項目達成が本フェーズの完了条件)

- [x] 認証(signup/login/logout/me)がプロトタイプと同じ挙動で動作する
- [x] ワークスペース/チャンネル/DM の作成・招待・キック・退出が動作する
- [x] メッセージ CRUD・スレッド返信・リアクション・メンションが動作する
- [x] 添付ファイル(画像/動画)のアップロード・表示が動作する
- [x] 通知(メンション/DM/招待/スレッド返信)がリアルタイムで届き、未読管理・キック後のスコープ再チェックも動作する
- [x] 検索が自分の所属範囲内で正しく機能する
- [x] 複数ブラウザタブでリアルタイム反映が機能する(単一インスタンスでのキック強制切断・Origin拒否含む)
- [x] フェーズ2〜7で追加した認可クリティカルテスト・受け入れテスト(AUTH-P01〜P10, AUTH-N01〜N29)が全てgreen
- [x] ArchUnitによる3層アーキテクチャ制約テスト(AUTH-N19〜N21)が全てgreen

### 実機確認の結果(Playwright + 手動確認)

`docker compose up -d postgres` → `apps/api-java`(:8080、`WEB_ORIGIN=http://localhost:5174`)→ `apps/web-next`(:5174) を起動し、
実ブラウザで以下を確認した。いずれも期待どおり動作し、機能差分は検出されなかった。

| 確認内容 | 結果 |
|---|---|
| 新規登録・ログイン・ログアウト・プロフィール(表示名/ステータス)更新の永続化 | OK |
| ワークスペース作成、プライベートチャンネル作成、DM開始 | OK |
| メッセージ送信、マークダウン描画(`**bold**`/`` `code` ``)、`@`メンションのハイライト | OK |
| メッセージ編集・削除(確認ダイアログ経由) | OK |
| スレッド返信・返信件数バッジ | OK |
| 絵文字リアクション(付与とカウント表示) | OK |
| 画像添付のアップロードと表示(`/uploads/{uuid}.png` から配信) | OK |
| 検索(自分の所属範囲のみ)と検索結果からのジャンプ | OK |
| 通知パネル(メンション/DM/チャンネル招待/ワークスペース招待)・通知からのジャンプ | OK |
| 未読区切り線・日付区切り・未読バッジ | OK |
| 複数タブでのリアルタイム反映(タブ2で送信 → タブ1へSTOMP経由で即時反映) | OK |
| オーナーによる招待・キック(確認ダイアログ経由)、キック後のメンバー一覧反映 | OK |
| 非オーナーにチャンネル追加ボタン・招待欄・他人のキックが表示されないこと | OK |
| 非参加者にプライベートチャンネルがサイドバーに出ないこと、直接URLアクセスが404で拒否されチャンネル名も漏れないこと | OK |
| 非オーナーの自主退出(ワークスペースから退出し一覧から消える) | OK |

実機確認中に発見・対応した点:

- `application-dev.yml` の `chatspace.web-origin` 既定値が旧 `apps/web`(:5173)のままだったため、`apps/web-next`(:5174)からのアクセスがCORSで拒否された。並行開発期間中の既定値としては正しい挙動のため、`WEB_ORIGIN=http://localhost:5174` を指定して確認し、本フェーズのリネーム時に `apps/web` のポートを **5173 に戻す**ことで解消した(別ポートで起動したい場合は引き続き `WEB_ORIGIN` で上書きできる)。

### 追加した認可クリティカルテスト(7項目・9メソッド)

チェックリストの「AUTH-N01〜N29 が全てgreen」を額面どおり満たすため、フェーズ4・5で
「設計・実装レベルでの担保」に留めていた7項目を自動テスト化した。加えてレビュー指摘により、
AUTH-N05 のうち自動テスト化されていなかった一括既読(`read-all`)の検証を追加した。

| テストID | 内容 | 追加先 |
|---|---|---|
| AUTH-N05(read-all) | 一括既読(`POST /notifications/read-all`)がキックで不可視になった通知を既読化しないこと(可視分だけが既読になることをDBレベルで確認) | `NotificationScopeAuthorizationTest` |
| AUTH-N06 | 通知スコープ条件のAND結合検証(3条件のいずれか1つでも`OR`に退行すると失敗する) | `NotificationScopeAuthorizationTest` |
| AUTH-N11 | キック後の強制切断(購読済みユーザーが新規メッセージを受信しない。キック前に受信できることも確認し偽陰性を排除) | `StompKickAuthorizationTest`(新設) |
| AUTH-N12 | キック確定の AFTER_COMMIT 保証(ロールバック時は強制切断が発生しない) | 同上 |
| AUTH-N13 | キック確定後の即時再接続でチャンネルトピックへ再購読できないこと | 同上 |
| AUTH-N17 | STOMPペイロード不正値拒否(宛先変数のUUID形式不正・サイズ上限超過。いずれも「正常なタイピングイベントは配信される」ことを先に確認したうえで非配信を検証し、サイズ超過は送信側のエラー通知/切断も確認する) | `StompAuthorizationTest` |
| AUTH-N24 | ワークスペースキック後のDMトピック再購読拒否 | `StompKickAuthorizationTest` |
| AUTH-N29 | 他人の個人キューを指定して購読しても他人宛イベントを受信しないこと | `StompAuthorizationTest` |

AUTH-N12用に、キックを実行した直後に例外を投げて同一トランザクションごとロールバックさせる
テスト補助コンポーネント `KickRollbackTestHelper` をテストソースセット側に追加した
(本番コードに意図的な失敗経路を持ち込まないため)。

また、複数のSTOMPテストクラスで共有するヘルパー(`connect`・`RecordingHandler`・
`collectingFrameHandler` 等)を `AbstractWebSocketIntegrationTest` へ移動して重複を解消した。

なお AUTH-N29 について、テスト設計書は「`UserDestinationResolver` が宛先を書き換えるため盗み見できない」
ことを想定していたが、本実装では `StompChannelInterceptor` のcatch-all default-denyが先に評価されるため
SUBSCRIBEの時点で拒否される(想定より早い段階での遮断)。テストは「他人宛イベントを受信しない」という
守るべき性質そのものを検証する形にしている。

### フェーズ11レビュー指摘への対応

完了報告後のレビューで以下3件の必須修正・3件のドキュメント修正を受け、対応済み。

| 指摘 | 対応 |
|---|---|
| AUTH-N05 が設計書(個別既読と `read-all` の両方が対象)を満たしていない。個別既読の404しか確認していなかった | `markAllRead_doesNotTouchNotificationsHiddenByChannelKick` を追加。可視・不可視の通知を同時に用意し、一括既読後に可視分のみ `readAt` が入り不可視分は `null` のままであることをリポジトリ経由で確認する |
| AUTH-N17 が「何も受信しなかった」ことしか見ておらず、タイピング配信機能自体が壊れていても通ってしまう | 両テストとも**先に正常なタイピングイベントが配信されること**を確認する正常系アサーションを追加。サイズ超過については送信側セッションのエラー通知または切断も検証する(テスト名も `..._isRejected` に変更) |
| `frontend-ci.yml` のpathフィルタが `apps/web/**` のみで、Java側の宛先定義だけを変更したPRで契約テスト(AUTH-N18)が走らない | `apps/api/src/main/java/com/chatspace/api/realtime/StompDestinations.java` と `apps/api/build.gradle.kts` をpull_request・pushの両トリガーに追加 |
| README のビルド手順が `cd apps/api` 後にルートへ戻らず `pnpm` を実行していた | バックエンドとフロントエンドのコードブロックを分割。起動手順・CLAUDE.md・`実装計画書/README.md` の同種の記述も同様に分割した |
| `phase12.md`・`phase13.md`(現在形の手順)に `cd apps/api-java` が残っていた | `cd apps/api` に更新。フェーズ0〜11の完了済みフェーズ文書は当時の記録としてそのまま残す |
| 契約テストのコメントに旧CI名 `frontend-next-ci.yml` が残っていた | `frontend-ci.yml` に更新 |

### 実施手順

- [x] 上記チェックリストを実機(手動含む)で確認する
- [x] `apps/api-java` → `apps/api` にリネーム
- [x] `apps/web-next` → `apps/web` にリネーム(開発ポートも 5174 → 5173 に戻した)
- [x] `pnpm-workspace.yaml` を `apps/web` のみに縮小
- [x] `packages/shared`(zod)を削除
- [x] 旧実装(Node/Hono/Socket.IO、旧React/Vite実装)を削除
- [x] `.github/workflows/backend-ci.yml`・`frontend-next-ci.yml` のpathフィルタを`apps/api`・`apps/web`に更新(`frontend-next-ci.yml` は `frontend-ci.yml` にリネーム)
- [x] `.github/workflows/claude-code-review.yml`・`quality-check`スキルからNode/TypeScriptバックエンド向けの記述を削除(段階的更新計画の最終段)
- [x] README.mdの「再設計について(進行中)」節を削除し、通常のセットアップ手順に一本化

あわせて実施した整理:

- ルート `package.json` のスクリプトを `apps/web` 1パッケージ前提に整理(`db:migrate`/`db:seed` 等のPrisma向けスクリプトを削除)
- `eslint.config.js` から旧実装・`packages/shared` 向けの設定を削除し、`apps/api`(Java)を除外対象に追加
- `.gitignore` から Prisma/SQLite・旧テスト用一時ディレクトリのエントリを削除
- `.env.sample` を新スタック(`apps/api` / `apps/web`)のみに整理
- `CLAUDE.md`「現在の状況」節を再設計完了後の内容へ更新
- `docs/` 配下の `apps/api-java` / `apps/web-next` というパス参照を最終的なパスへ更新(移行期間を前提とした記述も現状に合わせて修正)

> 旧実装の実体は削除せず、リポジトリ直下の `delete/phase11-old-implementation/`(`.gitignore` 対象)へ移動している。
> Gitの追跡からは外れているため、実ファイルの削除はユーザーが任意のタイミングで行うこと。

### 対象外(本フェーズでは扱わない)

- 水平スケール対応(フェーズ13)・パフォーマンステスト(フェーズ14)の完了。両フェーズは任意でありチェックリストの前提条件に含めない

### 確認方法・結果

バックエンド(`apps/api` で実行)。Testcontainersを使うため、事前に Docker が起動していること。

```bash
docker compose up -d postgres   # リポジトリルート
cd apps/api
./gradlew clean build           # Spotless + ArchUnit + 全テスト
./gradlew exportStompDestinations
```

フロントエンド(リポジトリルートで実行)。

```bash
pnpm install
pnpm run typecheck
pnpm run lint
pnpm run build
pnpm run test                   # STOMP宛先契約テスト
```

| 確認 | 結果 |
|---|---|
| `apps/api` `./gradlew clean build`(Spotless + ArchUnit + 全テスト) | BUILD SUCCESSFUL / **81テスト 失敗0・エラー0**(フェーズ10時点の72件 + 本フェーズ9件) |
| `apps/web` typecheck / lint(警告0) / build | 全て成功 |
| STOMP宛先契約テスト(AUTH-N18) | 1テスト green |

本フェーズで追加したテストメソッドは9件(AUTH-N05 read-all 1件、AUTH-N06 1件、AUTH-N11〜N13・N24 の4件、
AUTH-N17 の2件、AUTH-N29 1件)。認可テストクラスの内訳は
`StompAuthorizationTest` 12件・`StompKickAuthorizationTest` 4件・`NotificationScopeAuthorizationTest` 4件・
`LayeredArchitectureTest`(ArchUnit) 3件ほか。

## 関連ドキュメント

- [`README.md`](../README.md)
- [`docs/テスト設計書.md`](../docs/テスト設計書.md)
- [phase10.md](phase10.md)(前フェーズ)
- [phase12.md](phase12.md)(次フェーズ)
