import { PrismaClient } from "@prisma/client";
import bcrypt from "bcryptjs";

const prisma = new PrismaClient();

/**
 * 開発用の初期データを投入する。
 * ログイン確認用アカウント: alice / bob / carol (パスワードは全員 password123)
 */
async function main() {
  const passwordHash = await bcrypt.hash("password123", 12);

  const [alice, bob, carol] = await Promise.all([
    prisma.user.upsert({
      where: { userId: "alice" },
      update: {},
      create: { userId: "alice", passwordHash, displayName: "Alice", status: "元気です" },
    }),
    prisma.user.upsert({
      where: { userId: "bob" },
      update: {},
      create: { userId: "bob", passwordHash, displayName: "Bob", status: "在宅勤務中" },
    }),
    prisma.user.upsert({
      where: { userId: "carol" },
      update: {},
      create: { userId: "carol", passwordHash, displayName: "Carol", status: null },
    }),
  ]);

  const existingWorkspace = await prisma.workspace.findFirst({ where: { name: "Sample Workspace" } });
  const workspace =
    existingWorkspace ??
    (await prisma.workspace.create({
      data: {
        name: "Sample Workspace",
        ownerId: alice.id,
        members: {
          create: [
            { userId: alice.id, role: "OWNER" },
            { userId: bob.id, role: "MEMBER" },
            { userId: carol.id, role: "MEMBER" },
          ],
        },
      },
    }));

  const general =
    (await prisma.channel.findUnique({ where: { workspaceId_name: { workspaceId: workspace.id, name: "general" } } })) ??
    (await prisma.channel.create({
      data: {
        workspaceId: workspace.id,
        name: "general",
        type: "PUBLIC",
        members: { create: [{ userId: alice.id }, { userId: bob.id }, { userId: carol.id }] },
      },
    }));

  const secretChannel =
    (await prisma.channel.findUnique({
      where: { workspaceId_name: { workspaceId: workspace.id, name: "owner-only" } },
    })) ??
    (await prisma.channel.create({
      data: {
        workspaceId: workspace.id,
        name: "owner-only",
        type: "PRIVATE",
        members: { create: [{ userId: alice.id }] },
      },
    }));

  const messageCount = await prisma.message.count({ where: { channelId: general.id } });
  if (messageCount === 0) {
    await prisma.message.create({
      data: {
        channelId: general.id,
        authorId: alice.id,
        body: "ようこそ ChatSpace へ! **Markdown** も使えます。\n\n- 箇条書き\n- `コード`\n\n@bob さん、確認お願いします!",
      },
    });
    await prisma.message.create({
      data: { channelId: general.id, authorId: bob.id, body: "了解です、確認しました 👍" },
    });
  }

  console.log("シードデータを投入しました。");
  console.log(`  workspace: ${workspace.name} (${workspace.id})`);
  console.log(`  channels : #${general.name}, #${secretChannel.name}`);
  console.log("  users    : alice / bob / carol (password: password123)");
}

main()
  .catch((e) => {
    console.error(e);
    process.exitCode = 1;
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
