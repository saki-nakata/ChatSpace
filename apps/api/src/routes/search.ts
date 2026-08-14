import { Hono } from "hono";
import { searchQuerySchema } from "@chatspace/shared";
import { prisma } from "../db.js";
import { requireAuth } from "../auth/middleware.js";
import { requireWorkspaceMember } from "../lib/authz.js";
import { toMessageDTO } from "../lib/dto.js";
import { BadRequest } from "../lib/errors.js";
import { routeParams } from "../lib/http.js";
import type { AppEnv } from "../types.js";

export const searchRoutes = new Hono<AppEnv>();
searchRoutes.use("*", requireAuth);

const messageInclude = {
  author: true,
  attachments: true,
  reactions: true,
  mentions: { select: { mentionedUserId: true } as const },
  _count: { select: { replies: true } },
} as const;

const RESULT_LIMIT = 50;

// メッセージ検索は「自分が参加しているチャンネル・DM」の範囲でしか検索できない。
// 権限外のプライベートチャンネル・DMの内容が検索結果に漏れないことが最重要。
searchRoutes.get("/", async (c) => {
  const { workspaceId } = routeParams<{ workspaceId: string }>(c);
  const userId = c.get("userId");
  await requireWorkspaceMember(workspaceId, userId);

  const parsed = searchQuerySchema.safeParse({
    q: c.req.query("q"),
    channelId: c.req.query("channelId") || undefined,
  });
  if (!parsed.success) {
    throw BadRequest(parsed.error.issues[0]?.message ?? "検索キーワードを入力してください。");
  }
  const { q, channelId } = parsed.data;

  if (channelId) {
    // 指定チャンネルが本当に自分の参加チャンネルかを確認する(他チャンネルの内容漏洩防止)
    const membership = await prisma.channelMember.findUnique({
      where: { channelId_userId: { channelId, userId } },
    });
    if (!membership) {
      return c.json({ messages: [] });
    }
  }

  const [myChannelIds, myDMIds] = await Promise.all([
    prisma.channelMember
      .findMany({ where: { userId, channel: { workspaceId } }, select: { channelId: true } })
      .then((rows) => rows.map((r) => r.channelId)),
    prisma.dMThread
      .findMany({ where: { workspaceId, OR: [{ userAId: userId }, { userBId: userId }] }, select: { id: true } })
      .then((rows) => rows.map((r) => r.id)),
  ]);

  const scopeConditions = channelId
    ? [{ channelId }]
    : [
        ...(myChannelIds.length > 0 ? [{ channelId: { in: myChannelIds } }] : []),
        ...(myDMIds.length > 0 ? [{ dmId: { in: myDMIds } }] : []),
      ];

  if (scopeConditions.length === 0) {
    return c.json({ messages: [] });
  }

  // キーワード一致は DB レベルの contains で絞り込む(スコープ内メッセージ全件が対象になり、
  // 「直近N件だけ」といった暗黙の取りこぼしが起きないようにする)。
  // 注意: SQLite の Prisma は contains の大文字小文字無視(mode: "insensitive")に未対応のため、
  // 大文字小文字は区別される。全文検索エンジン未導入というプロトタイプ相応の制約として許容する。
  const matched = await prisma.message.findMany({
    where: { deletedAt: null, OR: scopeConditions, body: { contains: q } },
    include: messageInclude,
    orderBy: { createdAt: "desc" },
    take: RESULT_LIMIT,
  });

  return c.json({ messages: matched.map((m) => toMessageDTO(m, userId)) });
});
