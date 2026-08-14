import { create } from "zustand";
import type { UserDTO } from "@chatspace/shared";
import { authApi, userApi } from "../api/resources";
import { disconnectSocket, getSocket } from "../lib/socket";

interface AuthState {
  user: UserDTO | null;
  status: "idle" | "loading" | "ready";
  init: () => Promise<void>;
  login: (userId: string, password: string) => Promise<void>;
  signup: (userId: string, password: string, displayName: string) => Promise<void>;
  logout: () => Promise<void>;
  updateProfile: (input: { displayName?: string; status?: string; avatarUrl?: string | null }) => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  status: "idle",

  init: async () => {
    set({ status: "loading" });
    try {
      const { user } = await authApi.me();
      set({ user, status: "ready" });
      getSocket();
    } catch {
      set({ user: null, status: "ready" });
    }
  },

  login: async (userId, password) => {
    const { user } = await authApi.login({ userId, password });
    set({ user });
    getSocket();
  },

  signup: async (userId, password, displayName) => {
    const { user } = await authApi.signup({ userId, password, displayName });
    set({ user });
    getSocket();
  },

  logout: async () => {
    await authApi.logout();
    disconnectSocket();
    set({ user: null });
  },

  updateProfile: async (input) => {
    const { user } = await userApi.updateProfile(input);
    set({ user });
  },
}));
