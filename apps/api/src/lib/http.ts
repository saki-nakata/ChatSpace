import type { Context } from "hono";

/**
 * ネストした app.route() のマウントパス由来のパラメータは Hono の型推論が
 * 追いきれないため、ルート定義側で期待する形を明示してキャストするヘルパー。
 * 実行時の値は Hono のルーターがパスパターンから正しく抽出する。
 */
export function routeParams<T extends Record<string, string>>(c: Context): T {
  return c.req.param() as unknown as T;
}
