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

/**
 * workspaceId を渡した場合、チャンネルの実際の所属ワークスペースと一致するかも検証する。
 * 現状これを省いても権限外アクセスには繋がらない(チャンネル単体のメンバーシップで
 * 判定済みのため)が、URLの workspaceId とチャンネルの所属が食い違うリクエストを
 * 素通りさせないための一貫性チェック(将来の実装ミスに対する防御層)として設ける。
 */
export async function requireChannelMember(channelId: string, userId: string, workspaceId?: string) {
  const channel = await prisma.channel.findUnique({ where: { id: channelId } });
  if (!channel || (workspaceId && channel.workspaceId !== workspaceId)) {
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

/** workspaceId を渡した場合、DMの実際の所属ワークスペースと一致するかも検証する(上記と同趣旨) */
export async function requireDMAccess(dmId: string, userId: string, workspaceId?: string) {
  const dm = await prisma.dMThread.findUnique({ where: { id: dmId } });
  if (
    !dm ||
    (dm.userAId !== userId && dm.userBId !== userId) ||
    (workspaceId && dm.workspaceId !== workspaceId)
  ) {
    throw NotFound("DMが見つかりません。");
  }
  return dm;
}

export function otherDMUserId(dm: { userAId: string; userBId: string }, userId: string) {
  return dm.userAId === userId ? dm.userBId : dm.userAId;
}
