import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../api/resources", () => ({
  notificationApi: {
    list: vi.fn(),
    unreadCount: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
  },
}));
vi.mock("../realtime/stomp", () => ({ subscribeJson: vi.fn(() => () => {}) }));
vi.mock("../lib/browserNotifications", () => ({ showDesktopNotification: vi.fn() }));

import type { NotificationResponse } from "../api/types";
import { notificationApi } from "../api/resources";
import { showDesktopNotification } from "../lib/browserNotifications";
import { subscribeJson } from "../realtime/stomp";
import { useNotificationStore } from "./notificationStore";

const notification = (
  id: string,
  overrides: Partial<NotificationResponse> = {},
): NotificationResponse => ({
  id,
  type: "MENTION",
  text: `通知 ${id}`,
  readAt: undefined,
  ...overrides,
});

describe("notificationStore", () => {
  beforeEach(() => {
    // グローバル設定(restoreMocks等)は既存テストの挙動を変えるため使わず、ここで明示的にクリアする
    vi.clearAllMocks();
    useNotificationStore.getState().reset();
  });

  describe("fetchNotifications", () => {
    /** 全ワークスペース分をまとめて扱う設計(Tier C)のため、セッション中の重複取得を避ける。 */
    it("取得済みなら再取得しない", async () => {
      vi.mocked(notificationApi.list).mockResolvedValue({ notifications: [notification("n-1")] });

      await useNotificationStore.getState().fetchNotifications();
      await useNotificationStore.getState().fetchNotifications();

      expect(notificationApi.list).toHaveBeenCalledOnce();
    });

    it("forceを指定すると取得済みでも再取得する", async () => {
      vi.mocked(notificationApi.list).mockResolvedValue({ notifications: [] });

      await useNotificationStore.getState().fetchNotifications();
      await useNotificationStore.getState().fetchNotifications(true);

      expect(notificationApi.list).toHaveBeenCalledTimes(2);
    });
  });

  describe("markRead", () => {
    /**
     * 既読化は一覧の並び順・表示位置を変更しない(画面設計書S-13)。
     * 並び替えると、クリックした通知が手元から消えて次の通知を誤クリックする。
     */
    it("並び順を変えずに対象のreadAtだけを更新する", async () => {
      useNotificationStore.setState({
        notifications: [notification("n-1"), notification("n-2"), notification("n-3")],
      });
      vi.mocked(notificationApi.markRead).mockResolvedValue(undefined);
      vi.mocked(notificationApi.unreadCount).mockResolvedValue({ unreadCount: 2 });

      await useNotificationStore.getState().markRead("n-2");

      const { notifications, unreadCount } = useNotificationStore.getState();
      expect(notifications.map((n) => n.id)).toEqual(["n-1", "n-2", "n-3"]);
      expect(notifications[1].readAt).toBeTruthy();
      expect(notifications[0].readAt).toBeFalsy();
      expect(notifications[2].readAt).toBeFalsy();
      // 未読件数はローカルで減算せずサーバーの値で上書きする(他タブでの既読化とずれないため)
      expect(unreadCount).toBe(2);
    });
  });

  describe("markAllRead", () => {
    it("既読済みのreadAtは上書きしない", async () => {
      const alreadyRead = "2026-08-01T00:00:00.000Z";
      useNotificationStore.setState({
        notifications: [notification("n-1", { readAt: alreadyRead }), notification("n-2")],
      });
      vi.mocked(notificationApi.markAllRead).mockResolvedValue(undefined);
      vi.mocked(notificationApi.unreadCount).mockResolvedValue({ unreadCount: 0 });

      await useNotificationStore.getState().markAllRead();

      const { notifications } = useNotificationStore.getState();
      expect(notifications[0].readAt).toBe(alreadyRead);
      expect(notifications[1].readAt).toBeTruthy();
    });
  });

  describe("subscribeRealtime", () => {
    /** リアルタイム受信したNOTIFICATIONを先頭に積み、未読数を増やす。 */
    it("NOTIFICATIONイベントを先頭に追加し未読数を増やす", () => {
      useNotificationStore.setState({ notifications: [notification("n-old")], unreadCount: 1 });
      let handler!: (event: { type: string; payload: unknown }) => void;
      vi.mocked(subscribeJson).mockImplementation((_destination, cb) => {
        handler = cb as typeof handler;
        return () => {};
      });

      useNotificationStore.getState().subscribeRealtime("u-alice");
      handler({ type: "NOTIFICATION", payload: notification("n-new") });

      const { notifications, unreadCount } = useNotificationStore.getState();
      expect(notifications.map((n) => n.id)).toEqual(["n-new", "n-old"]);
      expect(unreadCount).toBe(2);
      expect(showDesktopNotification).toHaveBeenCalledOnce();
    });

    /** 同じ宛先には他種別のイベントも流れてくるため、種別で弾かないと無関係な件数が増える。 */
    it("NOTIFICATION以外のイベントは無視する", () => {
      useNotificationStore.setState({ notifications: [], unreadCount: 0 });
      let handler!: (event: { type: string; payload: unknown }) => void;
      vi.mocked(subscribeJson).mockImplementation((_destination, cb) => {
        handler = cb as typeof handler;
        return () => {};
      });

      useNotificationStore.getState().subscribeRealtime("u-alice");
      handler({ type: "MESSAGE_CREATED", payload: { id: "m-1" } });

      expect(useNotificationStore.getState().notifications).toEqual([]);
      expect(useNotificationStore.getState().unreadCount).toBe(0);
      expect(showDesktopNotification).not.toHaveBeenCalled();
    });
  });

  describe("reset", () => {
    it("全ての状態を初期値に戻す", () => {
      useNotificationStore.setState({
        notifications: [notification("n-1")],
        unreadCount: 5,
        nextCursor: { createdAt: "2026-08-01T00:00:00Z", id: "n-1" },
        loaded: true,
        loading: true,
        loadingMore: true,
      });

      useNotificationStore.getState().reset();

      const state = useNotificationStore.getState();
      expect(state.notifications).toEqual([]);
      expect(state.unreadCount).toBe(0);
      expect(state.nextCursor).toBeNull();
      expect(state.loaded).toBe(false);
      expect(state.loading).toBe(false);
      expect(state.loadingMore).toBe(false);
    });
  });
});
