## フェーズ15(任意) — パフォーマンステスト

**状態: 実施済み(2026-08-25、ローカル環境限定)**

旧フェーズ14の内容をそのまま繰り下げたもの(2026-08-22、フェーズ14をRenderデプロイ本体に差し替えたため)。

**フェーズ0-12の完了・機能同等性チェックリストの達成には不要**な学習発展フェーズ。機能同等性チェックリスト達成後の完了後フェーズとして実施できる。詳細は [`docs/テスト設計書.md`](../docs/テスト設計書.md) §7・[`docs/インフラ構成書.md`](../docs/インフラ構成書.md) を参照。

**スコープ変更(2026-08-25)**: 当初の実施条件は「本番とは別のRender Web Service・PostgreSQL・R2を用意し、ローカル環境との性能差も記録する」だったが、検証用Renderインスタンスの新規作成・環境変数投入・撤収という追加コストを避けるため、**実施環境をローカルのみに限定し、Renderとの性能比較はスコープ変更により対象外とした**(元の実施条件を別の方法で満たしたわけではない)。結果はローカル環境内での相対比較・実行計画の観察に限定され、Render本番相当の性能を示す証拠ではない。詳細・実測値は [`docs/performance-test-results-2026-08-25.md`](../docs/performance-test-results-2026-08-25.md) を参照。

### 実施条件(レビュー指摘対応)

- [x] 本番用Renderサービス・DBとは別環境で実施する(本番ユーザーデータ・本番R2バケットを使わない) — ローカル環境(専用の使い捨てPostgreSQL、既存の開発DBとも分離)で実施することで満たした
- [ ] ~~検証用Renderインスタンスに対して実施し、ローカル環境との性能差(dev/prodパリティの検証を兼ねる)も記録する~~ → **対象外(理由: スコープ変更によりRenderとの比較を対象外にしたため。上記参照)**
- [x] 最大VU(仮想ユーザー数)・継続時間・停止条件を実施前に決めておく — 各シナリオのVU数・rate・duration・thresholdsを`performance/*.js`のoptionsに明記済み(詳細は結果ドキュメント参照)
- [ ] ~~テスト後は使用した検証用サービス・DB・R2データを撤収する~~ → **対象外(理由: 検証用Render/R2を用意していないため撤収対象が存在しない)**。ローカルの専用使い捨てPostgreSQLコンテナは検証後に`docker stop`で破棄した

### 実装対象

- [x] k6によるシナリオベース負荷テストの導入(`performance/`ディレクトリ)
- [x] シナリオ(1) メッセージ送信APIのスループット・レイテンシ計測(`performance/message-send.js`)
- [x] シナリオ(2) 検索API — `pg_trgm`使用時の**2文字(`qz`)クエリ vs 5文字(`vxjkp`)クエリ**での性能差を計測(検索機能定義書§7の既知の制約を定性的に裏付け、詳細は結果ドキュメント参照。`performance/search.js` + `performance/seed-search-corpus.sh`)
- [x] シナリオ(3) STOMP WebSocket接続の同時接続数と、チャンネルへのメッセージ配信のファンアウト遅延(`performance/websocket-fanout.js`)
- [ ] ~~実施環境: 検証用Renderインスタンスに対して実施し、ローカル環境との性能差も記録する~~ → **対象外**(上記スコープ変更参照)

### 成果物

- [x] [`docs/performance-test-results-2026-08-25.md`](../docs/performance-test-results-2026-08-25.md) にシナリオ・負荷条件・結果・所見をまとめた

### 対象外(本フェーズでは扱わない)

- 実施しない場合でも、プロジェクトとしての完成・機能同等性チェックリストの達成には影響しない
- Render環境との性能比較(スコープ変更、上記参照)

### 確認方法

```bash
# ローカル専用の使い捨てPostgreSQL(既存の開発DBとは別。手順は結果ドキュメント参照)を起動した上で実行する
k6 run --vus 1 --iterations 1 performance/message-send.js   # スモーク
k6 run --vus 1 --iterations 1 performance/search.js         # スモーク(要 -e WORKSPACE_ID/-e CHANNEL_ID)
k6 run -e SMOKE=true performance/websocket-fanout.js        # スモーク(scenarios使用のため--vus/--iterationsは使えない)

bash performance/seed-search-corpus.sh                       # search.js用のコーパスを直接SQL投入
k6 run performance/message-send.js
k6 run -e WORKSPACE_ID=... -e CHANNEL_ID=... performance/search.js
k6 run performance/websocket-fanout.js
```

## 関連ドキュメント

- [`docs/performance-test-results-2026-08-25.md`](../docs/performance-test-results-2026-08-25.md)(実測結果)
- [`docs/テスト設計書.md`](../docs/テスト設計書.md) §7
- [`docs/インフラ構成書.md`](../docs/インフラ構成書.md)
- [`docs/機能定義書/検索機能定義書.md`](../docs/機能定義書/検索機能定義書.md)
- [phase14.md](phase14.md)(前フェーズ、Renderデプロイ本体)
- [README.md](README.md)(全体目次)
