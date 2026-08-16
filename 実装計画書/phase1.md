## フェーズ1 — 認証・データモデル・認可サービス骨格・シード

**状態: ✅ 完了(2026-08-15)**

JPAエンティティ11種、認証(JWT+Cookie)、認可サービスの骨格、シードデータ投入を実装した。詳細設計は [`docs/機能定義書/認証機能定義書.md`](../docs/機能定義書/認証機能定義書.md)・[`docs/DB設計書.md`](../docs/DB設計書.md) を正とする。

### 実施内容

#### 1. JPAエンティティ・Repository

DB設計書§3のテーブル定義書から、対応するドメインパッケージにエンティティとSpring Data JPA Repositoryを作成した。ID はアプリ側で `UUID.randomUUID()` を生成するコンストラクタ方式(Hibernateの `@GeneratedValue` は使わない、計画書§2の方針通り)。

| パッケージ | エンティティ | 備考 |
|---|---|---|
| `user` | `User` | |
| `workspace` | `Workspace`, `WorkspaceMember`, `WorkspaceRole`(enum) | |
| `channel` | `Channel`, `ChannelMember`, `ChannelType`(enum) | |
| `dm` | `DmThread` | |
| `message` | `Message`, `Attachment`, `Reaction`, `Mention`, `AttachmentKind`(enum) | `Message.parent`/`replies` は計画書§2通り自己参照 `@ManyToOne`/`@OneToMany`。他の外部キー(`channelId`/`dmId`/`authorId`等)はオブジェクト関連にせず生のUUIDカラムとして保持し、N+1・遅延ロードの罠を避けた(認可・スコープ再チェックがEXISTS/JOINベースの直接クエリで書かれる設計方針と一貫させるため) |
| `notification` | `Notification`, `NotificationType`(enum) | |

Repositoryは現時点で `JpaRepository<Entity, UUID>` の空継承のみ(`UserRepository` のみ `findByUserId`/`existsByUserId` を追加、signup/loginで使用)。各機能のCRUD・検索メソッドは対応フェーズ(2/3/5等)で追加する。

#### 2. 認証(認証機能定義書§3・§6)

- `JwtService`: Nimbus JOSE+JWTを直接使用(HS256、`sub`=内部ユーザーID、7日有効)。署名鍵は `chatspace.jwt-secret`(`JWT_SECRET`環境変数)から読み込み、未設定時は起動時に例外
- `PasswordService`: bcrypt(コスト12)。`matchAgainstDummyHash()` で、ユーザー不存在時も起動時生成済みの固定ダミーハッシュに対してbcrypt照合を実行し、応答時間差によるユーザーID列挙を防止
- `MaxUtf8Bytes`: パスワードのUTF-8バイト長(72バイト以下)を検証するカスタムBean Validation制約。`@Size`(文字数ベース)では日本語等マルチバイト文字で実質的な上限が意図とズレる問題に対応(認証機能定義書§5)
- `JwtAuthenticationFilter`(`OncePerRequestFilter`): Cookie欠如・不正・期限切れでも401を返さず`SecurityContext`を空のまま委譲。ユーザーIDを`Authentication#getName()`にセットする
- `SecurityConfig`: `SessionCreationPolicy.STATELESS`、CSRF無効化、`/auth/**`・`/health`のみ`permitAll()`、それ以外は認証必須。明示的な`AuthenticationEntryPoint`で未認証時に401を返す(未設定だとSpring Securityの既定動作が403になり得るため明示が必須だった)
- `AuthService`(ビジネスロジック層)・`AuthController`(`/auth/signup`, `/auth/login`, `/auth/logout`, `/auth/me`)。Cookie組み立て(`ResponseCookie`、`HttpOnly; SameSite=Lax; Secure`(本番のみ、`chatspace.cookie-secure`で切替))はController側の責務とし、Service層はHTTPの概念を持たない(計画書§1の3層アーキテクチャ方針、ArchUnit制約)

