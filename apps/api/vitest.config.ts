import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    globals: false,
    env: {
      DATABASE_URL: "file:./prisma/test.db",
      JWT_SECRET: "test-only-secret-do-not-use-in-production",
      PORT: "4099",
      WEB_ORIGIN: "http://localhost:5173",
      UPLOAD_DIR: "./test-uploads",
      NODE_ENV: "test",
    },
    globalSetup: "./test/globalSetup.ts",
    // 認可テストはDBを直接使うため、レコードの衝突を避けるためファイル単位で直列実行する
    fileParallelism: false,
    testTimeout: 15000,
  },
});
