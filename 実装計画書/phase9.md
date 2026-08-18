## フェーズ9 — フロントエンド本体(`apps/web-next`)

**状態: ✅ 完了(9-A〜9-F全て完了。2026-08-16)**

React + Vite + TypeScript でフロントエンドをゼロから再構築する。ルーティング・Zustand構成はプロトタイプ(`apps/web`)をほぼそのまま踏襲し、変わるのは主にAPIクライアントの型ソース(zod→OpenAPI生成型)とリアルタイム層(Socket.IO→STOMP)。詳細は [`docs/画面設計書.md`](../docs/画面設計書.md)・[`docs/画面遷移図.md`](../docs/画面遷移図.md) を正とする。

**分量が大きいため以下の6サブフェーズに分割して進める**(TripDiaryの複合フェーズの扱いを踏襲)。9-Fは当初の計画に含まれておらず、9-A〜9-E完了後にレビューで発覚した抜け漏れとして追加した(詳細は9-F節参照)。

### 9-A 基盤 — ✅ 完了

- [x] プロジェクト新設(`apps/web-next`、`package.json`の`name`は`@chatspace/web-next`。`pnpm-workspace.yaml`は`apps/*`の既存globが自動的にカバーするため変更不要だった)
- [x] React Router によるルーティング(`/login`, `/signup`, `/`, `/w/:workspaceId`, `c/:channelId`, `dm/:dmId`)
- [x] Cookie自動送信の確認(`fetch`の`credentials: "include"`、STOMPハンドシェイクは`WebSocket`が自動送信)
- [x] `@stomp/stompjs`によるSTOMPクライアント基盤(`src/realtime/stomp.ts`)
- [x] Zustand 4ストアの骨格: `authStore`/`workspaceStore`/`notificationStore`/`presenceStore`
- [x] フェーズ8で生成したOpenAPI型を使うAPIクライアント(`openapi-typescript`で`src/api/schema.d.ts`を生成、`src/api/resources.ts`で全エンドポイントをラップ)

### 9-B チャンネル/メッセージ表示・送信 — 完了

- [x] S-04(ワークスペースシェル)・S-05(チャンネル)・S-06(DM)画面
- [x] メッセージ送信・一覧表示・削除・リアクショントグル(STOMPによるリアルタイム同期込み)
- [x] Markdownレンダリング(`marked` + `DOMPurify`、`img`タグ除外、`@`メンションのハイライト表示)
- [x] `@`メンション自動補完(チャンネルスコープのみ。当初9-Cに割り当てていたが、9-Bのメッセージ入力機能と不可分なため前倒しでここに実装した)
- [x] タイピングイベント送信・受信(2秒スロットリング送信、3秒で自動失効)
- [x] メッセージ一覧: 仮想化リスト、上方向無限スクロール、prepend時のスクロール位置復元、新着メッセージ到達時の自動スクロール制御(最下部付近のみ自動、それ以外は「↓新着N件」ボタン)
- [x] 添付ファイルのアップロードUI: バックエンド側の`MessageResponse.attachments`欠落を解消したうえで実装(詳細は「実施内容の詳細」節)

### 9-C スレッド・リアクション・メンション — 完了

- [x] リアクションUI(絵文字トグル、Slack風のクイックリアクションピッカー)。9-Bで先行実装済み
- [x] S-07スレッドパネル: 初期20件+「さらに20件の返信を読み込む」ボタン、WebSocket新着返信の末尾追加
- [x] メンション入力補完(対象チャンネルの現メンバーのみ候補)。9-Bで前倒し実装済み(詳細は上記)

### 9-D 通知・プレゼンス・検索UI — 完了

- [x] プレゼンス表示(オンライン人数。9-Aで基盤実装、個別ユーザーのオンライン状態表示は9-EのS-10ワークスペースメンバー管理モーダルで統合済み)
- [x] S-12検索モーダル: 「さらに検索結果を表示」ボタン+スクロール併用、件数表示(`nextCursor`ベース)
- [x] S-13通知パネル: パネル内無限スクロール、新着WebSocket通知の先頭追加、既読化での位置不変、セッション内でのスクロール位置保持

### 9-E ワークスペース・チャンネル管理UI — 完了

- [x] DM開始(9-Bで暫定実装した`window.prompt`版をS-11正式モーダルに置き換え)
- [x] S-08(チャンネル作成)・S-09(チャンネルメンバー管理)・S-10(ワークスペースメンバー管理)・S-11(DM開始)・S-14(プロフィール編集)の各モーダル

### 9-F レスポンシブ対応 — 完了

**状態: 9-A〜9-Eの完了報告後、`apps/web-next`にブレークポイント接頭辞(`sm:`等)が1つも存在しない(レスポンシブ対応が丸ごと抜けている)ことがレビューで判明したため追加。**画面設計書.md§2(640px未満/640px以上の2段階)には図とクラス名レベルで仕様が明記されており、プロトタイプ`apps/web`には実装済みだった。新規設計ではなく、移植漏れを埋める作業として実施した。

- [x] サイドバー(`components/layout/Sidebar.tsx`に新規分離): 640px未満は`fixed`+`-translate-x-full`のドロワー化、`sm:static sm:translate-x-0`で640px以上は常時表示に復帰。背後に`bg-black/40 sm:hidden`のオーバーレイ(クリックで閉じる)。チャンネル/DMリンク押下時もドロワーを自動的に閉じる
- [x] トップバー(`TopBar.tsx`)に☰ボタン(`sm:hidden`、`aria-label="サイドバーを開く"`)を追加、`WorkspaceShellPage`がドロワー開閉状態を持ち`onOpenSidebar` propで連携
- [x] スレッドパネル展開時、メッセージ列を`hidden sm:flex`で640px未満は非表示にし、パネル(`ThreadPanel.tsx`)を`w-full sm:w-96`にする
- [x] `Modal.tsx`のオーバーレイ余白を`pt-24`固定から`px-4 pt-8 sm:pt-24`に調整(640px未満での窮屈さを解消)

### 先に書くテスト