#### 3. 共通基盤(`common`パッケージ)

- 例外階層: `NotFoundException`(404)・`ForbiddenException`(403)・`ConflictException`(409)・`BadRequestException`(400、`InvalidCredentialsException`はこれを継承)
- `GlobalExceptionHandler`(`@RestControllerAdvice`): 上記例外・Bean Validationエラー・未認証エラーをHTTPステータスへ変換
- `CurrentUser`アノテーション + `CurrentUserArgumentResolver`: 認証済みユーザーの内部UUIDをコントローラ引数へ注入(`WebMvcConfig`で登録)。以降の全フェーズのコントローラで再利用する想定

#### 4. 認可サービス骨格(計画書§3)

`WorkspaceAuthorizationService.requireMember/requireOwner`、`ChannelAuthorizationService.requireChannelMember`、`DmAuthorizationService.requireDmAccess` をメソッドシグネチャのみ作成(本体は `UnsupportedOperationException("フェーズ2で実装")`)。フェーズ2で本体実装する。

#### 5. シードデータ(`DevSeedRunner`)

`@Profile("seed")` の `CommandLineRunner`。alice/bob/carol(password123)と "Sample Workspace"(aliceがOWNER、bob/carolがMEMBER)を投入。`userRepository.count() > 0` の場合は再投入しない(再起動での重複防止)。

### 実機検証

`docker compose up -d postgres` → `./gradlew bootRun --args='--spring.profiles.active=dev,seed'` で起動し、以下をcurlで確認した(検証後、アプリ・Postgresコンテナとも停止済み):

| 確認項目 | 結果 |
|---|---|
| signup → 201、Cookie発行、レスポンスにパスワードハッシュを含まない | ✅ |
| signup 重複 → 409 | ✅ |
| `/auth/me` Cookie有り → 200(本人情報) | ✅ |
| `/auth/me` Cookie無し → 401 | ✅ |
| login(seed投入済みalice/password123) → 200、Cookie発行 | ✅ |
| login 誤パスワード・login 不存在ユーザー → いずれも同一メッセージの400(存在有無を漏らさない) | ✅ |
| logout → 204 | ✅ |
| signup バリデーションエラー(8文字未満パスワード) → 400 | ✅ |
| `/health` 未認証 → 200 | ✅ |
| 未定義の保護対象パス(`/workspaces`)を未認証アクセス → 401(ルーティング以前にSecurityFilterChainで拒否) | ✅ |
| 公開エンドポイント(signup)に不正Cookie同時送付 → 401にならず201のまま成功 | ✅ |

### ビルド確認

`./gradlew build`(コンパイル・Spotlessチェック・ArchUnitテスト・単体テスト・jar作成)が成功。ArchUnitの3層アーキテクチャ制約テスト(フェーズ0時点では対象クラス0件で`allowEmptyShould`により素通りしていた)が、本フェーズで実クラスが揃ったことで実際に制約チェックとして機能し、違反なしを確認した。単体テストは`JwtServiceTest`(5件: 発行・検証、改ざん検知、期限切れ検知、不正トークン、別鍵署名検知)・`MaxUtf8BytesValidatorTest`(5件: ASCII境界値、マルチバイト境界値、null許容)を追加、全て成功。

> **2026-08-16追記**: フェーズ1〜8完了時点のレビューで、`GlobalExceptionHandler`の例外処理網羅性(壊れたJSON・型不一致・想定外例外のフォールバック)、`SecurityConfig`へのCORS設定追加の指摘があった。詳細は[review-fixes-2026-08-16.md](review-fixes-2026-08-16.md)を参照。

## 関連ドキュメント

- [`docs/機能定義書/認証機能定義書.md`](../docs/機能定義書/認証機能定義書.md)
- [`docs/DB設計書.md`](../docs/DB設計書.md)
- [phase0.md](phase0.md)(前フェーズ)
- [phase2.md](phase2.md)(次フェーズ)
