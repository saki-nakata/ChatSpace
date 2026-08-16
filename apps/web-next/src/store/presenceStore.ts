import { create } from "zustand";
import { workspaceApi } from "../api/resources";
import { workspacePresenceTopic } from "../realtime/destinations";
import { subscribeJson, type RealtimeEvent } from "../realtime/stomp";

interface PresencePayload {
  userId: string;
  online: boolean;
}

interface PresenceState {
  onlineUserIds: Set<string>;
  fetchInitialPresence: (workspaceId: string) => Promise<void>;
  /** `/topic/workspaces.{id}.presence`のPRESENCE_UPDATEDイベントを購読する。戻り値の関数で購読解除する。 */
  subscribeRealtime: (workspaceId: string) => () => void;
}

export const usePresenceStore = create<PresenceState>((set) => ({
  onlineUserIds: new Set(),

  fetchInitialPresence: async (workspaceId) => {
    const userIds = await workspaceApi.presence(workspaceId);
    set({ onlineUserIds: new Set(userIds) });
  },

  subscribeRealtime: (workspaceId) => {
    return subscribeJson<PresencePayload>(
      workspacePresenceTopic(workspaceId),
      (event: RealtimeEvent<PresencePayload>) => {
        if (event.type !== "PRESENCE_UPDATED") return;
        set((state) => {
          const next = new Set(state.onlineUserIds);
          if (event.payload.online) {
            next.add(event.payload.userId);
          } else {
            next.delete(event.payload.userId);
          }
          return { onlineUserIds: next };
        });
      },
    );
  },
}));
