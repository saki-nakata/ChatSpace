import { Hono } from "hono";
import { SocketEvents, type DMThreadDTO } from "@chatspace/shared";
import { prisma } from "../db.js";
import { requireAuth } from "../auth/middleware.js";
import { otherDMUserId, requireDMAccess, requireWorkspaceMember } from "../lib/authz.js";
import { toMessageDTO, toUserDTO } from "../lib/dto.js";
import { BadRequest, NotFound } from "../lib/errors.js";
import { routeParams } from "../lib/http.js";
import { userRoom } from "../lib/notify.js";
import type { AppEnv } from "../types.js";

export const dmRoutes = new Hono<AppEnv>();
dmRoutes.use("*", requireAuth);

const dmMessageInclude = {
  author: true,
  attachments: true,
  reactions: true,
  mentions: { select: { mentionedUserId: true } as const },
  _count: { select: { replies: true } },
} as const;

async function toDMThreadDTO(
  dm: { id: string; workspaceId: string; userAId: string; userBId: string; lastReadAtA: Date; lastReadAtB: Date },
  viewerId: string,
): Promise<DMThreadDTO> {
  const otherId = otherDMUserId(dm, viewerId);
  const otherUser = await prisma.user.findUniqueOrThrow({ where: { id: otherId } });
  const myLastReadAt = dm.userAId === viewerId ? dm.lastReadAtA : dm.lastReadAtB;

  const [unreadCount, lastMessageRow] = await Promise.all([
    prisma.message.count({
      where: { dmId: dm.id, createdAt: { gt: myLastReadAt }, authorId: { not: viewerId }, deletedAt: null },
    }),
    prisma.message.findFirst({
      where: { dmId: dm.id, parentId: null },
      orderBy: { createdAt: "desc" },
      include: dmMessageInclude,
    }),
  ]);

  return {
    id: dm.id,
    workspaceId: dm.workspaceId,
    otherUser: toUserDTO(otherUser),
    unreadCount,
    lastReadAt: myLastReadAt.toISOString(),
    lastMessage: lastMessageRow ? toMessageDTO(lastMessageRow, viewerId) : null,
  };
}

dmRoutes.get("/", async (c) => {
  const { workspaceId } = routeParams<{ workspaceId: string }>(c);
  const userId = c.get("userId");
  await requireWorkspaceMember(workspaceId, userId);

  const threads = await prisma.dMThread.findMany({
    where: { workspaceId, OR: [{ userAId: userId }, { userBId: userId }] },
    orderBy: { createdAt: "desc" },
  });
  const dto = await Promise.all(threads.map((t) => toDMThreadDTO(t, userId)));
  return c.json({ dmThreads: dto });
});

// 指定したユーザーとの DM スレッドを取得、なければ作成する
dmRoutes.post("/", async (c) => {
  const { workspaceId } = routeParams<{ workspaceId: string }>(c);
  const userId = c.get("userId");
  await requireWorkspaceMember(workspaceId, userId);

  const body = await c.req.json().catch(() => null);
  const targetHandle = typeof (body as { userId?: unknown })?.userId === "string" ? (body as { userId: string }).userId : null;
  if (!targetHandle) throw BadRequest("userId を指定してください。");

  // リクエストの userId はログインID(ハンドル)であり、WorkspaceMember.userId が指す
  // 内部ユーザーID(User.id)とは別物なので、必ず User テーブルで解決してから使う。
  const targetUser = await prisma.user.findUnique({ where: { userId: targetHandle } });
  if (!targetUser) throw NotFound("指定されたユーザーが見つかりません。");
  if (targetUser.id === userId) throw BadRequest("自分自身とはDMできません。");

  const targetMembership = await prisma.workspaceMember.findUnique({
    where: { workspaceId_userId: { workspaceId, userId: targetUser.id } },
  });
  if (!targetMembership) throw NotFound("指定されたユーザーはこのワークスペースのメンバーではありません。");

  const [userAId, userBId] = [userId, targetUser.id].sort();
  const isNew = !(await prisma.dMThread.findUnique({
    where: { workspaceId_userAId_userBId: { workspaceId, userAId, userBId } },
  }));
  const dm = await prisma.dMThread.upsert({
    where: { workspaceId_userAId_userBId: { workspaceId, userAId, userBId } },
    create: { workspaceId, userAId, userBId },
    update: {},
  });

  // 相手側のクライアントにも新規DMスレッドを通知し、Socket.IOのdm部屋に参加させる。
  // これをしないと、相手が届いたメッセージをリアルタイムで受け取れない。
  if (isNew) {
    const io = c.get("io");
    io.to(userRoom(targetUser.id)).emit(SocketEvents.DMThreadCreated, await toDMThreadDTO(dm, targetUser.id));
  }

  return c.json({ dmThread: await toDMThreadDTO(dm, userId) }, 201);
});

dmRoutes.post("/:dmId/read", async (c) => {
  const { dmId } = c.req.param();
  const userId = c.get("userId");
  const dm = await requireDMAccess(dmId, userId);

  await prisma.dMThread.update({
    where: { id: dmId },
    data: dm.userAId === userId ? { lastReadAtA: new Date() } : { lastReadAtB: new Date() },
  });
  return c.body(null, 204);
});
