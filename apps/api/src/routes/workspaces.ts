import { Hono } from "hono";
import type { Server as SocketIOServer } from "socket.io";
import { createWorkspaceSchema, inviteToWorkspaceSchema, SocketEvents } from "@chatspace/shared";
import type { WorkspaceDTO, WorkspaceMemberDTO } from "@chatspace/shared";
import { prisma } from "../db.js";
import { requireAuth } from "../auth/middleware.js";
import { requireWorkspaceMember, requireWorkspaceOwner } from "../lib/authz.js";
import { toUserDTO } from "../lib/dto.js";
import { BadRequest, Conflict, NotFound } from "../lib/errors.js";
import { channelRoom, notifyWorkspaceInvite, userRoom, workspaceRoom } from "../lib/notify.js";
import { getOnlineUserIds } from "../lib/presence.js";
import type { AppEnv } from "../types.js";

export const workspaceRoutes = new Hono<AppEnv>();
workspaceRoutes.use("*", requireAuth);

workspaceRoutes.post("/", async (c) => {
  const body = await c.req.json().catch(() => null);
  const parsed = createWorkspaceSchema.safeParse(body);
  if (!parsed.success) {
    throw BadRequest(parsed.error.issues[0]?.message ?? "入力内容を確認してください。");
  }
  const userId = c.get("userId");

  const workspace = await prisma.workspace.create({
    data: {
      name: parsed.data.name,
      ownerId: userId,
      members: { create: { userId, role: "OWNER" } },
    },
  });

  const dto: WorkspaceDTO = {
    id: workspace.id,
    name: workspace.name,
    ownerId: workspace.ownerId,
    createdAt: workspace.createdAt.toISOString(),
    myRole: "OWNER",
  };
  return c.json({ workspace: dto }, 201);
});

workspaceRoutes.get("/", async (c) => {
  const userId = c.get("userId");
  const memberships = await prisma.workspaceMember.findMany({
    where: { userId },
    include: { workspace: true },
    orderBy: { joinedAt: "asc" },
  });
  const workspaces: WorkspaceDTO[] = memberships.map((m) => ({
    id: m.workspace.id,
    name: m.workspace.name,
    ownerId: m.workspace.ownerId,
    createdAt: m.workspace.createdAt.toISOString(),
    myRole: m.role === "OWNER" ? "OWNER" : "MEMBER",
  }));
  return c.json({ workspaces });
});

workspaceRoutes.get("/:workspaceId/members", async (c) => {
  const { workspaceId } = c.req.param();
  await requireWorkspaceMember(workspaceId, c.get("userId"));

  const members = await prisma.workspaceMember.findMany({
    where: { workspaceId },
    include: { user: true },
    orderBy: { joinedAt: "asc" },
  });
  const dto: WorkspaceMemberDTO[] = members.map((m) => ({
    user: toUserDTO(m.user),
    role: m.role === "OWNER" ? "OWNER" : "MEMBER",
    joinedAt: m.joinedAt.toISOString(),
  }));
  return c.json({ members: dto });
});

// 現在オンラインのメンバーのユーザーID一覧(参加直後のスナップショット用。
// それ以降の変化は Socket.IO の presence:updated イベントで受け取る)
workspaceRoutes.get("/:workspaceId/presence", async (c) => {
  const { workspaceId } = c.req.param();
  await requireWorkspaceMember(workspaceId, c.get("userId"));

  const members = await prisma.workspaceMember.findMany({ where: { workspaceId }, select: { userId: true } });
  const onlineUserIds = getOnlineUserIds(members.map((m) => m.userId));
  return c.json({ onlineUserIds });
});

