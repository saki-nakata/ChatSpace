import type {
  AttachmentDTO,
  AuthSessionDTO,
  ChannelDTO,
  CreateChannelInput,
  CreateWorkspaceInput,
  DMThreadDTO,
  InviteToWorkspaceInput,
  LoginInput,
  MessageDTO,
  NotificationDTO,
  SignupInput,
  UpdateProfileInput,
  UserDTO,
  WorkspaceDTO,
  WorkspaceMemberDTO,
} from "@chatspace/shared";
import { api } from "./client";

export const authApi = {
  signup: (input: SignupInput) => api.post<AuthSessionDTO>("/auth/signup", input),
  login: (input: LoginInput) => api.post<AuthSessionDTO>("/auth/login", input),
  logout: () => api.post<void>("/auth/logout"),
  me: () => api.get<AuthSessionDTO>("/auth/me"),
};

export const userApi = {
  updateProfile: (input: UpdateProfileInput) => api.patch<{ user: UserDTO }>("/users/me", input),
  lookup: (userId: string) => api.get<{ user: UserDTO | null }>(`/users/lookup?userId=${encodeURIComponent(userId)}`),
};

export const workspaceApi = {
  list: () => api.get<{ workspaces: WorkspaceDTO[] }>("/workspaces"),
  create: (input: CreateWorkspaceInput) => api.post<{ workspace: WorkspaceDTO }>("/workspaces", input),
  members: (workspaceId: string) =>
    api.get<{ members: WorkspaceMemberDTO[] }>(`/workspaces/${workspaceId}/members`),
  invite: (workspaceId: string, input: InviteToWorkspaceInput) =>
    api.post<{ member: WorkspaceMemberDTO }>(`/workspaces/${workspaceId}/invite`, input),
  kick: (workspaceId: string, userId: string) =>
    api.post<void>(`/workspaces/${workspaceId}/kick`, { userId }),
  leave: (workspaceId: string) => api.post<void>(`/workspaces/${workspaceId}/leave`),
  presence: (workspaceId: string) => api.get<{ onlineUserIds: string[] }>(`/workspaces/${workspaceId}/presence`),
};

export const channelApi = {
  list: (workspaceId: string) => api.get<{ channels: ChannelDTO[] }>(`/workspaces/${workspaceId}/channels`),
  create: (workspaceId: string, input: CreateChannelInput) =>
    api.post<{ channel: ChannelDTO }>(`/workspaces/${workspaceId}/channels`, input),
  join: (workspaceId: string, channelId: string) =>
    api.post<{ channel: ChannelDTO }>(`/workspaces/${workspaceId}/channels/${channelId}/join`),
  remove: (workspaceId: string, channelId: string) =>
    api.delete<void>(`/workspaces/${workspaceId}/channels/${channelId}`),
  markRead: (workspaceId: string, channelId: string) =>
    api.post<void>(`/workspaces/${workspaceId}/channels/${channelId}/read`),
  members: (workspaceId: string, channelId: string) =>
    api.get<{ members: { user: UserDTO; joinedAt: string }[] }>(
      `/workspaces/${workspaceId}/channels/${channelId}/members`,
    ),
  addMember: (workspaceId: string, channelId: string, userId: string) =>
    api.post<void>(`/workspaces/${workspaceId}/channels/${channelId}/members`, { userId }),
  removeMember: (workspaceId: string, channelId: string, userId: string) =>
    api.delete<void>(`/workspaces/${workspaceId}/channels/${channelId}/members/${userId}`),
};

export const dmApi = {
  list: (workspaceId: string) => api.get<{ dmThreads: DMThreadDTO[] }>(`/workspaces/${workspaceId}/dms`),
  open: (workspaceId: string, userId: string) =>
    api.post<{ dmThread: DMThreadDTO }>(`/workspaces/${workspaceId}/dms`, { userId }),
  markRead: (workspaceId: string, dmId: string) =>
    api.post<void>(`/workspaces/${workspaceId}/dms/${dmId}/read`),
};

interface MessagesResponse {
  messages: MessageDTO[];
}