`docs/テスト設計書.md` §6.2 の該当テストID。

- [x] AUTH-N18: STOMP宛先契約テスト(`src/realtime/destinations.ts`実装、`destinations.contract.test.ts`でフェーズ8の`stomp-destinations.json`と突き合わせてgreen)

### 実施内容の詳細(9-A・9-B・9-C)

#### OpenAPI型生成パイプライン

`apps/web-next/package.json`に`generate:api-types`スクリプト(`openapi-typescript ../api-java/openapi.json -o src/api/schema.d.ts`)を追加し、`src/api/types.ts`で使用する型を短い名前に再エクスポートする構成にした。生成物`schema.d.ts`はコミット対象(CIが`generate:api-types`を実行せず`pnpm install`後に直接`typecheck`/`build`するため、`apps/api-java/openapi.json`と同様にコミットしておく必要がある)。ESLintの対象からは除外した(自動生成物のため)。

#### STOMPクライアント・宛先契約テスト

`src/realtime/destinations.ts`は`apps/api-java`の`StompDestinations.java`を手動で同期する。契約テスト(`destinations.contract.test.ts`)は`apps/api-java/build/generated/stomp-destinations.json`(`./gradlew exportStompDestinations`で生成、コミット対象外のビルド成果物)と`DESTINATION_TEMPLATES`を突き合わせる。CI(`frontend-next-ci.yml`)はJDK 21をセットアップし、フロントエンドのテスト実行前にバックエンドの当該Gradleタスクを実行するよう更新した。

#### チャットUI(Slack実機を参考にしたビジュアル)

Slack公式サイトのプロダクト画像を参考に、以下のビジュアル言語を採用した。

- 左サイドバーは紺色(`slate-800`)、チャンネル/DMリストは`#`アイコン付き、選択中は明るいハイライト
- アバターは角丸正方形。画像未設定時はuserId由来の固定色 + イニシャル文字(`src/lib/avatarColor.ts`)
- メッセージは連続投稿(同一著者・5分以内)をグルーピングしてアバター・名前を省略する(`src/lib/time.ts`の`isSameGroup`)
- メッセージにマウスホバーすると右上にアクションバー(リアクション追加・削除)が浮かび上がる

`ChatView`コンポーネント(`src/components/chat/ChatView.tsx`)がチャンネル/DM共通のメッセージ表示・送信・リアクション・削除を担い、STOMPイベント(MESSAGE_CREATED/UPDATED/DELETED/REACTION_UPDATED)は全て「更新後のメッセージ全体」を運ぶため、idで一致するメッセージを置き換える単一のupsertハンドラで4種類とも処理する設計にした。

**レビュー指摘(reactedByMe)への追従**: `BroadcastMessageResponse`(サーバーの配信専用DTO)は`reactedByMe`を持たないため、フロントエンド側も`DisplayReaction.userIds`に自分のIDが含まれるかで「自分が押したか」を判定する設計にした(REST初回取得時の`reactedByMe`はあえて無視し、STOMP経由の更新と一貫した1つのロジックに統一)。

### 実機ブラウザ確認で発見・修正したバグ(3件)

Playwrightで実際にログイン→ワークスペース選択→チャンネル表示→メッセージ送信→リアクションの一連の操作を行い、以下の不具合を発見して修正した。いずれもcurlベースのスモークテストや既存の自動テストでは検出できていなかった(初めてブラウザから実際にエンドツーエンドで操作したことで顕在化した)。

