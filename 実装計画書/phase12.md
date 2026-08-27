## フェーズ12 — 仕上げ

**状態: ✅ 完了**

プロトタイプから継続する既知のギャップの解消、本番デプロイに向けたセキュリティ設定の最終確認、ログ・監査体制の整備を行った。詳細は [`docs/ログ運用設計書.md`](../docs/ログ運用設計書.md)・[`docs/インフラ構成書.md`](../docs/インフラ構成書.md) を参照。

### 実装対象

- [x] 認証系のレート制限追加(ログイン試行等のブルートフォース対策。プロトタイプ未実装の既知ギャップ、認証機能定義書§7参照)
- [x] **本番ドメイン確定後のSameSite/CSRF方針再確認** → ドメイン未定のため**判断基準の文書化に留め、実装は保留**(下記参照)
- [x] 構造化ログの導入(認可拒否・認証失敗イベントの記録。パスワード・JWT・メッセージ本文・PIIをログに残さない方針の徹底)
- [x] 監査ログ対象の実装(オーナー限定操作の実行、キック操作とロールバック時の非切断、通知配信失敗、STOMP接続拒否等)
- [x] Render環境でのログ確認方法の確立(設定手順・検索例を文書化。実インスタンスでの検証は本番デプロイ時の宿題として明記)
- [x] **情報漏洩監査**(ログ運用設計書§5.1に結果を記録)

---

### 1. 認証のレート制限

`com.chatspace.api.ratelimit` パッケージを新設した。

| クラス | 役割 |
|---|---|
| `AuthRateLimiter` | 固定ウィンドウ方式のインメモリカウンタ。試行枠の確保を原子的に行い、上限超過で `TooManyRequestsException`(429) |
| `ClientAddress` | クライアントIPの取得(`getRemoteAddr()`。プロキシヘッダの解釈はTomcatの`RemoteIpValve`へ委譲)とレート制限キーの組み立て |
| `ClockConfig` | `Clock` をDI可能にする(テストから時間を仮想的に進めるため) |

**既定値**: 15分ウィンドウ内に5回試行でき、それを超えると15分ブロック。環境変数 `AUTH_RATE_LIMIT_MAX_ATTEMPTS` / `_WINDOW` / `_BLOCK_DURATION` で調整可能。

**設計上の判断**

- **キーは「ユーザーID + クライアントIP」の組**にした。IP単独だと同一NAT配下の無関係な利用者を巻き込み、ユーザーID単独だと攻撃者が任意のアカウントを恒久的にロックアウトできてしまう(DoS)ため、両方を含めることでどちらの副作用も避ける
- **ブロック中は正しいパスワードでも照合を行わない**。照合結果によって応答が変わると、ブロック中でもパスワードの正誤を判定できてしまうため
- **試行回数を数え、ログイン成功でカウンタをリセット**する。照合結果を待たずに枠を確保することで同時実行の抜け道を塞ぎつつ、打ち間違い後に成功した正当な利用者は巻き込まない
- **ブロック中の追加試行はブロックを延長する**。ウィンドウ経過でカウンタが振り出しに戻ると、ブロック解除直後に再び上限回数まで試せてしまい総当たりの実効速度が上がるため
- ユーザーIDの**大文字小文字は正規化しない**。`findByUserId` が完全一致検索であり `Alice` と `alice` は別アカウントのため、正規化すると無関係なアカウント同士のカウンタが合算されてしまう
- `server.forward-headers-strategy: native`(Tomcat `RemoteIpValve`)を設定した。**信頼済みプロキシからのリクエストに限り** `X-Forwarded-For` を反映するため、外部から直接送られた偽装ヘッダは無視される

- **キー数に厳密な上限(50,000件)**を設けた。未認証の攻撃者はユーザーIDとIPの組を変えて任意個のキーを生成できるため、上限超過時は古いエントリを追い出す。全件走査は10秒間隔に制限し、リクエストごとの反復走査を防ぐ

**既知の制約**: 単一インスタンス前提のインメモリ実装のため、水平スケール時は実効上限がインスタンス数倍になる(水平スケール対応自体を実施しないことが確定したため、共有ストア化は行わない)。

