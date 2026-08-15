## フェーズ8 — OpenAPI生成パイプライン + STOMP宛先契約テスト

**状態: 未着手**

`packages/shared`(zod)廃止の代替として、REST型はOpenAPI自動生成に、STOMP宛先名はJava/TS間の契約テストに移行する。詳細は [`docs/要件定義書.md`](../docs/要件定義書.md)(API仕様書についての節)・[`docs/機能定義書/リアルタイム通信機能定義書.md`](../docs/機能定義書/リアルタイム通信機能定義書.md) を参照。

### 実装対象

- [ ] springdoc-openapi導入(`apps/api-java`)。既存の全Controllerに`@Operation`/`@ApiResponse`アノテーションを付与
- [ ] Gradleタスク`generateOpenApiDocs`で`openapi.json`を出力
- [ ] `apps/web-next`側で`openapi-typescript`により`openapi.json`からTS型を生成するパイプラインを構築(CIでは起動中サーバーではなくGradleタスクの出力を使う)
- [ ] Swagger UI(`/swagger-ui.html`)が開発サーバー起動中に閲覧できることを確認(手書きAPI仕様書は作成しない方針の実現)
- [ ] `StompDestinations.java`の宛先名リストをビルド時にJSONリソースとして出力するGradleタスクを追加
- [ ] `apps/web-next/src/realtime/destinations.ts`(手動同期)と、出力されたJSONを突き合わせるVitestの契約テストを追加
- [ ] `quality-check`スキルのOpenAPI生成物ドリフト検出(`generateOpenApiDocs`実行後の`git diff --exit-code`)が実際に機能することを確認

### 先に書くテスト

`docs/テスト設計書.md` §6.2 の該当テストID。

- [ ] AUTH-N18: STOMP宛先契約テスト(`StompDestinations.java`が出力するJSONと`destinations.ts`の定数が一致すること)

### 対象外(本フェーズでは扱わない)

- フロントエンド本体の実装(フェーズ9)。本フェーズは型生成パイプラインの構築のみ

### 確認方法

```bash
cd apps/api-java
./gradlew generateOpenApiDocs
cd ../web-next
pnpm run generate:api-types   # フェーズ9で導入するスクリプト
pnpm --filter @chatspace/web-next run test   # 契約テスト
```

## 関連ドキュメント

- [`docs/要件定義書.md`](../docs/要件定義書.md)
- [`docs/機能定義書/リアルタイム通信機能定義書.md`](../docs/機能定義書/リアルタイム通信機能定義書.md)
- [`docs/テスト設計書.md`](../docs/テスト設計書.md) §6.2
- [phase6.md](phase6.md)・[phase7.md](phase7.md)(前フェーズ群)
- [phase9.md](phase9.md)(次フェーズ)
