## フェーズ5 — メンション・通知

**状態: ✅ 完了(2026-08-16)**

`@メンション`の解決・通知、およびDM/チャンネル/招待/スレッド返信の各通知生成とリアルタイム配信を実装した。**通知のスコープ再チェック(AND結合)がプロトタイプに実在するギャップの修正であり最重要**。詳細は [`docs/機能定義書/メンション機能定義書.md`](../docs/機能定義書/メンション機能定義書.md)・[`docs/機能定義書/通知機能定義書.md`](../docs/機能定義書/通知機能定義書.md) を正とする。

### 実施内容

#### 1. `common/Cursor`への統合

`message`パッケージ内に閉じていた`Cursor`レコードを`common.Cursor`(`public record Cursor(Instant createdAt, UUID id)`)へ移動し、メッセージ・スレッド返信・メッセージコンテキストウィンドウ・通知一覧の全カーソルページネーションで共有する形にリファクタした。旧`message/Cursor.java`は`git mv`で`apps/api-java/delete/Cursor.java.old`へ退避(直接`rm`は使わず、削除はユーザー判断に委ねる方針のため)。

#### 2. `MentionResolver`(`message`パッケージ、package-private)

正規表現(`@([a-zA-Z0-9_.-]{3,20})`)でメッセージ本文からハンドル候補を抽出し、**投稿時点のライブな`ChannelMember`とのみ突合**して`Mention`レコードと`MENTION`通知を生成する。自己メンションは除外し、非メンバー・非該当ハンドルはエラーにせず黙って無視する(存在漏洩防止)。

#### 3. メンション自動補完(`MentionCandidateService`・`MentionController`)

`GET /workspaces/{workspaceId}/channels/{channelId}/mentions/candidates?q=`。`ChannelAuthorizationService.requireChannelMember`で呼び出し元自身のメンバーシップを先に検証(非メンバーは404)した上で、候補も同じ**ライブなチャンネルメンバーシップ**からのみ生成する(候補一覧そのものがメンバー漏洩経路にならないようにする)。前方一致・大文字小文字無視、最大20件。

#### 4. `NotificationRepository`のスコープ再チェック(最重要)

`VISIBLE_SCOPE_CONDITION`というJPQLフラグメント定数を作り、channel/dm/workspaceの3条件を**`AND`結合**した上で、一覧(`findVisibleFirstPage`/`findVisibleOlderThan`)・未読件数(`countVisibleUnread`)・個別既読取得(`findVisibleByIdAndUserId`)・全件既読(`markAllVisibleRead`)の4クエリすべてに同一条件を適用した。DM側の`EXISTS`は`DmThread`参加者チェックだけでなく`WorkspaceMember`との結合も必須とする(ワークスペースキックでは`DmThread`行自体は削除されないため)。

#### 5. `NotificationService`・通知生成トリガー配線

`notify(type, recipientUserId, fromUserId, ...)`が本人宛(自己通知)なら無視し、そうでなければ`Notification`を保存後`RealtimeEventPublisher.notification()`で`/user/queue/events`へ即時配信する。トリガーは以下の4箇所に配線:

- `MessageService.create()`: チャンネル投稿は`MentionResolver`経由で`MENTION`、DM投稿は相手に`DM`、返信(`parentId`あり)は親メッセージ投稿者に`THREAD_REPLY`
- `ChannelService.invite()`: 招待対象に`CHANNEL_INVITE`
- `WorkspaceService.invite()`: 招待対象に`WORKSPACE_INVITE`

#### 6. `NotificationController`

`GET /notifications`(`workspaceId`/`unreadOnly`/カーソルのクエリパラメータ)、`GET /notifications/unread-count`、`POST /notifications/{id}/read`、`POST /notifications/read-all?workspaceId=`。

### 遭遇した問題と対応

- 本フェーズはコンパイルエラー・テスト失敗とも発生せず、`./gradlew build`が最初の実行で成功した(フェーズ2・4で発生したJackson2→3移行やTestcontainersライフサイクル、循環依存等のトラブルは今回は再発しなかった)。

### 実機検証

`docker compose up -d postgres` → `bootRun --spring.profiles.active=dev,seed` で起動し確認した(検証後、アプリ・Postgresコンテナとも停止済み):

| 確認項目 | 結果 |
|---|---|
| ワークスペース招待 → 招待先に`WORKSPACE_INVITE`通知が正しい日本語文言(「Aliceさんがワークスペースに招待しました」)で生成される | ✅ |
| `/notifications/unread-count`が招待直後に1になる | ✅ |
| メンション自動補完APIがチャンネルメンバー(自分・招待先双方)を候補として返す | ✅ |
| チャンネルメッセージに`@bob`でメンション投稿 → bobに`MENTION`通知(`channelId`/`messageId`付き、「Aliceさんがあなたをメンションしました」)が生成され、`WORKSPACE_INVITE`と合わせて新しい順に並ぶ | ✅ |
| オーナーがチャンネルからbobをキック → bobの`/notifications`一覧から`MENTION`通知が消え、`WORKSPACE_INVITE`のみ残る | ✅ |
| キック後の`unread-count`が1(残存する`WORKSPACE_INVITE`分)のみ | ✅ |
| `POST /notifications/read-all` → `unread-count`が0になる | ✅ |

### ビルド確認

`./gradlew build`が成功。テスト総数39件(フェーズ1-4からの33件 + 本フェーズ6件)、全てgreen。

新規テスト(`docs/テスト設計書.md`§6.2準拠):

| テストID | テストクラス | 内容 |
|---|---|---|
| AUTH-N01 | `MentionAuthorizationTest` | チャンネル非メンバーへの`@メンション`が通知・`Mention`レコードを生成しないこと |
| AUTH-N02(前半) | `MentionAuthorizationTest` | メンション自動補完APIが非メンバーを候補に含まないこと |
| AUTH-N02(後半) | `MentionAuthorizationTest` | 非メンバーが候補取得APIを呼ぶと404になること |
| (過剰ブロック検証) | `MentionAuthorizationTest` | 正当なチャンネルメンバーへのメンションは通知を生成すること |
| AUTH-N03〜N05 | `NotificationScopeAuthorizationTest` | チャンネルキック後、一覧・未読件数・個別既読(404)のいずれからも除外されること(統合テスト1本で3項目を検証) |
| AUTH-N26(5) | `NotificationScopeAuthorizationTest` | ワークスペースキック後、過去のDM通知が一覧・未読件数から除外されること |

### 対象外(本フェーズでは扱わなかった、次フェーズ以降へ繰り越し)

- AUTH-N06(通知スコープ条件のAND結合自体をOR結合との比較で検証する専用回帰テスト)は、AUTH-N03〜N05・AUTH-N26の統合テストが実質的にAND結合が効いていることの証跡になるため、専用テストとしては追加していない
- ブラウザ通知(Web Push)・タブタイトル未読件数表示はフェーズ10(UX拡張)

> **2026-08-16追記**: フェーズ1〜8完了時点のレビューで、`MentionCandidateService`のService層認可(多層防御)の指摘があった。詳細は[review-fixes-2026-08-16.md](review-fixes-2026-08-16.md)を参照。

## 関連ドキュメント

- [`docs/機能定義書/メンション機能定義書.md`](../docs/機能定義書/メンション機能定義書.md)
- [`docs/機能定義書/通知機能定義書.md`](../docs/機能定義書/通知機能定義書.md)
- [`docs/テスト設計書.md`](../docs/テスト設計書.md) §6.2
- [phase4.md](phase4.md)(前フェーズ)
- [phase6.md](phase6.md)(次フェーズ)
