// フロント・バックで共有するレスポンス DTO 型。
// Prisma の型をそのまま使わず薄い DTO に変換して返すことで、
// パスワードハッシュ等の内部フィールドが誤って外部に漏れるのを防ぐ。

export type WorkspaceRole = "OWNER" | "MEMBER";
export type ChannelType = "PUBLIC" | "PRIVATE";
export type NotificationType = "MENTION" | "DM" | "CHANNEL_INVITE" | "WORKSPACE_INVITE" | "THREAD_REPLY";

export interface UserDTO {
  id: string;
  userId: string;
  displayName: string;
  avatarUrl: string | null;
  status: string | null;
  createdAt: string;
}

export interface WorkspaceDTO {
  id: string;
  name: string;
  ownerId: string;
  createdAt: string;
  myRole: WorkspaceRole;
}

export interface WorkspaceMemberDTO {
  user: UserDTO;
  role: WorkspaceRole;
  joinedAt: string;
}

export interface ChannelDTO {
  id: string;
  workspaceId: string;
  name: string;
  type: ChannelType;
  createdAt: string;
  isMember: boolean;
  unreadCount: number;
  /** 自分の既読位置(未読区切り線の算出に使う)。未参加なら null */
  lastReadAt: string | null;
}

export interface AttachmentDTO {
  id: string;
  url: string;
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  kind: "IMAGE" | "VIDEO";
}

export interface ReactionSummaryDTO {
  emoji: string;
  count: number;
  reactedByMe: boolean;
  userIds: string[];
}

export interface MessageDTO {
  id: string;
  channelId: string | null;
  dmId: string | null;
  parentId: string | null;
  author: UserDTO;
  body: string;
  createdAt: string;
  updatedAt: string;
  editedAt: string | null;
  deletedAt: string | null;
  attachments: AttachmentDTO[];
  reactions: ReactionSummaryDTO[];
  replyCount: number;
  mentionedUserIds: string[];
}

export interface DMThreadDTO {
  id: string;
  workspaceId: string;
  otherUser: UserDTO;
  unreadCount: number;
  lastMessage: MessageDTO | null;
  /** 自分の既読位置(未読区切り線の算出に使う) */
  lastReadAt: string;
}

export interface NotificationDTO {
  id: string;
  type: NotificationType;
  createdAt: string;
  readAt: string | null;
  workspaceId: string | null;
  channelId: string | null;
  dmId: string | null;
  messageId: string | null;
  /** messageId がスレッド返信の場合、その親メッセージのID(メイン一覧上の位置を特定するため) */
  threadParentId: string | null;
  fromUser: UserDTO | null;
  text: string;
}

export interface AuthSessionDTO {
  user: UserDTO;
}
