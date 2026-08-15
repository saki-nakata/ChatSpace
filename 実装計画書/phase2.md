## フェーズ2 — ワークスペース/チャンネル/DM の CRUD(メッセージ抜き)

**状態: ✅ 完了(2026-08-15)**

ワークスペース・チャンネル・DMのCRUDと、フェーズ1で骨格のみ作った認可サービス本体を完成させた。**DM機能はプロトタイプに実在するギャップ(ワークスペースメンバーシップ再確認漏れ)の修正を含む最重要フェーズ**であり、実機検証まで含めて対応を確認済み。詳細は [`docs/機能定義書/ワークスペース機能定義書.md`](../docs/機能定義書/ワークスペース機能定義書.md)・[`docs/機能定義書/チャンネル機能定義書.md`](../docs/機能定義書/チャンネル機能定義書.md)・[`docs/機能定義書/DM機能定義書.md`](../docs/機能定義書/DM機能定義書.md) を正とする。

### 実施内容

#### 1. 認可サービス本体

- `WorkspaceAuthorizationService.requireMember/requireOwner`: `WorkspaceMember` を返す(呼び出し側の二重クエリを避けるため、フェーズ1の`void`骨格から戻り値ありに変更)
- `ChannelAuthorizationService.requireChannelMember(channelId, userId, workspaceIdOrNull)`: チャンネル不存在・`workspaceId`不一致(confused-deputy)・非メンバーのいずれも404で統一
- `DmAuthorizationService.requireDmAccess(dmId, userId, workspaceIdOrNull)`: **DM参加者チェックとWorkspaceMember再確認をANDで検証**(DM機能定義書§6.2の必須修正。ワークスペースキック後も`DmThread.userAId/userBId`は消えないため、参加者チェックのみでは不十分という実在するギャップへの対応)

#### 2. ワークスペース(`WorkspaceController`/`Service`)

作成・一覧・メンバー一覧・プレゼンス取得(実装対象は認可ゲートのみ。実際のオンライン状態追跡はフェーズ4の`PresenceService`で行うため現時点では常に空配列)・招待(オーナー限定)・キック(オーナー限定、オーナー自身は400)・自主退出(オーナーは400)。キック・退出はいずれも`ChannelMemberRepository.deleteByUserIdAndWorkspaceId()`(JPQL `@Modifying`削除)で当該ワークスペース内の全チャンネルメンバーシップを同一トランザクションで道連れ削除する。

#### 3. チャンネル(`ChannelController`/`Service`)

作成(オーナー限定、同名409、PRIVATE時は指定ハンドルをワークスペースメンバーに絞り込んで初期メンバー化)・一覧(パブリック∪自分の所属、`isMember`/`unreadCount`付き)・パブリックへの自主参加(**プライベートへのjoin試行は404**、チャンネル機能定義書§3.3の存在秘匿方針)・既読更新・招待(オーナー限定、対象はワークスペースメンバーであることが必須)・メンバー一覧・退出/キック(本人 or オーナー限定)・削除(オーナー限定、子リソースはDB側`ON DELETE CASCADE`に委譲)。

#### 4. DM(`DmController`/`Service`)

一覧(未読数・最新メッセージプレビュー付き)・ハンドル指定での取得/新規作成(`userAId < userBId`正規化、自己DM禁止400、相手が非ワークスペースメンバーなら404)・既読更新。`unreadCount`/プレビューは`MessageRepository`にフェーズ3先取りの軽量クエリ(`countUnreadInChannel`/`countUnreadInDm`/`findFirstByDmId...`)を追加して算出(Message CRUD自体はフェーズ3のまま)。

#### 5. テスト基盤の新設(Testcontainers)

`AbstractIntegrationTest`(`com.chatspace.api.support`)を新設。**Testcontainers公式のsingleton containerパターン**(`@Container`を使わず静的初期化ブロックで手動起動)を採用: `@Container`を付けて`@Testcontainers`配下に置くと、JUnit5拡張がテストクラスごとの`afterAll`でコンテナを停止してしまい、2番目以降のテストクラスが接続拒否で失敗する実害を確認したため。`AuthorizationTestFixtures`でユーザー/ワークスペース/チャンネル生成とJWT Cookie発行のヘルパーを提供する(テスト設計書§5)。

### 遭遇した問題と対応

