import { create } from "zustand";
import type { Cursor, NotificationResponse } from "../api/types";
import { notificationApi } from "../api/resources";
import { USER_EVENTS_SUBSCRIPTION } from "../realtime/destinations";
import { subscribeJson, type RealtimeEvent } from "../realtime/stomp";
import { showDesktopNotification } from "../lib/browserNotifications";
import { NOTIFICATION_TYPE_LABELS } from "../lib/notificationLabels";
import { useAuthStore } from "./authStore";

interface NotificationState {
  notifications: NotificationResponse[];
  unreadCount: number;
  nextCursor: Cursor | null;
  /** `notifications`を取得済みか。常に全ワークスペース分をまとめて扱う(Tier C、B'案)ため、
   * ワークスペース切り替えでは再取得しない(セッション中1回だけ取得)。 */
  loaded: boolean;
  loading: boolean;
  loadingMore: boolean;
  fetchUnreadCount: () => Promise<void>;
  /** 初回取得。取得済みなら何もしない(`force`指定時は強制再取得)。 */
  fetchNotifications: (force?: boolean) => Promise<void>;
  loadMoreNotifications: () => Promise<void>;
  markRead: (notificationId: string) => Promise<void>;
  markAllRead: () => Promise<void>;
  /** `/user/queue/events`経由のNOTIFICATIONイベントを購読する。戻り値の関数で購読解除する。 */
  subscribeRealtime: () => () => void;
}

export const useNotificationStore = create<NotificationState>((set, get) => ({
  notifications: [],
  unreadCount: 0,
  nextCursor: null,
  loaded: false,
  loading: false,
  loadingMore: false,

  fetchUnreadCount: async () => {
    const counts = await notificationApi.unreadCount();
    set({ unreadCount: counts.unreadCount ?? 0 });
  },

  fetchNotifications: async (force = false) => {
    if (!force && get().loaded) return;
    set({ loading: true });
    const res = await notificationApi.list({});
    set({
      notifications: res.notifications ?? [],
      nextCursor: res.nextCursor ?? null,
      loaded: true,
      loading: false,
    });
  },

  loadMoreNotifications: async () => {
    const { nextCursor, loadingMore } = get();
    if (!nextCursor || loadingMore) return;
    set({ loadingMore: true });
    const res = await notificationApi.list({
      cursorCreatedAt: nextCursor.createdAt,
      cursorId: nextCursor.id,
    });
    set((state) => ({
      notifications: [...state.notifications, ...(res.notifications ?? [])],
      nextCursor: res.nextCursor ?? null,
      loadingMore: false,
    }));
  },

  markRead: async (notificationId) => {
    await notificationApi.markRead(notificationId);
    // 既読化は一覧の並び順・表示位置を変更しない(画面設計書S-13)。readAtのみ更新する
    set((state) => ({
      notifications: state.notifications.map((n) =>
        n.id === notificationId ? { ...n, readAt: new Date().toISOString() } : n,
      ),
    }));
    await get().fetchUnreadCount();
  },

  markAllRead: async () => {
    await notificationApi.markAllRead();
    const now = new Date().toISOString();
    set((state) => ({
      notifications: state.notifications.map((n) => (n.readAt ? n : { ...n, readAt: now })),
    }));
    await get().fetchUnreadCount();
  },

  subscribeRealtime: () => {
    return subscribeJson<NotificationResponse>(
      USER_EVENTS_SUBSCRIPTION,
      (event: RealtimeEvent<NotificationResponse>) => {
        if (event.type !== "NOTIFICATION") return;
        set((state) => ({
          notifications: [event.payload, ...state.notifications],
          unreadCount: state.unreadCount + 1,
        }));
        const n = event.payload;
        const title = n.type ? (NOTIFICATION_TYPE_LABELS[n.type] ?? n.type) : "ChatSpace";
        const userId = useAuthStore.getState().user?.id;
        if (userId) showDesktopNotification(userId, title, n.text ?? "新しい通知があります");
      },
    );
  },
}));
