import { Hono } from "hono";
import { createChannelSchema, SocketEvents } from "@chatspace/shared";
import type { ChannelDTO } from "@chatspace/shared";
import { prisma } from "../db.js";
import { requireAuth } from "../auth/middleware.js";
import { requireChannelMember, requireWorkspaceMember, requireWorkspaceOwner } from "../lib/authz.js";
import { BadRequest, Conflict, Forbidden, NotFound } from "../lib/errors.js";
import { channelRoom, notifyChannelInvite, userRoom, workspaceRoom } from "../lib/notify.js";
import { toUserDTO } from "../lib/dto.js";
import { routeParams } from "../lib/http.js";
import type { AppEnv } from "../types.js";

export const channelRoutes = new Hono<AppEnv>();
channelRoutes.use("*", requireAuth);

async function toChannelDTO(
  channel: { id: string; workspaceId: string; name: string; type: string; createdAt: Date },
  userId: string,
): Promise<ChannelDTO> {
  const membership = await prisma.channelMember.findUnique({
    where: { channelId_userId: { channelId: channel.id, userId } },
  });
  let unreadCount = 0;
  if (membership) {
    unreadCount = await prisma.message.count({
      where: {
        channelId: channel.id,
        createdAt: { gt: membership.lastReadAt },
        authorId: { not: userId },
        deletedAt: null,
      },
    });
  }
  return {
    id: channel.id,
    workspaceId: channel.workspaceId,
    name: channel.name,
    type: channel.type as ChannelDTO["type"],
    createdAt: channel.createdAt.toISOString(),
    isMember: Boolean(membership),
    unreadCount,
    lastReadAt: membership ? membership.lastReadAt.toISOString() : null,
  };
}

// オーナー限定: チャンネル作成。プライベートの場合は指定メンバーのみ参加できる。
channelRoutes.post("/", async (c) => {
  const { workspaceId } = routeParams<{ workspaceId: string }>(c);
  const userId = c.get("userId");
  await requireWorkspaceOwner(workspaceId, userId);

  const body = await c.req.json().catch(() => null);
  const parsed = createChannelSchema.safeParse(body);
  if (!parsed.success) {
    throw BadRequest(parsed.error.issues[0]?.message ?? "入力内容を確認してください。");
  }
  const { name, type, memberUserIds = [] } = parsed.data;

  const existing = await prisma.channel.findUnique({ where: { workspaceId_name: { workspaceId, name } } });
  if (existing) throw Conflict("同名のチャンネルが既に存在します。");

  let initialMemberIds = [userId];
  if (type === "PRIVATE" && memberUserIds.length > 0) {
    // memberUserIds はログインID(ハンドル)の配列。WorkspaceMember.userId が指す
    // 内部ユーザーID(User.id)とは別物なので、User テーブルで解決してから絞り込む。
    const users = await prisma.user.findMany({ where: { userId: { in: memberUserIds } } });
    const validMembers = await prisma.workspaceMember.findMany({
      where: { workspaceId, userId: { in: users.map((u) => u.id) } },
    });
    initialMemberIds = Array.from(new Set([userId, ...validMembers.map((m) => m.userId)]));
  }

  const channel = await prisma.channel.create({
    data: {
      workspaceId,
      name,
      type,
      members: { create: initialMemberIds.map((id) => ({ userId: id })) },
    },
  });

  const io = c.get("io");
  io.to(workspaceRoom(workspaceId)).emit(SocketEvents.ChannelCreated, await toChannelDTO(channel, userId));

  return c.json({ channel: await toChannelDTO(channel, userId) }, 201);
});

channelRoutes.get("/", async (c) => {
  const { workspaceId } = routeParams<{ workspaceId: string }>(c);
  const userId = c.get("userId");
  await requireWorkspaceMember(workspaceId, userId);

  const myMemberships = await prisma.channelMember.findMany({
    where: { userId, channel: { workspaceId } },
    select: { channelId: true },
  });
  const myChannelIds = myMemberships.map((m) => m.channelId);

  const channels = await prisma.channel.findMany({
    where: {
      workspaceId,
      OR: [{ type: "PUBLIC" }, { id: { in: myChannelIds } }],
    },
    orderBy: { createdAt: "asc" },
  });

  const dto = await Promise.all(channels.map((ch) => toChannelDTO(ch, userId)));
  return c.json({ channels: dto });
});

channelRoutes.post("/:channelId/read", async (c) => {
  const { channelId } = routeParams<{ workspaceId: string; channelId: string }>(c);
  const userId = c.get("userId");
  const { membership } = await requireChannelMember(channelId, userId);
  await prisma.channelMember.update({
    where: { id: membership.id },
    data: { lastReadAt: new Date() },
  });
  return c.body(null, 204);
});

