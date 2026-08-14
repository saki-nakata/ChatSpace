import { Hono } from "hono";
import { deleteCookie, setCookie } from "hono/cookie";
import { loginSchema, signupSchema } from "@chatspace/shared";
import { prisma } from "../db.js";
import { hashPassword, verifyPassword } from "../auth/password.js";
import { AUTH_COOKIE_NAME, signAuthToken } from "../auth/jwt.js";
import { requireAuth } from "../auth/middleware.js";
import { toUserDTO } from "../lib/dto.js";
import { BadRequest } from "../lib/errors.js";
import { env } from "../env.js";
import type { AppEnv } from "../types.js";

export const authRoutes = new Hono<AppEnv>();

const COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 7; // 7日

// ユーザーが存在しない場合に bcrypt.compare をスキップすると、存在有無で応答時間に
// 差が出てユーザーID列挙(タイミング攻撃)を許してしまう。存在しない場合もこのダミー
// ハッシュと比較することで、常に bcrypt.compare を実行し処理時間を揃える。
const DUMMY_PASSWORD_HASH = await hashPassword("timing-attack-mitigation-dummy-password");

function setAuthCookie(c: Parameters<typeof setCookie>[0], token: string) {
  setCookie(c, AUTH_COOKIE_NAME, token, {
    httpOnly: true,
    secure: env.isProduction,
    sameSite: "Lax",
    path: "/",
    maxAge: COOKIE_MAX_AGE_SECONDS,
  });
}

// メール認証は要件上不要。ユーザーID・パスワードのみで即時アカウント作成する。
authRoutes.post("/signup", async (c) => {
  const body = await c.req.json().catch(() => null);
  const parsed = signupSchema.safeParse(body);
  if (!parsed.success) {
    throw BadRequest(parsed.error.issues[0]?.message ?? "入力内容を確認してください。");
  }
  const { userId, password, displayName } = parsed.data;

  const existing = await prisma.user.findUnique({ where: { userId } });
  if (existing) {
    throw BadRequest("このユーザーIDは既に使用されています。");
  }

  const passwordHash = await hashPassword(password);
  const user = await prisma.user.create({
    data: { userId, passwordHash, displayName },
  });

  const token = await signAuthToken(user.id);
  setAuthCookie(c, token);

  return c.json({ user: toUserDTO(user) }, 201);
});

authRoutes.post("/login", async (c) => {
  const body = await c.req.json().catch(() => null);
  const parsed = loginSchema.safeParse(body);
  if (!parsed.success) {
    throw BadRequest("ユーザーIDとパスワードを入力してください。");
  }
  const { userId, password } = parsed.data;

  const user = await prisma.user.findUnique({ where: { userId } });
  const isValidPassword = await verifyPassword(password, user?.passwordHash ?? DUMMY_PASSWORD_HASH);
  if (!user || !isValidPassword) {
    throw BadRequest("ユーザーIDまたはパスワードが正しくありません。");
  }

  const token = await signAuthToken(user.id);
  setAuthCookie(c, token);

  return c.json({ user: toUserDTO(user) });
});

authRoutes.post("/logout", async (c) => {
  deleteCookie(c, AUTH_COOKIE_NAME, { path: "/" });
  return c.body(null, 204);
});

authRoutes.get("/me", requireAuth, async (c) => {
  const user = await prisma.user.findUniqueOrThrow({ where: { id: c.get("userId") } });
  return c.json({ user: toUserDTO(user) });
});
