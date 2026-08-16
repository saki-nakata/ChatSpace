# ChatSpace Java/Spring Boot 再設計 実装計画

Node.js/Hono/Socket.IO/Prisma/SQLite のプロトタイプを土台に、Java/Spring Boot + STOMP + PostgreSQL へゼロから再設計する。設計判断の詳細は [`docs/`](../docs/) の設計ドキュメント一式を参照。本ディレクトリはフェーズ単位の実装手順・進捗を記録する。

> 元は `.plans/java-spring-boot-redesign.md`(承認済み再設計計画書、複数回のレビューを経て確定)単一ファイルだったが、`docs/` 一式の作成に伴いフェーズ単位に分割した。設計判断の一次情報源は `docs/` および各フェーズの機能定義書であり、`.plans/` はGit管理対象外のローカル作業メモ(詳細は [`docs/要件定義書.md`](../docs/要件定義書.md) 冒頭の注記を参照)。

## フェーズ一覧

| フェーズ | 内容 | 状態 |
|---|---|---|
| [フェーズ-2](phase-2.md) | 技術検証スパイク(pg_trgm/pg_bigm調査、STOMP基本疎通確認、Renderドキュメント調査) | ✅ 完了 |
| [フェーズ-1](phase-1.md) | ドキュメント整備(`docs/`配下19ファイル) | ✅ 完了 |
| [フェーズ0](phase0.md) | リポジトリ雛形(`apps/api-java`新設、Spotless/ArchUnit、docker-compose、Flywayベースライン、CI新設) | ✅ 完了 |
| [フェーズ1](phase1.md) | 認証・データモデル・認可サービス骨格・シード | ✅ 完了 |
| [フェーズ2](phase2.md) | ワークスペース/チャンネル/DM の CRUD(メッセージ抜き) | ✅ 完了 |
| [フェーズ3](phase3.md) | メッセージ(チャンネル/DM共通)・リアクション・MessageScopeGuard | ✅ 完了 |
| [フェーズ4](phase4.md) | リアルタイム(STOMP、SUBSCRIBE認可、SEND default-deny、キック強制切断) | ✅ 完了 |
| [フェーズ5](phase5.md) | メンション・通知(スコープ再チェック含む) | ✅ 完了 |
| [フェーズ6](phase6.md) | 検索(pg_trgm、フェーズ3以降と並行可) | ✅ 完了 |
| [フェーズ7](phase7.md) | ファイルアップロード(フェーズ3以降と並行可) | ✅ 完了 |
| [フェーズ8](phase8.md) | OpenAPI生成パイプライン + STOMP宛先契約テスト | ✅ 完了(バックエンド側のみ、フロントエンド消費パイプラインはフェーズ9へ) |
| [フェーズ9](phase9.md) | フロントエンド本体(`apps/web-next`。9-A〜9-Eに細分化) | 未着手 |
| [フェーズ10](phase10.md) | UX拡張(未読区切り線、検索ジャンプ、タイピング表示、プレゼンスUI等) | 未着手 |
| [フェーズ11](phase11.md) | 機能同等性チェックリストの最終確認・旧実装削除とリネーム | 未着手 |
| [フェーズ12](phase12.md) | 仕上げ(レート制限、SameSite/CSRF再確認、ログ、情報漏洩監査) | 未着手 |
| [フェーズ13](phase13.md) | (任意)水平スケール対応 | 未着手・任意 |
| [フェーズ14](phase14.md) | (任意)パフォーマンステスト | 未着手・任意 |

## 実装方針の要点

- **認可を最大リスクと位置づけ、各フェーズで対応する認可・受け入れテストを先に(失敗する状態で)書き、実装と同時に通す**(テスト設計書.md参照)
- フェーズ0-12は単一インスタンス前提で完結させ、水平スケール対応(フェーズ13)は任意の学習発展フェーズとして切り離す
- 旧実装(`apps/api`, `apps/web`)は、新実装が機能同等性チェックリスト(フェーズ11参照)を満たすまで削除しない。並行開発中は新バックエンドを`apps/api-java`(ポート8080)、新フロントエンドを`apps/web-next`に配置する
- パッケージマネージャは pnpm のみ(npm/yarn は使わない)

## 確定した使用バージョン

Java 21 / Spring Boot 4.1.0 / Gradle 9.7.0 / PostgreSQL 16(ローカル)。詳細と選定経緯は [`docs/インフラ構成書.md`](../docs/インフラ構成書.md) §3.0、[フェーズ0](phase0.md) を参照。

## 確認方法(現時点)

```bash
docker compose up -d postgres
cd apps/api-java
./gradlew build          # Spotless + ArchUnit + テスト + ビルド
./gradlew bootRun --args='--spring.profiles.active=dev'   # Flywayマイグレーション自動適用
```

## 関連ドキュメント

| ドキュメント | ファイル |
|---|---|
| 要件定義書 | [`docs/要件定義書.md`](../docs/要件定義書.md) |
| テスト設計書(認可クリティカルテスト一覧) | [`docs/テスト設計書.md`](../docs/テスト設計書.md) |
| インフラ構成書 | [`docs/インフラ構成書.md`](../docs/インフラ構成書.md) |
| README(セットアップ手順) | [`../README.md`](../README.md) |
