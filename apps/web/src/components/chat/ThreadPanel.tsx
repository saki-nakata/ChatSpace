import { useEffect, useState } from "react";
import type { MessageDTO } from "@chatspace/shared";
import { SocketEvents } from "@chatspace/shared";
import { messageApi } from "../../api/resources";
import { getSocket } from "../../lib/socket";
import { useAuthStore } from "../../store/authStore";
import MessageList from "./MessageList";
import MessageComposer from "./MessageComposer";

type ScopeType = "channel" | "dm";

interface Props {
  workspaceId: string;
  scopeType: ScopeType;
  scopeId: string; // channelId or dmId
  parentMessage: MessageDTO;
  highlightReplyId?: string | null;
  onClose: () => void;
  onParentUpdated: (message: MessageDTO) => void;
}

export default function ThreadPanel({
  workspaceId,
  scopeType,
  scopeId,
  parentMessage,
  highlightReplyId,
  onClose,
  onParentUpdated,
}: Props) {
  const user = useAuthStore((s) => s.user);
  const [parent, setParent] = useState(parentMessage);
  const [replies, setReplies] = useState<MessageDTO[]>([]);

  useEffect(() => {
    setParent(parentMessage);
  }, [parentMessage]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      const { replies } =
        scopeType === "channel"
          ? await messageApi.repliesChannel(workspaceId, scopeId, parent.id)
          : await messageApi.repliesDM(workspaceId, scopeId, parent.id);
      if (!cancelled) setReplies(replies);
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [workspaceId, scopeType, scopeId, parent.id]);

  useEffect(() => {
    const socket = getSocket();
    const onCreated = (m: MessageDTO) => {
      if (m.parentId !== parent.id) return;
      setReplies((prev) => [...prev, m]);
    };
    const onUpdated = (m: MessageDTO) => {
      if (m.id === parent.id) {
        setParent(m);
        onParentUpdated(m);
        return;
      }
      if (m.parentId !== parent.id) return;
      setReplies((prev) => prev.map((r) => (r.id === m.id ? m : r)));
    };
    const onDeleted = (payload: { messageId: string }) => {
      setReplies((prev) => prev.map((r) => (r.id === payload.messageId ? { ...r, deletedAt: new Date().toISOString(), body: "" } : r)));
    };
    const onReaction = (m: MessageDTO) => {
      if (m.id === parent.id) {
        setParent(m);
        return;
      }
      if (m.parentId !== parent.id) return;
      setReplies((prev) => prev.map((r) => (r.id === m.id ? m : r)));
    };

    socket.on(SocketEvents.MessageCreated, onCreated);
    socket.on(SocketEvents.MessageUpdated, onUpdated);
    socket.on(SocketEvents.MessageDeleted, onDeleted);
    socket.on(SocketEvents.ReactionUpdated, onReaction);
    return () => {
      socket.off(SocketEvents.MessageCreated, onCreated);
      socket.off(SocketEvents.MessageUpdated, onUpdated);
      socket.off(SocketEvents.MessageDeleted, onDeleted);
      socket.off(SocketEvents.ReactionUpdated, onReaction);
    };
  }, [parent.id, onParentUpdated]);

  async function handleSend(body: string, attachmentIds: string[]) {
    if (scopeType === "channel") {
      await messageApi.createChannel(workspaceId, scopeId, { body, parentId: parent.id, attachmentIds });
    } else {
      await messageApi.createDM(workspaceId, scopeId, { body, parentId: parent.id, attachmentIds });
    }
  }

  async function handleEdit(message: MessageDTO, body: string) {
    if (scopeType === "channel") {
      await messageApi.updateChannel(workspaceId, scopeId, message.id, body);
    } else {
      await messageApi.updateDM(workspaceId, scopeId, message.id, body);
    }
  }

  async function handleDelete(message: MessageDTO) {
    if (scopeType === "channel") {
      await messageApi.deleteChannel(workspaceId, scopeId, message.id);
    } else {
      await messageApi.deleteDM(workspaceId, scopeId, message.id);
    }
  }

  async function handleReact(message: MessageDTO, emoji: string) {
    const { message: updated } =
      scopeType === "channel"
        ? await messageApi.reactChannel(workspaceId, scopeId, message.id, emoji)
        : await messageApi.reactDM(workspaceId, scopeId, message.id, emoji);
    if (updated.id === parent.id) {
      setParent(updated);
    } else {
      setReplies((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
    }
  }

  if (!user) return null;

  return (
    <div className="flex w-full shrink-0 flex-col border-l border-slate-200 bg-white sm:w-96">
      <div className="flex h-14 items-center justify-between border-b border-slate-200 px-4">
        <span className="text-sm font-semibold text-slate-700">スレッド</span>
        <button onClick={onClose} aria-label="スレッドを閉じる" className="text-slate-500 hover:text-slate-700">
          ✕
        </button>
      </div>

      <div className="border-b border-slate-100 pb-2">
        <MessageList
          messages={[parent]}
          currentUserId={user.id}
          onEdit={handleEdit}
          onDelete={handleDelete}
          onReact={handleReact}
          showThreadButton={false}
        />
      </div>

      <MessageList
        messages={replies}
        currentUserId={user.id}
        onEdit={handleEdit}
        onDelete={handleDelete}
        onReact={handleReact}
        showThreadButton={false}
        highlightMessageId={highlightReplyId}
      />

      <MessageComposer onSend={handleSend} placeholder="スレッドに返信..." />
    </div>
  );
}
