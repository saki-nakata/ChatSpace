import { nanoid } from "nanoid";
import type { Server as SocketIOServer } from "socket.io";
import { prisma } from "../src/db.js";
import { hashPassword } from "../src/auth/password.js";
import { signAuthToken, AUTH_COOKIE_NAME } from "../src/auth/jwt.js";

/**
 * ルートハンドラは c.get("io") を前提にしているため、テストでは実際に配信はしないが
 * 使用されているメソッド(to().emit() / in().socketsLeave())だけを備えたスタブを渡す。
 */
export function createFakeIo(): SocketIOServer {
  const chainable = {
    emit: () => chainable,
    socketsLeave: () => chainable,
  };
  const fake = {
    to: () => chainable,
    in: () => chainable,
  };
  return fake as unknown as SocketIOServer;
}

export async function createTestUser(prefix: string) {
  const userId = `${prefix}_${nanoid(8)}`;
  const passwordHash = await hashPassword("password123");
  const user = await prisma.user.create({
    data: { userId, passwordHash, displayName: `${prefix} display` },
  });
  return user;
}

export async function authCookieFor(userId: string): Promise<string> {
  const token = await signAuthToken(userId);
  return `${AUTH_COOKIE_NAME}=${token}`;
}

export async function createWorkspaceWithOwner(owner: { id: string }) {
  return prisma.workspace.create({
    data: {
      name: `workspace_${nanoid(6)}`,
      ownerId: owner.id,
      members: { create: { userId: owner.id, role: "OWNER" } },
    },
  });
}

export async function addWorkspaceMember(workspaceId: string, userId: string) {
  return prisma.workspaceMember.create({ data: { workspaceId, userId, role: "MEMBER" } });
}

export async function createChannel(params: {
  workspaceId: string;
  type: "PUBLIC" | "PRIVATE";
  memberIds: string[];
}) {
  return prisma.channel.create({
    data: {
      workspaceId: params.workspaceId,
      name: `channel_${nanoid(6)}`,
      type: params.type,
      members: { create: params.memberIds.map((userId) => ({ userId })) },
    },
  });
}
