import type { Context, Next } from "hono";
import { getCookie } from "hono/cookie";
import { AUTH_COOKIE_NAME, verifyAuthToken } from "./jwt.js";
import type { AppEnv } from "../types.js";

/**
 * 認証必須ミドルウェア。Cookie の JWT を検証し、c.set("userId", ...) する。
 * 未認証の場合は 401 を返しハンドラに処理を進めない。
 */
export async function requireAuth(c: Context<AppEnv>, next: Next) {
  const token = getCookie(c, AUTH_COOKIE_NAME);
  if (!token) {
    return c.json({ error: "認証が必要です。" }, 401);
  }
  const payload = await verifyAuthToken(token);
  if (!payload) {
    return c.json({ error: "認証が無効です。再度ログインしてください。" }, 401);
  }
  c.set("userId", payload.sub);
  await next();
}
