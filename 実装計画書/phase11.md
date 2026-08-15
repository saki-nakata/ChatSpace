## フェーズ11 — 機能同等性チェックリストの最終確認・旧実装削除とリネーム

**状態: 未着手**

新実装が旧実装(プロトタイプ)と機能的に同等であることを確認し、初めて旧実装を削除・リネームする。**このフェーズより前に旧実装(`apps/api`, `apps/web`)を削除しない。**

### 機能同等性チェックリスト(全項目達成が本フェーズの完了条件)

- [ ] 認証(signup/login/logout/me)がプロトタイプと同じ挙動で動作する
- [ ] ワークスペース/チャンネル/DM の作成・招待・キック・退出が動作する
- [ ] メッセージ CRUD・スレッド返信・リアクション・メンションが動作する
- [ ] 添付ファイル(画像/動画)のアップロード・表示が動作する
- [ ] 通知(メンション/DM/招待/スレッド返信)がリアルタイムで届き、未読管理・キック後のスコープ再チェックも動作する
- [ ] 検索が自分の所属範囲内で正しく機能する
- [ ] 複数ブラウザタブでリアルタイム反映が機能する(単一インスタンスでのキック強制切断・Origin拒否含む)
- [ ] フェーズ2〜7で追加した認可クリティカルテスト・受け入れテスト(AUTH-P01〜P09, AUTH-N01〜N29)が全てgreen
- [ ] ArchUnitによる3層アーキテクチャ制約テスト(AUTH-N19〜N21)が全てgreen

### 実施手順

- [ ] 上記チェックリストを実機(手動含む)で確認する
- [ ] `apps/api-java` → `apps/api` にリネーム
- [ ] `apps/web-next` → `apps/web` にリネーム
- [ ] `pnpm-workspace.yaml` を `apps/web` のみに縮小
- [ ] `packages/shared`(zod)を削除
- [ ] 旧実装(Node/Hono/Socket.IO、旧React/Vite実装)を削除
- [ ] `.github/workflows/backend-ci.yml`・`frontend-next-ci.yml` のpathフィルタを`apps/api`・`apps/web`に更新(または`apps/api-java`/`apps/web-next`向けの記述を`apps/api`/`apps/web`に置き換え)
- [ ] `.github/workflows/claude-code-review.yml`・`quality-check`スキルからNode/TypeScriptバックエンド向けの記述を削除(段階的更新計画の最終段)
- [ ] README.mdの「再設計について(進行中)」節を削除し、通常のセットアップ手順に一本化

### 対象外(本フェーズでは扱わない)

- 水平スケール対応(フェーズ13)・パフォーマンステスト(フェーズ14)の完了。両フェーズは任意でありチェックリストの前提条件に含めない

### 確認方法

```bash
cd apps/api-java && ./gradlew build   # 全テストgreen確認
cd apps/web-next && pnpm --filter @chatspace/web-next run build
# リネーム後
pnpm install && pnpm run build && pnpm run test
```

## 関連ドキュメント

- [`README.md`](../README.md)
- [`docs/テスト設計書.md`](../docs/テスト設計書.md)
- [phase10.md](phase10.md)(前フェーズ)
- [phase12.md](phase12.md)(次フェーズ)
