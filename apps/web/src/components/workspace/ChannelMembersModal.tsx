import { useEffect, useState } from "react";
import Modal from "../common/Modal";
import { channelApi } from "../../api/resources";
import { ApiError } from "../../api/client";
import type { ChannelMemberResponse } from "../../api/types";

interface ChannelMembersModalProps {
  workspaceId: string;
  channelId: string;
  channelName: string;
  isWorkspaceOwner: boolean;
  currentUserId: string;
  onClose: () => void;
  /** 自分自身がメンバーから外れた(削除された/退出した)際に呼ぶ。呼び出し元がサイドバー更新・遷移を担う。 */
  onSelfRemoved: () => void;
  restoreFocusTo?: HTMLElement | null;
}

/** S-09チャンネルメンバー管理モーダル(画面設計書)。 */
export default function ChannelMembersModal({
  workspaceId,
  channelId,
  channelName,
  isWorkspaceOwner,
  currentUserId,
  onClose,
  onSelfRemoved,
  restoreFocusTo,
}: ChannelMembersModalProps) {
  const [members, setMembers] = useState<ChannelMemberResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [inviteUserId, setInviteUserId] = useState("");
  const [inviting, setInviting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pendingUserId, setPendingUserId] = useState<string | null>(null);

  function loadMembers() {
    setLoading(true);
    channelApi.members(workspaceId, channelId).then((res) => {
      setMembers(res);
      setLoading(false);
    });
  }

  useEffect(loadMembers, [workspaceId, channelId]);

  async function handleInvite(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = inviteUserId.trim();
    if (!trimmed || inviting) return;
    setInviting(true);
    setError(null);
    try {
      await channelApi.invite(workspaceId, channelId, { userId: trimmed });
      setInviteUserId("");
      loadMembers();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "メンバーの追加に失敗しました。");
    } finally {
      setInviting(false);
    }
  }

  async function handleRemove(targetId: string, isSelf: boolean) {
    if (!targetId || pendingUserId) return;
    setPendingUserId(targetId);
    setError(null);
    try {
      await channelApi.removeMember(workspaceId, channelId, targetId);
      if (isSelf) {
        onClose();
        onSelfRemoved();
      } else {
        loadMembers();
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "操作に失敗しました。");
    } finally {
      setPendingUserId(null);
    }
  }

  return (
    <Modal title={`#${channelName} のメンバー`} onClose={onClose} restoreFocusTo={restoreFocusTo}>
      <div className="p-4">
        {isWorkspaceOwner && (
          <form onSubmit={handleInvite} className="mb-4 flex gap-2">
            <input
              autoFocus
              data-initial-focus
              type="text"
              value={inviteUserId}
              onChange={(e) => setInviteUserId(e.target.value)}
              placeholder="追加するユーザーID"
              className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
            />
            <button
              type="submit"
              disabled={!inviteUserId.trim() || inviting}
              className="rounded-md bg-brand-500 px-4 py-2 text-sm font-medium text-white hover:bg-brand-600 disabled:opacity-40"
            >
              追加
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
              const canRemove = isWorkspaceOwner && !isSelf;
              return (
                <li
                  key={m.user?.id}
                  className="flex items-center justify-between rounded-md px-2 py-2 hover:bg-slate-50"
                >
                  <span className="text-sm text-slate-700">
                    {m.user?.displayName} <span className="text-slate-400">@{m.user?.userId}</span>
                  </span>
                  {isSelf && (
                    <button
                      onClick={() => handleRemove(m.user?.id ?? "", true)}
                      disabled={pendingUserId === m.user?.id}
                      className="text-xs font-medium text-red-600 hover:underline disabled:opacity-40"
                    >
                      退出する
                    </button>
                  )}
                  {canRemove && (
                    <button
                      onClick={() => handleRemove(m.user?.id ?? "", false)}
                      disabled={pendingUserId === m.user?.id}
                      className="text-xs font-medium text-red-600 hover:underline disabled:opacity-40"
                    >
                      削除
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
