import { Hono } from "hono";
import { updateProfileSchema } from "@chatspace/shared";
import { prisma } from "../db.js";
import { requireAuth } from "../auth/middleware.js";
import { toUserDTO } from "../lib/dto.js";
import { BadRequest } from "../lib/errors.js";
import type { AppEnv } from "../types.js";

export const userRoutes = new Hono<AppEnv>();
userRoutes.use("*", requireAuth);

userRoutes.patch("/me", async (c) => {
  const body = await c.req.json().catch(() => null);
  const parsed = updateProfileSchema.safeParse(body);
  if (!parsed.success) {
    throw BadRequest(parsed.error.issues[0]?.message ?? "入力内容を確認してください。");
  }
  const user = await prisma.user.update({
    where: { id: c.get("userId") },
    data: parsed.data,
  });
  return c.json({ user: toUserDTO(user) });
});

// 招待・メンションの入力補助用に、ユーザーIDの完全一致検索のみ提供する
// (曖昧検索にすると全ユーザーの一覧が容易に取得できてしまうため)
userRoutes.get("/lookup", async (c) => {
  const userId = c.req.query("userId");
  if (!userId) throw BadRequest("userId を指定してください。");
  const user = await prisma.user.findUnique({ where: { userId } });
  if (!user) return c.json({ user: null });
  return c.json({ user: toUserDTO(user) });
});
