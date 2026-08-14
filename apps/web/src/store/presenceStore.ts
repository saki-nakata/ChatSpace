import { create } from "zustand";

interface PresenceState {
  onlineUserIds: Set<string>;
  setOnline: (userIds: string[]) => void;
  setUserPresence: (userId: string, online: boolean) => void;
  isOnline: (userId: string) => boolean;
}

export const usePresenceStore = create<PresenceState>((set, get) => ({
  onlineUserIds: new Set(),

  setOnline: (userIds) => set({ onlineUserIds: new Set(userIds) }),

  setUserPresence: (userId, online) =>
    set((s) => {
      const next = new Set(s.onlineUserIds);
      if (online) next.add(userId);
      else next.delete(userId);
      return { onlineUserIds: next };
    }),

  isOnline: (userId) => get().onlineUserIds.has(userId),
}));
