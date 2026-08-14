import { FormEvent, useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { WorkspaceMemberDTO } from "@chatspace/shared";
import Modal from "./Modal";
import { workspaceApi } from "../../api/resources";
import { ApiError } from "../../api/client";
import { useAuthStore } from "../../store/authStore";
import { useWorkspaceStore } from "../../store/workspaceStore";
import { usePresenceStore } from "../../store/presenceStore";

interface Props {
  workspaceId: string;
  isOwner: boolean | undefined;
  onClose: () => void;
}

export default function WorkspaceMembersModal({ workspaceId, isOwner, onClose }: Props) {
  const navigate = useNavigate();
  const currentUser = useAuthStore((s) => s.user);
  const removeWorkspace = useWorkspaceStore((s) => s.removeWorkspace);
  const onlineUserIds = usePresenceStore((s) => s.onlineUserIds);
  const [members, setMembers] = useState<WorkspaceMemberDTO[]>([]);
  const [inviteUserId, setInviteUserId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    const { members } = await workspaceApi.members(workspaceId);
    setMembers(members);
    setLoading(false);
  }, [workspaceId]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleInvite(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!inviteUserId.trim()) return;
    try {
      await workspaceApi.invite(workspaceId, { userId: inviteUserId.trim() });
      setInviteUserId("");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "招待に失敗しました。");
    }
  }

  async function handleKick(userId: string) {
    if (!confirm("このメンバーをワークスペースからキックしますか?")) return;
    try {
      await workspaceApi.kick(workspaceId, userId);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "キックに失敗しました。");
    }
  }

  async function handleLeave() {
    if (!confirm("このワークスペースから退出しますか?")) return;
    try {
      await workspaceApi.leave(workspaceId);
      removeWorkspace(workspaceId);
      onClose();
      navigate("/", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "退出に失敗しました。");
    }
  }

  return (
    <Modal title="メンバー管理" onClose={onClose}>
      {isOwner && (
        <form onSubmit={handleInvite} className="mb-4 flex gap-2 border-b border-slate-200 pb-4">
          <input
            className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            value={inviteUserId}
            onChange={(e) => setInviteUserId(e.target.value)}
            placeholder="ユーザーIDを入力して招待"
          />
          <button
            type="submit"
            className="rounded-md bg-brand-500 px-3 py-2 text-sm font-semibold text-white hover:bg-brand-600"
          >
            招待
          </button>
        </form>
      )}
      {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

      {loading ? (
        <p className="text-sm text-slate-500">読み込み中...</p>
      ) : (
        <ul className="space-y-2">
          {members.map((m) => (
            <li key={m.user.id} className="flex items-center justify-between text-sm">
              <div className="flex items-center gap-1.5">
                <span
                  className={`h-2 w-2 shrink-0 rounded-full ${
                    onlineUserIds.has(m.user.id) ? "bg-emerald-500" : "bg-slate-300"
                  }`}
                  title={onlineUserIds.has(m.user.id) ? "オンライン" : "オフライン"}
                />
                <span className="font-medium text-slate-800">{m.user.displayName}</span>
                <span className="text-xs text-slate-500">@{m.user.userId}</span>
                {m.role === "OWNER" && (
                  <span className="ml-2 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-amber-700">
                    オーナー
                  </span>
                )}
              </div>
              {isOwner && m.role !== "OWNER" && m.user.id !== currentUser?.id && (
                <button
                  onClick={() => handleKick(m.user.id)}
                  className="text-xs text-red-500 hover:text-red-700 hover:underline"
                >
                  キック
                </button>
              )}
              {m.role !== "OWNER" && m.user.id === currentUser?.id && (
                <button onClick={handleLeave} className="text-xs text-red-500 hover:text-red-700 hover:underline">
                  退出する
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </Modal>
  );
}
