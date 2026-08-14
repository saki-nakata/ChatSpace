import { prisma } from "../db.js";
import { Forbidden, NotFound } from "./errors.js";

/**
 * 認可ヘルパー群。
 *
 * 方針:
 * - ワークスペースオーナー限定操作（招待・キック・チャンネル追加削除）は
 *   requireWorkspaceOwner で弾く。
 * - プライベートチャンネル・DM・スレッドは「参加していないユーザーに存在すら
 *   知られてはならない」ため、権限がない場合は 403 ではなく 404 を返す。
 */

export async function requireWorkspaceMember(workspaceId: string, userId: string) {
  const membership = await prisma.workspaceMember.findUnique({
    where: { workspaceId_userId: { workspaceId, userId } },
  });
  if (!membership) {
    throw NotFound("ワークスペースが見つかりません。");
  }
  return membership;
}

export async function requireWorkspaceOwner(workspaceId: string, userId: string) {
  const membership = await requireWorkspaceMember(workspaceId, userId);
  if (membership.role !== "OWNER") {
    throw Forbidden("この操作はワークスペースのオーナーのみ実行できます。");
  }
  return membership;
}

export async function requireChannelMember(channelId: string, userId: string) {
  const channel = await prisma.channel.findUnique({ where: { id: channelId } });
  if (!channel) {
    throw NotFound("チャンネルが見つかりません。");
  }
  const membership = await prisma.channelMember.findUnique({
    where: { channelId_userId: { channelId, userId } },
  });
  if (!membership) {
    // プライベートチャンネルはもちろん、パブリックチャンネルも「参加している人だけが読める」
    // 方針のため、非参加者には存在を知らせず 404 を返す。
    throw NotFound("チャンネルが見つかりません。");
  }
  return { channel, membership };
}

export async function requireDMAccess(dmId: string, userId: string) {
  const dm = await prisma.dMThread.findUnique({ where: { id: dmId } });
  if (!dm || (dm.userAId !== userId && dm.userBId !== userId)) {
    throw NotFound("DMが見つかりません。");
  }
  return dm;
}

export function otherDMUserId(dm: { userAId: string; userBId: string }, userId: string) {
  return dm.userAId === userId ? dm.userBId : dm.userAId;
}