### 2. SameSite / CSRF 方針(判断を保留)

本番のドメイン構成が未定のため、**判断基準を [`docs/インフラ構成書.md`](../docs/インフラ構成書.md) §8.2.1 に文書化し、コードは変更していない**。

「別ドメインになるかもしれない」という想定でCSRFトークン方式を先行実装すると、同一ドメイン構成に落ち着いた場合に使われないコードと余分なテストが恒久的に残るため、必要になってから実装する方針とした。

なお**誤設定が無防備な公開に繋がらない構造**になっている点も確認した。別ドメイン構成で `SameSite=Lax` のまま公開した場合、CookieがAPIへ送信されないため**そもそもログインできない**ため、防御が外れたことに気付かないまま運用が始まる形の事故は起きない。

### 3. 構造化ログ・アクセスログ

**追加ライブラリを導入せず、Spring Boot 4 標準の構造化ログ機能を使う**方式に確定した(`logstash-logback-encoder` は不採用。標準機能で同等のことができるため依存を増やす必要がない)。

| クラス | 役割 |
|---|---|
| `RequestLoggingFilter` | `requestId` をMDCへ設定し、アクセスログ(`method`/`path`/`status`/`durationMs`/`userId`)を出力 |
| `CurrentUserProvider` | ログ用に、未認証でも例外を投げずに現在のユーザーIDを取得する(`GlobalExceptionHandler`の監査ログで使用) |

- 環境変数 `LOG_STRUCTURED_FORMAT=logstash` でJSON出力に切り替わる(未設定なら人間可読な行形式)
- ロガー名を `ACCESS` / `AUDIT` に固定し、アプリケーションログと分離して検索できるようにした
- **クエリ文字列はログに残さない**(検索クエリがユーザーの入力そのものでありPIIに準ずるため、`getRequestURI()` のみ記録)
- MDCはスレッドプールで再利用されるため、`finally` で必ず全キーを `remove` する
- **行形式でもキー・バリューを出力する**よう `logging.pattern.console` を上書きした(既定パターンは `%kvp` を含まず、行形式では監査情報が失われるため)

### 4. 監査ログ

`AuditLogger`(`com.chatspace.api.audit`)を新設し、ログ運用設計書§2の全対象を実装した。

| 監査イベント | 出力箇所 | レベル |
|---|---|---|
| `LOGIN_FAILED` / `LOGIN_RATE_LIMITED` | `AuthController` | WARN |
| `AUTHORIZATION_DENIED` | `GlobalExceptionHandler`(403/404) | WARN |
| `OWNER_ACTION_SUCCEEDED` | `AuditableActionEventListener`(AFTER_COMMIT)。発行元は `WorkspaceService`(招待・キック)・`ChannelService`(作成・招待・キック・削除) | INFO |
| `MEMBER_ACTION_SUCCEEDED` | 同上。チャンネルからの自主退出(`CHANNEL_LEAVE`)をオーナー操作と区別して記録 | INFO |
| `MEMBER_KICKED`(`closedSessionCount` 付き) | `MemberKickedEventListener`(AFTER_COMMIT) | INFO |
| `MEMBER_KICK_ROLLED_BACK` | `MemberKickedEventListener`(AFTER_ROLLBACK、新規追加) | WARN |
| `NOTIFICATION_DELIVERY_FAILED` | `RealtimeEventPublisher#notification` | WARN |
| `STOMP_HANDSHAKE_REJECTED` | `WebSocketAuthInterceptor` | WARN |
| `STOMP_DESTINATION_DENIED` | `StompChannelInterceptor` | WARN |
| `ATTACHMENT_ACCESS_DENIED` | `UploadService#serve` | WARN |

**設計上の判断**

