## フェーズ6 — 検索

**状態: ✅ 完了(2026-08-16)**

`pg_trgm` + `ILIKE` によるメッセージ検索を実装した。`pg_trgm`拡張とGINインデックスはフェーズ0で既にFlywayマイグレーション(`V1__create_extension_pg_trgm.sql`, `V8__create_messages.sql`)として作成済みのため、本フェーズは`search`パッケージの実装のみ。詳細は [`docs/機能定義書/検索機能定義書.md`](../docs/機能定義書/検索機能定義書.md) を正とする。

### 実施内容

#### 1. `MessageRepository`へのネイティブ検索クエリ追加

検索対象のエンティティは既存の`message`パッケージの`Message`であるため、リポジトリ自体は新設せず、既存の`MessageRepository`(`message`パッケージ)へ`searchFirstPage`/`searchOlderThan`の2メソッドを追加した(検索対象エンティティのオーナーであるリポジトリに検索クエリを置く方針)。計画書§5のSQL例は`channel_id = ANY(:ids)`だったが、Spring Data JPAのネイティブクエリでは`IN (:ids)`の方がコレクション引数のバインドが素直に効くため、`IN`句に変更した(結果として同じ絞り込みになる)。`deleted_at IS NULL`を明示し、`(created_at, id) < (:cursorCreatedAt, :cursorId)`の行値比較でカーソルページングする。

#### 2. `SearchService`

呼び出しユーザーの`myChannelIds`(`ChannelMemberRepository.findByUserIdAndWorkspaceId`)・`myDmIds`(`DmThreadRepository.findAllForUser`)をリクエストの都度ライブに解決する。`channelId`指定時は所属チェックを行い、非所属なら403/404を返さず空の結果を返す(検索機能定義書§3.1の存在秘匿方針)。ワイルドカードエスケープ(`\`→`%`→`_`の順)はSQLの`REPLACE`三重チェーンではなくJavaコード側で行い、エスケープ済みパターン文字列をバインドパラメータとして渡す方式にした(エスケープ順序をコードレベルで一箇所に固定でき、SQLインジェクションの余地もない)。`IN`句を空リストにできない制約への対処として、所属チャンネル/DMが0件の場合は実際のメッセージIDと衝突しないダミーUUID(`new UUID(0, 0)`)で埋める。

#### 3. ワークスペースキック後のDM検索除外の設計判断

`DmThreadRepository.findAllForUser`自体はDM参加者チェックのみで、現在の`WorkspaceMember`であるかは見ていない(ワークスペースキックでも`DmThread`行は削除されないため)。`SearchService`側でこれを個別に再チェックしなかった理由は、検索エンドポイント自体が`/workspaces/{workspaceId}/search`というワークスペーススコープであり、`SearchController`が`SearchService`を呼ぶ前に`WorkspaceAuthorizationService.requireMember(workspaceId, userId)`を必ず先に実行しているため。ワークスペースキック済みユーザーはこの時点で404となり検索処理自体に到達できない(通知・DM権限で行った「個別にライブ再チェックする」パターンとは異なり、エンドポイントの入口の認可チェックがそのまま担保する形)。

#### 4. `SearchController`

`GET /workspaces/{workspaceId}/search?q=&channelId=&cursorCreatedAt=&cursorId=`。`q`のバリデーション(1〜200文字)は`SearchService`内で手動チェックした(`@RequestParam`へのBean Validation適用には`@Validated`のコントローラ単位設定が必要で、他エンドポイントに影響を与えないよう見送った)。

### 遭遇した問題と対応

- 本フェーズもコンパイルエラー・テスト失敗は発生しなかった。ただしWindows環境で新規ファイルを作成した際、改行コードがCRLFになり`spotlessJavaCheck`(google-java-formatはLF前提)が失敗した。`./gradlew spotlessApply`で自動整形して解消(既存ファイルの編集ではLFが保たれるため、新規ファイル作成時特有の問題)。

### 実機検証

`docker compose up -d postgres` → `bootRun --spring.profiles.active=dev,seed` で起動し確認した(検証後、アプリ・Postgresコンテナとも停止済み):

| 確認項目 | 結果 |
|---|---|
| ワークスペース非メンバー(carol)が検索APIを呼ぶと404 | ✅ |
| チャンネルメンバー(bob)が投稿済みメッセージを検索でき、本文がヒットする | ✅ |
| `%`/`_`を含む検索語(`50%_完了`)がワイルドカードとして誤動作せず、文字どおりの一致でヒットする | ✅ |
| 非公開チャンネル(bob非メンバー)の内容が、bobの全体検索結果に一切含まれない | ✅ |
| bobが非公開チャンネルを`channelId`に指定して検索しても、エラーではなく`200`+空の結果が返る | ✅ |
| メッセージ削除後、当該メッセージが検索結果から消える | ✅ |

### ビルド確認

`./gradlew build`が成功。テスト総数44件(フェーズ1-5からの39件 + 本フェーズ5件)、全てgreen。

新規テスト(`docs/テスト設計書.md`§6.2準拠、`SearchAuthorizationTest`):

| テストID | 内容 |
|---|---|
| AUTH-N08 | 非所属チャンネルのメッセージが検索結果に含まれないこと |
| (§3.1関連) | `channelId`に非所属チャンネルを指定すると403/404ではなく空の結果が返ること |
| AUTH-N09 | ソフトデリート済みメッセージが検索結果に含まれないこと |
| (ワイルドカード対策) | `%`/`_`を含む検索語が文字どおりの一致として扱われ、無関係なメッセージにマッチしないこと |
| (DMキック除外) | ワークスペースキック後、検索エンドポイント自体が404を返すこと(DM内容の非表示を含めて担保) |

### 対象外(本フェーズでは扱わなかった、次フェーズ以降へ繰り越し)

- Render実インスタンスでの`pg_trgm` EXPLAIN検証(大量データでの性能検証)は、フェーズ-2からの繰り越し事項のまま。フェーズ15(任意のパフォーマンステスト)で実施予定
- 形態素解析による自然な単語区切り検索(スコープ外)

> **2026-08-16追記**: フェーズ1〜8完了時点のレビューで、`SearchService`のService層認可(多層防御)の指摘があった。詳細は[review-fixes-2026-08-16.md](review-fixes-2026-08-16.md)を参照。

## 関連ドキュメント

- [`docs/機能定義書/検索機能定義書.md`](../docs/機能定義書/検索機能定義書.md)
- [`docs/テスト設計書.md`](../docs/テスト設計書.md) §6.2
- [実装計画書/phase-2.md](phase-2.md)(Render検証の繰り越し事項)
- [phase5.md](phase5.md)(前フェーズ)
- [phase7.md](phase7.md)(次フェーズ)
