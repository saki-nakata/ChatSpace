import { create } from "zustand";
import type { UpdateProfileRequest, UserResponse } from "../api/types";
import { authApi, userApi } from "../api/resources";
import { disconnectStomp, getStompClient } from "../realtime/stomp";

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
 * 認証状態のストア(計画書§9-A)。apps/api-javaのAuthControllerは`UserResponse`をトップレベルで返す
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
    const user = await authApi.login({ userId, password });
    set({ user });
    getStompClient();
  },

  signup: async (userId, password, displayName) => {
    const user = await authApi.signup({ userId, password, displayName });
    set({ user });
    getStompClient();
  },

  logout: async () => {
    await authApi.logout();
    disconnectStomp();
    set({ user: null });
  },

  updateProfile: async (updates) => {
    const user = await userApi.updateProfile(updates);
    set({ user });
  },
}));