- **成功系(INFO)と拒否・失敗系(WARN)でログレベルを分けた**。全てWARNにすると、監視側で「WARNの増加=異常」という単純な閾値が使えなくなるため
- **「オーナー限定操作の拒否」専用イベントは設けなかった**。`requireOwner()` の失敗は `ForbiddenException` として `GlobalExceptionHandler` に到達し `AUTHORIZATION_DENIED`(HTTPメソッド・パス・例外型・userId を含む)として一元記録されるため、二重に出してもログ量が増えるだけで得られる情報が変わらないと判断した
- **キックのロールバックを記録するリスナーを追加した**(`AFTER_ROLLBACK`)。運用時に「キックのログがあるのに切断ログが無い」状態を、異常ではなくロールバックとして判別できるようにするため
- 通知配信の失敗は**握りつぶさずログに残す**。配信はコミット後の副作用のため例外で呼び出し元を巻き添えにはできないが、そのまま握りつぶすと「通知が届かない」障害が誰にも気付かれない

**機密情報の混入防止を型レベルで担保**: `AuditLogger` の各メソッドは識別子(UUID)・種別・理由コードのみを引数に取り、本文や認証情報を渡せる引数を持たない。`AuditLoggerRedactionTest` がリフレクションで引数名を検査し、`password`・`token`・`body`・`query` 等を含む引数が追加された時点で失敗する。

### 5. 情報漏洩監査

[`docs/ログ運用設計書.md`](../docs/ログ運用設計書.md) §5.1 に結果を記録した。要点は以下のとおり。

- §5の7観点のうち**4観点はログから追跡可能**。残り3観点(通知スコープ条件の整合、AND結合、ソフトデリート漏れ)は**ログからの事後検知に本質的に向かない**(正常な応答と異常な応答がログ上は同じ形になる)ため、実装時点の正しさを自動テストで担保する方式を継続し、定期的なサンプル確認で補う。この切り分け自体を監査の結論として記録した
- ログへの機密情報混入がないことを実地確認した。特に `show-sql: true` が **dev プロファイルのみ**であり、本番ではメッセージ本文がSQLログに出ないことを確認
- 拒否イベントのアラート発報の閾値設定は本書の対象外であり未実施(手動確認を前提とする)

---

### フェーズ12レビュー指摘への対応

完了報告後のレビューで、セキュリティ・監査上の完了阻害事項6件の指摘を受け、全て対応した。

| 重要度 | 指摘 | 対応 |
|---|---|---|
| 高 | **同時ログイン試行で上限を突破できる**。「ブロック確認」→ 照合 →「失敗記録」が別操作のため、同時リクエストが全て確認をすり抜けてbcrypt照合まで到達できた | `acquireAttempt()` として**確認と加算を`ConcurrentHashMap#compute`内で原子化**。失敗回数ではなく試行回数を数える方式に変更(照合結果を待たずに枠を確保するため)。成功時の`reset()`で正当な利用者の体験は不変。64スレッド同時実行の回帰テストを追加 |
| 高 | **`X-Forwarded-For`を無検証採用しレート制限を回避可能**。加えてキー数が無制限でメモリ/CPU DoS が可能 | 自前のヘッダ解釈を**廃止**し`getRemoteAddr()`のみを使用。プロキシヘッダの解釈は`forward-headers-strategy: native`(Tomcat `RemoteIpValve`、**信頼済みプロキシからのみ**反映)に委譲。キー数に厳密な上限(`MAX_TRACKED_KEYS=50,000`)を設け、全件走査は10秒間隔に制限。ヘッダ偽装とキー上限の回帰テストを追加 |
| 中 | **アクセスログの`userId`が常にnull**。`RequestLoggingFilter`がSecurityフィルタチェーンの外側で動くため、ログ出力時点で`SecurityContext`がクリア済みだった | `JwtAuthenticationFilter`がチェーン実行中にリクエスト属性へユーザーIDを退避し、フィルタはそれを読む方式へ変更。`CurrentUserProvider`への依存は解消。Logbackの`ListAppender`で実際のログイベントを捕捉し、認証済み/未認証の双方を検証するテストを追加 |
| 中 | **コミット前に成功監査ログが出る**。ロールバック時に「成功ログだけが残る」「キック成功とロールバックが両方出る」不整合。また**チャンネル自主退出が`CHANNEL_KICK`として記録**されていた | `AuditableActionEvent` + `@TransactionalEventListener(AFTER_COMMIT)`へ移行し、コミット後にのみ記録。自主退出は`MEMBER_ACTION_SUCCEEDED`(`CHANNEL_LEAVE`)としてオーナー操作と分離 |
| 中 | **攻撃者入力のユーザーIDを無加工で記録**。制御文字によるログ行偽装・巨大文字列・PII混入が可能 | `sanitizeAttemptedUserId`で制御文字を除去し64文字で切り詰め。併せて`LoginRequest.userId`に`@Size(max=100)`を追加(形式チェックではなく長さのみのため、アカウント存在有無は漏れない)。実値での検証テストを追加 |
| 中 | **行形式では監査フィールドが出力されない**。既定パターンが`%kvp`を含まないため`auditEvent`等が失われる | `logging.pattern.console`を上書きし、行形式でも`%kvp`と`requestId`を出力。実行ログで`auditEvent="LOGIN_FAILED" attemptedUserId="..." requestId=...`が出ることを確認済み |

