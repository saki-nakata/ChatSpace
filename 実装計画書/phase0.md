## フェーズ0 — リポジトリ雛形

**状態: ✅ 完了(2026-08-15)**

`apps/api-java` の Gradle プロジェクトを新設し、静的解析基盤・DBマイグレーション基盤・CIを整備した。旧実装(`apps/api`, `apps/web`)には一切手を加えていない。

### 実施内容

#### 1. Gradle プロジェクトの構築

Spring Initializr(`https://start.spring.io`)の公式APIから正確な依存関係アーティファクト名を取得した上で、`build.gradle.kts`・Gradle Wrapper 一式を手動構築した。

> **注記**: 検証時、Spring Initializr の Gradle プロジェクト生成機能(`/starter.zip?type=gradle-project`)が Spring Boot 4.0.7/4.1.0 の両方で `Bom 'org.springframework.boot:spring-boot-dependencies:...' could not be resolved` というサーバー側エラーを返す状態だった(Maven生成 `type=maven-project` は正常動作)。この不具合を回避するため、Maven生成で得た正確な依存関係名を元に、Gradle用ビルド定義とGradle Wrapper(`gradlew`/`gradlew.bat`/`gradle-wrapper.jar`/`gradle-wrapper.properties`)を個別に構築した。

**確定した使用バージョン:**

| 項目 | バージョン |
|---|---|
| Java | 21(LTS) |
| Spring Boot | 4.1.0(RELEASE) |
| Gradle | 9.7.0 |
| io.spring.dependency-management プラグイン | 1.1.7 |
| Spotless プラグイン | 8.9.0(google-java-format) |
| ArchUnit(archunit-junit5) | 1.5.0 |
| PostgreSQL(ローカル) | 16 |
| Nimbus JOSE+JWT | 10.9.1 |

**重要な発見**: Spring Boot 4系では一部スターターのアーティファクト名が変更されている(`spring-boot-starter-web` → `spring-boot-starter-webmvc`)。テスト用スターターも機能ごとに分割されている(例: `spring-boot-starter-webmvc-test`)。設計ドキュメント(`docs/`)はフレームワークのアーティファクト名を直接記載していなかったため実害はなかったが、今後の実装(特にSpring Security・STOMP関連)ではBoot 4系の実際のAPIを都度確認しながら進める必要がある。

依存関係: `spring-boot-starter-webmvc`, `spring-boot-starter-actuator`(`/health`用), `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `spring-boot-starter-websocket`, `spring-boot-starter-flyway`, `flyway-database-postgresql`, `nimbus-jose-jwt`, `postgresql`(runtime), `spring-boot-docker-compose`(developmentOnly)。テスト用に各starterの `-test` バリアント、`spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`, `archunit-junit5`。

#### 2. 静的解析基盤(計画書§1.1)

- **Spotless**(google-java-format)導入。`./gradlew spotlessApply` で自動整形、`spotlessCheck` は `build`/`check` タスクに自動的に含まれる
- **ArchUnit** による3層アーキテクチャ制約テスト(`src/test/java/com/chatspace/api/architecture/LayeredArchitectureTest.java`)を作成:
  - Controller → Repository 直接依存の禁止(層飛ばし禁止)
  - Service → `jakarta.servlet.*`/`org.springframework.web.*` への依存禁止
  - Controller の戻り値型にJPAエンティティを直接使わない(標準DSLでは検出できないジェネリクス型引数まで検査するカスタム`ArchCondition`を`EntityReturnTypeCondition.java`として実装)
  - **重要な学び**: ArchUnitは「`that()`述語に一致するクラスが0件」の場合、既定でタイポ検知のため**失敗**として扱う(空集合なら黙って通す、という設計ではない)。フェーズ0時点ではController/Service/Repositoryが1つも存在しないため、`allowEmptyShould(true)` を明示している。フェーズ1で実クラスを追加すれば対象が非0件になり、このテストが本来の制約チェックとして機能し始める

#### 3. ローカル開発環境

- リポジトリルートに `docker-compose.yml`(PostgreSQL 16のみ)を追加
- `application.yml`/`application-dev.yml`/`application-test.yml` を作成。`spring.docker.compose.file` でルートの `docker-compose.yml` を参照するよう設定(Spring Bootの `spring-boot-docker-compose` は既定でモジュール直下の compose ファイルを探すため、明示的なパス指定が必要だった)

#### 4. Flywayベースラインマイグレーション(V1〜V12)

DB設計書.mdのテーブル定義書から、以下の12ファイルを作成:

```
V1__create_extension_pg_trgm.sql
V2__create_users.sql
V3__create_workspaces.sql
V4__create_workspace_members.sql
V5__create_channels.sql
V6__create_channel_members.sql
V7__create_dm_threads.sql
V8__create_messages.sql       -- messages_scope_xor_check、message_body_trgm_idx含む
V9__create_attachments.sql
V10__create_reactions.sql
V11__create_mentions.sql
V12__create_notifications.sql -- notifications_type_check(THREAD_REPLY含む5種)
```

**実機検証**: Docker Desktopを起動し、`docker compose up -d postgres` でPostgreSQLを起動、`./gradlew bootRun --args='--spring.profiles.active=dev'` で実際に全12マイグレーションが正常適用されることを確認した。あわせて検索機能定義書.md記載のSQL(ワイルドカードエスケープ込み)を実データで検証し、日本語2文字クエリ「予算」が正しくマッチすることを確認した(ただしテーブルサイズが小さいため、EXPLAINでの意味のあるインデックス使用検証はできていない。フェーズ-2の繰り越し事項を参照)。検証後、投入したテストデータは削除しクリーンな状態に戻した。

#### 5. パッケージ構造の雛形

`com.chatspace.api` 配下に `config`/`common`/`auth`/`user`/`workspace`/`channel`/`dm`/`message`/`search`/`notification`/`upload`/`realtime` の12パッケージを作成し、それぞれ `package-info.java` で担当範囲と実装予定フェーズを記載した。

#### 6. CI・レビューワークフロー

- `.github/workflows/backend-ci.yml` を新設。`apps/api-java/**` 変更時に `./gradlew build` を実行(稼働中)
- `.github/workflows/frontend-next-ci.yml` を新設。`apps/web-next/**` へのpathフィルタのため、フェーズ9まで発火しない(休眠)
- `.github/workflows/claude-code-review.yml` にJava/Spring Boot向けレビュー観点(層飛ばし・HTTP概念混入・エンティティ直接返却・`@Transactional`境界・N+1・Bean Validation・Flywayマイグレーション改変禁止・`ddl-auto`不使用・STOMP宛先同期)を追加。ソフトデリートの扱いについてのチェック項目もtombstone方式に合わせて訂正した
- `.claude/skills/quality-check/SKILL.md`(Git管理対象外)にJavaセクションを `./gradlew build` ベースに具体化し、Flywayマイグレーション検証・OpenAPI生成物のドリフト検出・認可クリティカルテストの個別報告・docs差分確認範囲の絞り込みを追加

### ビルド確認

`./gradlew build`(コンパイル・Spotlessチェック・ArchUnitテスト・jar作成)が成功することを確認済み。

## 関連ドキュメント

- [`docs/インフラ構成書.md`](../docs/インフラ構成書.md) §3.0(確定バージョン)
- [`docs/DB設計書.md`](../docs/DB設計書.md)
- [phase-2.md](phase-2.md)
- [phase1.md](phase1.md)(次フェーズ)
