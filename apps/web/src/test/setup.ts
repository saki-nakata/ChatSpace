/**
 * vitest の共通セットアップ。
 *
 * `@testing-library/jest-dom` は `document` を前提とするため、jsdom 環境
 * (`*.test.tsx`)でのみ読み込む。node 環境のテスト(STOMP関連)で読み込むと
 * `document is not defined` で落ちる。
 */
if (typeof document !== "undefined") {
  await import("@testing-library/jest-dom/vitest");
  const { cleanup } = await import("@testing-library/react");
  const { afterEach } = await import("vitest");
  // テスト間でDOMを持ち越さない(フォーカス状態が次のテストへ漏れるのを防ぐ)
  afterEach(() => cleanup());
}

// トップレベルawaitを使うため、このファイルをモジュールとして扱わせる
export {};
