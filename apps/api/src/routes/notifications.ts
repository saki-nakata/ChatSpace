import { Hono } from "hono";
import type { NotificationDTO } from "@chatspace/shared";
import { prisma } from "../db.js";
import { requireAuth } from "../auth/middleware.js";
import { toUserDTO } from "../lib/dto.js";
import type { AppEnv } from "../types.js";

export const notificationRoutes = new Hono<AppEnv>();
notificationRoutes.use("*", requireAuth);

notificationRoutes.get("/", async (c) => {
  const userId = c.get("userId");
  const workspaceId = c.req.query("workspaceId");
  const unreadOnly = c.req.query("unreadOnly") === "true";

  const notifications = await prisma.notification.findMany({
    where: {
      userId,
      workspaceId: workspaceId || undefined,
      readAt: unreadOnly ? null : undefined,
    },
    include: { fromUser: true },
    orderBy: { createdAt: "desc" },
    take: 100,
  });

  const dto: NotificationDTO[] = notifications.map((n) => ({
    id: n.id,
    type: n.type as NotificationDTO["type"],
    createdAt: n.createdAt.toISOString(),
    readAt: n.readAt ? n.readAt.toISOString() : null,
    workspaceId: n.workspaceId,
    channelId: n.channelId,
    dmId: n.dmId,
    messageId: n.messageId,
    threadParentId: n.threadParentId,
    fromUser: n.fromUser ? toUserDTO(n.fromUser) : null,
    text: n.text,
  }));

  return c.json({ notifications: dto });
});

notificationRoutes.get("/unread-count", async (c) => {
  const userId = c.get("userId");
  const count = await prisma.notification.count({ where: { userId, readAt: null } });
  return c.json({ count });
});

notificationRoutes.post("/:notificationId/read", async (c) => {
  const { notificationId } = c.req.param();
  const userId = c.get("userId");
  await prisma.notification.updateMany({
    where: { id: notificationId, userId },
    data: { readAt: new Date() },
  });
  return c.body(null, 204);
});

notificationRoutes.post("/read-all", async (c) => {
  const userId = c.get("userId");
  const body = await c.req.json().catch(() => ({}));
  const workspaceId = typeof body?.workspaceId === "string" ? body.workspaceId : undefined;
  await prisma.notification.updateMany({
    where: { userId, readAt: null, workspaceId: workspaceId || undefined },
    data: { readAt: new Date() },
  });
  return c.body(null, 204);
});
