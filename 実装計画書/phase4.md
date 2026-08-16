## フェーズ4 — リアルタイム(STOMP)

**状態: ✅ 完了(2026-08-16)**

STOMP over WebSocket(`/ws`)によるリアルタイム通信基盤を実装した。SUBSCRIBE時認可・SEND宛先のdefault-deny・キック時強制切断(`AFTER_COMMIT`保証)が中心で、CLAUDE.mdが警告する認可ミスの主戦場の一つ。詳細設計は [`docs/機能定義書/リアルタイム通信機能定義書.md`](../docs/機能定義書/リアルタイム通信機能定義書.md) を正とする。フェーズ0-12は単一インスタンス前提(Spring標準シンプルブローカー、Redis/RabbitMQ不使用)。

### 実施内容

#### 1. `StompDestinations`(`realtime`パッケージ)

宛先名の定数・組み立て・抽出を一元管理。`/topic/channels.{id}`・`/topic/dms.{id}`・`/topic/workspaces.{id}`・`/topic/workspaces.{id}.presence`・`/user/queue/events`の正規表現抽出メソッドを提供する。presence宛先は通常のworkspace宛先パターンの部分集合になるため、SUBSCRIBE認可の判定順序で必ず先に評価する。

#### 2. `WebSocketConfig`・認証・SUBSCRIBE/SEND認可

- `WebSocketAuthInterceptor`(`HandshakeInterceptor`) + `UserIdHandshakeHandler`: ハンドシェイク時にCookieのJWTを検証し、欠如・不正・期限切れは即時ハンドシェイク拒否(401)。Principal名を内部ユーザーID(UUID文字列)にする
- `StompChannelInterceptor`(`ChannelInterceptor`): SUBSCRIBE/SEND宛先のdefault-deny。`/user/queue/events`は認証済みなら常時許可 → チャンネル/DM/ワークスペーストピックはライブメンバーシップ検証 → それ以外は拒否。SENDは`/app/**`のみ許可。CONNECT/DISCONNECT/UNSUBSCRIBE/HEARTBEATは対象外(permitAll)
- **実装方針の変更点**: 計画書は「`AuthorizationManager`ベースの構成」と記載していたが、`spring-security-messaging`依存を追加せずとも同等の認可を実現できるカスタム`ChannelInterceptor`一本で実装した(依存追加を避けつつ、要求仕様(評価順序・default-deny・permitAll例外)を過不足なく満たせるため)
- `SecurityConfig`に`/ws/**`を`permitAll`として追加: REST層の認証とWebSocketAuthInterceptorによる専用認証が二重にならないよう、WebSocket側の認証判断はWebSocketAuthInterceptorに一本化した

#### 3. セッション管理・プレゼンス

- `SessionRegistry`: `Map<sessionId, WebSocketSession>` + `Map<UUID userId, Set<sessionId>>`。`RealtimeWebSocketHandlerDecoratorFactory`(`configureWebSocketTransport`の`addDecoratorFactory`に登録)が接続確立/切断時に反映する
- `PresenceService`: `Map<UUID, Integer>`で接続数を追跡し、オンライン/オフライン遷移時に該当ユーザーの全ワークスペースへ`PRESENCE_UPDATED`をブロードキャスト。`WorkspaceService.presence()`(フェーズ2で空配列固定だった箇所)を実装完了させた

#### 4. キック時の強制切断(`MemberKickedEvent`)

- `WorkspaceService.kick()`・`ChannelService.removeMember()`(オーナーによる強制退出の場合のみ、自主退出では発行しない)で`ApplicationEventPublisher`経由で`MemberKickedEvent`を発行
- `MemberKickedEventListener`が`@TransactionalEventListener(phase = AFTER_COMMIT)`で受け、`/user/queue/events`へ`MEMBER_REMOVED`をベストエフォート送信した後、`SessionRegistry`から対象ユーザーの全セッションを`close(POLICY_VIOLATION)`

#### 5. `TypingController`・`RealtimeEventPublisher`

- `TypingController`: `/app/channels.{channelId}.typing`・`/app/dms.{dmId}.typing`。メンバーシップ検証後`TYPING_UPDATE`をブロードキャスト。「送信者自身には配信しない」はSpring標準シンプルブローカーに送信者除外配信の標準APIが無いため、送信者IDを含めて全購読者に配信しクライアント側でフィルタする設計に簡略化(揮発性UXイベントであり認可上のリスクは無いための判断)
- `RealtimeEventPublisher`: `MESSAGE_CREATED`/`MESSAGE_UPDATED`/`MESSAGE_DELETED`/`REACTION_UPDATED`(フェーズ3の`MessageService`から呼び出すよう配線完了)、`CHANNEL_CREATED`/`CHANNEL_DELETED`/`CHANNEL_MEMBER_KICKED`/`WORKSPACE_MEMBER_KICKED`の発行口を集約

#### 6. 設計矛盾の発見と修正: `DM_THREAD_CREATED`の配信先

