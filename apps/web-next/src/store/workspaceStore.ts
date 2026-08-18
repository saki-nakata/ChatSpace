import { create } from "zustand";
import type { WorkspaceResponse } from "../api/types";
import { workspaceApi } from "../api/resources";

interface WorkspaceState {
  workspaces: WorkspaceResponse[];
  status: "idle" | "loading" | "ready";
  fetchWorkspaces: () => Promise<void>;
  createWorkspace: (name: string) => Promise<WorkspaceResponse>;
  /** ログアウト・ログイン時に呼ぶ(`authStore`参照)。前ユーザーのワークスペース名が残るのを防ぐ。 */
  reset: () => void;
}

const INITIAL_STATE = {
  workspaces: [] as WorkspaceResponse[],
  status: "idle" as const,
};

/**
 * ワークスペース一覧のストア(計画書§9-A)。チャンネル/DM/メッセージ等の状態はフェーズ9-B以降で追加する
 * (S-04ワークスペースシェル以降のUI実装に合わせて拡張する設計)。
 */
export const useWorkspaceStore = create<WorkspaceState>((set, get) => ({
  ...INITIAL_STATE,

  fetchWorkspaces: async () => {
    set({ status: "loading" });
    const workspaces = await workspaceApi.list();
    set({ workspaces, status: "ready" });
  },

  createWorkspace: async (name) => {
    const workspace = await workspaceApi.create({ name });
    set({ workspaces: [...get().workspaces, workspace] });
    return workspace;
  },

  reset: () => set({ ...INITIAL_STATE }),
}));