- **Spring Boot 4.1のJackson 3系移行**: 統合テストで`com.fasterxml.jackson.databind.ObjectMapper`/`JsonNode`が解決できずコンパイルエラー。実際の依存関係(`spring-boot-starter-jackson`)を調査した結果、Spring Boot 4.1はデフォルトJSON実装を **Jackson 3系(`tools.jackson.databind`パッケージ)** に切り替えていることが判明(`com.fasterxml.jackson.annotations`のみ2系互換で残存)。`tools.jackson.databind.ObjectMapper`/`JsonNode`に置き換えて解決。**フェーズ3以降、JSON関連コードを書く際はこの新パッケージ名に注意する**
- **`AutoConfigureMockMvc`のパッケージ変更**: 同じくBoot 4.1で`org.springframework.boot.test.autoconfigure.web.servlet`→`org.springframework.boot.webmvc.test.autoconfigure`に移動
- **Testcontainersのsingleton containerパターンの罠**: 上記の通り、`@Container`アノテーションを使うとテストクラスをまたいだコンテナ共有が実質できない(公式ドキュメント通り、`@Container`無しの静的初期化ブロックで手動起動する必要がある)
- **テスト用JWT_SECRETの鍵長不足**: `application-test.yml`の`jwt-secret: test-only-secret`(16バイト)がHS256の最小鍵長(256bit/32バイト)を満たさず`KeyLengthException`。32バイト以上の値に修正
- **MockMvcでのCookie送信**: `.header(HttpHeaders.COOKIE, "chatspace_token=...")`では`MockHttpServletRequest.getCookies()`に反映されない(実サーブレットコンテナと異なりCookieヘッダの自動パースを持たないため)。`.cookie(new jakarta.servlet.http.Cookie(...))`に置き換えて解決。**フェーズ3以降のMockMvcテストでも必ずこの方式を使うこと**

### 実機検証

`docker compose up -d postgres` → `./gradlew bootRun --args='--spring.profiles.active=dev,seed'` で起動し、alice(OWNER)/bob/carol(MEMBER、シード済み)でcurl検証した(検証後、アプリ・Postgresコンテナとも停止済み):

| 確認項目 | 結果 |
|---|---|
| ワークスペース一覧・メンバー一覧・presence(空配列) | ✅ |
| 非オーナーのチャンネル作成 → 403 | ✅ |
| オーナーのチャンネル作成 → 201、同名チャンネル再作成 → 409 | ✅ |
| パブリックチャンネル一覧に非メンバーとしても表示され`isMember=false` | ✅ |
| パブリックチャンネルへのjoin(冪等、2回目も200) | ✅ |
| 既読更新 → 204 | ✅ |
| プライベートチャンネル作成(初期メンバー指定) → 非メンバーの一覧に一切出現しない | ✅ |
| 非メンバーのプライベートチャンネルへの直接join → **404**(403ではない) | ✅ |
| 非メンバーのプライベートチャンネルメンバー一覧取得 → 404 | ✅ |
| DMハンドル指定作成 → 201、自己DM → 400 | ✅ |
| ワークスペースキック(オーナー限定) → 204、キック後は`GET /members`から除外 | ✅ |
| **キック後、対象ユーザーのDM既読更新が404**(DmAuthorizationServiceのWorkspaceMember再確認が実働していることの実機確認) | ✅ |
| オーナー自身のキック・オーナーの`/leave` → いずれも400 | ✅ |

### ビルド確認

`./gradlew build`(Spotlessチェック・ArchUnit・単体テスト・統合テスト・ビルド)が成功。テスト総数20件(ArchUnit 3件、フェーズ1単体テスト10件、フェーズ2認可統合テスト7件)、全てgreen。

新規テスト(`docs/テスト設計書.md`§6.1準拠):

| テストID | テストクラス | 内容 |
|---|---|---|
| AUTH-P05 | `WorkspaceCrudAuthorizationTest` | workspaceId/channelId不一致時の404 |
| AUTH-P06 | 同上 | 非オーナーのチャンネル作成403 |
| AUTH-P07 | 同上 | 非オーナーのチャンネル招待403 |
| AUTH-P08 | 同上 | 非オーナーのキック403 |
| AUTH-P09 | 同上 | オーナーの作成・招待・キック成功、DB上のメンバーシップ削除確認 |
| AUTH-P10 | `DmAuthorizationTest` | DMハンドル解決 |
| AUTH-N22 | 同上 | `DmAuthorizationService.requireDmAccess()`のワークスペースキック後404(Service単体) |

### 対象外(本フェーズでは扱わなかった、次フェーズへ繰り越し)

- メッセージ本体のCRUD(フェーズ3)
- DMメッセージ取得の実エンドポイント経由でのキック後404確認(AUTH-N23、フェーズ3でメッセージ機能実装後)
- ワークスペース招待・チャンネル招待時の通知送信(`WORKSPACE_INVITE`/`CHANNEL_INVITE`、通知機能自体がフェーズ5)。コード中に`TODO(フェーズ5)`コメントで明記済み
- キック時のAFTER_COMMIT強制切断・リアルタイムイベント配信(フェーズ4)

## 関連ドキュメント

- [`docs/機能定義書/ワークスペース機能定義書.md`](../docs/機能定義書/ワークスペース機能定義書.md)
- [`docs/機能定義書/チャンネル機能定義書.md`](../docs/機能定義書/チャンネル機能定義書.md)
- [`docs/機能定義書/DM機能定義書.md`](../docs/機能定義書/DM機能定義書.md)
- [`docs/テスト設計書.md`](../docs/テスト設計書.md) §6.1・§6.2
- [phase1.md](phase1.md)(前フェーズ)
- [phase3.md](phase3.md)(次フェーズ)
