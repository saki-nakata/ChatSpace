## フェーズ9 — フロントエンド本体(`apps/web-next`)

**状態: 未着手**

React + Vite + TypeScript でフロントエンドをゼロから再構築する。ルーティング・Zustand構成・Markdownレンダリングはプロトタイプ(`apps/web`)をほぼそのまま踏襲し、変わるのは主にAPIクライアントの型ソース(zod→OpenAPI生成型)とリアルタイム層(Socket.IO→STOMP)。詳細は [`docs/画面設計書.md`](../docs/画面設計書.md)・[`docs/画面遷移図.md`](../docs/画面遷移図.md) を正とする。

**分量が大きいため以下の5サブフェーズに分割して進める**(TripDiaryの複合フェーズの扱いを踏襲)。

### 9-A 基盤

- [ ] プロジェクト新設(`apps/web-next`、`pnpm-workspace.yaml`に追加、`package.json`の`name`は`@chatspace/web-next`)
- [ ] React Router によるルーティング(`/login`, `/signup`, `/`, `/w/:workspaceId`, `c/:channelId`, `dm/:dmId`)
- [ ] Cookie自動送信の確認(REST・WebSocketハンドシェイクとも`credentials: include`相当)
- [ ] `@stomp/stompjs`によるSTOMPクライアント基盤(`lib/stomp.ts`。接続・サブスクリプション管理)
- [ ] Zustand 4ストアの骨格: `authStore`/`workspaceStore`/`notificationStore`/`presenceStore`
- [ ] フェーズ8で生成したOpenAPI型を使うAPIクライアント(`api/resources.ts`相当)

### 9-B チャンネル/メッセージ表示・送信

- [ ] S-04(ワークスペースシェル)・S-05(チャンネル)・S-06(DM)画面
- [ ] メッセージ一覧: 仮想化リスト、上方向無限スクロール、prepend時のスクロール位置復元、新着メッセージ到達時の自動スクロール制御(最下部付近のみ自動、それ以外は「↓新着N件」ボタン)、自分の送信は常に最下部へ
- [ ] メッセージ入力: Markdown・添付ファイル・`@`メンション自動補完・タイピングイベント送信

### 9-C スレッド・リアクション・メンション

- [ ] S-07スレッドパネル: 初期20件+「さらに20件の返信を読み込む」ボタン、WebSocket新着返信の末尾追加
- [ ] リアクションUI(絵文字トグル、`aria-pressed`)
- [ ] メンション入力補完(対象チャンネルの現メンバーのみ候補)

### 9-D 通知・プレゼンス・検索UI

- [ ] S-12検索モーダル: 「さらに検索結果を表示」ボタン+スクロール併用、件数表示(`nextCursor`ベース)
- [ ] S-13通知パネル: パネル内無限スクロール、新着WebSocket通知の先頭追加、既読化での位置不変、セッション内でのスクロール位置保持
- [ ] プレゼンス表示(オンライン/オフライン)

### 9-E ワークスペース・チャンネル管理UI

- [ ] S-08(チャンネル作成)・S-09(チャンネルメンバー管理)・S-10(ワークスペースメンバー管理)・S-11(DM開始)・S-14(プロフィール編集)の各モーダル

### 先に書くテスト

`docs/テスト設計書.md` §6.2 の該当テストID。

- [ ] AUTH-N18: STOMP宛先契約テスト(フロントエンド側`destinations.ts`実装、フェーズ8で用意した契約テストをgreenにする)

### 対象外(本フェーズでは扱わない)

- 未読区切り線・検索ジャンプハイライト・タイピングインジケーター表示・ブラウザ通知等のUX拡張(フェーズ10)

### 確認方法

```bash
cd apps/web-next
pnpm install
pnpm --filter @chatspace/web-next run typecheck
pnpm --filter @chatspace/web-next run lint
pnpm --filter @chatspace/web-next run build
pnpm --filter @chatspace/web-next dev   # 手動での画面確認
```

## 関連ドキュメント

- [`docs/画面設計書.md`](../docs/画面設計書.md)
- [`docs/画面遷移図.md`](../docs/画面遷移図.md)
- [`docs/機能定義書/リアルタイム通信機能定義書.md`](../docs/機能定義書/リアルタイム通信機能定義書.md)
- [phase8.md](phase8.md)(前フェーズ)
- [phase10.md](phase10.md)(次フェーズ)
