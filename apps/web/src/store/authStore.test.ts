import { beforeEach, describe, expect, it, vi } from "vitest";

// ストアの副作用(HTTP・STOMP)は本テストの対象外なのでモックする。
// 検証したいのは「認証状態の遷移に伴い、ユーザーに紐づくキャッシュが確実に破棄されるか」。
vi.mock("../api/resources", () => ({
  authApi: {
    me: vi.fn(),
    login: vi.fn(),
    signup: vi.fn(),
    logout: vi.fn(),
  },
  userApi: { updateProfile: vi.fn() },
  notificationApi: {},
  workspaceApi: {},
}));
vi.mock("../realtime/stomp", () => ({
  getStompClient: vi.fn(),
  disconnectStomp: vi.fn(),
  subscribeJson: vi.fn(() => () => {}),
}));

import { authApi } from "../api/resources";
import { disconnectStomp, getStompClient } from "../realtime/stomp";
import { useAuthStore } from "./authStore";
import { useNotificationStore } from "./notificationStore";
import { usePresenceStore } from "./presenceStore";
import { useWorkspaceStore } from "./workspaceStore";

const ALICE = { id: "u-alice", userId: "alice", displayName: "Alice" };
const BOB = { id: "u-bob", userId: "bob", displayName: "Bob" };

/** 前ユーザーのデータが各ストアに載っている状態を作る。 */
function seedPreviousUserState() {
  useNotificationStore.setState({
    notifications: [{ id: "n-1", text: "秘密のチャンネルで言及されました" }],
    unreadCount: 3,
    loaded: true,
  });
  useWorkspaceStore.setState({
    workspaces: [{ id: "w-1", name: "前ユーザーのワークスペース" }],
    status: "ready",
  });
  usePresenceStore.setState({ onlineUserIds: new Set(["u-carol"]) });
}

/** ユーザーに紐づくキャッシュが全て初期状態に戻っているか。 */
function expectUserScopedStoresCleared() {
  expect(useNotificationStore.getState().notifications).toEqual([]);
  expect(useNotificationStore.getState().unreadCount).toBe(0);
  expect(useNotificationStore.getState().loaded).toBe(false);
  expect(useWorkspaceStore.getState().workspaces).toEqual([]);
  expect(useWorkspaceStore.getState().status).toBe("idle");
  expect(usePresenceStore.getState().onlineUserIds.size).toBe(0);
}

describe("authStore", () => {
  beforeEach(() => {
    // グローバル設定(restoreMocks等)は既存テストの挙動を変えるため使わず、ここで明示的にクリアする
    vi.clearAllMocks();
    useAuthStore.setState({ user: null, status: "idle" });
    useNotificationStore.getState().reset();
    useWorkspaceStore.getState().reset();
    usePresenceStore.getState().reset();
  });

  describe("logout", () => {
    /**
     * 最重要ケース。ログアウト後に前ユーザーの通知本文やワークスペース名が残ると、
     * 共用端末で次の利用者に他人の情報が見えてしまう(マルチテナント構成における情報漏洩)。
     */
    it("ユーザーに紐づくキャッシュを全て破棄する", async () => {
      useAuthStore.setState({ user: ALICE, status: "ready" });
      seedPreviousUserState();

      await useAuthStore.getState().logout();

      expect(useAuthStore.getState().user).toBeNull();
      expectUserScopedStoresCleared();
    });

    it("STOMP接続を切断する(ログアウト後もイベントを受け続けない)", async () => {
      useAuthStore.setState({ user: ALICE, status: "ready" });

      await useAuthStore.getState().logout();

      expect(disconnectStomp).toHaveBeenCalledOnce();
    });
  });

  describe("login", () => {
    /**
     * ログアウトを経ずに別ユーザーでログインする経路(セッション切れ後の再ログイン等)への防御。
     * リセットが無いと、S-03ワークスペース選択画面は取得完了を待たず state をそのまま描画するため、
     * 新ユーザーの画面に前ユーザーのワークスペース名が一瞬表示される。
     */
    it("前ユーザーのキャッシュを引き継がない", async () => {
      useAuthStore.setState({ user: ALICE, status: "ready" });
      seedPreviousUserState();
      vi.mocked(authApi.login).mockResolvedValue(BOB);

      await useAuthStore.getState().login("bob", "password");

      expect(useAuthStore.getState().user).toEqual(BOB);
      expectUserScopedStoresCleared();
    });

    /**
     * リセットのタイミングの回帰テスト。API応答を待ってからリセットすると、
     * 待っている間は前ユーザーのデータが画面に残る。API呼び出し「前」に消えている必要がある。
     */
    it("APIの応答を待たずにキャッシュを破棄する", async () => {
      seedPreviousUserState();
      let notificationsWhenApiCalled: unknown;
      vi.mocked(authApi.login).mockImplementation(async () => {
        notificationsWhenApiCalled = useNotificationStore.getState().notifications;
        return BOB;
      });

      await useAuthStore.getState().login("bob", "password");

      expect(notificationsWhenApiCalled).toEqual([]);
    });

    it("ログインに失敗した場合でもキャッシュは破棄されたままになる", async () => {
      useAuthStore.setState({ user: ALICE, status: "ready" });
      seedPreviousUserState();
      vi.mocked(authApi.login).mockRejectedValue(new Error("認証に失敗しました"));

      await expect(useAuthStore.getState().login("bob", "wrong")).rejects.toThrow();

      // 失敗時に前ユーザーのデータが復活しないこと(復活すると認証されていない状態で他人の情報が見える)
      expectUserScopedStoresCleared();
    });
  });

  describe("signup", () => {
    it("前ユーザーのキャッシュを引き継がない", async () => {
      seedPreviousUserState();
      vi.mocked(authApi.signup).mockResolvedValue(BOB);

      await useAuthStore.getState().signup("bob", "password", "Bob");

      expect(useAuthStore.getState().user).toEqual(BOB);
      expectUserScopedStoresCleared();
    });
  });

  describe("init", () => {
    it("セッションが有効ならユーザーを復元しSTOMPへ接続する", async () => {
      vi.mocked(authApi.me).mockResolvedValue(ALICE);

      await useAuthStore.getState().init();

      expect(useAuthStore.getState().user).toEqual(ALICE);
      expect(useAuthStore.getState().status).toBe("ready");
      expect(getStompClient).toHaveBeenCalled();
    });

    /** 未認証(401)は異常ではないため、statusはreadyまで進めて画面を描画可能にする。 */
    it("セッションが無効ならuser=nullのままreadyにする", async () => {
      vi.mocked(authApi.me).mockRejectedValue(new Error("401"));

      await useAuthStore.getState().init();

      expect(useAuthStore.getState().user).toBeNull();
      expect(useAuthStore.getState().status).toBe("ready");
      expect(getStompClient).not.toHaveBeenCalled();
    });
  });
});
