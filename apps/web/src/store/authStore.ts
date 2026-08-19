import { create } from "zustand";
import type { UpdateProfileRequest, UserResponse } from "../api/types";
import { authApi, userApi } from "../api/resources";
import { disconnectStomp, getStompClient } from "../realtime/stomp";
import { useNotificationStore } from "./notificationStore";
import { usePresenceStore } from "./presenceStore";
import { useWorkspaceStore } from "./workspaceStore";

/**
 * ユーザーに紐づくキャッシュを一括初期化する。同じタブで別ユーザーがログインした際に、前ユーザーの
 * 通知内容・ワークスペース名・プレゼンスが残って見えるのを防ぐ(PR #6レビュー対応)。
 * 特に`workspaceStore`はS-03ワークスペース選択画面が取得完了を待たずstateをそのまま描画するため、
 * リセットしないと新ユーザーの画面に前ユーザーのワークスペース名が一瞬表示される。
 */
function resetUserScopedStores() {
  useNotificationStore.getState().reset();
  useWorkspaceStore.getState().reset();
  usePresenceStore.getState().reset();
}

interface AuthState {
  user: UserResponse | null;
  status: "idle" | "loading" | "ready";
  init: () => Promise<void>;
  login: (userId: string, password: string) => Promise<void>;
  signup: (userId: string, password: string, displayName: string) => Promise<void>;
  logout: () => Promise<void>;
  updateProfile: (updates: UpdateProfileRequest) => Promise<void>;
}

/**
 * 認証状態のストア(計画書§9-A)。apps/apiのAuthControllerは`UserResponse`をトップレベルで返す
 * (旧apps/webの`{ user }`ラップ形式とは異なる点に注意)。
 */
export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  status: "idle",

  init: async () => {
    set({ status: "loading" });
    try {
      const user = await authApi.me();
      set({ user, status: "ready" });
      getStompClient();
    } catch {
      set({ user: null, status: "ready" });
    }
  },

  login: async (userId, password) => {
    // 前ユーザーのキャッシュが新しいユーザーの画面に混入するのを防ぐ(ログアウトを経ずに
    // 別ユーザーでログインするケースへの防御。主な発生経路はlogout側だが念のため)。
    resetUserScopedStores();
    const user = await authApi.login({ userId, password });
    set({ user });
    getStompClient();
  },

  signup: async (userId, password, displayName) => {
    resetUserScopedStores();
    const user = await authApi.signup({ userId, password, displayName });
    set({ user });
    getStompClient();
  },

  logout: async () => {
    await authApi.logout();
    disconnectStomp();
    set({ user: null });
    resetUserScopedStores();
  },

  updateProfile: async (updates) => {
    const user = await userApi.updateProfile(updates);
    set({ user });
  },
}));
