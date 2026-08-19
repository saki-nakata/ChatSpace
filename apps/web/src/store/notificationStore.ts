import { create } from "zustand";
import type { Cursor, NotificationResponse } from "../api/types";
import { notificationApi } from "../api/resources";
import { USER_EVENTS_SUBSCRIPTION } from "../realtime/destinations";
import { subscribeJson, type RealtimeEvent } from "../realtime/stomp";
import { showDesktopNotification } from "../lib/browserNotifications";
import { NOTIFICATION_TYPE_LABELS } from "../lib/notificationLabels";

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
  /**
   * `/user/queue/events`経由のNOTIFICATIONイベントを購読する。戻り値の関数で購読解除する。
   * `userId`はタブが非表示・非フォーカス時のデスクトップ通知のON/OFF判定に使う(ユーザー単位の設定、D-3)。
   */
  subscribeRealtime: (userId: string) => () => void;
  /** ログアウト時・別ユーザーでのログイン時に呼ぶ(authStore参照)。前のユーザーの通知が残るのを防ぐ。 */
  reset: () => void;
}

const INITIAL_STATE = {
  notifications: [] as NotificationResponse[],
  unreadCount: 0,
  nextCursor: null as Cursor | null,
  loaded: false,
  loading: false,
  loadingMore: false,
};

export const useNotificationStore = create<NotificationState>((set, get) => ({
  ...INITIAL_STATE,

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

  subscribeRealtime: (userId) => {
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
        showDesktopNotification(userId, title, n.text ?? "新しい通知があります");
      },
    );
  },

  reset: () => set({ ...INITIAL_STATE }),
}));
