## フェーズ8 — OpenAPI生成パイプライン + STOMP宛先契約テスト

**状態: ✅ 完了(2026-08-16、バックエンド側のみ。フロントエンド側の消費パイプラインはフェーズ9へ繰り越し)**

`packages/shared`(zod)廃止の代替として、REST型はOpenAPI自動生成に、STOMP宛先名はJava/TS間の契約テストに移行する計画。詳細は [`docs/要件定義書.md`](../docs/要件定義書.md)(API仕様書についての節)・[`docs/機能定義書/リアルタイム通信機能定義書.md`](../docs/機能定義書/リアルタイム通信機能定義書.md) を参照。

### スコープに関する判断(計画との整合性確認)

計画書の実装対象チェックリストには、`apps/web-next`側の`openapi-typescript`パイプライン構築・`destinations.ts`とのVitest契約テストが含まれていた。しかし本フェーズ着手時点で**`apps/web-next`自体がまだ存在しない**(フロントエンド本体はフェーズ9で新設)。フェーズ8のセクション見出しは元々「対象外: フロントエンド本体の実装(フェーズ9)。本フェーズは型生成パイプラインの構築のみ」と明記していたため、この方針を字義通り適用し、**本フェーズはバックエンド側の生成パイプライン(springdoc-openapi・STOMP宛先JSON書き出し)の構築までとし、フロントエンド側の消費コード(openapi-typescript実行・destinations.ts同期・Vitest契約テスト)はフェーズ9(`apps/web-next`新設時)へ繰り越す**。バックエンド側は「フロントエンドが読み込めば即座に使える」状態まで仕上げてある。

### 実施内容

#### 1. springdoc-openapi導入

- `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0`を追加(Spring Boot 4.1.0に対応するバージョンであることを事前にリリースノートで確認済み)
- `config/OpenApiConfig`: `OpenAPI`Bean(タイトル・バージョン・説明)に加え、`SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class)`を静的初期化ブロックで登録。**`@CurrentUser`はCookieのJWTから解決するController引数であり、実際のHTTPリクエストパラメータではないため、これを登録しないと全エンドポイントの生成スキーマに偽の`userId`クエリパラメータが混入してしまう**(`HttpServletRequest`等の標準除外対象と同じ扱いが必要という、springdocの既知の注意点)
- `SecurityConfig`: `/v3/api-docs/**`・`/swagger-ui/**`・`/swagger-ui.html`を`permitAll`に追加(エンドポイント形状のみでユーザーデータを含まないドキュメントのため公開して問題ないと判断。他の保護対象エンドポイントの認証には影響しないことを実機で確認済み)

#### 2. `generateOpenApiDocs`タスクの実装方式(計画書の記述を実装レベルで具体化)

計画書は「Gradleタスク`generateOpenApiDocs`で`openapi.json`を出力」とのみ記載していたが、実装方式(公式の`springdoc-openapi-gradle-plugin`を使うか、独自実装するか)は未確定だった。調査の結果、公式Gradleプラグインの最終リリースが2024年8月(v1.9.0)と更新が止まっており、Spring Boot 4系との組み合わせでの動作実績が不透明だったため、**既存のTestcontainers統合テスト基盤を再利用する自前実装**を選んだ:

- `OpenApiDocsGenerationTest`(`AbstractIntegrationTest`を継承): `GET /v3/api-docs`を叩き、レスポンスを整形して`apps/api-java/openapi.json`(プロジェクトルート、Gradleの`test`タスクの既定作業ディレクトリを利用)へ書き出す
- Gradle側は`generateOpenApiDocs`という`Test`タスクを新設し、`filter { includeTestsMatching(...) }`でこのテストクラス1つだけを実行するよう限定
- **副作用のある特殊テストのため、通常の`test`/`build`タスクからは明示的に除外する**(`tasks.named<Test>("test") { filter { excludeTestsMatching(...) } }`)。当初この除外を入れ忘れており、`./gradlew build`のたびに`openapi.json`が黙って上書きされる状態になっていたことに気づき修正した

#### 3. `openapi.json`のコミット・ドリフト検出

生成された`apps/api-java/openapi.json`(59KB、44エンドポイント分)をリポジトリへコミットする。`.gitignore`には追加しない(`quality-check`スキルが`./gradlew generateOpenApiDocs && git diff --exit-code -- openapi.json`でドリフト(コントローラ変更後の再生成・コミット漏れ)を検出する設計のため、コミット対象であることが前提)。

#### 4. STOMP宛先名のJSON書き出し(`StompDestinationsExporter`)

