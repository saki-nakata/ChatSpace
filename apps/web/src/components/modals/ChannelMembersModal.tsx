import { FormEvent, useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { UserDTO } from "@chatspace/shared";
import Modal from "./Modal";
import { channelApi } from "../../api/resources";
import { ApiError } from "../../api/client";
import { useAuthStore } from "../../store/authStore";
import { useWorkspaceStore } from "../../store/workspaceStore";

interface Props {
  workspaceId: string;
  channelId: string;
  channelName: string;
  isWorkspaceOwner: boolean | undefined;
  onClose: () => void;
}

export default function ChannelMembersModal({ workspaceId, channelId, channelName, isWorkspaceOwner, onClose }: Props) {
  const navigate = useNavigate();
  const currentUser = useAuthStore((s) => s.user);
  const removeChannelFromStore = useWorkspaceStore((s) => s.removeChannel);
  const [members, setMembers] = useState<{ user: UserDTO; joinedAt: string }[]>([]);
  const [addUserId, setAddUserId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    const { members } = await channelApi.members(workspaceId, channelId);
    setMembers(members);
    setLoading(false);
  }, [workspaceId, channelId]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleAdd(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!addUserId.trim()) return;
    try {
      await channelApi.addMember(workspaceId, channelId, addUserId.trim());
      setAddUserId("");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "追加に失敗しました。");
    }
  }

  async function handleRemove(userId: string) {
    try {
      await channelApi.removeMember(workspaceId, channelId, userId);
      if (userId === currentUser?.id) {
        removeChannelFromStore(channelId);
        onClose();
        navigate(`/w/${workspaceId}`);
        return;
      }
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "操作に失敗しました。");
    }
  }

  return (
    <Modal title={`#${channelName} のメンバー`} onClose={onClose}>
      {isWorkspaceOwner && (
        <form onSubmit={handleAdd} className="mb-4 flex gap-2 border-b border-slate-200 pb-4">
          <input
            className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            value={addUserId}
            onChange={(e) => setAddUserId(e.target.value)}
            placeholder="追加するユーザーID"
          />
          <button
            type="submit"
            className="rounded-md bg-brand-500 px-3 py-2 text-sm font-semibold text-white hover:bg-brand-600"
          >
            追加
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
              <div>
                <span className="font-medium text-slate-800">{m.user.displayName}</span>
                <span className="ml-1 text-xs text-slate-500">@{m.user.userId}</span>
              </div>
              {(m.user.id === currentUser?.id || isWorkspaceOwner) && (
                <button
                  onClick={() => handleRemove(m.user.id)}
                  className="text-xs text-red-500 hover:text-red-700 hover:underline"
                >
                  {m.user.id === currentUser?.id ? "退出する" : "削除"}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </Modal>
  );
}