### 追加したテスト(フェーズ12時点22件 → 後日追加を含め30件)

| テストクラス | 件数 | 内容 |
|---|---|---|
| `AuthRateLimiterTest` | 13 | 仮想時計を使った単体テスト。上限までは通す/超過で拒否/ブロック解除/ウィンドウ跨ぎで累積しない/成功でリセット/キー分離/ブロック延長/**64スレッド同時実行でも上限を超えない**/**キー数が上限以下に保たれる** |
| `LoginRateLimitIntegrationTest` | 7 | HTTP経由。429と `Retry-After`/ブロック中は正しいパスワードでも拒否/成功でリセット/別IPは巻き込まない/存在しないユーザーIDにも効く/**`X-Forwarded-For`偽装で回避できない**/**巨大なユーザーIDは入口で弾く** |
| `SignupRateLimitIntegrationTest` | 5 | **公開デモ化に伴う後日追加**(本フェーズ時点では新規登録にレート制限が無かった)。HTTP経由。429と `Retry-After`/**ユーザーIDを毎回変えても回避できない**/別IPは巻き込まない/`X-Forwarded-For`偽装で回避できない/登録失敗も枠を消費する |
| `AccessLogFieldsIntegrationTest` | 4 | Logbackの`ListAppender`で実ログを捕捉。認証済みで`userId`が載る/未認証ではnull/認可拒否の監査フィールドとWARNレベル/攻撃者入力のサニタイズ |
| `AuditLoggerRedactionTest` | 1 | 監査ログAPIが本文・認証情報を受け取る引数を持たないことをリフレクションで検査 |

### 対象外(本フェーズでは扱わない)

- 水平スケール対応(検討の結果実施しないことが確定)・パフォーマンステスト(任意)
- アラート発報の閾値設定(ログ運用設計書の対象外)
- 添付ファイルのオブジェクトストレージ(S3/R2)化 — 費用面の検討事項としてインフラ構成書§7.1に記録(フェーズ13スコープ)

### 確認方法・結果

```bash
# リポジトリルート
docker compose up -d postgres
```

```bash
# バックエンド(apps/api で実行)
cd apps/api
./gradlew clean build
```

| 確認 | 結果 |
|---|---|
| `apps/api` `./gradlew clean build`(Spotless + ArchUnit + 全テスト) | BUILD SUCCESSFUL / **103テスト 失敗0・エラー0**(フェーズ11時点の81件 + 本フェーズ22件) |

## 関連ドキュメント

- [`docs/ログ運用設計書.md`](../docs/ログ運用設計書.md)
- [`docs/インフラ構成書.md`](../docs/インフラ構成書.md) §8.2.1(SameSite/CSRF判断手順)・§7.1(S3化の検討)・§10.1(Renderログ確認方法)
- [`docs/機能定義書/認証機能定義書.md`](../docs/機能定義書/認証機能定義書.md) §7
- [phase11.md](phase11.md)(前フェーズ)
- [phase13.md](phase13.md)(次フェーズ、任意)
