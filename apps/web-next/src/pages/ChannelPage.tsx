import { useEffect, useState } from "react";
import { useNavigate, useOutletContext, useParams, useSearchParams } from "react-router-dom";
import ChatView from "../components/chat/ChatView";
import ChannelMembersModal from "../components/workspace/ChannelMembersModal";
import { channelApi } from "../api/resources";
import { ApiError } from "../api/client";
import { useAuthStore } from "../store/authStore";
import type { UserResponse } from "../api/types";
import type { WorkspaceShellContext } from "./WorkspaceShellPage";

/** S-05チャンネル画面。ヘッダー(チャンネル名・メンバー管理・オーナー限定の削除)を含む。 */
export default function ChannelPage() {
  const { workspaceId, channelId } = useParams<{ workspaceId: string; channelId: string }>();
  const navigate = useNavigate();
  const currentUser = useAuthStore((s) => s.user);
  const { channels, isOwner, refreshSidebar } = useOutletContext<WorkspaceShellContext>();
  const [searchParams] = useSearchParams();
  // ?highlight=<ジャンプ先ID(スレッド返信の場合は親メッセージID)>&reply=<返信ID>(S-12検索/S-13通知からの遷移)。
  // replyがある場合のみS-07スレッドパネルを自動的に開く(詳細はChatViewのjumpMessageId/jumpReplyId参照)。
  const jumpMessageId = searchParams.get("highlight") ?? undefined;
  const jumpReplyId = searchParams.get("reply") ?? undefined;
  // チャンネル切替時もあえてリセットしない(前チャンネルのuserMapを一時的に見せたまま新しい方に
  // 差し替える)。ヘッダーごと空にするより自然で、切替のたびに画面全体が一瞬白くなる問題を避けられる
  // (レビュー指摘対応)。ChatView自体は`scopeKey`変更で自律的にメッセージを再取得するため、
  // userMapの読み込みでページ全体の描画をブロックする必要が無い。
  const [userMap, setUserMap] = useState<Record<string, UserResponse>>({});
  const [membersOpen, setMembersOpen] = useState(false);
  const [membersTrigger, setMembersTrigger] = useState<HTMLElement | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  useEffect(() => {
    if (!workspaceId || !channelId) return;
    channelApi.members(workspaceId, channelId).then((members) => {
      const map: Record<string, UserResponse> = {};
      for (const m of members) {
        if (m.user) map[m.user.id!] = m.user;
      }
      setUserMap(map);
    });
  }, [workspaceId, channelId]);

  if (!workspaceId || !channelId || !currentUser) return null;

  const channel = channels.find((c) => c.id === channelId);
  const channelName = channel?.name ?? "";

  async function handleDeleteChannel() {
    if (!workspaceId || !channelId) return;
    if (!window.confirm(`#${channelName} を削除しますか?この操作は取り消せません。`)) return;
    try {
      await channelApi.delete(workspaceId, channelId);
      refreshSidebar();
      navigate(`/w/${workspaceId}`);
    } catch (err) {
      setDeleteError(err instanceof ApiError ? err.message : "チャンネルの削除に失敗しました。");
    }
  }

  const header = (
    <div className="border-b border-slate-200 bg-white px-4 py-2">
      <div className="flex items-center justify-between">
        <h1 className="text-sm font-bold text-slate-800">
          {channel?.type === "PRIVATE" ? "🔒" : "#"} {channelName}
        </h1>
        <div className="flex items-center gap-3">
          <button
            onClick={(e) => {
              setMembersTrigger(e.currentTarget);
              setMembersOpen(true);
            }}
            className="text-xs font-medium text-slate-500 hover:text-slate-700"
          >
            メンバー
          </button>
          {isOwner && (
            <button
              onClick={handleDeleteChannel}
              className="text-xs font-medium text-red-600 hover:underline"
            >
              チャンネル削除
            </button>
          )}
        </div>
      </div>
      {deleteError && <p className="mt-1 text-xs text-red-600">{deleteError}</p>}
    </div>
  );

  return (
    <>
      <ChatView
        workspaceId={workspaceId}
        scope={{ channelId }}
        userMap={userMap}
        placeholder="メッセージを入力..."
        jumpMessageId={jumpMessageId}
        jumpReplyId={jumpReplyId}
        initialUnreadCount={channel?.unreadCount ?? undefined}
        header={header}
        onRead={refreshSidebar}
      />
      {membersOpen && (
        <ChannelMembersModal
          workspaceId={workspaceId}
          channelId={channelId}
          channelName={channelName}
          isWorkspaceOwner={isOwner}
          currentUserId={currentUser.id ?? ""}
          onClose={() => setMembersOpen(false)}
          onSelfRemoved={() => {
            refreshSidebar();
            navigate(`/w/${workspaceId}`);
          }}
          restoreFocusTo={membersTrigger}
        />
      )}
    </>
  );
}
