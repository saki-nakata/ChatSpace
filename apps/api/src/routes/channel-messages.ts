import { Hono } from "hono";
import { createMessageSchema, toggleReactionSchema, updateMessageSchema } from "@chatspace/shared";
import { requireAuth } from "../auth/middleware.js";
import { requireChannelMember } from "../lib/authz.js";
import * as messagesService from "../lib/messages-service.js";
import { BadRequest } from "../lib/errors.js";
import { routeParams } from "../lib/http.js";
import type { AppEnv } from "../types.js";

export const channelMessageRoutes = new Hono<AppEnv>();
channelMessageRoutes.use("*", requireAuth);

channelMessageRoutes.get("/", async (c) => {
  const { workspaceId, channelId } = routeParams<{ workspaceId: string; channelId: string }>(c);
  const userId = c.get("userId");
  await requireChannelMember(channelId, userId, workspaceId);
  const before = c.req.query("before");
  const limit = c.req.query("limit");
  const messages = await messagesService.listMessages({
    channelId,
    before,
    limit: limit ? Number(limit) : undefined,
    viewerId: userId,
  });
  return c.json({ messages });
});

channelMessageRoutes.get("/:messageId/context", async (c) => {
  const { workspaceId, channelId, messageId } = routeParams<{
    workspaceId: string;
    channelId: string;
    messageId: string;
  }>(c);
  const userId = c.get("userId");
  await requireChannelMember(channelId, userId, workspaceId);
  const context = await messagesService.getMessageContext({ channelId, workspaceId }, messageId, userId);
  return c.json(context);
});

channelMessageRoutes.get("/:messageId/replies", async (c) => {
  const { workspaceId, channelId, messageId } = routeParams<{
    workspaceId: string;
    channelId: string;
    messageId: string;
  }>(c);
  const userId = c.get("userId");
  await requireChannelMember(channelId, userId, workspaceId);
  const replies = await messagesService.listThreadReplies({ channelId, workspaceId }, messageId, userId);
  return c.json({ replies });
});

channelMessageRoutes.post("/", async (c) => {
  const { workspaceId, channelId } = routeParams<{ workspaceId: string; channelId: string }>(c);
  const userId = c.get("userId");
  await requireChannelMember(channelId, userId, workspaceId);

  const body = await c.req.json().catch(() => null);
  const parsed = createMessageSchema.safeParse(body);
  if (!parsed.success) throw BadRequest(parsed.error.issues[0]?.message ?? "入力内容を確認してください。");

  const message = await messagesService.createMessage(
    c.get("io"),
    { channelId, workspaceId },
    { authorId: userId, body: parsed.data.body, parentId: parsed.data.parentId, attachmentIds: parsed.data.attachmentIds },
  );
  return c.json({ message }, 201);
});

channelMessageRoutes.patch("/:messageId", async (c) => {
  const { workspaceId, channelId, messageId } = routeParams<{
    workspaceId: string;
    channelId: string;
    messageId: string;
  }>(c);
  const userId = c.get("userId");
  await requireChannelMember(channelId, userId, workspaceId);

  const body = await c.req.json().catch(() => null);
  const parsed = updateMessageSchema.safeParse(body);
  if (!parsed.success) throw BadRequest(parsed.error.issues[0]?.message ?? "入力内容を確認してください。");

  const message = await messagesService.updateMessage(
    c.get("io"),
    { channelId, workspaceId },
    { messageId, authorId: userId, body: parsed.data.body },
  );
  return c.json({ message });
});

channelMessageRoutes.delete("/:messageId", async (c) => {
  const { workspaceId, channelId, messageId } = routeParams<{
    workspaceId: string;
    channelId: string;
    messageId: string;
  }>(c);
  const userId = c.get("userId");
  await requireChannelMember(channelId, userId, workspaceId);
  await messagesService.deleteMessage(c.get("io"), { channelId, workspaceId }, { messageId, requesterId: userId });
  return c.body(null, 204);
});

channelMessageRoutes.post("/:messageId/reactions", async (c) => {
  const { workspaceId, channelId, messageId } = routeParams<{
    workspaceId: string;
    channelId: string;
    messageId: string;
  }>(c);
  const userId = c.get("userId");
  await requireChannelMember(channelId, userId, workspaceId);

  const body = await c.req.json().catch(() => null);
  const parsed = toggleReactionSchema.safeParse(body);
  if (!parsed.success) throw BadRequest(parsed.error.issues[0]?.message ?? "入力内容を確認してください。");

  const message = await messagesService.toggleReaction(
    c.get("io"),
    { channelId, workspaceId },
    { messageId, userId, emoji: parsed.data.emoji },
  );
  return c.json({ message });
});
