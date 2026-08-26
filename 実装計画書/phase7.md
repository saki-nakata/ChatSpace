## フェーズ7 — ファイルアップロード

**状態: ✅ 完了(2026-08-16)**

チャンネル/DM メッセージへの画像・動画添付を実装した。プロトタイプの `file-signature.ts`(マジックバイト判定)・`uploads.ts`(アップロード/配信)を Java へ1:1移植した(外部ライブラリなし)。設計の詳細は [`docs/機能定義書/添付ファイル機能定義書.md`](../docs/機能定義書/添付ファイル機能定義書.md) を参照。

---

### 実施内容

#### 1. `MimeSniffer`(`upload`パッケージ)

PNG/JPEG/GIF/WEBP/MP4/WEBMの固定シグネチャによるマジックバイト判定。クライアント申告の`Content-Type`・拡張子は一切信用せず、`detect(byte[] header)`が実データの先頭16バイトから判定結果(MIMEタイプ・`AttachmentKind`・保存用固定拡張子)を返す。単体テスト(`MimeSnifferTest`)で対応6形式の判定・非対応形式の拒否・拡張子偽装(シグネチャ不一致)の拒否・ヘッダー長不足時の安全な拒否を検証。

#### 2. `UploadService`(アップロード・配信)

- `upload(byte[] content, String originalFileName, UUID uploaderId)`: サイズ再チェック(`chatspace.max-attachment-size-bytes`、multipart解析レベルの一段目に続く二段目の防御)→マジックバイト判定→`UUID.randomUUID()`+検出MIME由来の固定拡張子で`storageKey`生成→ディスク保存→`Attachment`保存。
- `serve(String storageKey, UUID callerId)`: `storageKey`を正規表現(`^[A-Za-z0-9_-]+\.[a-z0-9]+$`)で検証→パス正規化後に保存ディレクトリ配下であることを再確認→**取得の都度のライブ権限再チェック**(最重要)。`Attachment.messageId`がある場合はそのメッセージのチャンネル/DMへの現在のメンバーシップを`ChannelAuthorizationService`/`DmAuthorizationService`で再検証し、`messageId`が無い場合は「誰かの現在のアバターURLと一致するか」(`UserRepository.existsByAvatarUrl`)→一致すれば認証済み全員に許可、しなければアップロード本人のみ許可。

#### 3. アーキテクチャ制約への対応(ArchUnit違反の修正)

当初`UploadService.upload()`の引数を`MultipartFile`で直接受け取る設計にしたところ、`LayeredArchitectureTest.servicesMustNotDependOnServletOrWebMvcTypes`(Service層は`org.springframework.web..`パッケージに依存できない)に抵触した。`MultipartFile`は`org.springframework.web.multipart`パッケージに属するため、Service層への直接引き渡しはHTTPの概念の持ち込みにあたる。**`UploadController`側で`MultipartFile#getBytes()`によりバイト列へ変換してから`UploadService`へ渡す**設計に修正し、Service層はプレーンな`byte[]`とファイル名のみを扱うようにした(計画書§1のレイヤー分離原則通り)。

#### 4. `UploadController`

`POST /uploads`(`multipart/form-data`、`file`フィールド)、`GET /uploads/{storageKey}`。配信レスポンスは`Content-Type`にサーバー検出MIME、`X-Content-Type-Options: nosniff`ヘッダーを付与する。

#### 5. アップロードサイズ上限の多層防御

`application.yml`の`spring.servlet.multipart.max-file-size`(25MB)/`max-request-size`(26MB、multipart境界・ヘッダー分の余裕)はフェーズ0のスキャフォールド時点で既に設定済みだった。本フェーズで追加したのは、`GlobalExceptionHandler`に`MaxUploadSizeExceededException`ハンドラ(multipart解析レベル拒否を400のJSONエラーへ変換)と、`UploadService`内での`content.length`による二段目の実サイズ再チェック。

#### 6. 未実装の既知ギャップ: プロフィール編集(アバター設定)エンドポイント

`serve()`のアバター判定ロジック(`UserRepository.existsByAvatarUrl`)は実装したが、**`User.avatarUrl`を実際に更新するプロフィール編集エンドポイント自体は、実装計画のどのバックエンドフェーズ(0〜8)にも明示的に含まれていないことが判明した**(フロントエンド側の`実装計画書/phase9.md`にS-14「プロフィール編集」モーダルの記載はあるが、対応するバックエンドAPIの実装フェーズが計画に無い)。このため`existsByAvatarUrl`分岐は現時点では実際には到達しない(常にfalse)。ロジック自体は仕様(§3.2・§6)通りに実装済みであり、プロフィール編集APIが将来追加されればそのまま機能する。フェーズ9(フロントエンド)着手前に、プロフィール編集APIをどのフェーズで実装するか(フェーズ12「仕上げ」に含めるか、フェーズ9着手前に差し込むか)を要確認事項として記録する。

> **後日追記**: 本フェーズ時点では未実装だったが、その後のフェーズで `UserProfileController`(`com.chatspace.api.profile`)として実装済み。したがって `serve()` のアバター判定分岐(`existsByAvatarUrl`)は現在は実際に到達する。上記は当時の状況の記録として残す。

### 遭遇した問題と対応

