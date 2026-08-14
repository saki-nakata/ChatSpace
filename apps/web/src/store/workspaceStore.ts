import { create } from "zustand";
import type { ChannelDTO, DMThreadDTO, MessageDTO, WorkspaceDTO } from "@chatspace/shared";
import { channelApi, dmApi, workspaceApi } from "../api/resources";
import { getSocket } from "../lib/socket";
import { SocketEvents } from "@chatspace/shared";

interface WorkspaceState {
  workspaces: WorkspaceDTO[];
  channels: ChannelDTO[];
  dmThreads: DMThreadDTO[];
  loadingWorkspaceId: string | null;

  loadWorkspaces: () => Promise<void>;
  addWorkspace: (w: WorkspaceDTO) => void;
  removeWorkspace: (workspaceId: string) => void;

  enterWorkspace: (workspaceId: string) => Promise<void>;
  refreshChannels: (workspaceId: string) => Promise<void>;
  refreshDMThreads: (workspaceId: string) => Promise<void>;

  upsertChannel: (c: ChannelDTO) => void;
  removeChannel: (channelId: string) => void;
  upsertDMThread: (d: DMThreadDTO) => void;

  bumpUnreadFromMessage: (message: MessageDTO, activeThreadKey: string | null, myUserId: string) => void;
  clearChannelUnread: (channelId: string) => void;
  clearDMUnread: (dmId: string) => void;
}

export const useWorkspaceStore = create<WorkspaceState>((set) => ({
  workspaces: [],
  channels: [],
  dmThreads: [],
  loadingWorkspaceId: null,

  loadWorkspaces: async () => {
    const { workspaces } = await workspaceApi.list();
    set({ workspaces });
  },

  addWorkspace: (w) => set((s) => ({ workspaces: [...s.workspaces, w] })),
  removeWorkspace: (workspaceId) =>
    set((s) => ({ workspaces: s.workspaces.filter((w) => w.id !== workspaceId) })),

  enterWorkspace: async (workspaceId) => {
    set({ loadingWorkspaceId: workspaceId, channels: [], dmThreads: [] });
    const socket = getSocket();
    socket.emit(SocketEvents.JoinWorkspace, workspaceId);

    const [{ channels }, { dmThreads }] = await Promise.all([
      channelApi.list(workspaceId),
      dmApi.list(workspaceId),
    ]);
    set({ channels, dmThreads, loadingWorkspaceId: null });

    for (const channel of channels) {
      if (channel.isMember) socket.emit(SocketEvents.JoinChannel, channel.id);
    }
    for (const dm of dmThreads) {
      socket.emit(SocketEvents.JoinDM, dm.id);
    }
  },

  refreshChannels: async (workspaceId) => {
    const { channels } = await channelApi.list(workspaceId);
    set({ channels });
    const socket = getSocket();
    for (const channel of channels) {
      if (channel.isMember) socket.emit(SocketEvents.JoinChannel, channel.id);
    }
  },

  refreshDMThreads: async (workspaceId) => {
    const { dmThreads } = await dmApi.list(workspaceId);
    set({ dmThreads });
    const socket = getSocket();
    for (const dm of dmThreads) socket.emit(SocketEvents.JoinDM, dm.id);
  },

  upsertChannel: (c) =>
    set((s) => {
      const exists = s.channels.some((ch) => ch.id === c.id);
      return {
        channels: exists ? s.channels.map((ch) => (ch.id === c.id ? c : ch)) : [...s.channels, c],
      };
    }),

  removeChannel: (channelId) =>
    set((s) => ({ channels: s.channels.filter((c) => c.id !== channelId) })),

  upsertDMThread: (d) =>
    set((s) => {
      const exists = s.dmThreads.some((t) => t.id === d.id);
      return {
        dmThreads: exists ? s.dmThreads.map((t) => (t.id === d.id ? d : t)) : [d, ...s.dmThreads],
      };
    }),

  bumpUnreadFromMessage: (message, activeThreadKey, myUserId) => {
    if (message.author.id === myUserId) return;
    if (message.parentId) return; // スレッド返信は一覧の未読数に含めない
    if (message.channelId) {
      if (activeThreadKey === `c:${message.channelId}`) return;
      set((s) => ({
        channels: s.channels.map((c) =>
          c.id === message.channelId ? { ...c, unreadCount: c.unreadCount + 1 } : c,
        ),
      }));
    } else if (message.dmId) {
      if (activeThreadKey === `d:${message.dmId}`) return;
      set((s) => ({
        dmThreads: s.dmThreads.map((t) =>
          t.id === message.dmId
            ? { ...t, unreadCount: t.unreadCount + 1, lastMessage: message }
            : t,
        ),
      }));
    }
  },

  clearChannelUnread: (channelId) =>
    set((s) => ({
      channels: s.channels.map((c) => (c.id === channelId ? { ...c, unreadCount: 0 } : c)),
    })),

  clearDMUnread: (dmId) =>
    set((s) => ({
      dmThreads: s.dmThreads.map((t) => (t.id === dmId ? { ...t, unreadCount: 0 } : t)),
    })),
}));
