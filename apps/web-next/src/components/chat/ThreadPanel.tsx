import { useEffect, useRef, useState } from "react";
import type { UserResponse } from "../../api/types";
import { isSameGroup } from "../../lib/time";
import MessageComposer from "./MessageComposer";
import MessageItem from "./MessageItem";
import type { DisplayMessage } from "./types";

interface MentionSource {
  workspaceId: string;
  channelId: string;
}

interface ThreadPanelProps {
  parentMessage: DisplayMessage;
  replies: DisplayMessage[];
  userMap: Record<string, UserResponse>;
  currentUserId: string;
  loading: boolean;
  hasMore: boolean;
  loadingMore: boolean;
  onLoadMore: () => void;
  onClose: () => void;
  onSend: (body: string, attachmentIds?: string[]) => Promise<void>;
  onToggleReaction: (messageId: string, emoji: string) => void;
  onEdit: (messageId: string, body: string) => Promise<void>;
  onDelete: (messageId: string) => void;
  mentionSource?: MentionSource;
  /**
   * 検索・通知からスレッド返信自体へジャンプした場合の対象ID。ChatViewが`around`付きで取得するため
   * 常に`replies`に含まれる(A-2)。
   */
  highlightReplyId?: string;
  /** `highlightReplyId`を実際にスクロール+ハイライトした直後に呼ぶ(呼び出し元がURLのジャンプ用パラメータを消す)。 */
  onHighlighted?: () => void;
}

const HIGHLIGHT_DURATION_MS = 2500;

/** S-07スレッドパネル(フェーズ9-C)。初期20件+「さらに20件」ボタン、WebSocket新着返信は末尾に追加する。 */
export default function ThreadPanel({
  parentMessage,
  replies,
  userMap,
  currentUserId,
  loading,
  hasMore,
  loadingMore,
  onLoadMore,
  onClose,
  onSend,
  onToggleReaction,
  onEdit,
  onDelete,
  mentionSource,
  highlightReplyId,
  onHighlighted,
}: ThreadPanelProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const replyRefs = useRef<Map<string, HTMLDivElement>>(new Map());
  const [highlightedId, setHighlightedId] = useState<string | null>(null);
  const handledHighlightRef = useRef<string | null>(null);
  // 最下部付近にいる間だけ新着返信・追加読み込みへ自動追従する(古い返信を読んでいる最中に新着で
  // 引き剥がされないようにする、D-4)。
  const atBottomRef = useRef(true);

  function handleScroll() {
    const el = containerRef.current;
    if (!el) return;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    atBottomRef.current = distanceFromBottom < 80;
  }

  useEffect(() => {
    if (!atBottomRef.current) return;
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [replies.length]);

  // ジャンプ対象の返信をスクロール+ハイライトする。ChatViewが`around`付きで取得するため、深い位置の
  // 返信でも`replies`に含まれている(A-2)。
  useEffect(() => {
    if (!highlightReplyId) {
      // ジャンプ完了後にChatView側がURLの?highlight=/?reply=をクリアするとundefinedになる。ここで
      // dedup用refもリセットしておかないと、同じ返信への再ジャンプ(再クリック)がブロックされ続ける(D-1)。
      handledHighlightRef.current = null;
      return;
    }
    if (handledHighlightRef.current === highlightReplyId) return;
    const el = replyRefs.current.get(highlightReplyId);
    if (!el) return;
    handledHighlightRef.current = highlightReplyId;
    el.scrollIntoView({ block: "center" });
    setHighlightedId(highlightReplyId);
    onHighlighted?.();
    const timer = setTimeout(() => setHighlightedId(null), HIGHLIGHT_DURATION_MS);
    return () => clearTimeout(timer);
  }, [highlightReplyId, replies, onHighlighted]);

  return (
    <div className="flex h-full w-full flex-shrink-0 flex-col border-l border-slate-200 bg-white sm:w-96">
      <div className="flex h-14 items-center gap-2 border-b border-slate-200 px-4">
        <button
          onClick={onClose}
          aria-label="スレッドを閉じる"
          className="rounded p-1 text-slate-500 hover:bg-slate-100 sm:hidden"
        >
          ←
        </button>
        <h2 className="flex-1 text-sm font-bold text-slate-800 sm:flex-none">スレッド</h2>
        <button
          onClick={onClose}
          aria-label="スレッドを閉じる"
          className="ml-auto hidden rounded px-2 py-1 text-sm text-slate-400 hover:bg-slate-100 sm:block"
        >
          ✕
        </button>
      </div>
      <div ref={containerRef} onScroll={handleScroll} className="flex-1 overflow-y-auto py-2">
        <MessageItem
          message={parentMessage}
          author={userMap[parentMessage.authorId]}
          grouped={false}
          currentUserId={currentUserId}
          userMap={userMap}
          onToggleReaction={(emoji) => onToggleReaction(parentMessage.id, emoji)}
          onEdit={(body) => onEdit(parentMessage.id, body)}
          onDelete={() => onDelete(parentMessage.id)}
        />
        <div className="my-2 flex items-center gap-2 px-4">
          <div className="h-px flex-1 bg-slate-200" />
          <span className="text-xs text-slate-400">{replies.length}件の返信</span>
          <div className="h-px flex-1 bg-slate-200" />
        </div>

        {loading ? (
          <p className="px-4 py-2 text-sm text-slate-400">読み込み中...</p>
        ) : (
          replies.map((reply, i) => (
            <div
              key={reply.id}
              ref={(el) => {
                if (el) replyRefs.current.set(reply.id, el);
                else replyRefs.current.delete(reply.id);
              }}
            >
              <MessageItem
                message={reply}
                author={userMap[reply.authorId]}
                grouped={isSameGroup(replies[i - 1], reply)}
                currentUserId={currentUserId}
                userMap={userMap}
                highlighted={reply.id === highlightedId}
                onToggleReaction={(emoji) => onToggleReaction(reply.id, emoji)}
                onEdit={(body) => onEdit(reply.id, body)}
                onDelete={() => onDelete(reply.id)}
              />
            </div>
          ))
        )}

        {!loading && hasMore && (
          <div className="px-4 py-2">
            <button
              onClick={onLoadMore}
              disabled={loadingMore}
              className="text-xs font-medium text-brand-600 hover:underline disabled:opacity-40"
            >
              {loadingMore ? "読み込み中..." : "さらに20件の返信を読み込む"}
            </button>
          </div>
        )}
        <div ref={bottomRef} />
      </div>
      <MessageComposer onSend={onSend} placeholder="スレッドに返信..." mentionSource={mentionSource} />
    </div>
  );
}
