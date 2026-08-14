import type { Server as SocketIOServer } from "socket.io";
import { SocketEvents } from "@chatspace/shared";
import type { MessageDTO } from "@chatspace/shared";
import { prisma } from "../db.js";
import { BadRequest, Forbidden, NotFound } from "./errors.js";
import { toMessageDTO } from "./dto.js";
import {
  channelRoom,
  dmRoom,
  notifyDMRecipient,
  notifyThreadReply,
  processMentionsForChannelMessage,
} from "./notify.js";

const messageInclude = {
  author: true,
  attachments: true,
  reactions: true,
  mentions: { select: { mentionedUserId: true } },
  _count: { select: { replies: true } },
} as const;

interface Scope {
  channelId?: string;
  dmId?: string;
  workspaceId: string;
}

function roomFor(scope: Scope) {
  return scope.channelId ? channelRoom(scope.channelId) : dmRoom(scope.dmId!);
}

/**
 * 取得したメッセージが、呼び出し元が権限チェック済みの scope(URLのchannelId/dmId)に
 * 実際に属しているかを検証する。
 *
 * requireChannelMember / requireDMAccess は「呼び出し元が URL の scope のメンバーか」
 * しか見ておらず、messageId 自体がその scope に属するかは別途保証が必要。
 * これを省くと、自分が参加している任意のチャンネル/DMのURLに、権限外の
 * チャンネル/DMのmessageIdを組み合わせて渡すことで、本文やスレッド返信を読めてしまう。
 */
function assertMessageInScope(
  message: { channelId: string | null; dmId: string | null },
  scope: Scope,
): void {
  if (message.channelId !== (scope.channelId ?? null) || message.dmId !== (scope.dmId ?? null)) {
    throw NotFound("メッセージが見つかりません。");
  }
}

export async function listMessages(params: {
  channelId?: string;
  dmId?: string;
  parentId?: string | null;
  before?: string;
  limit?: number;
  viewerId: string;
}) {
  const limit = Math.min(params.limit ?? 50, 100);
  const messages = await prisma.message.findMany({
    where: {
      channelId: params.channelId ?? undefined,
      dmId: params.dmId ?? undefined,
      parentId: params.parentId === undefined ? null : params.parentId,
      ...(params.before ? { createdAt: { lt: new Date(params.before) } } : {}),
    },
    include: messageInclude,
    orderBy: { createdAt: "desc" },
    take: limit,
  });
  const dtos = messages.map((m) => toMessageDTO(m, params.viewerId));
  return dtos.reverse();
}

const CONTEXT_WINDOW = 25;

/**
 * 検索結果などから特定のメッセージにジャンプするための、その周辺だけを含む
 * メッセージ一覧を返す。対象がスレッド返信の場合は、メイン一覧には表示されない
 * (listMessages は parentId: null のみを返す)ため、親メッセージを軸にした
 * ウィンドウを返しつつ、呼び出し元にスレッドを開いて該当返信をハイライトすべきことを伝える。
 */
export async function getMessageContext(scope: Scope, messageId: string, viewerId: string) {
  const target = await prisma.message.findUnique({ where: { id: messageId } });
  if (!target) throw NotFound("メッセージが見つかりません。");
  assertMessageInScope(target, scope);

  const isReply = target.parentId !== null;
  const anchor = isReply
    ? await prisma.message.findUniqueOrThrow({ where: { id: target.parentId! } })
    : target;

  const [olderBatch, newerBatch] = await Promise.all([
    prisma.message.findMany({
      where: {
        channelId: scope.channelId ?? undefined,
        dmId: scope.dmId ?? undefined,
        parentId: null,
        createdAt: { lte: anchor.createdAt },
      },
      include: messageInclude,
      orderBy: { createdAt: "desc" },
      take: CONTEXT_WINDOW + 1,
    }),
    prisma.message.findMany({
      where: {
        channelId: scope.channelId ?? undefined,
        dmId: scope.dmId ?? undefined,
        parentId: null,
        createdAt: { gt: anchor.createdAt },
      },
      include: messageInclude,
      orderBy: { createdAt: "asc" },
      take: CONTEXT_WINDOW,
    }),
  ]);

  const hasMoreOlder = olderBatch.length > CONTEXT_WINDOW;
  const trimmedOlder = olderBatch.slice(0, CONTEXT_WINDOW);
  const combined = [...trimmedOlder.reverse(), ...newerBatch];
  const messages = combined.map((m) => toMessageDTO(m, viewerId));

  return {
    anchorMessageId: anchor.id,
    replyMessageId: isReply ? target.id : null,
    hasMoreOlder,
    messages,
  };
}

export async function listThreadReplies(scope: Scope, parentId: string, viewerId: string) {
  const parent = await prisma.message.findUnique({ where: { id: parentId } });
  if (!parent) throw NotFound("メッセージが見つかりません。");
  assertMessageInScope(parent, scope);

  const replies = await prisma.message.findMany({
    where: { parentId },
    include: messageInclude,
    orderBy: { createdAt: "asc" },
  });
  return replies.map((m) => toMessageDTO(m, viewerId));
}