`StompDestinations`の全宛先(WebSocketエンドポイント・宛先テンプレート・タイピング送信先)を`Map<String, String>`として`build/generated/stomp-destinations.json`へ書き出す。Springコンテキストの起動が不要なため、Testcontainersを使う統合テストではなく`JavaExec`タスク(`exportStompDestinations`)から直接`main`メソッドを実行する設計にした(高速、DB不要)。

このタスクの実装過程で、`TypingController`の`@MessageMapping`アノテーション値(`"/channels.{channelId}.typing"`等)が`StompDestinations`定数として一元管理されていなかったことに気づき、`CHANNEL_TYPING_MAPPING`/`DM_TYPING_MAPPING`定数を追加して`TypingController`側もこれを参照するようリファクタした(JSON書き出しの正としても、`@MessageMapping`の値としても同じ定数を共有する)。

### 遭遇した問題と対応

- **`generateOpenApiDocs`が通常の`test`タスクにも紛れ込む問題**: `OpenApiDocsGenerationTest`は単に`src/test/java`配下に置いた`@Test`クラスであるため、フィルタなしの通常`test`タスクからも自動的に発見・実行されてしまい、`openapi.json`への書き込みという副作用が`./gradlew build`のたびに黙って発生していた。`tasks.named<Test>("test") { filter { excludeTestsMatching(...) } }`で明示的に除外して解決(上記実施内容§2参照)。
- **`@CurrentUser`のスキーマ汚染**: 対応を怠ると、全エンドポイントの生成スキーマに実際には存在しない`userId`クエリパラメータが混入する。`SpringDocUtils.addAnnotationsToIgnore`で解決(上記§1参照)。
- 上記2件以外はコンパイルエラー・テスト失敗とも発生しなかった。

### 実機検証

`docker compose up -d postgres` → `bootRun --spring.profiles.active=dev,seed` で起動し確認した(検証後、アプリ・Postgresコンテナとも停止済み):

| 確認項目 | 結果 |
|---|---|
| 未認証で`GET /swagger-ui/index.html`が200で表示される | ✅ |
| 未認証で`GET /v3/api-docs`が200で正しいOpenAPI 3.1形式のJSON(タイトル・バージョン含む)を返す | ✅ |
| セキュリティ設定変更後も、保護対象エンドポイント(`GET /workspaces`)への未認証アクセスは引き続き401 | ✅ |
| ログイン後、`GET /workspaces`が200で正常に応答(回帰なし) | ✅ |

### ビルド確認

`./gradlew build`が成功。テスト総数59件(フェーズ1-7と同数。`OpenApiDocsGenerationTest`は副作用があるため通常の`test`タスクからは除外し、`./gradlew generateOpenApiDocs`実行時のみ動作する専用テストとして分離)、全てgreen。

`./gradlew generateOpenApiDocs`・`./gradlew exportStompDestinations`はいずれも単体で正常動作を確認済み。

### 対象外(本フェーズでは扱わなかった、次フェーズ以降へ繰り越し)

- **`apps/web-next`側の消費パイプライン**(`openapi-typescript`実行スクリプト、`destinations.ts`とのVitest契約テスト=AUTH-N18)は、フロントエンド本体が存在しないため実装不可能であり、フェーズ9(`apps/web-next`新設)へ繰り越す。バックエンド側の出力(`openapi.json`・`stomp-destinations.json`)はフェーズ9からすぐ参照できる状態になっている
- 各Controllerへの`@Operation`/`@ApiResponse`アノテーションによる詳細説明・正確なレスポンスコード(例: 作成系エンドポイントの`201`)の付与は行っていない。springdocはコントローラの型情報から妥当なスキーマを自動生成できており(`@CurrentUser`以外は正しく認識される)、型生成パイプラインとしての機能に支障はないため優先度低の改善事項として記録するのみに留める。人間向けのSwagger UI説明文の充実はフェーズ12(仕上げ)以降の任意改善とする
- `quality-check`スキルの`git diff --exit-code -- openapi.json`によるドリフト検出は、生成コマンド自体の動作確認(本フェーズ)は完了したが、実際のCI/レビューフローでの運用確認はコントローラを変更する次回以降のPRで自然に検証される

## 関連ドキュメント

- [`docs/要件定義書.md`](../docs/要件定義書.md)
- [`docs/機能定義書/リアルタイム通信機能定義書.md`](../docs/機能定義書/リアルタイム通信機能定義書.md)
- [`docs/テスト設計書.md`](../docs/テスト設計書.md) §6.2
- [phase6.md](phase6.md)・[phase7.md](phase7.md)(前フェーズ群)
- [phase9.md](phase9.md)(次フェーズ。`apps/web-next`側の消費パイプラインもここで実装する)