- **ArchUnit違反**: 上記§3の通り、`UploadService`が`MultipartFile`に直接依存する設計だと`servicesMustNotDependOnServletOrWebMvcTypes`に抵触した。Controllerでバイト列に変換してから渡す設計に修正して解消。
- **パストラバーサル文字列を含むテストの期待値調整**: `..%2f..%2fsecret.png`のようなURLエンコード済みパストラバーサル文字列を含むリクエストは、`UploadService`の正規表現・パス正規化チェックに到達する前に**Spring自身のルーティング層で400として拒否される**ことが判明した(MockMvc環境でも再現)。到達したとしても自前のチェックで404になる設計だが、どちらの層で拒否されても「読み出しに成功しない」ことが本質的に重要なため、該当テストの期待値は`is4xxClientError()`に緩和した。
- **Windows(Git Bash)でのcurl multipartアップロード**: 実機スモークテストで`curl -F "file=@/tmp/test.png"`のようなMSYS形式パスを指定すると`curl: (26) Failed to open/read local data from file/application`で失敗した。mingw64ネイティブビルドのcurl.exeは`-F`引数内のパスにMSYSの自動パス変換が効かないため、`cygpath`等でWindows形式パス(`C:\Users\...`)に変換してから渡す必要があった(このセッション固有の実行環境の制約であり、アプリケーションコード自体の問題ではない)。

### 実機検証

`docker compose up -d postgres` → `bootRun --spring.profiles.active=dev,seed` で起動し確認した(検証後、アプリ・Postgresコンテナとも停止済み):

| 確認項目 | 結果 |
|---|---|
| 有効なPNGファイルのアップロードが201で成功し、`storageKey`・`fileName`・`mimeType`・`sizeBytes`が正しく返る | ✅ |
| アップロード直後(投稿前プレビュー段階)、アップロード本人は取得(200)でき、他人は404 | ✅ |
| チャンネルメッセージに添付後、同チャンネルメンバーは取得(200)でき、ワークスペース非メンバーは404 | ✅ |
| チャンネルからキックされた後、以前は取得できていたユーザーが同じ添付ファイルを取得しようとすると404(AUTH-N07) | ✅ |
| レスポンスヘッダーが`Content-Type: image/png`(サーバー検出値)・`X-Content-Type-Options: nosniff` | ✅ |
| 拡張子偽装ファイル(`.png`だが中身はプレーンテキスト)のアップロードが400で拒否される | ✅ |
| 実際に27MBのファイルをアップロードすると、multipart解析レベルで400として拒否される(`MaxUploadSizeExceededException`) | ✅ |

### ビルド確認

`./gradlew build`が成功。テスト総数59件(フェーズ1-6からの44件 + 本フェーズ15件〈`MimeSnifferTest` 8件 + `UploadAuthorizationTest` 7件〉)、全てgreen。

新規テスト(`docs/テスト設計書.md`§6.2準拠):

| テストID | テストクラス | 内容 |
|---|---|---|
| (単体テスト) | `MimeSnifferTest` | PNG/JPEG/GIF/WEBP/MP4/WEBMの判定・非対応形式拒否・拡張子偽装拒否・短すぎるヘッダーの安全な拒否 |
| AUTH-N07 | `UploadAuthorizationTest` | チャンネルキック後、以前アクセスできた添付ファイルが404になること |
| AUTH-N25 | `UploadAuthorizationTest` | ワークスペースキック後、DM添付ファイルが404になること |
| (セキュリティテスト) | `UploadAuthorizationTest` | 未知の`storageKey`・パストラバーサル文字列を含む`storageKey`が拒否されること |
| (セキュリティテスト) | `UploadAuthorizationTest` | 拡張子偽装ファイルのアップロードが400になること(統合テストレベル) |
| (多層防御) | `UploadAuthorizationTest` | サービス層での`getSize()`超過チェックが400になること |
| (過剰ブロック検証) | `UploadAuthorizationTest` | 投稿前プレビュー段階でアップロード本人は取得でき、他人は404になること |

### 対象外(本フェーズでは扱わなかった、既知のギャップとして記録)

- ウイルス・マルウェアスキャンは対象外(マジックバイト検証のみ、プロトタイプから継続)
- DBへの`Attachment`レコード保存失敗時、既にディスクへ書き込み済みのファイルを削除する後始末処理は未実装(孤児ファイルが溜まるのみで認可・DoSには影響しないため優先度低)
  - → **後日対応済み**: 2026-08-16のレビュー指摘を受け、`UploadService#registerRollbackCleanup` でトランザクションのロールバック時にストレージ側のオブジェクトを削除するようになった
- `messageId IS NULL`のまま放置された未使用アップロードの定期クリーンアップは未実装(同上)
  - → **現在も未実装**。ただし公開デモ化により脅威モデルが変わっており、「孤児ファイルが溜まるだけ」ではなくストレージ濫用の経路になりうる(要件定義書§3.2の既知ギャップとして再掲)
- 上記「未実装の既知ギャップ」に記載の通り、プロフィール編集(アバター設定)APIが未実装のため、`serve()`のアバター公開ロジックは現時点で実際には到達しない(→ 後日 `UserProfileController` として実装済み。上記追記を参照)

> **2026-08-16追記**: フェーズ1〜8完了時点のレビューで、アップロードのトランザクションロールバック時の孤児ファイル対策・ファイルサイズ上限メッセージの二重管理解消・`MimeSniffer`のMP4ブランド検証の指摘があった。詳細は[review-fixes-2026-08-16.md](review-fixes-2026-08-16.md)を参照。

## 関連ドキュメント

- [`docs/機能定義書/添付ファイル機能定義書.md`](../docs/機能定義書/添付ファイル機能定義書.md)
- [`docs/テスト設計書.md`](../docs/テスト設計書.md)(§6.2 AUTH-N07・AUTH-N25)
- [`docs/DB設計書.md`](../docs/DB設計書.md)(`Attachment`エンティティ)
- [phase6.md](phase6.md)(前フェーズ)
- [phase8.md](phase8.md)(次フェーズ)
