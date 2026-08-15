## フェーズ3 — メッセージ(チャンネル/DM共通)・リアクション・MessageScopeGuard

**状態: 未着手**

メッセージCRUD・スレッド返信・リアクションを実装する。`MessageScopeGuard`によるconfused-deputy対策が本フェーズの中核。詳細は [`docs/機能定義書/メッセージング機能定義書.md`](../docs/機能定義書/メッセージング機能定義書.md)・[`docs/機能定義書/リアクション機能定義書.md`](../docs/機能定義書/リアクション機能定義書.md) を正とする。

### 実装対象

- [ ] `MessageScopeGuard.assertInScope(message, scope)` — `messageId`を受け取る全エンドポイント(context取得・replies取得・編集・削除・リアクション切り替え)の先頭で呼ぶ。URLスコープ(channelId/dmId)と実際のメッセージ所属が一致しない場合404(confused-deputy対策)
- [ ] `MessageService`: 投稿(本文1〜4000文字、`parentId`検証は返信先が同一スコープ・トップレベルメッセージであること[2階層目の返信を拒否]、添付ファイルID検証)、編集(投稿者本人のみ)、削除(論理削除、投稿者本人のみ)
- [ ] **ソフトデリートはtombstone方式**: 一覧・スレッド・コンテキスト取得は削除済み行を除外せず、`body`を伏せたtombstoneとして返す(検索のみ`deletedAt IS NULL`で除外。メッセージング機能定義書§3.3・§6.3)
- [ ] `listMessages`(トップレベル、`(createdAt, id)`複合カーソル、tombstone含む)
- [ ] `listThreadReplies` — **初期20件(古い順)+ 追加読み込み**方式に確定(投稿日時昇順で全件返す、という当初の矛盾を解消)。WebSocket新着返信は末尾に追加
- [ ] `getMessageContext`(around取得) — レスポンスに`targetMessageId`/`olderCursor`/`newerCursor`/`hasOlder`/`hasNewer`を追加し、ジャンプ後の双方向継続取得を可能にする(新規APIは追加せず既存`context`APIを拡張)
- [ ] `ReactionService`: `(messageId, userId, emoji)`一意制約によるトグル、削除済みメッセージへのリアクションは404

### 先に書くテスト

`docs/テスト設計書.md` §6.1・§6.2 の該当テストID。

- [ ] AUTH-P01: クロスチャンネルのメッセージID漏洩防止(非参加のプライベートチャンネルのメッセージIDを、参加中の別チャンネルのURLスコープ経由で操作できないこと)
- [ ] AUTH-P02: 正当なスコープ経由であれば同じ操作が成功すること(AUTH-P01の過剰ブロック検証)
- [ ] AUTH-P03・P04: プライベートチャンネルの非可視性(一覧除外・直接ID指定404)
- [ ] AUTH-N23: ワークスペースキック後のDMメッセージ取得404(HTTPエンドポイント経由)
- [ ] 2階層目スレッド返信の拒否(既に返信であるメッセージを`parentId`に指定すると400)
- [ ] tombstone表示の確認(削除済みメッセージが一覧・スレッド・コンテキスト取得からは除外されず、検索からは除外されること)

### 対象外(本フェーズでは扱わない)

- リアルタイム配信(WebSocket経由の即時反映はフェーズ4)。本フェーズはREST APIのみで完結させ、ポーリングで確認する

### 確認方法

```bash
docker compose up -d postgres
cd apps/api-java
./gradlew build
```

## 関連ドキュメント

- [`docs/機能定義書/メッセージング機能定義書.md`](../docs/機能定義書/メッセージング機能定義書.md)
- [`docs/機能定義書/リアクション機能定義書.md`](../docs/機能定義書/リアクション機能定義書.md)
- [`docs/テスト設計書.md`](../docs/テスト設計書.md) §6.1・§6.2
- [phase2.md](phase2.md)(前フェーズ)
- [phase4.md](phase4.md)(次フェーズ)
