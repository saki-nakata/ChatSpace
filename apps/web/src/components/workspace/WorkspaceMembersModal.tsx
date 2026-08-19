import { useEffect, useState } from "react";
import Modal from "../common/Modal";
import { workspaceApi } from "../../api/resources";
import { ApiError } from "../../api/client";
import type { WorkspaceMemberResponse } from "../../api/types";

interface WorkspaceMembersModalProps {
  workspaceId: string;
  currentUserId: string;
  onlineUserIds: Set<string>;
  onClose: () => void;
  /** 自分自身がキックされた/退出した際に呼ぶ。呼び出し元がストア更新・遷移を担う。 */
  onSelfLeft: () => void;
  restoreFocusTo?: HTMLElement | null;
}

/** S-10ワークスペースメンバー管理モーダル(画面設計書)。 */
export default function WorkspaceMembersModal({
  workspaceId,
  currentUserId,
  onlineUserIds,
  onClose,
  onSelfLeft,
  restoreFocusTo,
}: WorkspaceMembersModalProps) {
  const [members, setMembers] = useState<WorkspaceMemberResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [inviteUserId, setInviteUserId] = useState("");
  const [inviting, setInviting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pendingUserId, setPendingUserId] = useState<string | null>(null);

  function loadMembers() {
    setLoading(true);
    workspaceApi.members(workspaceId).then((res) => {
      setMembers(res);
      setLoading(false);
    });
  }

  useEffect(loadMembers, [workspaceId]);

  const isOwner = members.some((m) => m.user?.id === currentUserId && m.role === "OWNER");

  async function handleInvite(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = inviteUserId.trim();
    if (!trimmed || inviting) return;
    setInviting(true);
    setError(null);
    try {
      await workspaceApi.invite(workspaceId, { userId: trimmed });
      setInviteUserId("");
      loadMembers();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "招待に失敗しました。");
    } finally {
      setInviting(false);
    }
  }

  async function handleKick(targetId: string, displayName: string | undefined) {
    if (!targetId || pendingUserId) return;
    if (!window.confirm(`${displayName ?? "このメンバー"}をワークスペースからキックしますか?`)) return;
    setPendingUserId(targetId);
    setError(null);
    try {
      await workspaceApi.kick(workspaceId, { userId: targetId });
      loadMembers();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "キックに失敗しました。");
    } finally {
      setPendingUserId(null);
    }
  }

  async function handleLeave() {
    if (pendingUserId) return;
    if (!window.confirm("このワークスペースから退出しますか?")) return;
    setPendingUserId(currentUserId);
    setError(null);
    try {
      await workspaceApi.leave(workspaceId);
      onClose();
      onSelfLeft();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "退出に失敗しました。");
      setPendingUserId(null);
    }
  }

  return (
    <Modal title="メンバー管理" onClose={onClose} restoreFocusTo={restoreFocusTo}>
      <div className="p-4">
        {isOwner && (
          <form onSubmit={handleInvite} className="mb-4 flex gap-2">
            <input
              autoFocus
              data-initial-focus
              type="text"
              value={inviteUserId}
              onChange={(e) => setInviteUserId(e.target.value)}
              placeholder="招待するユーザーID"
              className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
            />
            <button
              type="submit"
              disabled={!inviteUserId.trim() || inviting}
              className="rounded-md bg-brand-500 px-4 py-2 text-sm font-medium text-white hover:bg-brand-600 disabled:opacity-40"
            >
              招待
            </button>
          </form>
        )}

        {error && <p className="mb-2 text-sm text-red-600">{error}</p>}

        {loading ? (
          <p className="py-4 text-center text-sm text-slate-400">読み込み中...</p>
        ) : (
          <ul className="max-h-80 space-y-1 overflow-y-auto">
            {members.map((m) => {
              const isSelf = m.user?.id === currentUserId;
              const online = m.user?.id ? onlineUserIds.has(m.user.id) : false;
              const canKick = isOwner && m.role !== "OWNER" && !isSelf;
              return (
                <li
                  key={m.user?.id}
                  className="flex items-center justify-between rounded-md px-2 py-2 hover:bg-slate-50"
                >
                  <span className="flex items-center gap-2 text-sm text-slate-700">
                    <span
                      className={`h-2 w-2 rounded-full ${online ? "bg-green-500" : "bg-slate-300"}`}
                      aria-hidden="true"
                    />
                    {m.user?.displayName} <span className="text-slate-400">@{m.user?.userId}</span>
                    {m.role === "OWNER" && (
                      <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-medium text-amber-700">
                        オーナー
                      </span>
                    )}
                  </span>
                  {isSelf && !isOwner && (
                    <button
                      onClick={handleLeave}
                      disabled={pendingUserId === currentUserId}
                      className="text-xs font-medium text-red-600 hover:underline disabled:opacity-40"
                    >
                      退出する
                    </button>
                  )}
                  {canKick && (
                    <button
                      onClick={() => handleKick(m.user?.id ?? "", m.user?.displayName)}
                      disabled={pendingUserId === m.user?.id}
                      className="text-xs font-medium text-red-600 hover:underline disabled:opacity-40"
                    >
                      キック
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </Modal>
  );
}
