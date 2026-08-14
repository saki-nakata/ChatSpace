import { beforeAll, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { prisma } from "../src/db.js";
import {
  addWorkspaceMember,
  authCookieFor,
  createChannel,
  createFakeIo,
  createTestUser,
  createWorkspaceWithOwner,
} from "./helpers.js";

const { app, setIo } = createApp();
setIo(createFakeIo());

async function jsonRequest(
  path: string,
  options: { method?: string; cookie?: string; body?: unknown } = {},
) {
  const res = await app.request(path, {
    method: options.method ?? "GET",
    headers: {
      ...(options.cookie ? { cookie: options.cookie } : {}),
      ...(options.body !== undefined ? { "content-type": "application/json" } : {}),
    },
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  return { status: res.status, data };
}

describe("認可: messageId のスコープ検証(cross-channel leak)", () => {
  it("参加していないプライベートチャンネルのメッセージを、参加中の別チャンネルのURLスコープ経由で読めない", async () => {
    const owner = await createTestUser("owner");
    const outsider = await createTestUser("outsider");
    const workspace = await createWorkspaceWithOwner(owner);
    await addWorkspaceMember(workspace.id, outsider.id);

    const privateChannel = await createChannel({
      workspaceId: workspace.id,
      type: "PRIVATE",
      memberIds: [owner.id], // outsider はメンバーではない
    });
    const publicChannel = await createChannel({
      workspaceId: workspace.id,
      type: "PUBLIC",
      memberIds: [owner.id, outsider.id],
    });

    const ownerCookie = await authCookieFor(owner.id);
    const created = await jsonRequest(
      `/workspaces/${workspace.id}/channels/${privateChannel.id}/messages`,
      { method: "POST", cookie: ownerCookie, body: { body: "秘密のメッセージ" } },
    );
    expect(created.status).toBe(201);
    const secretMessageId = created.data.message.id as string;

    const outsiderCookie = await authCookieFor(outsider.id);

    // 公開チャンネルのURLスコープに、プライベートチャンネルのmessageIdを混ぜてアクセス
    const repliesRes = await jsonRequest(
      `/workspaces/${workspace.id}/channels/${publicChannel.id}/messages/${secretMessageId}/replies`,
      { cookie: outsiderCookie },
    );
    expect(repliesRes.status).toBe(404);

    const reactRes = await jsonRequest(
      `/workspaces/${workspace.id}/channels/${publicChannel.id}/messages/${secretMessageId}/reactions`,
      { method: "POST", cookie: outsiderCookie, body: { emoji: "👍" } },
    );
    expect(reactRes.status).toBe(404);

    const editRes = await jsonRequest(
      `/workspaces/${workspace.id}/channels/${publicChannel.id}/messages/${secretMessageId}`,
      { method: "PATCH", cookie: outsiderCookie, body: { body: "改ざん" } },
    );
    expect(editRes.status).toBe(404);

    const deleteRes = await jsonRequest(
      `/workspaces/${workspace.id}/channels/${publicChannel.id}/messages/${secretMessageId}`,
      { method: "DELETE", cookie: outsiderCookie },
    );
    expect(deleteRes.status).toBe(404);
  });

  it("正当なスコープ経由であれば同じ操作が成功する(過剰にブロックしていないことの確認)", async () => {
    const owner = await createTestUser("owner");
    const member = await createTestUser("member");
    const workspace = await createWorkspaceWithOwner(owner);
    await addWorkspaceMember(workspace.id, member.id);
    const channel = await createChannel({
      workspaceId: workspace.id,
      type: "PUBLIC",
      memberIds: [owner.id, member.id],
    });

    const ownerCookie = await authCookieFor(owner.id);
    const created = await jsonRequest(`/workspaces/${workspace.id}/channels/${channel.id}/messages`, {
      method: "POST",
      cookie: ownerCookie,
      body: { body: "こんにちは" },
    });
    expect(created.status).toBe(201);
    const messageId = created.data.message.id as string;

    const memberCookie = await authCookieFor(member.id);
    const reactRes = await jsonRequest(
      `/workspaces/${workspace.id}/channels/${channel.id}/messages/${messageId}/reactions`,
      { method: "POST", cookie: memberCookie, body: { emoji: "🎉" } },
    );
    expect(reactRes.status).toBe(200);
    expect(reactRes.data.message.reactions).toHaveLength(1);
  });
});

describe("認可: プライベートチャンネルの非可視性", () => {
  it("非参加者のチャンネル一覧にプライベートチャンネルが含まれない", async () => {
    const owner = await createTestUser("owner");
    const outsider = await createTestUser("outsider");
    const workspace = await createWorkspaceWithOwner(owner);
    await addWorkspaceMember(workspace.id, outsider.id);
    const privateChannel = await createChannel({ workspaceId: workspace.id, type: "PRIVATE", memberIds: [owner.id] });

    const outsiderCookie = await authCookieFor(outsider.id);
    const listRes = await jsonRequest(`/workspaces/${workspace.id}/channels`, { cookie: outsiderCookie });
    expect(listRes.status).toBe(200);
    const ids = listRes.data.channels.map((c: { id: string }) => c.id);
    expect(ids).not.toContain(privateChannel.id);
  });

  it("非参加者は直接IDを指定してもプライベートチャンネルのメッセージ一覧を取得できない(404)", async () => {
    const owner = await createTestUser("owner");
    const outsider = await createTestUser("outsider");
    const workspace = await createWorkspaceWithOwner(owner);
    await addWorkspaceMember(workspace.id, outsider.id);
    const privateChannel = await createChannel({ workspaceId: workspace.id, type: "PRIVATE", memberIds: [owner.id] });

    const outsiderCookie = await authCookieFor(outsider.id);
    const res = await jsonRequest(`/workspaces/${workspace.id}/channels/${privateChannel.id}/messages`, {
      cookie: outsiderCookie,
    });
    expect(res.status).toBe(404);
  });
});

describe("認可: オーナー限定操作", () => {
  it("一般メンバーはチャンネルを作成できない(403)", async () => {
    const owner = await createTestUser("owner");
    const member = await createTestUser("member");
    const workspace = await createWorkspaceWithOwner(owner);
    await addWorkspaceMember(workspace.id, member.id);

    const memberCookie = await authCookieFor(member.id);
    const res = await jsonRequest(`/workspaces/${workspace.id}/channels`, {
      method: "POST",
      cookie: memberCookie,
      body: { name: "shouldfail", type: "PUBLIC" },
    });
    expect(res.status).toBe(403);
  });

  it("一般メンバーはチャンネルへの招待を実行できない(403)", async () => {
    const owner = await createTestUser("owner");
    const member = await createTestUser("member");
    const another = await createTestUser("another");
    const workspace = await createWorkspaceWithOwner(owner);
    await addWorkspaceMember(workspace.id, member.id);
    await addWorkspaceMember(workspace.id, another.id);
    const channel = await createChannel({ workspaceId: workspace.id, type: "PUBLIC", memberIds: [owner.id, member.id] });

    const memberCookie = await authCookieFor(member.id);
    const res = await jsonRequest(`/workspaces/${workspace.id}/channels/${channel.id}/members`, {
      method: "POST",
      cookie: memberCookie,
      body: { userId: another.userId },
    });
    expect(res.status).toBe(403);
  });

  it("一般メンバーは他メンバーをキックできない(403)", async () => {
    const owner = await createTestUser("owner");
    const member = await createTestUser("member");
    const target = await createTestUser("target");
    const workspace = await createWorkspaceWithOwner(owner);
    await addWorkspaceMember(workspace.id, member.id);
    await addWorkspaceMember(workspace.id, target.id);

    const memberCookie = await authCookieFor(member.id);
    const res = await jsonRequest(`/workspaces/${workspace.id}/kick`, {
      method: "POST",
      cookie: memberCookie,
      body: { userId: target.id },
    });
    expect(res.status).toBe(403);
  });

  it("オーナーはチャンネル作成・招待・キックを実行できる", async () => {
    const owner = await createTestUser("owner");
    const member = await createTestUser("member");
    const workspace = await createWorkspaceWithOwner(owner);
    await addWorkspaceMember(workspace.id, member.id);
    const ownerCookie = await authCookieFor(owner.id);

    const createRes = await jsonRequest(`/workspaces/${workspace.id}/channels`, {
      method: "POST",
      cookie: ownerCookie,
      body: { name: `owner_ch_${Date.now()}`, type: "PUBLIC" },
    });
    expect(createRes.status).toBe(201);

    const kickRes = await jsonRequest(`/workspaces/${workspace.id}/kick`, {
      method: "POST",
      cookie: ownerCookie,
      body: { userId: member.id },
    });
    expect(kickRes.status).toBe(204);

    const stillMember = await prisma.workspaceMember.findUnique({
      where: { workspaceId_userId: { workspaceId: workspace.id, userId: member.id } },
    });
    expect(stillMember).toBeNull();
  });
});

describe("回帰: DMのユーザーID解決(ハンドル↔内部ID)", () => {
  it("ログインハンドルを指定してDMを開始できる", async () => {
    const alice = await createTestUser("alice");
    const bob = await createTestUser("bob");
    const workspace = await createWorkspaceWithOwner(alice);
    await addWorkspaceMember(workspace.id, bob.id);

    const aliceCookie = await authCookieFor(alice.id);
    const res = await jsonRequest(`/workspaces/${workspace.id}/dms`, {
      method: "POST",
      cookie: aliceCookie,
      body: { userId: bob.userId },
    });
    expect(res.status).toBe(201);
    expect(res.data.dmThread.otherUser.id).toBe(bob.id);
  });
});

beforeAll(async () => {
  // マイグレーション適用後にDB接続を確立しておく(初回クエリでの遅延を避ける程度の意味合い)
  await prisma.$connect();
});
