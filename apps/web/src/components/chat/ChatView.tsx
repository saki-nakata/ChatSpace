import { ReactNode, useCallback, useEffect, useRef, useState } from "react";
import type { MessageDTO } from "@chatspace/shared";
import { SocketEvents } from "@chatspace/shared";
import { channelApi, dmApi, messageApi } from "../../api/resources";
import { getSocket } from "../../lib/socket";
import { useAuthStore } from "../../store/authStore";
import { useWorkspaceStore } from "../../store/workspaceStore";
import MessageList from "./MessageList";
import MessageComposer, { type MentionCandidate } from "./MessageComposer";
import ThreadPanel from "./ThreadPanel";
import TypingIndicator from "./TypingIndicator";

type ScopeType = "channel" | "dm";

interface Props {
  workspaceId: string;
  scopeType: ScopeType;
  scopeId: string;
  title: string;
  subtitle?: string;
  headerExtra?: ReactNode;
  /** 検索結果などから特定メッセージにジャンプする場合に指定する */
  initialHighlightMessageId?: string | null;
  /** ジャンプ先がスレッド返信だった場合、開くべき返信のID */
  initialReplyMessageId?: string | null;
}

const PAGE_SIZE = 50;

export default function ChatView({
  workspaceId,
  scopeType,
  scopeId,
  title,
  subtitle,
  headerExtra,
  initialHighlightMessageId,
  initialReplyMessageId,
}: Props) {
  const user = useAuthStore((s) => s.user);
  const clearChannelUnread = useWorkspaceStore((s) => s.clearChannelUnread);
  const clearDMUnread = useWorkspaceStore((s) => s.clearDMUnread);
  const channels = useWorkspaceStore((s) => s.channels);
  const dmThreads = useWorkspaceStore((s) => s.dmThreads);

  const [messages, setMessages] = useState<MessageDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMoreOlder, setHasMoreOlder] = useState(true);
  const [jumped, setJumped] = useState(Boolean(initialHighlightMessageId));
  const [highlightId, setHighlightId] = useState<string | null>(initialHighlightMessageId ?? null);
  const [readBoundary, setReadBoundary] = useState<string | null>(null);
  const [threadParent, setThreadParent] = useState<MessageDTO | null>(null);
  const [threadHighlightReplyId, setThreadHighlightReplyId] = useState<string | null>(null);
  const [mentionCandidates, setMentionCandidates] = useState<MentionCandidate[]>([]);

  useEffect(() => {
    if (scopeType !== "channel") {
      setMentionCandidates([]);
      return;
    }
    let cancelled = false;
    channelApi.members(workspaceId, scopeId).then(({ members }) => {
      if (cancelled) return;
      setMentionCandidates(members.map((m) => ({ userId: m.user.userId, displayName: m.user.displayName })));
    });
    return () => {
      cancelled = true;
    };
  }, [workspaceId, scopeType, scopeId]);

  const scopeMatches = useCallback(
    (m: MessageDTO) => (scopeType === "channel" ? m.channelId === scopeId : m.dmId === scopeId),
    [scopeType, scopeId],
  );

  const markRead = useCallback(async () => {
    if (scopeType === "channel") {
      await channelApi.markRead(workspaceId, scopeId);
      clearChannelUnread(scopeId);
    } else {
      await dmApi.markRead(workspaceId, scopeId);
      clearDMUnread(scopeId);
    }
  }, [workspaceId, scopeType, scopeId, clearChannelUnread, clearDMUnread]);

  // 未読区切り線の位置は、このスコープを開いた瞬間の既読位置(lastReadAt)で固定する。
  // markRead() で未読が0になった後もセッション中は表示を保つため ref で1度だけ捕捉する。
  const lastReadSnapshotRef = useRef<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setThreadParent(null);
    setThreadHighlightReplyId(null);
    setHighlightId(initialHighlightMessageId ?? null);
    setJumped(Boolean(initialHighlightMessageId));
    setReadBoundary(null);

    const store = scopeType === "channel" ? channels.find((c) => c.id === scopeId) : dmThreads.find((d) => d.id === scopeId);
    lastReadSnapshotRef.current = store?.lastReadAt ?? null;

    const socket = getSocket();
    socket.emit(scopeType === "channel" ? SocketEvents.JoinChannel : SocketEvents.JoinDM, scopeId);

    async function load() {
      if (initialHighlightMessageId) {
        const ctx =
          scopeType === "channel"
            ? await messageApi.contextChannel(workspaceId, scopeId, initialHighlightMessageId)
            : await messageApi.contextDM(workspaceId, scopeId, initialHighlightMessageId);
        if (cancelled) return;
        setMessages(ctx.messages);
        setHasMoreOlder(ctx.hasMoreOlder);
        setLoading(false);
        if (initialReplyMessageId) {
          const anchor = ctx.messages.find((m) => m.id === ctx.anchorMessageId);
          if (anchor) {
            setThreadParent(anchor);
            setThreadHighlightReplyId(initialReplyMessageId);
          }
        }
        return;
      }

      const { messages } =
        scopeType === "channel"
          ? await messageApi.listChannel(workspaceId, scopeId)
          : await messageApi.listDM(workspaceId, scopeId);
      if (cancelled) return;
      setMessages(messages);
      setHasMoreOlder(messages.length >= PAGE_SIZE);
      if (lastReadSnapshotRef.current) {
        const boundary = new Date(lastReadSnapshotRef.current).getTime();
        const first = messages.find((m) => new Date(m.createdAt).getTime() > boundary);
        setReadBoundary(first?.id ?? null);
      }
      setLoading(false);
      markRead();
    }
    load();
    return () => {
      cancelled = true;
    };
    // channels/dmThreads は既読スナップショット取得にのみ使い、更新の都度この effect を
    // 再実行したくない(unreadCount 変化のたびに再読込されてしまう)ため依存に含めない。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspaceId, scopeType, scopeId, markRead, initialHighlightMessageId, initialReplyMessageId]);

  async function handleLoadMore() {
    if (loadingMore || messages.length === 0) return;
    setLoadingMore(true);
    try {
      const oldest = messages[0];
      const { messages: older } =
        scopeType === "channel"
          ? await messageApi.listChannel(workspaceId, scopeId, oldest.createdAt)
          : await messageApi.listDM(workspaceId, scopeId, oldest.createdAt);
      setHasMoreOlder(older.length >= PAGE_SIZE);
      setMessages((prev) => [...older, ...prev]);
    } finally {
      setLoadingMore(false);
    }
  }

  async function backToLatest() {
    setJumped(false);
    setHighlightId(null);
    setLoading(true);
    const { messages } =
      scopeType === "channel"
        ? await messageApi.listChannel(workspaceId, scopeId)
        : await messageApi.listDM(workspaceId, scopeId);
    setMessages(messages);
    setHasMoreOlder(messages.length >= PAGE_SIZE);
    setLoading(false);
  }

  useEffect(() => {
    const socket = getSocket();

    const onCreated = (m: MessageDTO) => {
      if (!scopeMatches(m)) return;
      if (m.parentId) {
        setMessages((prev) => prev.map((x) => (x.id === m.parentId ? { ...x, replyCount: x.replyCount + 1 } : x)));
        return;
      }
      if (!jumped) {
        setMessages((prev) => [...prev, m]);
        if (m.author.id !== user?.id) markRead();
      }
    };
    const onUpdated = (m: MessageDTO) => {
      if (!scopeMatches(m) || m.parentId) return;
      setMessages((prev) => prev.map((x) => (x.id === m.id ? m : x)));
    };
    const onDeleted = (payload: { messageId: string; channelId: string | null; dmId: string | null }) => {
      const matches = scopeType === "channel" ? payload.channelId === scopeId : payload.dmId === scopeId;
      if (!matches) return;
      setMessages((prev) =>
        prev.map((x) => (x.id === payload.messageId ? { ...x, deletedAt: new Date().toISOString(), body: "" } : x)),
      );
    };
    const onReaction = (m: MessageDTO) => {
      if (!scopeMatches(m) || m.parentId) return;
      setMessages((prev) => prev.map((x) => (x.id === m.id ? m : x)));
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
  }, [scopeMatches, scopeType, scopeId, user?.id, markRead, jumped]);

  async function handleSend(body: string, attachmentIds: string[]) {
    if (scopeType === "channel") {
      await messageApi.createChannel(workspaceId, scopeId, { body, attachmentIds });
    } else {
      await messageApi.createDM(workspaceId, scopeId, { body, attachmentIds });
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
    setMessages((prev) => prev.map((x) => (x.id === updated.id ? updated : x)));
  }

  if (!user) return null;

  return (
    <div className="flex h-full overflow-x-hidden">
      <div className={`min-w-0 flex-1 flex-col ${threadParent ? "hidden sm:flex" : "flex"}`}>
        <div className="flex h-12 shrink-0 items-center justify-between border-b border-slate-200 px-4">
          <div className="min-w-0">
            <h2 className="truncate text-sm font-semibold text-slate-800">{title}</h2>
            {subtitle && <p className="truncate text-xs text-slate-500">{subtitle}</p>}
          </div>
          <div className="flex items-center gap-3">
            {jumped && (
              <button
                onClick={backToLatest}
                className="rounded-md border border-slate-200 px-2 py-1 text-xs text-slate-500 hover:bg-slate-50"
              >
                最新に戻る
              </button>
            )}
            {headerExtra}
          </div>
        </div>

        {loading ? (
          <div className="flex flex-1 items-center justify-center text-sm text-slate-500">読み込み中...</div>
        ) : (
          <MessageList
            messages={messages}
            currentUserId={user.id}
            onEdit={handleEdit}
            onDelete={handleDelete}
            onReact={handleReact}
            onOpenThread={(m) => setThreadParent(m)}
            onLoadMore={handleLoadMore}
            hasMoreOlder={hasMoreOlder}
            loadingMore={loadingMore}
            highlightMessageId={highlightId}
            unreadDividerBefore={readBoundary}
          />
        )}

        <TypingIndicator scopeType={scopeType} scopeId={scopeId} />
        <MessageComposer
          onSend={handleSend}
          scopeType={scopeType}
          scopeId={scopeId}
          mentionCandidates={scopeType === "channel" ? mentionCandidates : undefined}
        />
      </div>

      {threadParent && (
        <ThreadPanel
          workspaceId={workspaceId}
          scopeType={scopeType}
          scopeId={scopeId}
          parentMessage={threadParent}
          highlightReplyId={threadHighlightReplyId}
          onClose={() => setThreadParent(null)}
          onParentUpdated={(m) => setMessages((prev) => prev.map((x) => (x.id === m.id ? m : x)))}
        />
      )}
    </div>
  );
}