// オーナー限定: ユーザーIDを指定してワークスペースに招待(即時参加)する
workspaceRoutes.post("/:workspaceId/invite", async (c) => {
  const { workspaceId } = c.req.param();
  const ownerId = c.get("userId");
  await requireWorkspaceOwner(workspaceId, ownerId);
  const workspace = await prisma.workspace.findUniqueOrThrow({ where: { id: workspaceId } });

  const body = await c.req.json().catch(() => null);
  const parsed = inviteToWorkspaceSchema.safeParse(body);
  if (!parsed.success) {
    throw BadRequest(parsed.error.issues[0]?.message ?? "入力内容を確認してください。");
  }

  const invitee = await prisma.user.findUnique({ where: { userId: parsed.data.userId } });
  if (!invitee) {
    throw NotFound("指定されたユーザーIDのユーザーが見つかりません。");
  }

  const existing = await prisma.workspaceMember.findUnique({
    where: { workspaceId_userId: { workspaceId, userId: invitee.id } },
  });
  if (existing) {
    throw Conflict("既にこのワークスペースのメンバーです。");
  }

  await prisma.workspaceMember.create({
    data: { workspaceId, userId: invitee.id, role: "MEMBER" },
  });

  const io = c.get("io");
  await notifyWorkspaceInvite(io, {
    userId: invitee.id,
    workspaceId,
    fromUserId: ownerId,
    workspaceName: workspace.name,
  });
  return c.json({ member: { user: toUserDTO(invitee), role: "MEMBER", joinedAt: new Date().toISOString() } }, 201);
});

// ワークスペースからメンバーを退出させる共通処理(オーナーによるキック・自主退出の両方で使う)
async function removeMemberFromWorkspace(io: SocketIOServer, workspaceId: string, targetUserId: string) {
  // 部屋から強制退出させる対象チャンネルを、削除前に控えておく
  const kickedChannelIds = (
    await prisma.channelMember.findMany({
      where: { userId: targetUserId, channel: { workspaceId } },
      select: { channelId: true },
    })
  ).map((m) => m.channelId);

  await prisma.$transaction([
    prisma.channelMember.deleteMany({
      where: { userId: targetUserId, channel: { workspaceId } },
    }),
    prisma.workspaceMember.delete({
      where: { workspaceId_userId: { workspaceId, userId: targetUserId } },
    }),
  ]);

  io.to(workspaceRoom(workspaceId)).emit(SocketEvents.WorkspaceMemberKicked, {
    workspaceId,
    userId: targetUserId,
  });

  // 権限を失ったユーザーのソケットを部屋から強制的に退出させる。
  // これをしないと、ブラウザを再読み込みするまでチャンネルの新着を受信し続けてしまう。
  const targetRoom = userRoom(targetUserId);
  io.in(targetRoom).socketsLeave(workspaceRoom(workspaceId));
  if (kickedChannelIds.length > 0) {
    io.in(targetRoom).socketsLeave(kickedChannelIds.map(channelRoom));
  }
}

// オーナー限定: メンバーをワークスペースから強制退出させる
workspaceRoutes.post("/:workspaceId/kick", async (c) => {
  const { workspaceId } = c.req.param();
  const ownerId = c.get("userId");
  await requireWorkspaceOwner(workspaceId, ownerId);

  const body = await c.req.json().catch(() => ({}));
  const targetUserId = typeof body?.userId === "string" ? body.userId : null;
  if (!targetUserId) throw BadRequest("userId を指定してください。");
  if (targetUserId === ownerId) throw BadRequest("オーナー自身をキックすることはできません。");

  const target = await prisma.workspaceMember.findUnique({
    where: { workspaceId_userId: { workspaceId, userId: targetUserId } },
  });
  if (!target) throw NotFound("指定されたメンバーが見つかりません。");

  await removeMemberFromWorkspace(c.get("io"), workspaceId, targetUserId);
  return c.body(null, 204);
});

// 自分自身がワークスペースから退出する。オーナーはオーナー権限の移譲・削除の仕組みが
// 無いため退出できない(削除するにはワークスペースごと削除する運用を想定)。
workspaceRoutes.post("/:workspaceId/leave", async (c) => {
  const { workspaceId } = c.req.param();
  const userId = c.get("userId");
  const membership = await requireWorkspaceMember(workspaceId, userId);
  if (membership.role === "OWNER") {
    throw BadRequest("オーナーはワークスペースから退出できません。");
  }

  await removeMemberFromWorkspace(c.get("io"), workspaceId, userId);
  return c.body(null, 204);
});