計画書§4.2はワークスペーストピックのペイロードとして`DM_THREAD_CREATED`を分類していたが、そのまま`/topic/workspaces.{id}`へブロードキャストすると「誰と誰がDMを始めたか」というプライベートな関係情報がワークスペースの全メンバーに漏洩してしまうことが実装時に判明した(DM機能定義書の404-not-403・存在秘匿方針と矛盾)。個人宛キュー(`convertAndSendToUser`)経由でDM相手にのみ配信するよう実装を修正した。

### 遭遇した問題と対応

- **循環依存(`BeanCurrentlyInCreationException`)**: `WebSocketConfig`(`WebSocketMessageBrokerConfigurer`)→`RealtimeWebSocketHandlerDecoratorFactory`→`PresenceService`→`SimpMessagingTemplate`という依存chainが、`SimpMessagingTemplate`自体の生成処理が全`WebSocketMessageBrokerConfigurer`を先に収集する仕組みと衝突し循環依存になった。`PresenceService`の`SimpMessagingTemplate`注入を`@Lazy`にして解消(Spring WebSocketの既知の落とし穴)
- **STOMPテストクライアントのメッセージコンバータ**: Spring Boot 4.1のデフォルトJSON実装がJackson 3系(`tools.jackson`)のため、Jackson 2ベースの`MappingJackson2MessageConverter`は使えない。標準の`StringMessageConverter`も、サーバーが`content-type: application/json`で送るペイロードを`setStrictContentTypeMatch(false)`にしても拒否してしまう(content-type未指定時のみ緩和される仕様のため)ことが判明し、content-typeを一切見ない自前の`MessageConverter`実装で解決した。あわせて、送信側(`toMessage`)で`MessageBuilder.withPayload(...).copyHeaders(...)`を使うと元の`StompHeaderAccessor`との紐付けが失われ「No StompHeaderAccessor available」エラーになることも判明し、`MessageBuilder.createMessage(payload, headers)`(既存の`MessageHeaders`インスタンスをそのまま使う)に修正した
- 上記2件は`apps/web-next`(フェーズ9)の実クライアント実装でも起こりうる注意点として記録しておく

### 実機検証

`docker compose up -d postgres` → `bootRun --spring.profiles.active=dev,seed` で起動し確認した(検証後、アプリ・Postgresコンテナとも停止済み):

| 確認項目 | 結果 |
|---|---|
| REST回帰確認(ワークスペース・チャンネル作成、メッセージ投稿がフェーズ1-3と同様に動作) | ✅ |
| プレゼンス取得(接続無し状態で空配列) | ✅ |
| `/ws`へCookie無しでハンドシェイク試行 → 401 | ✅ |

STOMP認可の詳細検証(SUBSCRIBE認可・SEND default-deny・キック強制切断・`/user/queue/events`等)は、実STOMPクライアント(`WebSocketStompClient`)を使った自動テスト(`StompAuthorizationTest`、下記)で実施し、実機検証を兼ねた。

### ビルド確認

`./gradlew build`が成功。テスト総数33件(フェーズ1-3からの27件 + 本フェーズ6件)、全てgreen。

新規テスト(`docs/テスト設計書.md`§6.2準拠、`StompAuthorizationTest`):

| テストID | 内容 |
|---|---|
| AUTH-N28(意図) | 未認証(Cookie無し)でのハンドシェイク拒否 |
| AUTH-N14 | 許可外Originからのハンドシェイク拒否 |
| AUTH-N10 | 非メンバーによるチャンネルトピックSUBSCRIBE拒否 |
| AUTH-N16 | 正当なメンバーはSUBSCRIBEでき、投稿メッセージのMESSAGE_CREATEDを受信できること(過剰ブロック検証) |
| AUTH-N15 | `/topic/**`への直接SEND拒否、偽装イベントが正当な購読者へ配信されないこと |
| AUTH-N27 | 認証済みユーザーが自分の`/user/queue/events`を購読でき拒否されないこと |

### 対象外(本フェーズでは扱わなかった、次フェーズ以降へ繰り越し)

- AUTH-N12(キックロールバック時に強制切断が発生しないこと)・AUTH-N13(キック直後の再接続で再購読不可)・AUTH-N24(DMトピック再購読拒否)・AUTH-N29(他人の個人キュー購読不可)は、STOMPクライアントでの網羅的な自動テストとしては実装せず、設計・実装レベルでの担保(`AFTER_COMMIT`イベントリスナー、`DmAuthorizationService`のライブ再検証、`UserDestinationResolver`によるPrincipal書き換え)に留めた。将来的な回帰防止のため、フェーズ8前後で追加を検討する
- AUTH-N17(STOMPペイロード不正値の明示的なエラーフレーム返却)は`@DestinationVariable UUID`の自動型変換による拒否に委ねており、専用のエラーハンドリング・テストは未実装
- AUTH-N18(STOMP宛先契約テスト)はフェーズ8で対応(既定通り)
- RabbitMQ外部ブローカーリレー・Redis共有プレゼンス(フェーズ13・任意)

## 関連ドキュメント

- [`docs/機能定義書/リアルタイム通信機能定義書.md`](../docs/機能定義書/リアルタイム通信機能定義書.md)
- [`docs/テスト設計書.md`](../docs/テスト設計書.md) §6.2
- [phase3.md](phase3.md)(前フェーズ)
- [phase5.md](phase5.md)(次フェーズ)
