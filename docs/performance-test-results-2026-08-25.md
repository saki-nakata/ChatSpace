# パフォーマンステスト結果(2026-08-25)

フェーズ15(任意)の実施記録。`実装計画書/phase15.md`・`docs/テスト設計書.md` §7で定義された3シナリオをk6で実施した。

## 実施環境・スコープの位置づけ

**本結果はローカル環境内での相対比較であり、Render本番相当の性能を示す証拠ではない。**

元のフェーズ15は「本番用Renderサービス・DBとは別のRender環境を用意し、ローカル環境との性能差も記録する」想定だったが、検証用Renderインスタンスの新規作成・環境変数投入・撤収という追加コストを避けるため、**スコープ変更によりRenderとの比較は対象外**とした(元のDoDを別の方法で満たしたわけではない)。

- バックエンド: ローカルで`./gradlew bootRun --args='--spring.profiles.active=dev,seed'`(Docker上のRelease/本番ビルドではなく、Gradle起動・JITウォームアップ未実施の状態)
- DB: 既存の開発用DB(`docker-compose.yml`、ポート5432)は使わず、専用の使い捨てPostgreSQLコンテナ(ポート5433)を別途起動し、検証後に破棄した。既存の開発DBへの汚染・`DevSeedRunner`のシードスキップ条件との衝突を避けるため
- k6: v2.0.0(ローカルインストール済み)

## シナリオ1: メッセージ送信APIのスループット・レイテンシ

**負荷条件**: `stages: 0→10VU(10s) → 10VU維持(30s) → 10→0VU(5s)`(合計約46秒)

| 指標 | 値 |
|---|---|
| 総リクエスト数 | 16,559(成功16,555件、失敗0件) |
| checks成功率 | 100.00% |
| http_req_failed | 0.00% |
| スループット | 約361 req/s |
| message_post_duration_ms avg | 22.56ms |
| message_post_duration_ms p90 | 26.24ms |
| message_post_duration_ms p95 | 28.46ms(閾値`p(95)<1000ms`を大幅にクリア) |
| message_post_duration_ms p99 | 38.29ms |
| message_post_duration_ms max | 108.92ms |

ローカル環境では10並列VUでのメッセージ送信は安定して30ms未満(p95)で応答しており、ボトルネックは確認されなかった。

## シナリオ2: 検索APIのクエリ長による性能差

**コーパス**: 専用チャンネルに、ひらがなのフィラーメッセージ5万件 + 短queryマーカー`qz`(2文字、ASCII)を含むメッセージ200件 + 長queryマーカー`vxjkp`(5文字、ASCII)を含むメッセージ200件を`docker exec`経由のSQL直接INSERTで投入(`performance/seed-search-corpus.sh`)。2つのマーカーは投入直後にSQLで**厳密に200件ずつ**であることを確認済み(部分文字列の包含関係が無いよう選定しており、`PAGE_SIZE=50`の打ち切りが両条件に同一に効く)。投入後`ANALYZE messages`でプランナ統計を更新した。

**負荷条件**: 5VU、30秒間、短queryと長queryを交互に発行

| 指標 | 短query(`qz`、2文字) | 長query(`vxjkp`、5文字) |
|---|---|---|
| avg | 71.36ms | 11.60ms |
| p90 | 81.97ms | 13.21ms |
| p95 | 86.88ms | 14.34ms |
| p99 | 111.63ms | 17.57ms |
| max | 130.46ms | 25.96ms |

短queryは長queryよりAPIレイテンシで**約6倍遅い**という明確な差が観測された(`検索機能定義書§7`の既知の制約と定性的に一致)。checks成功率は両queryとも100%。

### EXPLAIN (ANALYZE, BUFFERS) による実行計画の観察

「差が出ることを前提にせず、観測されたScan種別をそのまま記録する」方針で確認した(短queryが必ずseq scanになる、長queryが必ずGINを使う、という保証は無いため)。

**短queryマーカー(`qz`)**:
```
Limit (actual time=0.374..0.475 rows=50 loops=1)
  Buffers: shared hit=189
  -> Index Scan using messages_channel_created_id_idx on messages
       Index Cond: (channel_id = '...')
       Filter: ((deleted_at IS NULL) AND (body ~~* '%qz%'))
       Rows Removed by Filter: 200
       Buffers: shared hit=189
Execution Time: 0.527 ms
```
`message_body_trgm_idx`(GIN)は使われず、既存のチャンネル+作成日時の複合インデックス(`messages_channel_created_id_idx`)を使ってスキャンし、ILIKEでフィルタしている。

