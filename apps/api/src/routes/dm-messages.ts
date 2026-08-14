import { Hono } from "hono";
import { createMessageSchema, toggleReactionSchema, updateMessageSchema } from "@chatspace/shared";
import { requireAuth } from "../auth/middleware.js";
import { requireDMAccess } from "../lib/authz.js";
import * as messagesService from "../lib/messages-service.js";
import { BadRequest } from "../lib/errors.js";
import { routeParams } from "../lib/http.js";
import type { AppEnv } from "../types.js";

export const dmMessageRoutes = new Hono<AppEnv>();
dmMessageRoutes.use("*", requireAuth);

dmMessageRoutes.get("/", async (c) => {
  const { dmId } = routeParams<{ workspaceId: string; dmId: string }>(c);
  const userId = c.get("userId");
  await requireDMAccess(dmId, userId);
  const before = c.req.query("before");
  const limit = c.req.query("limit");
  const messages = await messagesService.listMessages({
    dmId,
    before,
    limit: limit ? Number(limit) : undefined,
    viewerId: userId,
  });
  return c.json({ messages });
});

dmMessageRoutes.get("/:messageId/context", async (c) => {
  const { workspaceId, dmId, messageId } = routeParams<{
    workspaceId: string;
    dmId: string;
    messageId: string;
  }>(c);
  const userId = c.get("userId");
  await requireDMAccess(dmId, userId);
  const context = await messagesService.getMessageContext({ dmId, workspaceId }, messageId, userId);
  return c.json(context);
});

dmMessageRoutes.get("/:messageId/replies", async (c) => {
  const { workspaceId, dmId, messageId } = routeParams<{
    workspaceId: string;
    dmId: string;
    messageId: string;
  }>(c);
  const userId = c.get("userId");
  await requireDMAccess(dmId, userId);
  const replies = await messagesService.listThreadReplies({ dmId, workspaceId }, messageId, userId);
  return c.json({ replies });
});

dmMessageRoutes.post("/", async (c) => {
  const { workspaceId, dmId } = routeParams<{ workspaceId: string; dmId: string }>(c);
  const userId = c.get("userId");
  await requireDMAccess(dmId, userId);

  const body = await c.req.json().catch(() => null);
  const parsed = createMessageSchema.safeParse(body);
  if (!parsed.success) throw BadRequest(parsed.error.issues[0]?.message ?? "入力内容を確認してください。");

  const message = await messagesService.createMessage(
    c.get("io"),
    { dmId, workspaceId },
    { authorId: userId, body: parsed.data.body, parentId: parsed.data.parentId, attachmentIds: parsed.data.attachmentIds },
  );
  return c.json({ message }, 201);
});

dmMessageRoutes.patch("/:messageId", async (c) => {
  const { workspaceId, dmId, messageId } = routeParams<{ workspaceId: string; dmId: string; messageId: string }>(c);
  const userId = c.get("userId");
  await requireDMAccess(dmId, userId);

  const body = await c.req.json().catch(() => null);
  const parsed = updateMessageSchema.safeParse(body);
  if (!parsed.success) throw BadRequest(parsed.error.issues[0]?.message ?? "入力内容を確認してください。");

  const message = await messagesService.updateMessage(
    c.get("io"),
    { dmId, workspaceId },
    { messageId, authorId: userId, body: parsed.data.body },
  );
  return c.json({ message });
});

dmMessageRoutes.delete("/:messageId", async (c) => {
  const { workspaceId, dmId, messageId } = routeParams<{ workspaceId: string; dmId: string; messageId: string }>(c);
  const userId = c.get("userId");
  await requireDMAccess(dmId, userId);
  await messagesService.deleteMessage(c.get("io"), { dmId, workspaceId }, { messageId, requesterId: userId });
  return c.body(null, 204);
});

dmMessageRoutes.post("/:messageId/reactions", async (c) => {
  const { workspaceId, dmId, messageId } = routeParams<{ workspaceId: string; dmId: string; messageId: string }>(c);
  const userId = c.get("userId");
  await requireDMAccess(dmId, userId);

  const body = await c.req.json().catch(() => null);
  const parsed = toggleReactionSchema.safeParse(body);
  if (!parsed.success) throw BadRequest(parsed.error.issues[0]?.message ?? "入力内容を確認してください。");

  const message = await messagesService.toggleReaction(
    c.get("io"),
    { dmId, workspaceId },
    { messageId, userId, emoji: parsed.data.emoji },
  );
  return c.json({ message });
});
