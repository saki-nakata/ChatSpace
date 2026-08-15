## フェーズ7 — ファイルアップロード

**状態: 未着手**

チャンネル/DM メッセージへの画像・動画添付を実装する。プロトタイプの `file-signature.ts`(マジックバイト判定)・`uploads.ts`(アップロード/配信)を Java へ1:1移植する(外部ライブラリなし)。設計の詳細は [`docs/機能定義書/添付ファイル機能定義書.md`](../docs/機能定義書/添付ファイル機能定義書.md) を参照。

**フェーズ3以降と並行実施可能**(フェーズ表参照)。`upload` パッケージはフェーズ0で雛形作成済み。

---

### 先に書くテスト(失敗する状態でまず追加する)

- **AUTH-N07**: 添付ファイルのライブ権限再チェック — アップロード後にチャンネル/DMからキックされたユーザーが、以後 `GET /uploads/{storageKey}` で取得できなくなること(取得の都度403/404)
- **AUTH-N25**: ワークスペースキック後のDM添付ファイル取得不可 — DM添付ファイルの配信は `DmAuthorizationService.requireDmAccess(dmId, userId, workspaceId)` を `workspaceId` 付きで呼ぶため、ワークスペースからキックされたユーザーは既知の `storageKey` を知っていても取得できないこと

### 実装チェックリスト

- [ ] `MimeSniffer` クラス: PNG/JPEG/GIF/WEBP/MP4/WEBM のマジックバイト判定(単体テスト: 対応6形式の判定 + 非対応形式・拡張子偽装ファイルの拒否)
- [ ] `application.yml` に `spring.servlet.multipart.max-file-size=25MB` / `max-request-size=26MB` を設定する(**25MB同値にしない**。multipart境界・ヘッダ分のオーバーヘッドを見込んだ二重防御の一段目)
- [ ] `UploadService` でも `MultipartFile#getSize()` により実サイズを再確認する(二段目の防御)
- [ ] `UploadController`: `POST /uploads`(`multipart/form-data`、`file` フィールド、未指定は400)/ `GET /uploads/{storageKey}`
- [ ] `UploadService#upload()`: 検出MIMEが `ALLOWED_ATTACHMENT_MIME_TYPES` に不一致なら400。`storageKey` は `UUID.randomUUID()` + 検出MIME由来の固定拡張子で生成し、クライアント指定のファイル名・拡張子は保存パス決定に使わない(元ファイル名は `fileName` として表示用に別保持)
- [ ] `UploadService#serve()`: `storageKey` を正規表現(`^[A-Za-z0-9_-]+\.[a-z0-9]+$`)で検証し、パス正規化後に保存ディレクトリ配下であることを再確認してから読み出す(不一致・逸脱は404で存在を秘匿)
- [ ] `UploadService#serve()` のライブ権限再チェック: `Attachment.messageId` がある場合は都度そのメッセージのチャンネル/DMへのメンバーシップを再検証。`messageId IS NULL` の場合は「いずれかのユーザーの現在のアバターか」で全認証済みユーザーに許可、それ以外(投稿前プレビュー)はアップロード本人のみ許可
- [ ] レスポンスヘッダ: `Content-Type` はサーバー検出MIME、`X-Content-Type-Options: nosniff` を付与
- [ ] `chatspace.upload-dir` 設定プロパティでローカルディスク保存先を構成

### セキュリティテスト(あわせて追加)

- [ ] `storageKey` にパストラバーサル文字列(`../` 等)を含むリクエストが404で拒否されること
- [ ] 拡張子偽装ファイル(`.png` だが中身が非対応形式)がマジックバイト検証で拒否されること
- [ ] 25MBを超えるリクエストが multipart 解析レベルで拒否されること(`max-file-size`/`max-request-size`)

### 確認方法

```bash
./gradlew test    # AUTH-N07・AUTH-N25含む新規受け入れテストが green
./gradlew build
```

---

## 関連ドキュメント

- [`docs/機能定義書/添付ファイル機能定義書.md`](../docs/機能定義書/添付ファイル機能定義書.md)
- [`docs/テスト設計書.md`](../docs/テスト設計書.md)(§6.2 AUTH-N07・AUTH-N25)
- [`docs/DB設計書.md`](../docs/DB設計書.md)(`Attachment` エンティティ)
- [README.md](README.md)
