import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { DESTINATION_TEMPLATES } from "./destinations";

/**
 * STOMP宛先名の契約テスト(AUTH-N18、計画書§7)。
 *
 * <p>`apps/api`の`StompDestinationsExporter`(フェーズ8で新設)が書き出す
 * `build/generated/stomp-destinations.json`と、このリポジトリの`destinations.ts`(手動同期)を突き合わせ、
 * ドリフト(バックエンド側の宛先変更にフロントエンドが追随できていない状態)を検出する。
 *
 * <p>JSONファイルは`cd apps/api && ./gradlew exportStompDestinations`で生成する(コミット対象外の
 * ビルド成果物)。CI(`frontend-ci.yml`)ではこのテストの前に同タスクを実行する。
 */
describe("STOMP宛先の契約テスト(Java/TS突き合わせ)", () => {
  it("apps/apiが出力するJSONと destinations.ts の定数が一致する", () => {
    const jsonPath = fileURLToPath(
      new URL("../../../api/build/generated/stomp-destinations.json", import.meta.url),
    );

    let backendDestinations: Record<string, string>;
    try {
      backendDestinations = JSON.parse(readFileSync(jsonPath, "utf-8"));
    } catch {
      throw new Error(
        "stomp-destinations.jsonが見つかりません。先に" +
          " `cd apps/api && ./gradlew exportStompDestinations` を実行してください。",
      );
    }

    expect(DESTINATION_TEMPLATES).toEqual(backendDestinations);
  });
});
