import { useEffect, useRef } from "react";
import type { MessageDTO } from "@chatspace/shared";
import MessageItem from "./MessageItem";
import { formatDateDivider, isDifferentDay } from "../../lib/time";

interface Props {
  messages: MessageDTO[];
  currentUserId: string;
  onEdit: (message: MessageDTO, body: string) => Promise<void>;
  onDelete: (message: MessageDTO) => Promise<void>;
  onReact: (message: MessageDTO, emoji: string) => Promise<void>;
  onOpenThread?: (message: MessageDTO) => void;
  showThreadButton?: boolean;
  onLoadMore?: () => void;
  hasMoreOlder?: boolean;
  loadingMore?: boolean;
  highlightMessageId?: string | null;
  /** 未読区切り線: これより新しい(createdAt >)先頭メッセージの直前に線を引く */
  unreadDividerBefore?: string | null;
}

export default function MessageList({
  messages,
  currentUserId,
  onEdit,
  onDelete,
  onReact,
  onOpenThread,
  showThreadButton = true,
  onLoadMore,
  hasMoreOlder,
  loadingMore,
  highlightMessageId,
  unreadDividerBefore,
}: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const highlightRef = useRef<HTMLDivElement>(null);
  const prevScrollHeightRef = useRef<number | null>(null);
  const lastMessageIdRef = useRef<string | null>(null);
  const hasScrolledToHighlightRef = useRef(false);

  // メッセージ配列が更新されたときのスクロール制御。
  // 「上スクロールで過去ログを読み込んだ直後」は見た目の位置を維持し、
  // 「末尾に新着が追加された/初回表示」は下端へ、それ以外は何もしない。
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    if (highlightMessageId && !hasScrolledToHighlightRef.current) {
      if (highlightRef.current) {
        highlightRef.current.scrollIntoView({ block: "center" });
        hasScrolledToHighlightRef.current = true;
        lastMessageIdRef.current = messages[messages.length - 1]?.id ?? null;
        return;
      }
    }

    if (prevScrollHeightRef.current !== null) {
      const delta = container.scrollHeight - prevScrollHeightRef.current;
      container.scrollTop += delta;
      prevScrollHeightRef.current = null;
      lastMessageIdRef.current = messages[messages.length - 1]?.id ?? null;
      return;
    }

    const newLastId = messages[messages.length - 1]?.id ?? null;
    if (!highlightMessageId && (lastMessageIdRef.current === null || newLastId !== lastMessageIdRef.current)) {
      bottomRef.current?.scrollIntoView({ block: "end" });
    }
    lastMessageIdRef.current = newLastId;
  }, [messages, highlightMessageId]);

  function handleScroll() {
    const container = containerRef.current;
    if (!container || !onLoadMore || loadingMore || hasMoreOlder === false) return;
    if (container.scrollTop < 80) {
      prevScrollHeightRef.current = container.scrollHeight;
      onLoadMore();
    }
  }

  return (
    <div ref={containerRef} onScroll={handleScroll} className="flex-1 overflow-y-auto">
      {/* min-h-full + justify-end で、メッセージが少ないときは Slack と同じく下詰めにする */}
      <div className="flex min-h-full flex-col justify-end py-3">
        {loadingMore && <p className="py-2 text-center text-xs text-slate-500">過去のメッセージを読み込み中...</p>}
        {hasMoreOlder === false && messages.length > 0 && (
          <p className="py-2 text-center text-xs text-slate-500">これより過去のメッセージはありません</p>
        )}
        {messages.length === 0 && (
          <p className="px-4 py-8 text-center text-sm text-slate-500">まだメッセージはありません。最初のメッセージを送ってみましょう。</p>
        )}
        {messages.map((m, i) => {
          const prev = messages[i - 1];
          const showDateDivider = !prev || isDifferentDay(prev.createdAt, m.createdAt);
          return (
            <div key={m.id}>
              {showDateDivider && (
                <div className="my-3 flex items-center gap-2 px-3">
                  <div className="h-px flex-1 bg-slate-200" />
                  <span className="text-xs font-medium text-slate-500">{formatDateDivider(m.createdAt)}</span>
                  <div className="h-px flex-1 bg-slate-200" />
                </div>
              )}
              {unreadDividerBefore && m.id === unreadDividerBefore && (
                <div className="my-2 flex items-center gap-2 px-3">
                  <div className="h-px flex-1 bg-red-300" />
                  <span className="text-xs font-semibold text-red-500">ここから未読</span>
                  <div className="h-px flex-1 bg-red-300" />
                </div>
              )}
              <div
                ref={m.id === highlightMessageId ? highlightRef : undefined}
                className={m.id === highlightMessageId ? "rounded bg-amber-100 transition-colors duration-1000" : ""}
              >
                <MessageItem
                  message={m}
                  isOwn={m.author.id === currentUserId}
                  onEdit={(body) => onEdit(m, body)}
                  onDelete={() => onDelete(m)}
                  onReact={(emoji) => onReact(m, emoji)}
                  onOpenThread={onOpenThread ? () => onOpenThread(m) : undefined}
                  showThreadButton={showThreadButton}
                />
              </div>
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>
    </div>
  );
}