export async function createMessage(
  io: SocketIOServer,
  scope: Scope,
  params: {
    authorId: string;
    body: string;
    parentId?: string | null;
    attachmentIds?: string[];
  },
): Promise<MessageDTO> {
  let parentMessage: Awaited<ReturnType<typeof prisma.message.findUnique>> = null;
  if (params.parentId) {
    parentMessage = await prisma.message.findUnique({ where: { id: params.parentId } });
    if (!parentMessage) throw NotFound("返信先のメッセージが見つかりません。");
    if (parentMessage.channelId !== (scope.channelId ?? null) || parentMessage.dmId !== (scope.dmId ?? null)) {
      throw BadRequest("返信先のメッセージが不正です。");
    }
  }

  const attachmentIds = params.attachmentIds ?? [];
  if (attachmentIds.length > 0) {
    const attachments = await prisma.attachment.findMany({ where: { id: { in: attachmentIds } } });
    if (attachments.length !== attachmentIds.length) {
      throw BadRequest("添付ファイルが見つかりません。");
    }
    for (const attachment of attachments) {
      if (attachment.uploaderId !== params.authorId) {
        throw Forbidden("自分がアップロードしたファイルのみ添付できます。");
      }
      if (attachment.messageId !== null) {
        throw BadRequest("既に他のメッセージへ添付済みのファイルです。");
      }
    }
  }

  const message = await prisma.message.create({
    data: {
      channelId: scope.channelId ?? null,
      dmId: scope.dmId ?? null,
      parentId: params.parentId ?? null,
      authorId: params.authorId,
      body: params.body,
      attachments: { connect: attachmentIds.map((id) => ({ id })) },
    },
    include: messageInclude,
  });

  if (scope.channelId) {
    await processMentionsForChannelMessage(io, {
      messageId: message.id,
      body: params.body,
      authorId: params.authorId,
      channelId: scope.channelId,
      workspaceId: scope.workspaceId,
    });
    if (parentMessage) {
      await notifyThreadReply(io, {
        replyMessageId: message.id,
        parentMessageId: parentMessage.id,
        parentAuthorId: parentMessage.authorId,
        replierId: params.authorId,
        workspaceId: scope.workspaceId,
        channelId: scope.channelId,
      });
    }
  } else if (scope.dmId) {
    const dm = await prisma.dMThread.findUniqueOrThrow({ where: { id: scope.dmId } });
    const recipientId = dm.userAId === params.authorId ? dm.userBId : dm.userAId;
    await notifyDMRecipient(io, {
      messageId: message.id,
      authorId: params.authorId,
      recipientId,
      dmId: scope.dmId,
      workspaceId: scope.workspaceId,
    });
  }

  const refreshed = await prisma.message.findUniqueOrThrow({ where: { id: message.id }, include: messageInclude });
  const dto = toMessageDTO(refreshed, params.authorId);
  io.to(roomFor(scope)).emit(SocketEvents.MessageCreated, dto);
  return dto;
}

export async function updateMessage(
  io: SocketIOServer,
  scope: Scope,
  params: { messageId: string; authorId: string; body: string },
): Promise<MessageDTO> {
  const message = await prisma.message.findUnique({ where: { id: params.messageId } });
  if (!message || message.deletedAt) throw NotFound("メッセージが見つかりません。");
  assertMessageInScope(message, scope);
  if (message.authorId !== params.authorId) throw Forbidden("自分のメッセージのみ編集できます。");

  const updated = await prisma.message.update({
    where: { id: params.messageId },
    data: { body: params.body, editedAt: new Date() },
    include: messageInclude,
  });
  const dto = toMessageDTO(updated, params.authorId);
  io.to(roomFor(scope)).emit(SocketEvents.MessageUpdated, dto);
  return dto;
}

export async function deleteMessage(
  io: SocketIOServer,
  scope: Scope,
  params: { messageId: string; requesterId: string },
): Promise<void> {
  const message = await prisma.message.findUnique({ where: { id: params.messageId } });
  if (!message || message.deletedAt) throw NotFound("メッセージが見つかりません。");
  assertMessageInScope(message, scope);
  if (message.authorId !== params.requesterId) throw Forbidden("自分のメッセージのみ削除できます。");

  await prisma.message.update({ where: { id: params.messageId }, data: { deletedAt: new Date() } });
  io.to(roomFor(scope)).emit(SocketEvents.MessageDeleted, {
    messageId: params.messageId,
    channelId: scope.channelId ?? null,
    dmId: scope.dmId ?? null,
  });
}

export async function toggleReaction(
  io: SocketIOServer,
  scope: Scope,
  params: { messageId: string; userId: string; emoji: string },
): Promise<MessageDTO> {
  const message = await prisma.message.findUnique({ where: { id: params.messageId } });
  if (!message || message.deletedAt) throw NotFound("メッセージが見つかりません。");
  assertMessageInScope(message, scope);

  const existing = await prisma.reaction.findUnique({
    where: {
      messageId_userId_emoji: { messageId: params.messageId, userId: params.userId, emoji: params.emoji },
    },
  });

  if (existing) {
    await prisma.reaction.delete({ where: { id: existing.id } });
  } else {
    await prisma.reaction.create({
      data: { messageId: params.messageId, userId: params.userId, emoji: params.emoji },
    });
  }

  const refreshed = await prisma.message.findUniqueOrThrow({ where: { id: params.messageId }, include: messageInclude });
  const dto = toMessageDTO(refreshed, params.userId);
  io.to(roomFor(scope)).emit(SocketEvents.ReactionUpdated, dto);
  return dto;
}
