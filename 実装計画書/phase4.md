## フェーズ4 — リアルタイム(STOMP)

**状態: 未着手**

STOMP over WebSocket(`/ws`)によるリアルタイム通信基盤を実装する。SUBSCRIBE時認可・SEND宛先のdefault-deny・キック時強制切断(`AFTER_COMMIT`保証)が中心で、CLAUDE.mdが警告する認可ミスの主戦場の一つ。詳細設計は [`docs/機能定義書/リアルタイム通信機能定義書.md`](../docs/機能定義書/リアルタイム通信機能定義書.md) を正とし、本書では実装順序と受け入れテストのみを扱う。フェーズ0-12は単一インスタンス前提(Spring標準シンプルブローカー、Redis/RabbitMQ不使用)。

### 前提

- フェーズ3(メッセージ・リアクション・`MessageScopeGuard`)完了後に着手する
- `WorkspaceAuthorizationService`/`ChannelAuthorizationService`/`DmAuthorizationService`(フェーズ1-3で実装済み想定)のメンバーシップ判定ロジックをSTOMP側からも再利用する

### 実装対象

- [ ] `StompDestinations.java`(`realtime`パッケージ) — 宛先名の定数を一元管理(`/topic/channels.{id}`, `/topic/dms.{id}`, `/topic/workspaces.{id}`, `/topic/workspaces.{id}.presence`, `/user/queue/events`, `/app/channels.{id}.typing`)
- [ ] `WebSocketConfig`(`WebSocketMessageBrokerConfigurer`) — `/ws`エンドポイント登録(SockJSフォールバックなし)、`enableSimpleBroker("/topic", "/queue")`、`setApplicationDestinationPrefixes("/app")`、`setUserDestinationPrefix("/user")`、`setAllowedOriginPatterns(WEB_ORIGIN由来)`
- [ ] `WebSocketAuthInterceptor`(`HandshakeInterceptor`) — ハンドシェイク時にCookieの`chatspace_token`を`JwtService`で検証、`DefaultHandshakeHandler.determineUser()`でPrincipalを設定(欠如・不正・期限切れは即時ハンドシェイク拒否)
- [ ] STOMPチャネルインターセプタ(`ChannelInterceptor`) — CONNECTフレームでPrincipal有無を再確認
- [ ] SUBSCRIBE認可(`AuthorizationManager`ベースの構成) — 評価順序を機能定義書§7.1の通りに実装する: ①`/user/queue/events`は認証済みなら常時許可 → ②チャンネル/DM/ワークスペーストピックはライブメンバーシップ検証 → ③それ以外はdefault-deny
- [ ] SEND宛先のdefault-deny — `/app/**`のみ認証済みユーザーに許可、`/topic/**`・`/queue/**`・`/user/**`への直接SENDは拒否。`SimpMessageType.CONNECT`/`DISCONNECT`/`UNSUBSCRIBE`/`HEARTBEAT`は`permitAll()`として対象から明示的に除外(`anyMessage().denyAll()`で接続自体が壊れる典型的な誤りを避ける)
- [ ] `TypingController`(`@MessageMapping("/channels.{channelId}.typing")`) — 対象チャンネル/DMのメンバーシップ検証後、`/topic/channels.{channelId}`へ`TYPING_UPDATE`をブロードキャスト(送信者自身は除外、永続化しない)。ペイロードバリデーション(channelId/dmId形式、サイズ上限)を実装
- [ ] `PresenceService` — `Map<UUID, Integer>`で接続数を追跡(単一インスタンス、複数タブ対応)。オンライン/オフライン遷移時に`/topic/workspaces.{workspaceId}.presence`へブロードキャスト
- [ ] `WebSocketHandlerDecoratorFactory` — `configureWebSocketTransport`の`addDecoratorFactory`に登録。`afterConnectionEstablished`/`afterConnectionClosed`で`SessionRegistry`(`Map<sessionId, WebSocketSession>` + `Map<UUID userId, Set<sessionId>>`)を更新
- [ ] `MemberKickedEvent` — `WorkspaceService`/`ChannelService`のキック処理内で`ApplicationEventPublisher`経由で発行するのみ(直接`SessionRegistry`を呼ばない)
- [ ] キック強制切断リスナー — `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`で`MemberKickedEvent`を受け、`SessionRegistry`から対象ユーザーの全セッションを`session.close(CloseStatus.POLICY_VIOLATION)`。切断前にベストエフォートで`/user/queue/events`へ`MEMBER_REMOVED`を送信
- [ ] `RealtimeEventPublisher`(`SimpMessagingTemplate`ラッパー) — `MESSAGE_CREATED`/`MESSAGE_UPDATED`/`MESSAGE_DELETED`/`REACTION_UPDATED`(フェーズ3実装済みのService層から呼ばれる)、`CHANNEL_CREATED`/`CHANNEL_DELETED`/`DM_THREAD_CREATED`/`CHANNEL_MEMBER_KICKED`/`WORKSPACE_MEMBER_KICKED`の発行口を集約
- [ ] クライアント側`heartbeatIncoming`/`heartbeatOutgoing`/`reconnectDelay`の具体的な秒数を確定する(Render運用実態を踏まえ、機能定義書§3の指針に沿って本フェーズで決定)

