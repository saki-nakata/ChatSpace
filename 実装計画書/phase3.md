## フェーズ3 — メッセージ(チャンネル/DM共通)・リアクション・MessageScopeGuard

**状態: ✅ 完了(2026-08-16)**

メッセージCRUD・スレッド返信・リアクションを実装した。`MessageScopeGuard`によるconfused-deputy対策が本フェーズの中核。詳細は [`docs/機能定義書/メッセージング機能定義書.md`](../docs/機能定義書/メッセージング機能定義書.md)・[`docs/機能定義書/リアクション機能定義書.md`](../docs/機能定義書/リアクション機能定義書.md) を正とする。

### 実施内容

#### 1. `MessageScopeGuard`(confused-deputy対策、最重要)

`message`パッケージのpackage-privateユーティリティクラスとして実装(`MessageService`と同一パッケージに置き、認可チェックの取りこぼしを防ぐ設計方針、§6.1)。`assertInScope(message, channelId, dmId)`は、渡された`messageId`が実際にURLスコープに属するかを検証し、不一致は404。`messageId`を受け取る全エンドポイント(context・replies・編集・削除・リアクション切り替え・返信投稿時のparentId検証)の先頭で呼ぶ。

#### 2. エンティティ拡張

- `Message`: `edit(body, editedAt)`・`markDeleted(deletedAt)`・`isDeleted()`・`getParentId()`を追加
- `Attachment`: `attachToMessage(messageId)`を追加(§6.4の投稿時所有権検証で使用)

#### 3. `MessageService`

- **投稿**: `parentId`指定時は実在確認(404)→同一スコープ確認(不一致は400)→**2階層目返信の拒否**(返信先が既に返信なら400)の順で検証。添付ファイルIDは実在・アップロード者=投稿者・未使用であることを検証(§6.4)
- **編集・削除**: 投稿者本人のみ(403)。削除済みへの書き込み系操作は404
- **ソフトデリート(tombstone方式)**: 一覧・スレッド・コンテキスト取得は削除済み行を除外せず、`deleted=true`・`body=""`として返す(§3.3・§6.3)
- **一覧・スレッド返信**: `(createdAt, id)`複合カーソルページング。一覧は50件/ページ(新しい順→古い方向へページング)、スレッド返信は20件/ページ(古い順→新しい方向へページング、§3.4)
- **コンテキスト取得(around)**: 対象前後最大25件ずつ+`olderCursor`/`newerCursor`/`hasOlder`/`hasNewer`。対象がスレッド返信の場合は親メッセージを軸にウィンドウを組み、`replyMessageId`を返す(§3.6)
- **リアクション**: `(messageId, userId, emoji)`一意制約でトグル。レスポンスは絵文字ごとに集約(`count`/`reactedByMe`/`userIds`)、N+1回避のためメッセージ一覧取得時はバッチクエリで一括取得
- **返信数(`replyCount`)**: 一覧表示のスレッドバッジ用に、親IDごとの返信数をバッチクエリで取得

#### 4. `MessageController`(チャンネル/DM統合)

チャンネルメッセージ(`/workspaces/{workspaceId}/channels/{channelId}/messages/**`)とDMメッセージ(`/workspaces/{workspaceId}/dms/{dmId}/messages/**`)は同一`MessageService`を共有するため、**1つのコントローラでSpring MVCの複数パスパターン+任意path変数**(`@PathVariable(required = false)`で`channelId`/`dmId`のどちらか一方のみが実際のリクエストで埋まる)により統合した。認可の事前チェック(`ChannelAuthorizationService.requireChannelMember`/`DmAuthorizationService.requireDmAccess`)はスコープに応じて出し分ける。7エンドポイント(投稿・一覧・context・replies・編集・削除・リアクション)×チャンネル/DM=14通りを、実装上は7メソッドに集約できた。

### 実機検証

`docker compose up -d postgres` → `bootRun --spring.profiles.active=dev,seed` で起動し、alice/bobでcurl検証した(検証後、アプリ・Postgresコンテナとも停止済み):

| 確認項目 | 結果 |
|---|---|
| メッセージ投稿・一覧取得 | ✅ |
| スレッド返信の投稿・一覧取得、親メッセージの`replyCount`更新 | ✅ |
| 2階層目スレッド返信の拒否(400) | ✅ |
| 他人のメッセージ編集・削除 → 403、本人 → 成功 | ✅ |
| リアクションのトグル(付与→解除) | ✅ |
| コンテキスト取得(トップレベル対象・スレッド返信対象の両方、`replyMessageId`の設定含む) | ✅ |
| 削除後のtombstone表示(`deleted=true`・`body=""`、一覧から除外されない) | ✅ |
| 削除済みメッセージへの編集・リアクション → 404 | ✅ |
| **confused-deputy**: 非参加のプライベートチャンネルの`messageId`を、参加中の別チャンネルのURLスコープ経由で操作 → 404 | ✅ |
| DMメッセージの投稿・一覧取得(統合コントローラでチャンネルと同じ経路が正しく機能) | ✅ |

### ビルド確認

`./gradlew build`が成功。テスト総数27件(フェーズ1・2からの20件 + 本フェーズ7件)、全てgreen。

新規テスト(`docs/テスト設計書.md`§6.1・§6.2準拠):

| テストID/内容 | テストクラス |
|---|---|
| AUTH-P01(クロスチャンネルのメッセージID漏洩防止、5エンドポイント) | `MessageScopeAuthorizationTest` |
| AUTH-P02(正当なスコープ経由での成功、過剰ブロック検証) | 同上 |
| AUTH-P03(プライベートチャンネルの一覧非表示) | `ChannelVisibilityAuthorizationTest` |
| AUTH-P04(プライベートチャンネルへの直接アクセス404) | 同上 |
| AUTH-N23(ワークスペースキック後のDMメッセージ取得404、HTTPエンドポイント経由) | `DmMessageKickAuthorizationTest` |
| 2階層目スレッド返信の拒否 | `MessageCrudIntegrationTest` |
| tombstone表示の確認 | 同上 |

### 対象外(本フェーズでは扱わなかった、次フェーズへ繰り越し)

- リアルタイム配信(WebSocket経由の即時反映、`MESSAGE_CREATED`/`MESSAGE_DELETED`/`REACTION_UPDATED`イベント)はフェーズ4。コード中に`TODO(フェーズ4)`コメントで明記済み
- チャンネル投稿時のメンション処理、返信のスレッド返信通知、DM投稿時の受信通知はフェーズ5(通知機能実装後)。`TODO(フェーズ5)`コメントで明記済み
- 複合カーソルページネーションの同一ミリ秒衝突ケースの専用テスト(実装はDB設計書§1.1の方針通り`(createdAt, id)`で統一済みだが、専用の検証テストは未追加)

## 関連ドキュメント

- [`docs/機能定義書/メッセージング機能定義書.md`](../docs/機能定義書/メッセージング機能定義書.md)
- [`docs/機能定義書/リアクション機能定義書.md`](../docs/機能定義書/リアクション機能定義書.md)
- [`docs/テスト設計書.md`](../docs/テスト設計書.md) §6.1・§6.2
- [phase2.md](phase2.md)(前フェーズ)
- [phase4.md](phase4.md)(次フェーズ)