// パブリックチャンネルへの自主参加
channelRoutes.post("/:channelId/join", async (c) => {
  const { workspaceId, channelId } = routeParams<{ workspaceId: string; channelId: string }>(c);
  const userId = c.get("userId");
  await requireWorkspaceMember(workspaceId, userId);

  const channel = await prisma.channel.findUnique({ where: { id: channelId } });
  if (!channel || channel.workspaceId !== workspaceId) throw NotFound("チャンネルが見つかりません。");
  if (channel.type !== "PUBLIC") {
    throw Forbidden("プライベートチャンネルは招待された場合のみ参加できます。");
  }

  await prisma.channelMember.upsert({
    where: { channelId_userId: { channelId, userId } },
    create: { channelId, userId },
    update: {},
  });

  return c.json({ channel: await toChannelDTO(channel, userId) });
});

// オーナー限定: ワークスペースメンバーをチャンネルに招待する
// (CLAUDE.md「オーナー限定操作(キック、チャンネル追加・削除、招待)」に合わせ、
//  チャンネル単位の招待もワークスペースオーナーのみ実行できる)
channelRoutes.post("/:channelId/members", async (c) => {
  const { workspaceId, channelId } = routeParams<{ workspaceId: string; channelId: string }>(c);
  const userId = c.get("userId");
  await requireWorkspaceOwner(workspaceId, userId);

  const channel = await prisma.channel.findUnique({ where: { id: channelId } });
  if (!channel || channel.workspaceId !== workspaceId) {
    throw NotFound("チャンネルが見つかりません。");
  }

  const body = await c.req.json().catch(() => null);
  const targetHandle = typeof (body as { userId?: unknown })?.userId === "string" ? (body as { userId: string }).userId : null;
  if (!targetHandle) throw BadRequest("userId を指定してください。");

  // リクエストの userId はログインID(ハンドル)。ChannelMember/WorkspaceMember.userId が
  // 指す内部ユーザーID(User.id)とは別物なので、必ず User テーブルで解決してから使う。
  const targetUser = await prisma.user.findUnique({ where: { userId: targetHandle } });
  if (!targetUser) throw NotFound("指定されたユーザーが見つかりません。");

  const targetMembership = await prisma.workspaceMember.findUnique({
    where: { workspaceId_userId: { workspaceId, userId: targetUser.id } },
  });
  if (!targetMembership) {
    throw NotFound("指定されたユーザーはこのワークスペースのメンバーではありません。");
  }

  await prisma.channelMember.upsert({
    where: { channelId_userId: { channelId, userId: targetUser.id } },
    create: { channelId, userId: targetUser.id },
    update: {},
  });

  const io = c.get("io");
  await notifyChannelInvite(io, {
    userId: targetUser.id,
    workspaceId,
    channelId,
    fromUserId: userId,
    channelName: channel.name,
  });

  return c.body(null, 204);
});

// チャンネル退出。workspace オーナーは他メンバーを強制退出させることもできる。
channelRoutes.delete("/:channelId/members/:targetUserId", async (c) => {
  const { workspaceId, channelId, targetUserId } = routeParams<{
    workspaceId: string;
    channelId: string;
    targetUserId: string;
  }>(c);
  const userId = c.get("userId");
  await requireChannelMember(channelId, userId);

  if (targetUserId !== userId) {
    await requireWorkspaceOwner(workspaceId, userId);
  }

  await prisma.channelMember.deleteMany({ where: { channelId, userId: targetUserId } });

  const io = c.get("io");
  io.to(channelRoom(channelId)).emit(SocketEvents.ChannelMemberKicked, { channelId, userId: targetUserId });
  // 退出/キックされたユーザーのソケットを部屋から強制退出させ、以後のリアルタイム配信を止める
  io.in(userRoom(targetUserId)).socketsLeave(channelRoom(channelId));

  return c.body(null, 204);
});

channelRoutes.get("/:channelId/members", async (c) => {
  const { channelId } = c.req.param();
  await requireChannelMember(channelId, c.get("userId"));
  const members = await prisma.channelMember.findMany({
    where: { channelId },
    include: { user: true },
    orderBy: { joinedAt: "asc" },
  });
  return c.json({
    members: members.map((m) => ({ user: toUserDTO(m.user), joinedAt: m.joinedAt.toISOString() })),
  });
});

// オーナー限定: チャンネル削除
channelRoutes.delete("/:channelId", async (c) => {
  const { workspaceId, channelId } = routeParams<{ workspaceId: string; channelId: string }>(c);
  const userId = c.get("userId");
  await requireWorkspaceOwner(workspaceId, userId);

  const channel = await prisma.channel.findUnique({ where: { id: channelId } });
  if (!channel || channel.workspaceId !== workspaceId) throw NotFound("チャンネルが見つかりません。");

  await prisma.channel.delete({ where: { id: channelId } });

  const io = c.get("io");
  io.to(workspaceRoom(workspaceId)).emit(SocketEvents.ChannelDeleted, { workspaceId, channelId });

  return c.body(null, 204);
});