export const messageApi = {
  listChannel: (workspaceId: string, channelId: string, before?: string) =>
    api.get<MessagesResponse>(
      `/workspaces/${workspaceId}/channels/${channelId}/messages${before ? `?before=${encodeURIComponent(before)}` : ""}`,
    ),
  listDM: (workspaceId: string, dmId: string, before?: string) =>
    api.get<MessagesResponse>(
      `/workspaces/${workspaceId}/dms/${dmId}/messages${before ? `?before=${encodeURIComponent(before)}` : ""}`,
    ),
  createChannel: (
    workspaceId: string,
    channelId: string,
    input: { body: string; parentId?: string | null; attachmentIds?: string[] },
  ) => api.post<{ message: MessageDTO }>(`/workspaces/${workspaceId}/channels/${channelId}/messages`, input),
  createDM: (
    workspaceId: string,
    dmId: string,
    input: { body: string; parentId?: string | null; attachmentIds?: string[] },
  ) => api.post<{ message: MessageDTO }>(`/workspaces/${workspaceId}/dms/${dmId}/messages`, input),
  updateChannel: (workspaceId: string, channelId: string, messageId: string, body: string) =>
    api.patch<{ message: MessageDTO }>(
      `/workspaces/${workspaceId}/channels/${channelId}/messages/${messageId}`,
      { body },
    ),
  updateDM: (workspaceId: string, dmId: string, messageId: string, body: string) =>
    api.patch<{ message: MessageDTO }>(`/workspaces/${workspaceId}/dms/${dmId}/messages/${messageId}`, { body }),
  deleteChannel: (workspaceId: string, channelId: string, messageId: string) =>
    api.delete<void>(`/workspaces/${workspaceId}/channels/${channelId}/messages/${messageId}`),
  deleteDM: (workspaceId: string, dmId: string, messageId: string) =>
    api.delete<void>(`/workspaces/${workspaceId}/dms/${dmId}/messages/${messageId}`),
  reactChannel: (workspaceId: string, channelId: string, messageId: string, emoji: string) =>
    api.post<{ message: MessageDTO }>(
      `/workspaces/${workspaceId}/channels/${channelId}/messages/${messageId}/reactions`,
      { emoji },
    ),
  reactDM: (workspaceId: string, dmId: string, messageId: string, emoji: string) =>
    api.post<{ message: MessageDTO }>(`/workspaces/${workspaceId}/dms/${dmId}/messages/${messageId}/reactions`, {
      emoji,
    }),
  repliesChannel: (workspaceId: string, channelId: string, messageId: string) =>
    api.get<{ replies: MessageDTO[] }>(
      `/workspaces/${workspaceId}/channels/${channelId}/messages/${messageId}/replies`,
    ),
  repliesDM: (workspaceId: string, dmId: string, messageId: string) =>
    api.get<{ replies: MessageDTO[] }>(`/workspaces/${workspaceId}/dms/${dmId}/messages/${messageId}/replies`),
  contextChannel: (workspaceId: string, channelId: string, messageId: string) =>
    api.get<MessageContextResponse>(
      `/workspaces/${workspaceId}/channels/${channelId}/messages/${messageId}/context`,
    ),
  contextDM: (workspaceId: string, dmId: string, messageId: string) =>
    api.get<MessageContextResponse>(`/workspaces/${workspaceId}/dms/${dmId}/messages/${messageId}/context`),
};

export interface MessageContextResponse {
  anchorMessageId: string;
  replyMessageId: string | null;
  hasMoreOlder: boolean;
  messages: MessageDTO[];
}

export const searchApi = {
  search: (workspaceId: string, q: string, channelId?: string) =>
    api.get<MessagesResponse>(
      `/workspaces/${workspaceId}/search?q=${encodeURIComponent(q)}${channelId ? `&channelId=${channelId}` : ""}`,
    ),
};

export const notificationApi = {
  list: (workspaceId?: string) =>
    api.get<{ notifications: NotificationDTO[] }>(`/notifications${workspaceId ? `?workspaceId=${workspaceId}` : ""}`),
  unreadCount: () => api.get<{ count: number }>("/notifications/unread-count"),
  markRead: (notificationId: string) => api.post<void>(`/notifications/${notificationId}/read`),
  markAllRead: (workspaceId?: string) => api.post<void>("/notifications/read-all", { workspaceId }),
};

export const uploadApi = {
  upload: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return api.upload<{ attachment: AttachmentDTO }>("/uploads", form);
  },
};
