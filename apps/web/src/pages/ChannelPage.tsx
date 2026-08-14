import { useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceStore } from "../store/workspaceStore";
import { channelApi } from "../api/resources";
import ChatView from "../components/chat/ChatView";
import ChannelMembersModal from "../components/modals/ChannelMembersModal";

export default function ChannelPage() {
  const { workspaceId, channelId } = useParams<{ workspaceId: string; channelId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { channels, workspaces, removeChannel } = useWorkspaceStore();
  const channel = channels.find((c) => c.id === channelId);
  const workspace = workspaces.find((w) => w.id === workspaceId);
  const [showMembers, setShowMembers] = useState(false);

  if (!workspaceId || !channelId) return null;

  async function handleDeleteChannel() {
    if (!confirm(`#${channel?.name} を削除しますか?この操作は取り消せません。`)) return;
    await channelApi.remove(workspaceId!, channelId!);
    removeChannel(channelId!);
    navigate(`/w/${workspaceId}`);
  }

  return (
    <>
      <ChatView
        key={channelId}
        workspaceId={workspaceId}
        scopeType="channel"
        scopeId={channelId}
        title={channel ? `${channel.type === "PRIVATE" ? "🔒" : "#"} ${channel.name}` : "..."}
        initialHighlightMessageId={searchParams.get("highlight")}
        initialReplyMessageId={searchParams.get("reply")}
        headerExtra={
          <div className="flex items-center gap-3 text-xs">
            <button onClick={() => setShowMembers(true)} className="text-slate-500 hover:text-slate-800">
              メンバー
            </button>
            {workspace?.myRole === "OWNER" && (
              <button onClick={handleDeleteChannel} className="text-red-500 hover:text-red-700">
                チャンネル削除
              </button>
            )}
          </div>
        }
      />
      {showMembers && channel && (
        <ChannelMembersModal
          workspaceId={workspaceId}
          channelId={channelId}
          channelName={channel.name}
          isWorkspaceOwner={workspace?.myRole === "OWNER"}
          onClose={() => setShowMembers(false)}
        />
      )}
    </>
  );
}
