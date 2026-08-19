## フェーズ13(任意) — 水平スケール対応

**状態: 未着手・任意**

**フェーズ0-12の完了・機能同等性チェックリストの達成には不要**な学習発展フェーズ。CLAUDE.mdの既知制約(プレゼンス管理が単一プロセス前提)を本格的に解消したい場合、または分散システムの構成を学習目的で経験したい場合にのみ実施する。詳細は [`docs/インフラ構成書.md`](../docs/インフラ構成書.md)(水平スケール対応の章)を正とする。

### 着手前に決めること

- [ ] Render実機で行うか、ローカルdocker-composeでの検証に留めるか(Render上で実施する場合、ディスクと複数インスタンスが排他のため添付ファイルはオブジェクトストレージ化が必須になる)

### 実装対象

- [ ] STOMPブローカーを Spring シンプルブローカーから **RabbitMQ 外部ブローカーリレー** へ切替
- [ ] プレゼンス管理をローカル`Map`から **Redis 共有プレゼンス** へ切替
- [ ] `session_eviction_outbox`テーブルのFlywayマイグレーション追加(DB設計書§4参照)
- [ ] キック処理のトランザクション内で`WorkspaceMember`/`ChannelMember`削除と同一トランザクションで`session_eviction_outbox`へ1行INSERT(一次防御)
- [ ] `@Scheduled`ディスパッチャ: `SELECT ... FOR UPDATE SKIP LOCKED`で未配送レコードを取得し、Redis Pub/Sub `session-evict`チャネルへ発行
- [ ] 各インスタンスでの定期整合性チェック(例: 30秒間隔、二次防御)
- [ ] `/user/queue/events`の複数インスタンス配送: **`userDestinationBroadcast`/`userRegistryBroadcast`の要否は本計画書の時点では確定させず、フェーズ13着手時に実際のSTOMPフレームを観測しながら再検証する**(過去のレビューで、事前の断定が不正確だった反省を踏まえた方針)
- [ ] RabbitMQユーザーキューの`x-expires`/`auto-delete`/`exclusive`設定: SUBSCRIBEフレームヘッダ経由での付与が必要。**実装着手前にRabbitMQ STOMPプラグインの公式ドキュメントで正確な仕様を裏取りすること**
- [ ] ブローカーリレーの認証・TLS(アプリ⇔RabbitMQ間の専用資格情報、本番はTLS必須)
- [ ] ハートビート・再接続設定(リレー⇔RabbitMQ間、クライアント側`heartbeatIncoming`/`heartbeatOutgoing`/`reconnectDelay`)
- [ ] 添付ファイルの共有ボリューム化(ローカルdocker-composeでは名前付きボリューム、Render上ではオブジェクトストレージ化)

### 検証(2インスタンスsmoke test)

`docs/テスト設計書.md` §7 のテストID。

- [ ] SCALE-01: メッセージ・プレゼンスが両インスタンスにまたがって伝播すること
- [ ] SCALE-02: 個人通知が別インスタンス発生イベントからでも`/user/queue/events`経由で届くこと
- [ ] SCALE-03: RabbitMQ管理UIで切断後にユーザーキューが実際に消滅すること
- [ ] SCALE-04: 別インスタンス経由のキック実行で、セッション強制切断・再購読拒否・新規メッセージ非受信の3条件すべてが成立すること
- [ ] SCALE-05: Redis一時停止状態でのキックでも、outbox経由または定期整合性チェックにより最終的に強制切断が成立すること
- [ ] SCALE-06: インスタンスAへアップロードした添付ファイルをインスタンスB経由で取得できること

### 確認方法

```bash
# docker-compose.yml に Redis・RabbitMQ・共有ボリュームを追記した上で
docker compose up -d
cd apps/api
./gradlew build   # Testcontainers に Redis/RabbitMQ を追加
# 2インスタンス起動して SCALE-01〜06 を手動/自動で確認
```

## 関連ドキュメント

- [`docs/インフラ構成書.md`](../docs/インフラ構成書.md)
- [`docs/DB設計書.md`](../docs/DB設計書.md) §4
- [`docs/テスト設計書.md`](../docs/テスト設計書.md) §7
- [phase4.md](phase4.md)(単一インスタンス設計、対比元)
- [phase14.md](phase14.md)(次フェーズ、任意)
