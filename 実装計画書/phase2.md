## フェーズ2 — ワークスペース/チャンネル/DM の CRUD(メッセージ抜き)

**状態: 未着手**

ワークスペース・チャンネル・DMのCRUDと、フェーズ1で骨格のみ作った認可サービス本体を完成させる。**DM機能はプロトタイプに実在するギャップ(ワークスペースメンバーシップ再確認漏れ)の修正を含む最重要フェーズ。** 詳細は [`docs/機能定義書/ワークスペース機能定義書.md`](../docs/機能定義書/ワークスペース機能定義書.md)・[`docs/機能定義書/チャンネル機能定義書.md`](../docs/機能定義書/チャンネル機能定義書.md)・[`docs/機能定義書/DM機能定義書.md`](../docs/機能定義書/DM機能定義書.md) を正とする。

### 実装対象

- [ ] `WorkspaceAuthorizationService`本体: `requireMember`(非メンバーは404)、`requireOwner`(オーナー以外は403)
- [ ] `WorkspaceController`/`Service`: 作成、一覧、メンバー一覧、プレゼンス取得、招待(オーナー限定)、キック(オーナー限定)、自主退出(オーナーは退出不可)
- [ ] `ChannelAuthorizationService`本体: `requireChannelMember(channelId, userId, workspaceIdOrNull)`(存在しない・workspaceId不一致・非メンバーいずれも404)
- [ ] `ChannelController`/`Service`: 作成(オーナー限定、パブリック/プライベート)、一覧(パブリック+自分の所属)、既読更新、自主参加(パブリックのみ)、メンバー招待(オーナー限定)、メンバー削除(自己退出 or オーナー限定)、削除(オーナー限定)
- [ ] **プライベートチャンネルへの`join`試行は非メンバーに対して404を返す**(403にしない。存在秘匿方針との整合、チャンネル機能定義書§3.3参照)
- [ ] **`DmAuthorizationService.requireDmAccess()`**: DM参加者チェックに加えて、**呼び出し時点で有効な`WorkspaceMember`であることを必須条件として明示的に検証する**(いずれか欠けても404)。プロトタイプの`authz.ts`はこの検証が漏れており、`dm-messages.ts`の7箇所・`uploads.ts`いずれも補っていなかった実在のギャップの修正
- [ ] `DmController`/`Service`: DMスレッド一覧・作成(ハンドルによる相手解決)、既読更新

### 先に書くテスト

`docs/テスト設計書.md` §6.1・§6.2 の該当テストID。実装前に失敗する状態で書く。

- [ ] AUTH-P05: workspaceId/channelId不一致時の404
- [ ] AUTH-P06〜P08: オーナー限定操作の403(チャンネル作成・招待・キック)
- [ ] AUTH-P09: オーナーは実行でき、キック後はDB上のメンバーシップが実際に削除されていること
- [ ] AUTH-N22: `DmAuthorizationService.requireDmAccess()`の単体/統合テスト(HTTPエンドポイント無しで、キック後に404相当の例外を投げること)
- [ ] DMハンドル解決(プロトタイプ`test/authorization.test.ts`からの移植)

### 対象外(本フェーズでは扱わない)

- メッセージ本体のCRUD(フェーズ3)
- DMメッセージ取得の実エンドポイント経由でのキック後404確認(AUTH-N23、フェーズ3でメッセージ機能実装後)

### 確認方法

```bash
docker compose up -d postgres
cd apps/api-java
./gradlew build
```

## 関連ドキュメント

- [`docs/機能定義書/ワークスペース機能定義書.md`](../docs/機能定義書/ワークスペース機能定義書.md)
- [`docs/機能定義書/チャンネル機能定義書.md`](../docs/機能定義書/チャンネル機能定義書.md)
- [`docs/機能定義書/DM機能定義書.md`](../docs/機能定義書/DM機能定義書.md)
- [`docs/テスト設計書.md`](../docs/テスト設計書.md) §6.1・§6.2
- [phase1.md](phase1.md)(前フェーズ)
- [phase3.md](phase3.md)(次フェーズ)
