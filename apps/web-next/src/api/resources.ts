import { api } from "./client";
import type {
  ChannelMemberResponse,
  ChannelResponse,
  CreateChannelRequest,
  CreateDmRequest,
  CreateMessageRequest,
  CreateWorkspaceRequest,
  DmThreadResponse,
  EditMessageRequest,
  InviteChannelMemberRequest,
  InviteWorkspaceMemberRequest,
  KickWorkspaceMemberRequest,
  LoginRequest,
  MentionCandidatesResponse,
  MessageContextResponse,
  MessageListResponse,
  MessageResponse,
  NotificationListResponse,
  SearchResponse,
  SignupRequest,
  ToggleReactionRequest,
  UpdateProfileRequest,
  UploadResponse,
  UserResponse,
  WorkspaceMemberResponse,
  WorkspaceResponse,
} from "./types";

export const authApi = {
  me: () => api.get<UserResponse>("/auth/me"),
  login: (body: LoginRequest) => api.post<UserResponse>("/auth/login", body),
  signup: (body: SignupRequest) => api.post<UserResponse>("/auth/signup", body),
  logout: () => api.post<void>("/auth/logout"),
};

export const userApi = {
  updateProfile: (body: UpdateProfileRequest) => api.patch<UserResponse>("/users/me", body),
};

export const workspaceApi = {
  list: () => api.get<WorkspaceResponse[]>("/workspaces"),
  create: (body: CreateWorkspaceRequest) => api.post<WorkspaceResponse>("/workspaces", body),
  leave: (workspaceId: string) => api.post<void>(`/workspaces/${workspaceId}/leave`),
  invite: (workspaceId: string, body: InviteWorkspaceMemberRequest) =>
    api.post<void>(`/workspaces/${workspaceId}/invite`, body),
  kick: (workspaceId: string, body: KickWorkspaceMemberRequest) =>
    api.post<void>(`/workspaces/${workspaceId}/kick`, body),
  members: (workspaceId: string) =>
    api.get<WorkspaceMemberResponse[]>(`/workspaces/${workspaceId}/members`),
  presence: (workspaceId: string) => api.get<string[]>(`/workspaces/${workspaceId}/presence`),
};

export const channelApi = {
  list: (workspaceId: string) => api.get<ChannelResponse[]>(`/workspaces/${workspaceId}/channels`),
  create: (workspaceId: string, body: CreateChannelRequest) =>
    api.post<ChannelResponse>(`/workspaces/${workspaceId}/channels`, body),
  join: (workspaceId: string, channelId: string) =>
    api.post<void>(`/workspaces/${workspaceId}/channels/${channelId}/join`),
  markRead: (workspaceId: string, channelId: string) =>
    api.post<void>(`/workspaces/${workspaceId}/channels/${channelId}/read`),
  members: (workspaceId: string, channelId: string) =>
    api.get<ChannelMemberResponse[]>(`/workspaces/${workspaceId}/channels/${channelId}/members`),
  invite: (workspaceId: string, channelId: string, body: InviteChannelMemberRequest) =>
    api.post<void>(`/workspaces/${workspaceId}/channels/${channelId}/members`, body),
  removeMember: (workspaceId: string, channelId: string, targetUserId: string) =>
    api.delete<void>(`/workspaces/${workspaceId}/channels/${channelId}/members/${targetUserId}`),
  delete: (workspaceId: string, channelId: string) =>
    api.delete<void>(`/workspaces/${workspaceId}/channels/${channelId}`),
  mentionCandidates: (workspaceId: string, channelId: string, q: string) =>
    api.get<MentionCandidatesResponse>(
      `/workspaces/${workspaceId}/channels/${channelId}/mentions/candidates`,
      { q },
    ),
};

export const dmApi = {
  list: (workspaceId: string) => api.get<DmThreadResponse[]>(`/workspaces/${workspaceId}/dms`),
  getOrCreate: (workspaceId: string, body: CreateDmRequest) =>
    api.post<DmThreadResponse>(`/workspaces/${workspaceId}/dms`, body),
  markRead: (workspaceId: string, dmId: string) =>
    api.post<void>(`/workspaces/${workspaceId}/dms/${dmId}/read`),
};

/** チャンネル/DM共通のメッセージAPI(計画書§3、apps/api-javaのMessageControllerと同様に統合する)。 */
function messagesBasePath(workspaceId: string, scope: { channelId?: string; dmId?: string }) {
  if (scope.channelId) return `/workspaces/${workspaceId}/channels/${scope.channelId}/messages`;
  if (scope.dmId) return `/workspaces/${workspaceId}/dms/${scope.dmId}/messages`;
  throw new Error("channelIdまたはdmIdのいずれかを指定してください。");
}

export const messageApi = {
  list: (
    workspaceId: string,
    scope: { channelId?: string; dmId?: string },
    cursor?: { cursorCreatedAt?: string; cursorId?: string },
  ) => api.get<MessageListResponse>(messagesBasePath(workspaceId, scope), cursor),
  create: (workspaceId: string, scope: { channelId?: string; dmId?: string }, body: CreateMessageRequest) =>
    api.post<MessageResponse>(messagesBasePath(workspaceId, scope), body),
  edit: (
    workspaceId: string,
    scope: { channelId?: string; dmId?: string },
    messageId: string,
    body: EditMessageRequest,
  ) => api.patch<MessageResponse>(`${messagesBasePath(workspaceId, scope)}/${messageId}`, body),
  remove: (workspaceId: string, scope: { channelId?: string; dmId?: string }, messageId: string) =>
    api.delete<void>(`${messagesBasePath(workspaceId, scope)}/${messageId}`),
  context: (workspaceId: string, scope: { channelId?: string; dmId?: string }, messageId: string) =>
    api.get<MessageContextResponse>(`${messagesBasePath(workspaceId, scope)}/${messageId}/context`),
  replies: (
    workspaceId: string,
    scope: { channelId?: string; dmId?: string },
    messageId: string,
    query?: { cursorCreatedAt?: string; cursorId?: string; around?: string },
  ) =>
    api.get<MessageListResponse>(`${messagesBasePath(workspaceId, scope)}/${messageId}/replies`, query),
  toggleReaction: (
    workspaceId: string,
    scope: { channelId?: string; dmId?: string },
    messageId: string,
    body: ToggleReactionRequest,
  ) => api.post<MessageResponse>(`${messagesBasePath(workspaceId, scope)}/${messageId}/reactions`, body),
};

export const notificationApi = {
  list: (query?: { workspaceId?: string; unreadOnly?: boolean; cursorCreatedAt?: string; cursorId?: string }) =>
    api.get<NotificationListResponse>("/notifications", query),
  unreadCount: () => api.get<Record<string, number>>("/notifications/unread-count"),
  markRead: (notificationId: string) => api.post<void>(`/notifications/${notificationId}/read`),
  markAllRead: (workspaceId?: string) =>
    api.post<void>("/notifications/read-all", undefined, { workspaceId }),
};

export const searchApi = {
  search: (
    workspaceId: string,
    query: { q: string; channelId?: string; cursorCreatedAt?: string; cursorId?: string },
  ) => api.get<SearchResponse>(`/workspaces/${workspaceId}/search`, query),
};

export const uploadApi = {
  upload: async (file: File) => {
    const form = new FormData();
    form.append("file", file);
    const res = await api.upload<UploadResponse>("/uploads", form);
    return res.attachment;
  },
};