1. **`application-dev.yml`が`WEB_ORIGIN`環境変数を無視していた**: `web-origin: http://localhost:5173`とハードコードされており、`application.yml`の`${WEB_ORIGIN:http://localhost:5173}`プレースホルダ形式になっていなかった。`devプロファイル使用時は常に環境変数が無視され、旧`apps/web`(:5173)以外のオリジンからのCORS/WebSocket接続確認ができない状態だった。`apps/api-java/src/main/resources/application-dev.yml`を修正。
2. **`CurrentUserArgumentResolver`が匿名認証を認証済みと誤認する実在するバグ(重大)**: `/auth/me`は`permitAll`だが`@CurrentUser`を使うため、未ログイン状態で呼ぶとSpring Securityの`AnonymousAuthenticationToken`(`isAuthenticated()`が`true`を返す仕様)を認証済みとみなし、principal名`"anonymousUser"`を`UUID.fromString()`しようとして500エラーになっていた。`AnonymousAuthenticationToken`の除外チェックを追加し、401(`{"message":"認証が必要です。"}`)を返すよう修正。回帰テスト`AuthMeAuthorizationTest`を追加。
3. **STOMP未接続時の`subscribe()`呼び出しでクラッシュ**: `Client.activate()`直後はWebSocketハンドシェイクが未完了のため、その状態で`Client.subscribe()`を呼ぶと`There is no underlying STOMP connection`例外になっていた(直接URL遷移・ページリロード時に`WorkspaceShellPage`のマウント直後に発生)。`src/realtime/stomp.ts`の`subscribeJson`を、接続済みなら即座に、未接続なら`onConnect`(再接続時も含む)を待ってから購読する設計に変更した。

いずれも[review-fixes-2026-08-16.md](review-fixes-2026-08-16.md)と同様の「実機確認でしか見つからない実在するバグ」のパターンであり、自動テスト・curlスモークテストに加えてブラウザでの実操作確認が引き続き重要であることを裏付けている。

#### Markdown・メンション自動補完・タイピングイベント

- **Markdown**: `src/lib/markdown.ts`が`marked.parse()` → `DOMPurify.sanitize()`(`img`タグを許可リストから除外。Markdown経由の外部画像URLによるトラッキングピクセル的なIP/タイミング漏洩を防ぐため)→ `@handle`をハイライトする独自の`highlightMentions()`の順で処理する。`DOMPurify`の`afterSanitizeAttributes`フックで全リンクに`target="_blank" rel="noopener noreferrer"`を強制する。
- **メンション自動補完**: `MessageComposer`が`@`トリガー(`/(?:^|\s)@([a-zA-Z0-9_.-]{0,20})$/`)を検出すると200msデバウンスで`channelApi.mentionCandidates()`を呼ぶ。DMスコープでは対象APIが存在しないため無効化している。候補はサーバー側で「現在のライブなチャンネルメンバーシップ」に絞り込まれる(メンション機能定義書の非メンバー漏洩防止要件をフロントエンドの補完API呼び出しでも満たす)。
- **タイピングイベント**: `MessageComposer`の`onTyping`を`ChatView`が受け、2000msスロットリングで`/app/channels.{channelId}.typing`(または`dms.{dmId}.typing`)へSENDする。受信側は同一トピックの`TYPING_UPDATE`イベントを`typingUserIds`(Set)で管理し、ユーザーごとに3000msの自動失効タイマーを持つ。

**ブラウザでの実機確認(2ブラウザタブ、alice/bob)で判明した点**: STOMPのSEND→ブロードキャスト→受信側での`JSON.parse`までは正しく動作すること(`WebSocket.prototype.send`/`JSON.parse`をフックして実フレームを確認済み)を検証した。一方、`ChannelPage`/`DMPage`の`userMap`はページマウント時に1回だけ`channelApi.members()`等で取得し、その後のメンバーシップ変更(このテストではチャンネルへの新規招待)を反映しないため、マウント後に参加したユーザーの表示名(タイピング表示・メッセージ送信者名の両方)が解決できず、該当ユーザーは「不明なユーザー」表示・タイピング表示欄が空のままになる。これはタイピング機能自体のバグではなく、`userMap`が既存の設計(9-A)からして静的スナップショットである以上の既知の制約であり、新規に導入したものではない。ページを再読み込みするか、9-D以降でメンバー変更のリアルタイム反映(`workspaceTopic`経由の`MEMBER_ADDED`等)を`userMap`にも波及させる設計にすれば解消する。優先度が低いため今回は対応せず、既知のギャップとして記録するに留める。

#### S-07スレッドパネル(9-C)

- `src/components/chat/ThreadPanel.tsx`(新規): 親メッセージ + 返信一覧(`MessageItem`を再利用)+ 専用の`MessageComposer`をチャンネル/DM表示エリアの右側に並べて表示する。開閉は`ChatView`が`openThreadId`(開いているスレッドの親メッセージID)で管理し、`messages`配列からその場で親メッセージオブジェクトを解決する(親が編集・削除された場合もパネルへ自動反映される)。
- **スレッドの開始導線**: 「N件の返信」リンクは`replyCount > 0`の時だけ表示されるため、返信が0件のメッセージにはこれだけでは辿り着けない。ホバー時のアクションバー(リアクション追加・削除と同じ並び)に🧵ボタンを追加し、返信件数によらず常にスレッドを開始・再訪できるようにした(実装中に自分で気づいて追加した導線で、後述のバグとは別)。
- **STOMPイベントのスレッド振り分け**: `MESSAGE_CREATED`/`UPDATED`/`DELETED`/`REACTION_UPDATED`イベントは`payload.parentId`の有無で振り分ける。`parentId`が無ければメイン一覧(`messages`)へ、開いているスレッドの`parentId`と一致すれば返信一覧(`threadReplies`)へ。開いていない他のスレッドの返信イベントはどちらにも入れず無視する(そのスレッドを開いていない他ユーザーには親メッセージ側の`replyCount`更新だけが届けば十分なため)。STOMPハンドラのクロージャは`[workspaceId, scopeKey]`変更時にしか再生成されないため、「今どのスレッドを開いているか」は`openThreadIdRef`(ref)経由で参照する。
- 返信一覧の取得(`messageApi.replies`)は昇順(古い順)でバックエンドから返るため反転不要。「さらに20件を読み込む」は`nextCursor`を使って末尾に追加する(新しい方向へページング、Slackの挙動と同じ)。

### 実機ブラウザ確認で発見・修正したバグ(9-C、2件)

スレッドパネルの実装後、実際にPlaywrightでメッセージ送信→スレッド返信→削除の一連を操作し、以下2件を発見・修正した。いずれも自動テストでは検出できていなかった。

4. **メインのメッセージ一覧が新しい順(降順)で表示されていた(重大、9-B由来の既存バグ)**: `MessageRepository.findTopLevelFirstPage`はページング(上方向の無限スクロール実装時に「最新から遡る」ため)を見越して`ORDER BY created_at DESC`で返す設計になっているが、`ChatView`の初回取得・削除後の再取得のどちらも配列をそのまま`setMessages`していたため、画面には**新しいメッセージが上、古いメッセージが下**という、チャット表示として逆順の状態になっていた。これまでのブラウザ確認では毎回メッセージが1〜2件しかない状態でしか見ておらず、複数件が同時に並ぶ場面で初めて顕在化した(9-Bの時点で存在していたが未発見だったバグ)。`ChatView.tsx`の該当2箇所で`.slice().reverse()`して時系列順に直した。
5. **スレッド返信を作成しても、親メッセージの「N件の返信」表示が他ユーザー側でリアルタイム更新されない**: `MessageService.create()`は返信作成時に返信自体の`MESSAGE_CREATED`しかブロードキャストしておらず、親メッセージの`replyCount`が増えたことを示すイベントが存在しなかった。スレッドパネルを開いていないユーザーの画面では、ページを再読み込みするまで「N件の返信」バッジが更新されない状態だった。`apps/api-java`の`MessageService.create()`に、返信作成時は親メッセージも`MESSAGE_UPDATED`として再配信する処理を追加(`BroadcastMessageResponse`は既存の型をそのまま利用)。既存のテスト(`MessageCrudIntegrationTest`等)にモックの呼び出し回数アサーションは無く、追加のブロードキャストによる副作用は無いことを`./gradlew build`(スポットレス・ArchUnit・テスト込み)で確認済み。

#### メッセージ一覧の仮想化・無限スクロール、添付ファイルアップロードUI(9-B繰り越し分)

- **仮想化・上方向無限スクロール**: `react-virtuoso`を新規導入。`ChatView.tsx`の`firstItemIndex`(公式の「Prepend Items」パターン、初期値100万からprepend件数だけ減算)でスクロール位置を保ったまま古いページを先頭に追加する。`startReached`で`messageApi.list`をカーソルページングし、`mainNextCursor`が尽きたら追加取得を止める。
- **新着メッセージの自動スクロール制御**: `followOutput={(isAtBottom) => isAtBottom ? "smooth" : false}`により最下部にいる間だけ追従させる(react-virtuosoが`firstItemIndex`変化=prependと単純append=新着を区別するため、追加のフラグ管理は不要)。最下部から離れている間の新着(`MESSAGE_CREATED`のみ、編集/削除/リアクションは対象外)は`unseenCount`を積み上げ、「↓新着N件」ボタンで`scrollToIndex({ index: "LAST" })`へジャンプする。自分の送信は`atBottom`状態によらず必ず最下部へ強制スクロールする(Slackと同じ挙動)。
- **削除時の再取得を撤廃**: 旧実装はメッセージ削除後にメイン一覧を`messageApi.list`で再取得していたが、これは仮想化リストがprependで積み上げたページを毎回1ページ目まで巻き戻してしまい、無限スクロールと両立しない。STOMPの`MESSAGE_DELETED`は同一トピックの購読者全員(操作者本人を含む)へ配信される設計(create/edit/リアクションと同じ)であるため、既存の単一upsertハンドラで十分tombstone状態に更新できる。再取得はスレッド返信側(件数が少なく巻き戻りの実害が無い)にのみ残した。
- **添付ファイルアップロードUI**: バックエンド`MessageResponse`にOpenAPIスキーマ`attachments`フィールドが存在しなかった(フェーズ3策定時点の設計漏れ)ため、まず`apps/api-java`側で`MessageAttachmentResponse`(`upload.AttachmentResponse`と同一項目だが、`upload`パッケージが既に`message`パッケージに依存しているため循環を避けて`message`パッケージ内に別途定義)を追加し、`MessageResponse`/`BroadcastMessageResponse`双方に`attachments`を持たせた(tombstone時は空配列)。`./gradlew generateOpenApiDocs`→`pnpm run generate:api-types`でスキーマを再生成。フロントエンドは`MessageComposer`に📎ボタン・ファイル選択(`image/png,jpeg,gif,webp,video/mp4,webm`、バックエンドのマジックバイト判定と一致させた許可リスト)・選択と同時アップロード・プレビューサムネイル・アップロード失敗時のインライン表示を追加し、`messageApi.create`へ`attachmentIds`を渡す。`MessageItem`は`attachments`を画像(クリックで別タブ表示)/動画(`<video controls>`)として描画する。

### 実機ブラウザ確認で発見・修正した問題(仮想化・添付ファイル、2件)

Chrome DevToolsで実際にログイン→チャンネル表示→メッセージ送信→画像添付の一連を操作し、以下2件を発見・修正した。いずれも`typecheck`/`lint`/`build`/自動テストでは検出できていなかった。

6. **react-virtuosoの`components`propに`undefined`を条件付きで渡すとクラッシュする**: 古いページ読み込み中だけヘッダーを出す意図で`components={loadingOlder ? {...} : undefined}`としたところ、`Cannot read properties of undefined (reading 'EmptyPlaceholder')`で画面全体がクラッシュした。`components`は常に同じ形の(かつ`loadingOlder`変化時のみ参照が変わるよう`useMemo`した)オブジェクトを渡し、内部で`loadingOlder`に応じてヘッダーの中身を出し分ける形に修正した。
7. **添付ファイル付きメッセージを本文なしで送信すると400になる**: 当初Slackに倣い「本文が空でも添付ファイルがあれば送信可」とフロントエンドを実装したが、実機確認で`本文は1〜4000文字で入力してください。`エラーが判明した。`docs/機能定義書/メッセージング機能定義書.md`§5を確認したところ、本文は添付ファイルの有無によらず必須(1〜4000文字)と明記された意図的な仕様だった(バックエンドのバグではない)。フロントエンド側を仕様に合わせ、本文が空の間は添付ファイルがあっても送信ボタンを無効化するよう修正した。

#### S-12検索モーダル・S-13通知パネル(9-D)

- **共通のアクセシビリティ基盤を新規導入**: `docs/画面設計書.md`§3が「新規要件」として明記する`role="dialog"`・`aria-modal`・`aria-labelledby`・Escapeでの閉じる操作・フォーカストラップ・開閉時のフォーカス管理(初期フォーカス・起動元への復帰)を、`src/lib/useDialogA11y.ts`(共通フック)+`src/components/common/Modal.tsx`(モーダル基盤)として1箇所に集約した。S-12検索モーダルと今回実装したS-13通知パネルの両方がこれを使う設計とし、9-Eの管理系モーダル(S-08〜S-11・S-14)でも同じ基盤を再利用する想定。
- **S-12検索モーダル**(`src/components/search/SearchModal.tsx`): キーワード入力→`searchApi.search()`。「さらに検索結果を表示」ボタン押下後はスクロール(`IntersectionObserver`)でも追加取得できる併用方式(画面設計書のレビュー指摘通り)。件数表示は追加の`COUNT(*)`を発行せず`nextCursor`の有無から組み立てる(`nextCursor != null`の間は「N件以上」、`null`になった時点で確定件数)。結果クリックで該当チャンネル/DMへ`?highlight=<id>`(スレッド返信の場合は親メッセージID)付きで遷移し、返信ヒット時は`&reply=<返信ID>`も付与する。
- **S-13通知パネル**(`src/components/notification/NotificationPanel.tsx`): `notificationStore`にページング状態(`nextCursor`/`loadedForWorkspaceId`等)を追加し、パネル下端到達(`IntersectionObserver`)で次の50件を取得する。「パネルを閉じても同一セッション中は再取得しない」「スクロール位置を保持する」という画面設計書の要件のため、パネルは一度開いたら`open=false`時も`hidden`クラスで隠すだけでアンマウントしない設計にした(コンポーネント自体・スクロール位置・取得済み一覧のDOM状態がすべて保持される)。既読化(個別・全件)は一覧の並び順・表示位置を変更しない(`readAt`のみ更新)。WebSocket新着通知は先頭に追加(既存の`subscribeRealtime`のまま)。
- **検索結果・通知クリック時のスレッド自動オープン**: `ChatView`に`initialOpenThreadId`propを追加し、`ChannelPage`/`DMPage`が`?reply=`クエリの有無で`?highlight=`を親スレッドIDとして解釈し渡す。ただし親メッセージが直近ページ(仮想化リストの初回読み込み分)に無い場合は何も起きない(スレッドパネルは開かない)。`messageApi.context()`で古い親メッセージを個別に取得してリストへ差し込むことも検討したが、仮想化リストの`firstItemIndex`は「連続したページのprepend」を前提にしており、非連続な1件を任意の位置へ挿入すると仮想インデックスの整合性が崩れるリスクがあるため見送った。同様の理由で、検索結果の`highlight`パラメータによる「該当メッセージへスクロールして視覚的にハイライトする」機能自体も本フェーズでは実装していない(既存の対象外注記の通りフェーズ10で対応)。
- **ワークスペースメンバー全体のuserMap**: 検索結果・通知はチャンネル/DMを横断し得るため、`WorkspaceShellPage`で`workspaceApi.members()`を新たに取得し`TopBar`以下へ渡す(既存の`ChannelPage`/`DMPage`のuserMapは単一チャンネル/DMのメンバーに限定されるため、そのままでは使えなかった)。

### 実機ブラウザ確認で発見・修正した問題(S-12/S-13アクセシビリティ、3件)

Chrome DevToolsで検索モーダル・通知パネルの開閉・Escape・フォーカス移動を実際に操作し、以下3件を発見・修正した。いずれも`typecheck`/`lint`/`build`では検出できない、実際にキーボード操作した際の挙動としてのみ顕在化するバグだった。

8. **モーダルの初期フォーカスが閉じるボタン(✕)に当たってしまう**: 検索入力欄に`autoFocus`を指定していたが、閉じるボタンがDOM順で先頭にあるため、フック側の「先頭のフォーカス可能要素」ロジックがそちらを掴んでしまっていた。
9. **Escapeで閉じても起動元のボタンへフォーカスが戻らずbodyに落ちる**: `onClose`を呼び出し元がインラインの矢印関数で渡すと親の再描画のたびに参照が変わり、`useEffect`の依存配列に`onClose`を含めていたことで「直前にフォーカスされていた要素」の記録がモーダル自身の要素で毎回上書きされていた。加えて、React の`autoFocus`はこのフックの`useEffect`より先(コミット時)に発火するため、フック内部で`document.activeElement`を自己キャプチャする方式では起動元を正しく記録できないことも判明した。呼び出し元(`TopBar`)がクリックイベントの`e.currentTarget`を明示的に`restoreFocusTo` propとして渡す設計に変更した(このとき`ref.current`をrender中に読むと`react-hooks/refs`に抵触するため、`useState`でトリガー要素を保持する)。
10. **上記2点を修正した直後、依然として閉じるボタンにフォーカスが残る問題が再発**: 原因は`FOCUSABLE_SELECTOR`(カンマ区切りのセレクタリスト)に対して`:not([data-initial-focus-skip])`を単純に文字列連結していたため、`:not()`がリストの最後の枝にしか掛からず、閉じるボタンを除外できていなかったこと。`:is(${FOCUSABLE_SELECTOR}):not(...)`のように`:is()`でリスト全体を1つのセレクタにまとめてから`:not()`を掛けることで解決した。

これら3件はいずれもReact 18の`StrictMode`(開発時のeffect二重実行)や、DOM順序に依存した素朴なセレクタ実装が組み合わさって初めて顕在化するタイプの不具合であり、コードレビューだけでは見つけにくく実機でのキーボード操作確認が有効だった好例と言える。

#### S-08〜S-11・S-14管理系モーダル、プロフィール編集API(9-E)

- **プロフィール編集API(既知のギャップの解消)**: `実装計画書/phase7.md`に記録されていた「プロフィール編集エンドポイントがバックエンドのどのフェーズにも含まれていない」既知のギャップを解消するため、新規`com.chatspace.api.profile`パッケージ(`UserProfileController`/`UserProfileService`)で`PATCH /users/me`を追加した。表示名・ステータス・アバターURLはすべて任意(nullのフィールドは更新しない)の部分更新とし、アバター変更は選択と同時に即座に保存、表示名・ステータスは「保存」ボタンでまとめて保存する2系統のフローに対応させた。**アバターURLは呼び出し元自身がアップロードした添付ファイルであることを検証する**(`AttachmentRepository`で`storageKey`から所有者を引き、一致しなければ拒否)。これを検証しないと、他人がアップロードした投稿前プレビュー画像(本来アップロード本人にしか見えない)を自分のアバターに指定することで、`UploadService.authorizeUnattachedServe`の「アバターとして使われているファイルは認証済み全ユーザーに公開する」分岐を悪用し、非公開ファイルを実質的に公開できてしまう権限昇格になる(添付ファイル機能定義書§6)。`user`パッケージが`message`パッケージへ依存する形にすると既存の`message→user`依存(メンション解決)と循環するため、新規`profile`パッケージ(誰にも依存されないリーフパッケージ)に検証ロジックごと切り出した。
- **S-08チャンネル作成・S-09チャンネルメンバー管理・S-10ワークスペースメンバー管理・S-11 DM開始**(`src/components/workspace/`): いずれもバックエンドAPIは既存(フェーズ2)のものをそのまま使用。招待系API(`InviteChannelMemberRequest`/`InviteWorkspaceMemberRequest`)は対象をログインID文字列で受け取るのに対し、削除・キック系API(`removeMember`のパス変数/`KickWorkspaceMemberRequest`)は内部UUIDを要求する非対称な設計になっている点に注意して実装した(メンバー一覧取得結果の`user.id`を使う)。S-11は9-Bで`window.prompt`による暫定実装だったものを正式モーダルに置き換えた。
- **S-14プロフィール編集**(`src/components/profile/ProfileModal.tsx`): アバタークリック→`uploadApi.upload()`→即座に`PATCH /users/me`、表示名・ステータスは「保存」ボタンでまとめて更新。ログアウトボタンは保存とは独立。
- **チャンネル/DMヘッダーの新規追加**: S-09の起動元が画面設計書上「S-05ヘッダーのメンバーボタン」だが、`ChatView`にはヘッダー領域自体が存在しなかった(9-B時点でチャンネル名の表示すら無かった)。`ChatView`に`header`propを追加し、`ChannelPage`(チャンネル名・メンバー・オーナー限定のチャンネル削除)・`DMPage`(相手の表示名・ステータス)がそれぞれ構築して渡す形にした。チャンネル削除はブラウザ標準の`confirm()`で確認後に実行する(画面設計書の記載通り)。
- **`WorkspaceShellContext`(React Router `Outlet` context)**: チャンネル一覧・DM一覧・オーナー判定・オンラインユーザー集合・サイドバー再取得関数を`WorkspaceShellPage`から`useOutletContext()`経由で子ルート(`ChannelPage`/`DMPage`)へ渡す設計にした。チャンネル名など「サイドバーで既に取得済みのデータ」を各ページが個別に再フェッチしないための共有。

### 実機ブラウザ確認で発見・修正した問題(9-E、1件)

11. **`data-initial-focus-skip`による閉じるボタン除外だけでは不十分だった**: S-14プロフィール編集モーダルを開くと、`autoFocus`を指定した表示名入力ではなく、それより前にあるアバター変更ボタンにフォーカスが当たってしまった。9-Dで修正した「閉じるボタンをDOM順の初期フォーカス候補から除外する」対処は、Modal共通の閉じるボタンには効くが、各モーダル固有の要素(アバターボタン等)がautoFocus対象より前にある場合には及ばないことが判明した。`document.activeElement`がコンテナ内かどうかで判定する方式もStrictMode下では信頼できない(cleanupのフォーカス復帰でactiveElementが起動元要素に書き換わり、2回目のeffect実行時点で無効な判定になる)ため、根本的に作り直した: 各モーダルのautoFocus対象要素に`data-initial-focus`を明示的に付与し、`useDialogA11y`はDOM順序や`document.activeElement`の状態に依存せず`[data-initial-focus]`を最優先で採用する設計に変更した。

#### レスポンシブ対応(9-F)

- **サイドバー幅・配色の食い違いの解消**: `apps/web-next`は`w-60`/`bg-slate-800`、プロトタイプ`apps/web`と画面設計書.mdは`w-64`/`bg-brand-900`と食い違っていた。`apps/web/tailwind.config.js`の`brand-500`に「Slack風の紫をベースにしたブランドカラー」と明記されており、Slack実サイトのデフォルトテーマ(暗い紫系)とも一致することを確認したうえで、`apps/web-next`側をプロトタイプ・画面設計書.mdの値(`w-64`/`bg-brand-900`)に合わせた(画面設計書.mdの更新は不要と判断)。
- **スレッドパネルの戻る導線(プロトタイプに無い新規判断)**: プロトタイプの`ThreadPanel.tsx`は640px未満でも「✕」のみだったが、実際のSlackはモバイル幅でスレッドをメイン画面を置き換える全画面表示にし、左上の「←」で元の一覧へ戻るUIになっている(ユーザー確認により判明)。`apps/web-next`側は640px未満で「← スレッド」(左上、`onClose`を呼ぶ)、640px以上で従来通り右上「✕」を表示する形にした。スレッドを閉じてもメッセージ一覧はアンマウントされない(`hidden`で隠すだけ)ため、スクロール位置は自然に保持される。
- **`100dvh`によるiOS Safari対策(プロトタイプに無い新規判断)**: iOS Safariはアドレスバーの伸縮でビューポート高さが変わり、`height: 100%`指定のみだと入力欄が画面外へ押し出される既知の問題があるため、`@supports (height: 100dvh)`で対応ブラウザにのみ`100dvh`を適用する形にした(未対応ブラウザは既存の`100%`のままフォールバック)。
- **`WorkspaceShellPage`からのサイドバー分離**: 640行弱まで肥大化していた`WorkspaceShellPage.tsx`からサイドバーのJSX(約80行)を`components/layout/Sidebar.tsx`へ切り出した。チャンネル一覧・DM一覧・オーナー判定等のデータはそのまま`WorkspaceShellPage`が保持し、`Sidebar`はドロワー開閉状態(`isOpen`/`onClose`)とモーダル起動コールバックのみを受け取る表示専用コンポーネントとした(モーダルの状態管理自体はプロトタイプと異なり`WorkspaceShellPage`に集約したまま、`restoreFocusTo`によるフォーカス復帰の仕組みと整合させるため)。

Chrome DevToolsで390×844(モバイル幅)・1280×800(デスクトップ幅)の両方を実機確認した。サイドバードロワーの開閉・チャンネル遷移時の自動クローズ・スレッドパネルの全画面化と「←」での復帰(メッセージ一覧のスクロール位置保持を含む)・モーダルの余白を確認し、いずれも意図通り動作した。デスクトップ幅では従来通りサイドバー常時表示・☰ボタン非表示・スレッドパネル右側並置・スレッドパネル「✕」が正しく機能することも確認済み。

### レビュー指摘への対応(9-F、Terraによるレビュー、2件)

初回実装後、以下2件の指摘を受け対応した。いずれもTouch操作・極小幅という「実際にその条件で操作しないと気づきにくい」種類の不具合だった。

12. **返信0件のメッセージからモバイルでスレッドを開始できない(重大)**: `MessageItem.tsx`のアクションバー(🙂+・🧵・削除)は`hidden group-hover:flex`でホバー時のみ表示する設計だったが、タッチ端末には`:hover`状態が無い(またはブラウザ依存で不安定)ため、640px未満では常時非表示になっていた。一方「N件の返信」リンクは`replyCount > 0`の場合のみ表示されるため、返信が1件も無いメッセージはモバイルからスレッドを開始する手段が一切無かった(リアクション追加・自分のメッセージの削除も同様に不可能だった)。`hidden group-hover:flex`を`flex sm:hidden sm:group-hover:flex`に変更し、640px未満は常時表示・640px以上は従来通りホバー表示に分けることで解消した(アクションバー全体が同じ原因を共有していたため、🧵ボタンだけでなく🙂+・削除も同時に解消される)。
13. **通知パネルが極小幅端末で画面左端からはみ出す**: `NotificationPanel.tsx`が`right-4`(右から16px)かつ固定`w-80`(320px)だったため、画面幅320pxちょうどの端末では左端が16pxはみ出しクリップし得る状態だった。`w-80`を`w-[calc(100vw-2rem)] sm:w-80`に変更し、640px未満は画面幅から左右16pxずつ引いた幅に、640px以上は従来通り固定320pxに戻した。

修正後、320×700(極小幅)・390×844(モバイル)・1280×800(デスクトップ)の3パターンで再度実機確認し、返信0件のメッセージからスレッドを開始できること、通知パネルが320px幅でもクリップしないこと、デスクトップ幅でアクションバー常時表示・通知パネル固定幅という従来挙動に回帰していないことを確認した。

**既知の限界(未対応)**: ブレークポイントごとのレイアウト崩れを検知する自動テストは無い(`vitest`+`jsdom`は実レイアウト・メディアクエリを評価しないため、この種の回帰は検出できない)。上記の実機確認は本セッション時点のスナップショットであり、以後のコード変更を継続的に保証するものではない。Playwright等によるビジュアル/レイアウトテストの導入は本フェーズのスコープ外とし、対応する場合は別途検討する。

### レビュー指摘への対応(9-F、Opusによる網羅レビュー、10件+その他4件)

`apps/web-next`全体を対象にした網羅的なレビューで、旧`apps/web`からの回帰・未実装のまま残っていた機能・アクセシビリティ上の欠落が多数指摘された。優先度の高いものから順に対応した。

**機能面の回帰・欠落**

14. **既読化APIがどこからも呼ばれていない(最優先・重大な回帰)**: `channelApi.markRead`/`dmApi.markRead`は`resources.ts`に定義済みだったが、`apps/web-next`のどこからも呼び出されておらず、チャンネルを開いても未読バッジが永久に減らない状態だった。`ChatView.tsx`に`markScopeRead()`を追加し、(1)スコープを開いた初回読み込み完了時、(2)自分以外の投稿によるMESSAGE_CREATED受信時、の2箇所で呼ぶようにした(旧`apps/web`の`ChatView.tsx`と同じ設計)。呼び出し後は`onRead`プロパティ経由で`ChannelPage`/`DMPage`が`WorkspaceShellContext`の`refreshSidebar`を呼び、サイドバーの未読バッジを更新する。
15. **メッセージ編集UIが無い**: `messageApi.edit`は存在するが呼び出し箇所が無く、`(編集済み)`バッジだけ表示されるのに編集手段が皆無だった。`MessageItem.tsx`にインライン編集(✏️ボタン→`textarea`へ切替→保存/キャンセル、Enterで保存・Shift+Enterで改行・Escapeでキャンセル)を追加し、`ChatView.tsx`に`handleEdit`を実装、`ThreadPanel.tsx`にも`onEdit`を通した(スレッド内の親メッセージ・返信の両方で編集可能)。
16. **サイドバーのアクティブチャンネル強調・未読の太字表示が無い**: `Sidebar.tsx`が素の`Link`だったため現在開いているチャンネル/DMが視覚的に分からなかった。`NavLink`+`isActive`で`bg-brand-800 text-white`を当てる(旧`apps/web`と同じ)、未読チャンネルは`font-semibold text-white`にする対応を追加した。
17. **日付区切り線が無い**: メッセージ一覧に日付の境目が無く、時刻(`HH:mm`)のみでは「いつのメッセージか」が遡って判別できなかった。`ChatView.tsx`の`rows`計算に`dateLabel`(直前メッセージと暦日が異なる場合のみ`formatDateLabel`で算出、`lib/time.ts`に`isSameCalendarDay`を追加)を持たせ、`itemContent`でメッセージ行の直前に区切り線を描画する形にした。**日付区切り線を独立したVirtuoso行にはしていない**: `firstItemIndex`ベースのprepend処理は「読み込んだメッセージ件数」と「増えた行数」が一致することを前提にしており、区切り線を別行にすると新しい日付境界を跨ぐprependのたびにこの前提が崩れ、スクロール位置復元が壊れるリスクがあったため(9-B時点で見送った「検索/通知ジャンプ時に古いメッセージをリストへ差し込む」対応を見送った理由と同じ)。区切り線は各メッセージ行に内包する形にすることでこの問題を回避した。

**操作性・アクセシビリティ**

18. **ホバーアクションバーにキーボードで到達できない**: `sm:hidden sm:group-hover:flex`(9-Fの前回対応で追加)は、`display:none`の要素がそもそもフォーカス不能でTabで到達できないため、`group-focus-within`を後から追記しても実効性が無いことが実機確認で判明した(先に`sm:group-focus-within:flex`だけを追記して試したところ機能しなかった)。`display`の出し分けをやめ、`opacity-0 pointer-events-none`⇔`opacity-100 pointer-events-auto`(`group-hover`/`group-focus-within`で切替)に設計し直した。これにより640px以上でもボタン自体は常時DOM上でフォーカス可能なまま(Tabで到達でき、フォーカスが当たった瞬間に視覚的に出現する)になる。実機でクリック→Tabキーの経路を検証し、フォーカスが意図通り次のボタンへ移り、バーが表示されることを確認した。
19. **モバイルではアクションバーが全メッセージの本文に重なって常時表示**: 前回対応でモバイルは常時`flex`にしたが、`absolute right-4 top-0`のままだったため本文1行目に重なっていた。`static`(モバイル)/`sm:absolute`(デスクトップ)の出し分けに加え、外側コンテナに`flex-wrap`を付与し、モバイルではアクションバーを`w-full`にして自然に折り返させる(本文の下に独立した行として表示される)ようにした。
20. **サイドバードロワーにEscape・フォーカストラップ・フォーカス復帰が無い**: モーダル類は`useDialogA11y`で作り込んであったが、ドロワー(`Sidebar.tsx`)だけ素の`-translate-x-full`のみで、閉じている間もチャンネルリンクがTab順に残り、開いている間もEscapeで閉じられなかった。`Sidebar`にも`useDialogA11y`(`isOpen`を`active`として渡す)を適用し、`TopBar`の☰ボタンから`restoreFocusTo`を渡す配線を追加した。640px以上では`isOpen`が常にfalseのまま(☰ボタン自体が`sm:hidden`で押せない)なので、フックは実質無効化されたまま常時表示の静的サイドバーとして機能し、既存の挙動に影響しない。
21. **入力欄が自動リサイズしない**: `rows={1}`+`resize-none`のみで高さ調整ロジックが無く、`max-h-40`が実質死んでいた。`MessageComposer.tsx`に`body`変更時に`scrollHeight`を反映する`useEffect`を追加した(標準的なtextarea自動リサイズの手法)。
22. **メッセージ削除に確認が無い**: ホバーメニュー上のワンクリックで即削除・取り消し不可だった。`MessageItem.tsx`の削除ボタンに`window.confirm()`を追加した(チャンネル削除と同じパターン)。
23. **ログイン/サインアップのフォームアクセシビリティ**: `label`が`htmlFor`/`id`で紐付いておらずラベルクリックでフォーカス移動できない、`autoComplete`が無くパスワードマネージャが効かない状態だった。`LoginPage.tsx`/`SignupPage.tsx`の全入力欄に`id`/`htmlFor`のペアと適切な`autoComplete`(`username`/`current-password`/`new-password`/`nickname`)を追加した。

**その他**

24. **チャンネル切替時に画面全体が一瞬白くなる**: `ChannelPage.tsx`/`DMPage.tsx`が`userMap`読み込み中`!loaded`でヘッダーごと`null`を返していたため、切替のたびに右ペイン全体が一瞬空になっていた。`loaded`ゲートを撤廃し、切替時も直前のuserMapを表示したまま新しい方に差し替わるようにした(`ChatView`自体は`scopeKey`変更で自律的にメッセージを再取得するため、ページ側がuserMap読み込みで描画全体をブロックする必要が無い)。`DMPage.tsx`は合わせて、独自に`dmApi.list()`を再フェッチしていた処理を`WorkspaceShellContext`の`dms`から解決する形に変更し、二重フェッチも解消した。
25. **リアクションのホバーツールチップが無い**: 誰が押したか確認できなかった。`MessageItem.tsx`のリアクションチップに`userMap`から解決した表示名一覧を`title`属性として追加した。
26. **サイドバー最上部にワークスペース名が無い**: 「← ワークスペース一覧」のみでどのワークスペースにいるか画面上から分からなかった。`WorkspaceShellPage`が`workspaceStore`から現在のワークスペース名を`Sidebar`へ渡し、見出しとして表示するようにした。

**対応しなかった指摘(既知の限界として記録済み)**

- **古いスレッドへジャンプするとパネルが開かない**: 検索結果・通知から初回読み込み分(直近50件)より古い親メッセージのスレッドへ遷移すると無反応になる件。これは9-D実装時点で発見済みの制約であり(「実施内容の詳細」節参照)、`messageApi.context()`で個別に取得してリストへ差し込む対応も検討したが、仮想化リストの`firstItemIndex`が「連続したページのprepend」を前提にしているため、非連続な1件を任意位置へ挿入すると仮想インデックスの整合性が崩れるリスクがあり見送っている。今回の日付区切り線対応(指摘17)でも同じ理由から独立行を避けた設計にしており、一貫した判断としている。

実機確認は390×844(モバイル)・1280×800(デスクトップ)の両方で実施した。既読化のAPI呼び出し(ネットワークログで`POST .../read`の204応答を確認)、メッセージ編集(保存後に`(編集済み)`表示・リロード後も内容が保持されること)、削除確認ダイアログのキャンセルでメッセージが残ること、サイドバーのアクティブハイライトと日付区切り線、キーボードのみでのアクションバー到達(クリックでリアクションチップにフォーカス→Tabキーで🙂+ボタンへ移動しバーが視覚的に出現すること)、モバイルでのアクションバー折り返し表示、サイドバードロワーのEscape閉じとフォーカス復帰、入力欄の自動リサイズ(5行入力で高さが伸び、クリアすると縮むこと)を、いずれも実際の操作で確認した。

### 対象外(本フェーズでは扱わない)

- 未読区切り線・検索ジャンプハイライト(該当メッセージへのスクロール・視覚的ハイライト)・ブラウザ通知等のUX拡張(フェーズ10)

### 確認方法

```bash
cd apps/web-next
pnpm install
pnpm --filter @chatspace/web-next run typecheck
pnpm --filter @chatspace/web-next run lint
pnpm --filter @chatspace/web-next run build
cd ../api-java && ./gradlew exportStompDestinations   # 契約テストの前提
cd ../web-next && pnpm --filter @chatspace/web-next run test
pnpm --filter @chatspace/web-next run dev   # 手動での画面確認(:5174)
```

## 関連ドキュメント

- [`docs/画面設計書.md`](../docs/画面設計書.md)
- [`docs/画面遷移図.md`](../docs/画面遷移図.md)
- [`docs/機能定義書/リアルタイム通信機能定義書.md`](../docs/機能定義書/リアルタイム通信機能定義書.md)
- [phase8.md](phase8.md)(前フェーズ)
- [phase10.md](phase10.md)(次フェーズ)
