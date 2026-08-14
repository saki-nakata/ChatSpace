import { useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceStore } from "../store/workspaceStore";
import ChatView from "../components/chat/ChatView";

export default function DMPage() {
  const { workspaceId, dmId } = useParams<{ workspaceId: string; dmId: string }>();
  const [searchParams] = useSearchParams();
  const dmThreads = useWorkspaceStore((s) => s.dmThreads);
  const dm = dmThreads.find((t) => t.id === dmId);

  if (!workspaceId || !dmId) return null;

  return (
    <ChatView
      key={dmId}
      workspaceId={workspaceId}
      scopeType="dm"
      scopeId={dmId}
      title={dm ? dm.otherUser.displayName : "..."}
      subtitle={dm?.otherUser.status ?? undefined}
      initialHighlightMessageId={searchParams.get("highlight")}
      initialReplyMessageId={searchParams.get("reply")}
    />
  );
}
