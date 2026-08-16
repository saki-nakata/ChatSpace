import js from "@eslint/js";
import tseslint from "typescript-eslint";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import globals from "globals";

export default tseslint.config(
  {
    ignores: [
      "**/dist/**",
      "**/node_modules/**",
      "**/.vite/**",
      "apps/api/uploads/**",
      "apps/api/prisma/migrations/**",
      ".playwright-mcp/**",
      "delete/**",
      // openapi-typescript生成物(手動編集しない、計画書§7)
      "apps/web-next/src/api/schema.d.ts",
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["apps/api/**/*.ts", "packages/shared/**/*.ts"],
    languageOptions: {
      globals: globals.node,
    },
  },
  {
    files: ["apps/web/**/*.{ts,tsx}", "apps/web-next/**/*.{ts,tsx}"],
    languageOptions: {
      globals: globals.browser,
    },
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      "react-refresh/only-export-components": ["warn", { allowConstantExport: true }],
      // このプロジェクトは React Compiler を使わず、外部REST APIへの単純な
      // フェッチオンマウント(useEffect内でのローディング状態更新)を多用する設計。
      // その標準的なパターン自体は正しく動作するため、このルールは無効化する。
      "react-hooks/set-state-in-effect": "off",
    },
  },
  {
    // Vitestの契約テストはNode環境で実行するため、globals.browser相当のブラウザAPIを前提にしない
    files: ["apps/web-next/**/*.test.ts"],
    languageOptions: {
      globals: globals.node,
    },
  },
  {
    rules: {
      "@typescript-eslint/no-unused-vars": ["warn", { argsIgnorePattern: "^_" }],
      "@typescript-eslint/no-explicit-any": "warn",
    },
  },
);