### 先に書くテスト

`docs/テスト設計書.md` §6.2 の該当テストID。実装前に失敗する状態で書く。

- [ ] AUTH-N10: SUBSCRIBE時認可拒否(非メンバーによるチャンネル/DM/ワークスペーストピック購読の拒否)
- [ ] AUTH-N11: キック後の強制切断(購読済みユーザーがキック後、新規メッセージを受信しない)
- [ ] AUTH-N12: キック確定のAFTER_COMMIT保証(ロールバック時は強制切断が発生しない)
- [ ] AUTH-N13: キック確定後の即時再接続拒否(コミット後の削除済みメンバーシップを参照し再購読不可)
- [ ] AUTH-N14: 許可外Origin拒否(`WEB_ORIGIN`未許可のOriginからのCONNECT/SENDが拒否される)
- [ ] AUTH-N15: SEND default-deny(`/topic/**`等への直接SEND拒否、偽装`MESSAGE_CREATED`が他ユーザーへ配信されない)
- [ ] AUTH-N16: SEND default-deny下での正常フロー(通常のCONNECT/SUBSCRIBE/`/app/**`宛SEND/受信が正常動作する、過剰ブロックの検証)
- [ ] AUTH-N17: STOMPペイロード不正値拒否(タイピングイベント等の不正ペイロードがエラーフレームで拒否される)
- [ ] AUTH-N24: ワークスペースキック後のDMトピック再購読拒否
- [ ] AUTH-N27: `/user/queue/events`のSUBSCRIBE許可(自分の個人キューを購読でき、サーバー送信の通知を受信できる)
- [ ] AUTH-N28: `/user/queue/events`の未認証拒否
- [ ] AUTH-N29: 他人の個人キュー購読不可(`UserDestinationResolver`によるPrincipal書き換えで他人宛通知を盗み見できない)

AUTH-N18(STOMP宛先契約テスト)はフェーズ8で対応するため本フェーズの対象外。AUTH-N27の一部(実際の`NOTIFICATION`ペイロード配信)はフェーズ5の`NotificationService`実装後に完成する。

### 対象外(本フェーズでは扱わない)

- RabbitMQ外部ブローカーリレー・Redis共有プレゼンス(フェーズ13・任意)
- STOMP宛先契約テスト(フェーズ8、`apps/web-next`側実装後)
- メッセージ配信のExactly-once保証(ベストエフォート、DBが最終的な正)

### 確認方法

```bash
docker compose up -d postgres
cd apps/api-java
./gradlew build      # Spotless + ArchUnit + 単体/統合/STOMP統合テスト + ビルド
```

## 関連ドキュメント

- [`docs/機能定義書/リアルタイム通信機能定義書.md`](../docs/機能定義書/リアルタイム通信機能定義書.md)
- [`docs/テスト設計書.md`](../docs/テスト設計書.md) §6.2
- [`docs/シーケンス図.md`](../docs/シーケンス図.md)(STOMP接続・キック強制切断・通知配信)
- [phase3.md](phase3.md)(前フェーズ)
- [phase5.md](phase5.md)(次フェーズ)
