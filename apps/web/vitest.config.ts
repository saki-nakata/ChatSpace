import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    // 既定はnode。DOMを必要とするコンポーネントテストのみjsdomに切り替える
    // (STOMPのテストはブラウザAPIに依存しないため、余計なグローバルを持ち込まない)。
    environment: "node",
    environmentMatchGlobs: [["src/**/*.test.tsx", "jsdom"]],
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
    setupFiles: ["src/test/setup.ts"],
  },
});
