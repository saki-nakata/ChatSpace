import { useOutletContext, useParams, useSearchParams } from "react-router-dom";
import ChatView from "../components/chat/ChatView";
import { useAuthStore } from "../store/authStore";
import type { UserResponse } from "../api/types";
import type { WorkspaceShellContext } from "./WorkspaceShellPage";

/** S-06 DM画面。 */
export default function DMPage() {
  const { workspaceId, dmId } = useParams<{ workspaceId: string; dmId: string }>();
  const { dms, refreshSidebar } = useOutletContext<WorkspaceShellContext>();
  const [searchParams] = useSearchParams();
  // ChannelPageと同じ経路(S-12検索/S-13通知からの遷移、詳細はChannelPage参照)
  const initialOpenThreadId = searchParams.get("reply") ? searchParams.get("highlight") ?? undefined : undefined;
  const me = useAuthStore((s) => s.user);

  if (!workspaceId || !dmId || !me) return null;

  // サイドバーが既に取得済みのDM一覧(WorkspaceShellContext)から相手ユーザーを解決する
  // (以前はここで個別にdmApi.list()を再フェッチしていたが、二重取得かつ切替のたびに画面が
  // 一瞬白くなる原因だったため廃止。レビュー指摘対応)。
  const otherUser = dms.find((t) => t.id === dmId)?.otherUser ?? null;

  const userMap: Record<string, UserResponse> = { [me.id!]: me };
  if (otherUser) userMap[otherUser.id!] = otherUser;

  const header = (
    <div className="border-b border-slate-200 bg-white px-4 py-2">
      <h1 className="text-sm font-bold text-slate-800">{otherUser?.displayName ?? "不明なユーザー"}</h1>
      {otherUser?.status && <p className="text-xs text-slate-400">{otherUser.status}</p>}
    </div>
  );

  return (
    <ChatView
      workspaceId={workspaceId}
      scope={{ dmId }}
      userMap={userMap}
      placeholder={otherUser ? `${otherUser.displayName}さんへのメッセージ` : "メッセージを入力..."}
      initialOpenThreadId={initialOpenThreadId}
      header={header}
      onRead={refreshSidebar}
    />
  );
}
