import type { Server as SocketIOServer } from "socket.io";
import { extractMentionedUserIds, SocketEvents, type NotificationDTO } from "@chatspace/shared";
import { prisma } from "../db.js";
import { toUserDTO } from "./dto.js";

export function userRoom(userId: string) {
  return `user:${userId}`;
}
export function workspaceRoom(workspaceId: string) {
  return `workspace:${workspaceId}`;
}
export function channelRoom(channelId: string) {
  return `channel:${channelId}`;
}
export function dmRoom(dmId: string) {
  return `dm:${dmId}`;
}

async function pushNotification(
  io: SocketIOServer,
  input: {
    userId: string;
    type: "MENTION" | "DM" | "CHANNEL_INVITE" | "WORKSPACE_INVITE" | "THREAD_REPLY";
    workspaceId?: string | null;
    channelId?: string | null;
    dmId?: string | null;
    messageId?: string | null;
    threadParentId?: string | null;
    fromUserId?: string | null;
    text: string;
  },
) {
  const notification = await prisma.notification.create({
    data: {
      userId: input.userId,
      type: input.type,
      workspaceId: input.workspaceId ?? null,
      channelId: input.channelId ?? null,
      dmId: input.dmId ?? null,
      messageId: input.messageId ?? null,
      threadParentId: input.threadParentId ?? null,
      fromUserId: input.fromUserId ?? null,
      text: input.text,
    },
    include: { fromUser: true },
  });

  const dto: NotificationDTO = {
    id: notification.id,
    type: notification.type as NotificationDTO["type"],
    createdAt: notification.createdAt.toISOString(),
    readAt: notification.readAt ? notification.readAt.toISOString() : null,
    workspaceId: notification.workspaceId,
    channelId: notification.channelId,
    dmId: notification.dmId,
    messageId: notification.messageId,
    threadParentId: notification.threadParentId,
    fromUser: notification.fromUser ? toUserDTO(notification.fromUser) : null,
    text: notification.text,
  };

  io.to(userRoom(input.userId)).emit(SocketEvents.Notification, dto);
}

/**
 * メッセージ本文の @メンションを解決して Mention レコードと通知を作成する。
 * 「メンションはチャンネル参加者に含まれるユーザーのみ通知対象とする」ことで、
 * 権限外のユーザーへ存在情報が漏れることを防ぐ。
 */
export async function processMentionsForChannelMessage(
  io: SocketIOServer,
  params: {
    messageId: string;
    body: string;
    authorId: string;
    channelId: string;
    workspaceId: string;
  },
) {
  const candidateUserIds = extractMentionedUserIds(params.body);
  if (candidateUserIds.length === 0) return;

  const channelMembers = await prisma.channelMember.findMany({
    where: { channelId: params.channelId },
    include: { user: true },
  });
  const memberByHandle = new Map(channelMembers.map((m) => [m.user.userId, m.user]));

  const resolvedUsers = candidateUserIds
    .map((handle) => memberByHandle.get(handle))
    .filter((u): u is NonNullable<typeof u> => Boolean(u))
    .filter((u) => u.id !== params.authorId);

  if (resolvedUsers.length === 0) return;

  await prisma.mention.createMany({
    data: resolvedUsers.map((u) => ({ messageId: params.messageId, mentionedUserId: u.id })),
  });

  const author = await prisma.user.findUniqueOrThrow({ where: { id: params.authorId } });

  for (const u of resolvedUsers) {
    await pushNotification(io, {
      userId: u.id,
      type: "MENTION",
      workspaceId: params.workspaceId,
      channelId: params.channelId,
      messageId: params.messageId,
      fromUserId: params.authorId,
      text: `${author.displayName} さんがあなたをメンションしました`,
    });
  }
}

/** 自分が投稿した(スレッド)メッセージへの返信を通知する(@メンションが無くても届くようにする) */
export async function notifyThreadReply(
  io: SocketIOServer,
  params: {
    replyMessageId: string;
    parentMessageId: string;
    parentAuthorId: string;
    replierId: string;
    workspaceId: string;
    channelId?: string | null;
    dmId?: string | null;
  },
) {
  if (params.parentAuthorId === params.replierId) return;
  const replier = await prisma.user.findUniqueOrThrow({ where: { id: params.replierId } });
  await pushNotification(io, {
    userId: params.parentAuthorId,
    type: "THREAD_REPLY",
    workspaceId: params.workspaceId,
    channelId: params.channelId ?? null,
    dmId: params.dmId ?? null,
    messageId: params.replyMessageId,
    threadParentId: params.parentMessageId,
    fromUserId: params.replierId,
    text: `${replier.displayName} さんがあなたのメッセージにスレッド返信しました`,
  });
}

export async function notifyDMRecipient(
  io: SocketIOServer,
  params: {
    messageId: string;
    authorId: string;
    recipientId: string;
    dmId: string;
    workspaceId: string;
  },
) {
  if (params.authorId === params.recipientId) return;
  const author = await prisma.user.findUniqueOrThrow({ where: { id: params.authorId } });
  await pushNotification(io, {
    userId: params.recipientId,
    type: "DM",
    workspaceId: params.workspaceId,
    dmId: params.dmId,
    messageId: params.messageId,
    fromUserId: params.authorId,
    text: `${author.displayName} さんからダイレクトメッセージが届きました`,
  });
}

export async function notifyWorkspaceInvite(
  io: SocketIOServer,
  params: { userId: string; workspaceId: string; fromUserId: string; workspaceName: string },
) {
  const fromUser = await prisma.user.findUniqueOrThrow({ where: { id: params.fromUserId } });
  await pushNotification(io, {
    userId: params.userId,
    type: "WORKSPACE_INVITE",
    workspaceId: params.workspaceId,
    fromUserId: params.fromUserId,
    text: `${fromUser.displayName} さんがワークスペース「${params.workspaceName}」に招待しました`,
  });
}

export async function notifyChannelInvite(
  io: SocketIOServer,
  params: {
    userId: string;
    workspaceId: string;
    channelId: string;
    fromUserId: string;
    channelName: string;
  },
) {
  const fromUser = await prisma.user.findUniqueOrThrow({ where: { id: params.fromUserId } });
  await pushNotification(io, {
    userId: params.userId,
    type: "CHANNEL_INVITE",
    workspaceId: params.workspaceId,
    channelId: params.channelId,
    fromUserId: params.fromUserId,
    text: `${fromUser.displayName} さんがチャンネル「${params.channelName}」に招待しました`,
  });
}
