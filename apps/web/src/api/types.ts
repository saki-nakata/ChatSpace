import type { components } from "./schema";

/**
 * `schema.d.ts`(`openapi-typescript`生成、フェーズ8の`apps/api/openapi.json`が元)のスキーマ型を
 * 短い名前で再エクスポートする(計画書§7: REST型はOpenAPI自動生成に置き換える)。
 */
export type UserResponse = components["schemas"]["UserResponse"];
export type WorkspaceResponse = components["schemas"]["WorkspaceResponse"];
export type WorkspaceMemberResponse = components["schemas"]["WorkspaceMemberResponse"];
export type ChannelResponse = components["schemas"]["ChannelResponse"];
export type ChannelMemberResponse = components["schemas"]["ChannelMemberResponse"];
export type DmThreadResponse = components["schemas"]["DmThreadResponse"];
export type MessageResponse = components["schemas"]["MessageResponse"];
export type MessageListResponse = components["schemas"]["MessageListResponse"];
export type MessageContextResponse = components["schemas"]["MessageContextResponse"];
export type ReactionSummary = components["schemas"]["ReactionSummary"];
export type NotificationResponse = components["schemas"]["NotificationResponse"];
export type NotificationListResponse = components["schemas"]["NotificationListResponse"];
export type SearchResponse = components["schemas"]["SearchResponse"];
export type SearchResultItem = components["schemas"]["SearchResultItem"];
export type AttachmentResponse = components["schemas"]["AttachmentResponse"];
export type MessageAttachmentResponse = components["schemas"]["MessageAttachmentResponse"];
export type UploadResponse = components["schemas"]["UploadResponse"];
export type MentionCandidatesResponse = components["schemas"]["MentionCandidatesResponse"];
export type Cursor = components["schemas"]["Cursor"];

export type LoginRequest = components["schemas"]["LoginRequest"];
export type SignupRequest = components["schemas"]["SignupRequest"];
export type CreateWorkspaceRequest = components["schemas"]["CreateWorkspaceRequest"];
export type CreateChannelRequest = components["schemas"]["CreateChannelRequest"];
export type CreateDmRequest = components["schemas"]["CreateDmRequest"];
export type CreateMessageRequest = components["schemas"]["CreateMessageRequest"];
export type EditMessageRequest = components["schemas"]["EditMessageRequest"];
export type ToggleReactionRequest = components["schemas"]["ToggleReactionRequest"];
export type InviteWorkspaceMemberRequest = components["schemas"]["InviteWorkspaceMemberRequest"];
export type InviteChannelMemberRequest = components["schemas"]["InviteChannelMemberRequest"];
export type KickWorkspaceMemberRequest = components["schemas"]["KickWorkspaceMemberRequest"];
export type UpdateProfileRequest = components["schemas"]["UpdateProfileRequest"];
