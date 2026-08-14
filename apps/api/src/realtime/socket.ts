import type { Server as HttpServer } from "node:http";
import { Server as SocketIOServer, type Socket } from "socket.io";
import { SocketEvents } from "@chatspace/shared";
import { AUTH_COOKIE_NAME, verifyAuthToken } from "../auth/jwt.js";
import { prisma } from "../db.js";
import { env } from "../env.js";
import { channelRoom, dmRoom, userRoom, workspaceRoom } from "../lib/notify.js";
import { markOffline, markOnline } from "../lib/presence.js";

function parseCookieHeader(header: string | undefined): Record<string, string> {
  const result: Record<string, string> = {};
  if (!header) return result;
  for (const part of header.split(";")) {
    const idx = part.indexOf("=");
    if (idx === -1) continue;
    const key = part.slice(0, idx).trim();
    const value = part.slice(idx + 1).trim();
    if (key) result[key] = decodeURIComponent(value);
  }
  return result;
}

interface SocketData {
  userId: string;
  displayName: string;
}

async function broadcastPresence(io: SocketIOServer, userId: string, online: boolean): Promise<void> {
  const memberships = await prisma.workspaceMember.findMany({ where: { userId }, select: { workspaceId: true } });
  for (const { workspaceId } of memberships) {
    io.to(workspaceRoom(workspaceId)).emit(SocketEvents.PresenceUpdated, { userId, online });
  }
}

export function createSocketServer(httpServer: HttpServer): SocketIOServer {
  const io = new SocketIOServer(httpServer, {
    cors: {
      origin: env.webOrigin,
      credentials: true,
    },
  });

  // Cookie の JWT を検証できないソケットは接続を許可しない
  io.use(async (socket, next) => {
    const cookies = parseCookieHeader(socket.request.headers.cookie);
    const token = cookies[AUTH_COOKIE_NAME];
    if (!token) return next(new Error("認証が必要です。"));
    const payload = await verifyAuthToken(token);
    if (!payload) return next(new Error("認証が無効です。"));
    const user = await prisma.user.findUnique({ where: { id: payload.sub } });
    if (!user) return next(new Error("認証が無効です。"));
    (socket.data as SocketData).userId = user.id;
    (socket.data as SocketData).displayName = user.displayName;
    next();
  });

  io.on("connection", (socket: Socket) => {
    const userId = (socket.data as SocketData).userId;
    const displayName = (socket.data as SocketData).displayName;
    socket.join(userRoom(userId));

    if (markOnline(userId)) {
      broadcastPresence(io, userId, true).catch(() => {});
    }

    socket.on("disconnect", () => {
      if (markOffline(userId)) {
        broadcastPresence(io, userId, false).catch(() => {});
      }
    });

    socket.on(SocketEvents.JoinWorkspace, async (workspaceId: unknown) => {
      if (typeof workspaceId !== "string") return;
      const membership = await prisma.workspaceMember.findUnique({
        where: { workspaceId_userId: { workspaceId, userId } },
      });
      if (membership) socket.join(workspaceRoom(workspaceId));
    });

    socket.on(SocketEvents.JoinChannel, async (channelId: unknown) => {
      if (typeof channelId !== "string") return;
      const membership = await prisma.channelMember.findUnique({
        where: { channelId_userId: { channelId, userId } },
      });
      if (membership) socket.join(channelRoom(channelId));
    });

    socket.on(SocketEvents.LeaveChannel, (channelId: unknown) => {
      if (typeof channelId !== "string") return;
      socket.leave(channelRoom(channelId));
    });

    socket.on(SocketEvents.JoinDM, async (dmId: unknown) => {
      if (typeof dmId !== "string") return;
      const dm = await prisma.dMThread.findUnique({ where: { id: dmId } });
      if (dm && (dm.userAId === userId || dm.userBId === userId)) {
        socket.join(dmRoom(dmId));
      }
    });

    socket.on(SocketEvents.Typing, async (payload: unknown) => {
      const data = payload as { channelId?: string; dmId?: string } | null;
      if (!data) return;

      if (data.channelId) {
        const membership = await prisma.channelMember.findUnique({
          where: { channelId_userId: { channelId: data.channelId, userId } },
        });
        if (!membership) return;
        socket.to(channelRoom(data.channelId)).emit(SocketEvents.UserTyping, {
          userId,
          displayName,
          channelId: data.channelId,
          dmId: null,
        });
      } else if (data.dmId) {
        const dm = await prisma.dMThread.findUnique({ where: { id: data.dmId } });
        if (!dm || (dm.userAId !== userId && dm.userBId !== userId)) return;
        socket.to(dmRoom(data.dmId)).emit(SocketEvents.UserTyping, {
          userId,
          displayName,
          channelId: null,
          dmId: data.dmId,
        });
      }
    });
  });

  return io;
}
