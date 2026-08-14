import { create } from "zustand";
import type { NotificationDTO } from "@chatspace/shared";
import { notificationApi } from "../api/resources";

interface NotificationState {
  notifications: NotificationDTO[];
  unreadCount: number;
  load: (workspaceId?: string) => Promise<void>;
  refreshUnreadCount: () => Promise<void>;
  push: (n: NotificationDTO) => void;
  markRead: (id: string) => Promise<void>;
  markAllRead: (workspaceId?: string) => Promise<void>;
}

export const useNotificationStore = create<NotificationState>((set) => ({
  notifications: [],
  unreadCount: 0,

  load: async (workspaceId) => {
    const { notifications } = await notificationApi.list(workspaceId);
    set({ notifications });
  },

  refreshUnreadCount: async () => {
    const { count } = await notificationApi.unreadCount();
    set({ unreadCount: count });
  },

  push: (n) => {
    set((s) => ({ notifications: [n, ...s.notifications], unreadCount: s.unreadCount + 1 }));
  },

  markRead: async (id) => {
    await notificationApi.markRead(id);
    set((s) => ({
      notifications: s.notifications.map((n) => (n.id === id && !n.readAt ? { ...n, readAt: new Date().toISOString() } : n)),
      unreadCount: Math.max(0, s.unreadCount - (s.notifications.find((n) => n.id === id && !n.readAt) ? 1 : 0)),
    }));
  },

  markAllRead: async (workspaceId) => {
    await notificationApi.markAllRead(workspaceId);
    const now = new Date().toISOString();
    set((s) => ({
      notifications: s.notifications.map((n) => (n.readAt ? n : { ...n, readAt: now })),
      unreadCount: 0,
    }));
  },
}));