**長queryマーカー(`vxjkp`)**:
```
Limit (actual time=0.470..0.477 rows=50 loops=1)
  Buffers: shared hit=17
  -> Sort (Sort Key: created_at DESC, id DESC)
       -> Bitmap Heap Scan on messages
            Recheck Cond: (body ~~* '%vxjkp%')
            Filter: ((deleted_at IS NULL) AND (channel_id = '...'))
            Buffers: shared hit=11
            -> Bitmap Index Scan on message_body_trgm_idx
                 Index Cond: (body ~~* '%vxjkp%')
                 Buffers: shared hit=7
Execution Time: 0.624 ms
```
こちらは`message_body_trgm_idx`(GINトライグラムインデックス)への`Bitmap Index Scan`が選ばれた。

**重要な注記(観測事実として正直に記載する)**: 上記2つのEXPLAIN ANALYZEの`Execution Time`自体はどちらも1ミリ秒未満でほぼ同等であり、k6で観測されたAPIレベルの約60〜70msの差を直接説明するものではない。したがって「GINインデックスの有無だけで6倍の差が生まれた」と単純に結論づけることはできない。この差はSQL実行そのものではなく、Spring/Hibernateのクエリ構築・JSON直列化・JVM/JITウォームアップ順序(短queryが各イテレーションで常に先に発行される実装のため)などAPIパイプラインの他要素に起因する可能性がある。正確な内訳の特定にはアプリ側のタイミング計測(クエリログの実行時間出力等)が別途必要であり、本パスのスコープ外とした。

## シナリオ3: STOMP WebSocket接続の同時接続数とメッセージ配信のファンアウト遅延

**負荷条件**: subscriber 20VU(各1接続を約85秒保持、`per-vu-iterations`) + publisher(2秒に1回、60秒間で30回送信、subscriber接続完了を待って10秒後に開始)

| 指標 | 値 |
|---|---|
| WS接続成功数 | 20/20(100%) |
| ws_message_received_rate | 100.00%(600/600 = 20subscriber × 30メッセージ) |
| dropped_iterations(publisher) | 0(意図した30/30回すべて実行) |
| ws_end_to_end_latency_ms avg | 42.67ms |
| ws_end_to_end_latency_ms p90 | 46.6ms |
| ws_end_to_end_latency_ms p95 | 64ms |
| ws_end_to_end_latency_ms p99 | 228ms |
| ws_end_to_end_latency_ms max | 229ms |
| message_post_latency_ms avg(参考比較) | 42.77ms |
| message_post_latency_ms p95(参考比較) | 60.22ms |
| message_post_latency_ms p99(参考比較) | 180.88ms |

`ws_end_to_end_latency_ms`は「REST送信〜DBコミット〜`AFTER_COMMIT`送出〜WS受信」までの体感遅延全体であり、**純粋なブローカーのファンアウト遅延ではない**。`message_post_latency_ms`(POST自体の応答時間)は別指標として並記する参考比較にとどめ、両者を単純に差し引いた「内訳」は算出していない(サンプル数・分布が異なる集約後のp95同士を減算しても正確な内訳にはならないため。正確な内訳が必要な場合はメッセージID単位の生データ相関が別途必要)。

両指標がほぼ同じオーダーであることから、エンドツーエンド遅延の大部分はREST POST自体の応答時間に占められており、ブローカーのファンアウト自体(サーバー内でのbroadcast〜WS送出)に大きな追加遅延は無いと推測される(推測にとどめ、断定はしない)。

## 総括

- 3シナリオともローカル環境で完走し、失敗・タイムアウトは無かった
- メッセージ送信・WebSocketファンアウトともにローカル環境では明確なボトルネックは見られなかった
- 検索APIについては、クエリ長によるAPIレベルのレイテンシ差(短query約71ms vs 長query約12ms)を実測し、実行計画レベルでも使用インデックスが異なることを確認できた。ただし差の全量がインデックス選択のみに起因するとまでは特定できておらず、`検索機能定義書§7`の制約を**定性的に**裏付けるにとどまる
- 本結果はローカル環境(非Docker、JITウォームアップ無し)での相対比較であり、Render本番環境の性能を示すものではない
