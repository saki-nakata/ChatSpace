import { execSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const apiRoot = path.dirname(fileURLToPath(new URL("../package.json", import.meta.url)));

/**
 * テスト用DB(prisma/test.db)にマイグレーションを適用してからテストスイート全体を実行する。
 * 既存のtest.dbに対しては未適用分だけが適用されるため、繰り返し実行しても安全。
 */
export async function setup() {
  execSync("pnpm exec prisma migrate deploy", {
    cwd: apiRoot,
    env: {
      ...process.env,
      DATABASE_URL: "file:./prisma/test.db",
    },
    stdio: "inherit",
  });
}
