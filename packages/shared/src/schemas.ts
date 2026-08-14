import { z } from "zod";

/**
 * バリデーションルールはフロント・バック両方で同じ zod スキーマを使う。
 * サーバー側の最終防衛ラインとして必ずここのスキーマで再検証すること。
 */

export const USER_ID_REGEX = /^[a-zA-Z0-9_.-]{3,20}$/;

export const signupSchema = z.object({
  userId: z
    .string()
    .regex(USER_ID_REGEX, "英数字・_・.・- のみ、3〜20文字で入力してください"),
  password: z.string().min(8, "パスワードは8文字以上で入力してください").max(72),
  displayName: z.string().min(1, "表示名を入力してください").max(50),
});
export type SignupInput = z.infer<typeof signupSchema>;

export const loginSchema = z.object({
  userId: z.string().min(1),
  password: z.string().min(1),
});
export type LoginInput = z.infer<typeof loginSchema>;

// アバターは /uploads/xxx のようなアプリ内相対パス(添付ファイルAPIが返す形式)で
// 保存する。z.string().url() は絶対URLしか許さず、相対パスを常に弾いてしまうため使わない。
export const updateProfileSchema = z.object({
  displayName: z.string().min(1).max(50).optional(),
  status: z.string().max(100).optional(),
  avatarUrl: z
    .string()
    .min(1)
    .refine((v) => v.startsWith("/uploads/") || /^https?:\/\//.test(v), {
      message: "アバター画像のURLが不正です。",
    })
    .optional()
    .nullable(),
});
export type UpdateProfileInput = z.infer<typeof updateProfileSchema>;

export const createWorkspaceSchema = z.object({
  name: z.string().min(1, "ワークスペース名を入力してください").max(80),
});
export type CreateWorkspaceInput = z.infer<typeof createWorkspaceSchema>;

export const inviteToWorkspaceSchema = z.object({
  userId: z.string().min(1, "招待するユーザーIDを入力してください"),
});
export type InviteToWorkspaceInput = z.infer<typeof inviteToWorkspaceSchema>;

export const CHANNEL_NAME_REGEX = /^[a-zA-Z0-9_-]{1,50}$/;

export const createChannelSchema = z.object({
  name: z
    .string()
    .regex(CHANNEL_NAME_REGEX, "英数字・_・- のみ、1〜50文字で入力してください"),
  type: z.enum(["PUBLIC", "PRIVATE"]),
  memberUserIds: z.array(z.string()).optional(),
});
export type CreateChannelInput = z.infer<typeof createChannelSchema>;

export const createMessageSchema = z.object({
  body: z.string().min(1, "メッセージを入力してください").max(4000),
  parentId: z.string().optional().nullable(),
  attachmentIds: z.array(z.string()).max(10).optional(),
});
export type CreateMessageInput = z.infer<typeof createMessageSchema>;

export const updateMessageSchema = z.object({
  body: z.string().min(1).max(4000),
});
export type UpdateMessageInput = z.infer<typeof updateMessageSchema>;

export const toggleReactionSchema = z.object({
  emoji: z.string().min(1).max(8),
});
export type ToggleReactionInput = z.infer<typeof toggleReactionSchema>;

export const searchQuerySchema = z.object({
  q: z.string().min(1).max(200),
  channelId: z.string().optional(),
});
export type SearchQueryInput = z.infer<typeof searchQuerySchema>;

// --- 添付ファイル ---
export const ALLOWED_IMAGE_MIME_TYPES = [
  "image/png",
  "image/jpeg",
  "image/gif",
  "image/webp",
] as const;
export const ALLOWED_VIDEO_MIME_TYPES = ["video/mp4", "video/webm"] as const;
export const ALLOWED_ATTACHMENT_MIME_TYPES = [
  ...ALLOWED_IMAGE_MIME_TYPES,
  ...ALLOWED_VIDEO_MIME_TYPES,
];
export const MAX_ATTACHMENT_SIZE_BYTES = 25 * 1024 * 1024; // 25MB

// --- メンション抽出 ---
export const MENTION_REGEX = /@([a-zA-Z0-9_.-]{3,20})/g;
export function extractMentionedUserIds(body: string): string[] {
  const ids = new Set<string>();
  for (const match of body.matchAll(MENTION_REGEX)) {
    ids.add(match[1]);
  }
  return Array.from(ids);
}
