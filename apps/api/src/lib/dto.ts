import type { Attachment, Message, Reaction, User, WorkspaceMember } from "@prisma/client";
import type {
  AttachmentDTO,
  MessageDTO,
  ReactionSummaryDTO,
  UserDTO,
  WorkspaceRole,
} from "@chatspace/shared";

export function toUserDTO(user: User): UserDTO {
  return {
    id: user.id,
    userId: user.userId,
    displayName: user.displayName,
    avatarUrl: user.avatarUrl,
    status: user.status,
    createdAt: user.createdAt.toISOString(),
  };
}

export function toAttachmentDTO(attachment: Attachment): AttachmentDTO {
  return {
    id: attachment.id,
    url: `/uploads/${attachment.storageKey}`,
    fileName: attachment.fileName,
    mimeType: attachment.mimeType,
    sizeBytes: attachment.sizeBytes,
    kind: attachment.kind as AttachmentDTO["kind"],
  };
}

export function summarizeReactions(reactions: Reaction[], viewerId: string): ReactionSummaryDTO[] {
  const byEmoji = new Map<string, Reaction[]>();
  for (const reaction of reactions) {
    const list = byEmoji.get(reaction.emoji) ?? [];
    list.push(reaction);
    byEmoji.set(reaction.emoji, list);
  }
  return Array.from(byEmoji.entries()).map(([emoji, list]) => ({
    emoji,
    count: list.length,
    reactedByMe: list.some((r) => r.userId === viewerId),
    userIds: list.map((r) => r.userId),
  }));
}

type MessageWithRelations = Message & {
  author: User;
  attachments: Attachment[];
  reactions: Reaction[];
  mentions: { mentionedUserId: string }[];
  _count?: { replies: number };
};

export function toMessageDTO(message: MessageWithRelations, viewerId: string): MessageDTO {
  const isDeleted = message.deletedAt !== null;
  return {
    id: message.id,
    channelId: message.channelId,
    dmId: message.dmId,
    parentId: message.parentId,
    author: toUserDTO(message.author),
    body: isDeleted ? "" : message.body,
    createdAt: message.createdAt.toISOString(),
    updatedAt: message.updatedAt.toISOString(),
    editedAt: message.editedAt ? message.editedAt.toISOString() : null,
    deletedAt: message.deletedAt ? message.deletedAt.toISOString() : null,
    attachments: isDeleted ? [] : message.attachments.map(toAttachmentDTO),
    reactions: isDeleted ? [] : summarizeReactions(message.reactions, viewerId),
    replyCount: message._count?.replies ?? 0,
    mentionedUserIds: isDeleted ? [] : message.mentions.map((m) => m.mentionedUserId),
  };
}

export function toRole(member: Pick<WorkspaceMember, "role">): WorkspaceRole {
  return member.role === "OWNER" ? "OWNER" : "MEMBER";
}
