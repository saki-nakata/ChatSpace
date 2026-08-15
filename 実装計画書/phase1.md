## フェーズ1 — 認証・データモデル・認可サービス骨格・シード

**状態: 未着手**

JPAエンティティ全種類、認証(JWT+Cookie)、認可サービスの骨格、シードデータ投入を実装する。以降の全フェーズが依存する基盤フェーズ。詳細設計は [`docs/機能定義書/認証機能定義書.md`](../docs/機能定義書/認証機能定義書.md)・[`docs/DB設計書.md`](../docs/DB設計書.md) を正とする。

### 前提

- フェーズ0(Gradleプロジェクト・Flywayベースラインマイグレーション・ArchUnit)完了済み

### 実装対象

- [ ] JPAエンティティ12種(`User`, `Workspace`, `WorkspaceMember`, `Channel`, `ChannelMember`, `DmThread`, `Message`, `Attachment`, `Reaction`, `Mention`, `Notification`)をDB設計書のテーブル定義に対応するパッケージ(`user`/`workspace`/`channel`/`dm`/`message`/`notification`)に作成。Java enum + `@Enumerated(EnumType.STRING)`で列挙値を表現
- [ ] `JwtService`(Nimbus JOSE+JWT、HS256、`sub`=内部ユーザーID、7日有効)
- [ ] `PasswordService`(bcryptコスト12、UTF-8バイト長72バイト以下のカスタムバリデーション、ログイン時のダミーハッシュ照合によるタイミング攻撃対策)
- [ ] `JwtAuthenticationFilter`(`OncePerRequestFilter`) — Cookie欠如・不正時は401を返さず`SecurityContext`を空のまま委譲する設計(認証機能定義書§3.5)
- [ ] `SecurityConfig` — `SessionCreationPolicy.STATELESS`、CSRF無効化、`/auth/**`・`/health`は`permitAll()`
- [ ] `AuthController`(`/auth/signup`, `/auth/login`, `/auth/logout`, `/auth/me`) — signup重複時は409
- [ ] `WorkspaceAuthorizationService`/`ChannelAuthorizationService`/`DmAuthorizationService`の骨格(メソッドシグネチャのみ。`requireMember`/`requireOwner`/`requireChannelMember`/`requireDmAccess`。本体実装はフェーズ2で完成させる)
- [ ] Spring `CommandLineRunner`(`dev,seed`プロファイル) — alice/bob/carol(password123)、Sample Workspaceを投入。旧`seed.ts`からの移行はせず新規シードのみ

### 先に書くテスト

`docs/テスト設計書.md` §6.3 参照(このフェーズは認可クリティカルテストIDの対象外、単体テストのみ)。

- [ ] JWT発行・検証の単体テスト(`JwtService`が正しい`sub`claim・7日有効期限で署名し、改ざん・期限切れトークンを拒否すること)
- [ ] パスワードハッシュのUTF-8バイト長バリデーション単体テスト

### 対象外(本フェーズでは扱わない)

- ワークスペース/チャンネル/DMのCRUD本体(フェーズ2)
- 認可サービスの本体ロジック(フェーズ2で完成)

### 確認方法

```bash
docker compose up -d postgres
cd apps/api-java
./gradlew build
./gradlew bootRun --args='--spring.profiles.active=dev,seed'
# curl http://localhost:8080/auth/signup 等で動作確認
```

## 関連ドキュメント

- [`docs/機能定義書/認証機能定義書.md`](../docs/機能定義書/認証機能定義書.md)
- [`docs/DB設計書.md`](../docs/DB設計書.md)
- [phase0.md](phase0.md)(前フェーズ)
- [phase2.md](phase2.md)(次フェーズ)
