import { Hono } from "hono";
import { nanoid } from "nanoid";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import {
  ALLOWED_ATTACHMENT_MIME_TYPES,
  ALLOWED_IMAGE_MIME_TYPES,
  MAX_ATTACHMENT_SIZE_BYTES,
} from "@chatspace/shared";
import { prisma } from "../db.js";
import { requireAuth } from "../auth/middleware.js";
import { requireChannelMember, requireDMAccess } from "../lib/authz.js";
import { BadRequest, NotFound } from "../lib/errors.js";
import { toAttachmentDTO } from "../lib/dto.js";
import { detectMimeFromBuffer } from "../lib/file-signature.js";
import { env } from "../env.js";
import type { AppEnv } from "../types.js";

export const uploadRoutes = new Hono<AppEnv>();
uploadRoutes.use("*", requireAuth);

// パストラバーサル対策: 元のファイル名やクライアント指定の拡張子は一切信用せず、
// 許可された MIME タイプに対応する固定の拡張子のみを使ってランダムなファイル名を生成する。
const EXTENSION_BY_MIME: Record<string, string> = {
  "image/png": ".png",
  "image/jpeg": ".jpg",
  "image/gif": ".gif",
  "image/webp": ".webp",
  "video/mp4": ".mp4",
  "video/webm": ".webm",
};

uploadRoutes.post("/", async (c) => {
  const form = await c.req.parseBody().catch(() => null);
  const file = form?.file;
  if (!file || typeof file === "string") {
    throw BadRequest("file フィールドでファイルを送信してください。");
  }

  if (file.size > MAX_ATTACHMENT_SIZE_BYTES) {
    throw BadRequest(`ファイルサイズは ${MAX_ATTACHMENT_SIZE_BYTES / 1024 / 1024}MB 以下にしてください。`);
  }

  const buffer = Buffer.from(await file.arrayBuffer());

  // ブラウザが送ってくる file.type(Content-Type)は偽装可能なため信用しない。
  // 実データの先頭バイト(マジックナンバー)から判定した MIME のみを正として扱う。
  const detectedType = detectMimeFromBuffer(buffer);
  if (!detectedType || !ALLOWED_ATTACHMENT_MIME_TYPES.includes(detectedType as (typeof ALLOWED_ATTACHMENT_MIME_TYPES)[number])) {
    throw BadRequest("許可されていないファイル形式です(画像: png/jpeg/gif/webp、動画: mp4/webm のみ)。");
  }

  const extension = EXTENSION_BY_MIME[detectedType];
  const storageKey = `${nanoid()}${extension}`;
  const uploadDir = path.resolve(env.uploadDir);
  await mkdir(uploadDir, { recursive: true });
  const destination = path.join(uploadDir, storageKey);
  await writeFile(destination, buffer);

  const kind = (ALLOWED_IMAGE_MIME_TYPES as readonly string[]).includes(detectedType) ? "IMAGE" : "VIDEO";

  const attachment = await prisma.attachment.create({
    data: {
      uploaderId: c.get("userId"),
      storageKey,
      fileName: file.name || storageKey,
      mimeType: detectedType,
      sizeBytes: file.size,
      kind,
    },
  });

  return c.json({ attachment: toAttachmentDTO(attachment) }, 201);
});

const STORAGE_KEY_PATTERN = /^[A-Za-z0-9_-]+\.[a-z0-9]+$/;

// 添付ファイルの配信。ファイル名は推測困難な乱数だが、それだけに頼らず
// 「そのメッセージが所属するチャンネル/DMの参加者かどうか」を都度検証する。
// これによりプライベートチャンネル・DMの添付ファイルが非参加者に漏れることを防ぐ。
uploadRoutes.get("/:storageKey", async (c) => {
  const { storageKey } = c.req.param();
  if (!STORAGE_KEY_PATTERN.test(storageKey)) throw NotFound("ファイルが見つかりません。");

  const attachment = await prisma.attachment.findUnique({ where: { storageKey } });
  if (!attachment) throw NotFound("ファイルが見つかりません。");

  const userId = c.get("userId");
  if (attachment.messageId === null) {
    // アバター画像は特定のメッセージに紐付かず messageId が常に null のままなので、
    // 「いずれかのユーザーの現在のアバターとして使われているか」を別途確認する。
    // アバターは UserDTO 経由で認証済みユーザーになら誰でも表示される情報のため、
    // 画像本体も同様に認証済みユーザー全員が閲覧できてよい。
    const isSomeonesAvatar = await prisma.user.findFirst({
      where: { avatarUrl: `/uploads/${storageKey}` },
      select: { id: true },
    });
    if (!isSomeonesAvatar) {
      // それ以外(メッセージ送信前のプレビュー中)は、アップロード本人のみ閲覧可能
      if (attachment.uploaderId !== userId) throw NotFound("ファイルが見つかりません。");
    }
  } else {
    const message = await prisma.message.findUnique({ where: { id: attachment.messageId } });
    if (!message) throw NotFound("ファイルが見つかりません。");
    if (message.channelId) {
      await requireChannelMember(message.channelId, userId);
    } else if (message.dmId) {
      await requireDMAccess(message.dmId, userId);
    } else {
      throw NotFound("ファイルが見つかりません。");
    }
  }

  const filePath = path.join(path.resolve(env.uploadDir), attachment.storageKey);
  const data = await readFile(filePath).catch(() => null);
  if (!data) throw NotFound("ファイルが見つかりません。");

  return c.body(new Uint8Array(data), 200, {
    "Content-Type": attachment.mimeType,
    "Cache-Control": "private, max-age=86400",
    // ブラウザによる Content-Type の推測(MIMEスニッフィング)を無効化する
    "X-Content-Type-Options": "nosniff",
  });
});
