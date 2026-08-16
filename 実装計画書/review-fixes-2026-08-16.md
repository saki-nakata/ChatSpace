## Opus によるフェーズ1〜8レビュー指摘への対応(2026-08-16)

フェーズ1〜8完了時点のコード全体をOpusにレビューしてもらい、指摘事項全項目(重大2件・中程度1件・アーキテクチャ懸念1件・例外処理3件・テスト不足3件・軽微6件)を本セッション内でまとめて修正した。個々のフェーズ完了後に見つかった指摘のため、各`phaseN.md`の本文は書き換えず、本ファイルに一括で記録する(該当フェーズのドキュメントには本ファイルへのリンクのみ追記する)。

### 重大(セキュリティ)

1. **プライベートチャンネルのSTOMP経由の情報漏洩** — `ChannelService.create()`/`removeMember()`/`delete()`が、チャンネルの`type`を問わず無条件で`/topic/workspaces.{id}`へブロードキャストしていた。プライベートチャンネルの作成・キック・削除イベント(チャンネル名を含む)が、非参加のワークスペースメンバー全員に届いてしまっていた。REST側は`ChannelVisibilityAuthorizationTest`で正しく塞がれていたが、STOMP経路だけ素通りしていた(DMで同種の問題を修正したのと同じ構造の見落とし)。
   - 修正: `RealtimeEventPublisher`に`channelCreatedForUser`/`channelDeletedForUser`/`channelMemberKickedForUser`(個人キュー配信)を追加し、`ChannelService`側でプライベートチャンネルの場合はワークスペース全体ブロードキャストではなく実メンバーの個人キューへ配信するよう分岐した。
   - テスト: `StompAuthorizationTest`に`createPrivateChannel_doesNotBroadcastToWorkspaceTopic`・`createPrivateChannel_notifiesMemberViaPersonalQueue`・`kickFromPrivateChannel_doesNotBroadcastToWorkspaceTopic`の3件を追加(実STOMP接続での検証)。
   - 関連: [phase2.md](phase2.md)(ChannelService新設)、[phase4.md](phase4.md)(RealtimeEventPublisher新設)

2. **`reactedByMe`が操作者視点のまま全購読者へ配信される** — `MessageService`のリアクション更新・メッセージ作成/編集/削除イベントは、操作を行った本人(`callerId`)基準で計算した`MessageResponse`(`reactedByMe`含む)をそのまま`RealtimeEventPublisher`経由で全購読者に配信していた。STOMPの1配信は購読者全員に同一ペイロードが届くため、Aさんがリアクションを押すと、同じチャンネルのBさん・Cさんの画面にも実際には押していないのに`reactedByMe: true`が届く不具合があった。
   - 修正: `BroadcastReactionSummary`/`BroadcastMessageResponse`(`reactedByMe`を持たないレコード)を新設し、STOMP配信時のみこれに変換して送るようにした。REST応答(`MessageResponse`、戻り値)は引き続きviewerごとに正しい`reactedByMe`を返す。クライアント側は配信ペイロードの`userIds`に自分のIDが含まれるかで判定する設計になる(将来のフェーズ9フロントエンド実装時に反映する)。
   - 関連: [phase3.md](phase3.md)(MessageService新設)

### 中程度

3. **リアルタイム配信が`@Transactional`メソッドの内側でトランザクションコミット前に発生していた** — `MessageService`・`WorkspaceService.kick`・`ChannelService`各所とも、DBコミットが確定する前にSTOMP配信されており、コミットが失敗(ロールバック)した場合にDBに存在しないメッセージ等がクライアントに表示されたまま残る不整合があった。キック時の強制切断は`MemberKickedEvent` + `@TransactionalEventListener(AFTER_COMMIT)`で正しくコミット後に倒していたが、同じ設計がイベント配信側には適用されていなかった。
   - 修正: `RealtimeEventPublisher`自体に、トランザクション同期が有効なら`AFTER_COMMIT`まで配信を遅延し、無効なら即時配信する`dispatch()`ヘルパーを追加。呼び出し側(各Service)は一切変更不要。
   - 関連: [phase4.md](phase4.md)

### アーキテクチャ懸念(多層防御)

4. **認可チェックがController層にしか存在しないService(`MessageService`・`SearchService`・`MentionCandidateService`)があった** — `MessageService`/`SearchService`のjavadocが「呼び出し元でスコープ検証済みである前提」と明記しており、Controllerを経由せず(将来のSTOMPハンドラ等から)直接呼ばれると無防備になる設計だった。計画書§1が掲げる「ServiceはSTOMPハンドラや統合テストから再利用できるように」という方針とも噛み合っていなかった。
   - 修正: `MessageService`の全公開メソッド、`SearchService.search()`、`MentionCandidateService.findCandidates()`の先頭に、対応する`*AuthorizationService`の呼び出しを追加(Controller側の既存チェックは残したまま、多層防御として追加)。
   - テスト: `MessageServiceTest`・`SearchServiceTest`・`MentionCandidateServiceTest`をMockitoで新設し、Controllerを経由せず直接呼んでも非メンバーが弾かれることを検証。
   - 関連: [phase3.md](phase3.md)、[phase6.md](phase6.md)、[phase5.md](phase5.md)

### 例外処理の網羅性

5. `GlobalExceptionHandler`に`HttpMessageNotReadableException`(壊れたJSONボディ)・`MethodArgumentTypeMismatchException`(パラメータの型不一致、例: `cursorId`にUUID以外)・`Exception`のフォールバック(想定外の実行時例外、スタックトレースはサーバーログのみ・クライアントには汎用メッセージ)を追加。いずれも従来はSpring既定の`ErrorResponse`形状でないエラーが返っていた。
   - 関連: [phase1.md](phase1.md)(GlobalExceptionHandler新設)

### 軽微な指摘

6. `LayeredArchitectureTest`の`allowEmptyShould(true)`を削除(フェーズ0の暫定措置。Controller/Serviceが多数実在する現在は不要かつ有害)。[phase0.md](phase0.md)
7. `SecurityConfig`に`CorsConfigurationSource`(`chatspace.web-origin`ベース、`allowCredentials(true)`)を追加。従来REST側にCORS設定が存在せず、別オリジンのフロントエンドから到達不能だった。[phase1.md](phase1.md)
8. `UploadService.saveToDisk`にロールバック時のファイル削除(`TransactionSynchronization`)を追加し、トランザクションロールバック時の孤児ファイル発生を防止。[phase7.md](phase7.md)
9. ファイルサイズ上限メッセージ(`GlobalExceptionHandler`と`UploadService`の2箇所)のハードコードによる二重管理を解消し、`chatspace.max-attachment-size-bytes`から動的に算出するよう統一。[phase7.md](phase7.md)
10. `MimeSniffer`のMP4判定に`ftyp`ブランド許可リスト(isom/mp41/mp42/avc1等)を追加し、`.m4a`等の音声ファイルが`video/mp4`として誤判定されないようにした。[phase7.md](phase7.md)
11. `ChannelService.list()`の未読件数取得がチャンネル数ぶんのN+1クエリになっていたため、`MessageRepository.countUnreadInChannels()`(1クエリで一括取得)に置き換えた。[phase2.md](phase2.md)

### ビルド・テスト結果

`./gradlew build`が全69テスト(フェーズ1-8の60件 + 本レビュー対応で追加した9件)green。実機スモークテスト(CORS プリフライト・プライベートチャンネル非公開・壊れたJSON/不正パラメータのエラー形状・未読件数一括取得・動的サイズ上限メッセージ)も完了。
