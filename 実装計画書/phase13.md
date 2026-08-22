## フェーズ13 — 添付ファイルのオブジェクトストレージ化(Cloudflare R2)

**状態: ✅ 実装完了**(実機でのR2疎通確認は「確認方法」のDoDを参照)

Renderへのデプロイ方針確定(2026-08-22)に伴い新設したフェーズ。旧フェーズ13(水平スケール対応・RabbitMQ/Redis)は
実施しないことが確定したため、本フェーズの内容に差し替えた(旧内容は削除。Renderが1インスタンス運用でマネージド
RabbitMQも無く、本番で動かす先が無いことが理由)。

Render の Web Service(無料枠)には永続ディスクをアタッチできず、再デプロイ(コミット・再起動)のたびに
添付ファイルが失われる。これを解消するため、添付ファイルの保存先をローカルディスクと Cloudflare R2(S3互換
オブジェクトストレージ)で切り替え可能にする。詳細な設計判断は [`docs/インフラ構成書.md`](../docs/インフラ構成書.md)
§7.1を正とする。

### 実装対象

- [x] `AttachmentStorage`インターフェース新設(`put`/`exists`/`load`/`delete`)
- [x] `LocalDiskAttachmentStorage`(既定実装、`chatspace.storage.type=local`)。既存の `UploadService` にあった
      パストラバーサル対策(パス正規化+`uploadDir`配下チェック)をそのまま移設
- [x] `S3AttachmentStorage`(`chatspace.storage.type=s3`)。AWS SDK v2の`S3Client`でCloudflare R2に接続
- [x] `S3StorageProperties`(`@ConfigurationProperties` + Bean Validation。`type=s3`なのに未設定なら起動時に失敗)
- [x] `S3StorageConfig`(`S3Client`Bean。path-styleアドレッシング・`chunkedEncodingEnabled(false)`をR2向けに設定)
- [x] `UploadService`を`AttachmentStorage`経由に書き換え。**`serve()`の認可ロジック(`authorizeServe`呼び出し・
      拒否時の監査ログ)は無変更**。「存在確認」(`exists()`)と「本文取得」(`load()`)を分離し、本文取得は
      ライブ権限再チェックに成功した後にのみ行う(レビュー指摘対応: 認可拒否されるリクエストのたびにR2へ
      本文転送させないため)
- [x] ロールバック時の孤児削除(`saveToDisk`内の`Files.deleteIfExists`)を`AttachmentStorage.delete()`経由に置き換え
- [x] `build.gradle.kts`に`software.amazon.awssdk:s3`を追加
- [x] `application.yml`に`chatspace.storage.*`設定キーを追加、`.env.sample`に`STORAGE_TYPE`系を追加

### テスト

- [x] 既存`UploadAuthorizationTest`(認可クリティカルテスト、AUTH-N07・AUTH-N25等)を無改修のまま実行し、
      回帰が無いことを確認(`chatspace.storage.type`未設定→既定`local`)
- [x] `S3AttachmentStorageTest`(単体、Mockito): put/load/deleteのリクエスト組み立て、`exists()`が
      `NoSuchKeyException`/404で`false`を返すことを検証
- [x] `UploadServiceOrderingTest`(単体、Mockito): ライブ権限再チェック失敗時に`storage.load()`が
      一度も呼ばれないことを検証(DoS再発防止のリグレッションテスト)

### R2移行完了の定義(フェーズ14着手・CD有効化の前提条件)

コードのマージだけでは「移行完了」とみなさない。以下を実機で確認してから初めてフェーズ14(Renderデプロイ・CD有効化)に着手する。

- [ ] 実際のR2バケットに対して、アプリ経由でアップロード→取得→削除が成功すること
- [ ] Render上でアプリを起動し、`STORAGE_TYPE=s3`設定で添付ファイルが正しく保存・取得できること
- [ ] 一度Renderで再デプロイ(再起動)した後も、以前アップロードした添付ファイルが取得できること
      (ローカルディスクではなくR2が実際にソースオブトゥルースになっていることの確認)

### 確認方法

```bash
cd apps/api
./gradlew build   # Spotless + ArchUnit + 既存UploadAuthorizationTest + 新規S3AttachmentStorageTest・UploadServiceOrderingTest
```

上記コマンドはローカル実行時 `chatspace.storage.type` 未設定のため R2 に接続せず、既定の
`LocalDiskAttachmentStorage` で完結する。R2実機確認は上記DoDを参照。

## 関連ドキュメント

- [`docs/インフラ構成書.md`](../docs/インフラ構成書.md) §7.1
- [`docs/機能定義書/添付ファイル機能定義書.md`](../docs/機能定義書/添付ファイル機能定義書.md)
- [phase7.md](phase7.md)(添付ファイル機能の初期実装)
- [phase14.md](phase14.md)(次フェーズ、Renderデプロイ本体。フェーズ13完了が前提条件)
