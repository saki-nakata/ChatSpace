import { useEffect, useRef, useState, type FormEvent } from "react";
import Modal from "../common/Modal";
import { searchApi } from "../../api/resources";
import type { Cursor, SearchResultItem, UserResponse } from "../../api/types";
import { formatRelativeTime } from "../../lib/time";

interface SearchModalProps {
  workspaceId: string;
  userMap: Record<string, UserResponse>;
  onClose: () => void;
  onNavigateToMessage: (target: {
    channelId?: string | null;
    dmId?: string | null;
    highlightId: string;
    replyId?: string;
  }) => void;
  restoreFocusTo?: HTMLElement | null;
}

/** S-12検索モーダル(画面設計書)。「さらに検索結果を表示」ボタン+ボタン押下後はスクロールでも追加取得できる併用方式。 */
export default function SearchModal({
  workspaceId,
  userMap,
  onClose,
  onNavigateToMessage,
  restoreFocusTo,
}: SearchModalProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResultItem[]>([]);
  const [nextCursor, setNextCursor] = useState<Cursor | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);
  const [scrollLoadEnabled, setScrollLoadEnabled] = useState(false);
  const sentinelRef = useRef<HTMLDivElement>(null);

  async function runSearch(e?: FormEvent) {
    e?.preventDefault();
    const trimmed = query.trim();
    if (!trimmed) return;
    setLoading(true);
    setScrollLoadEnabled(false);
    const res = await searchApi.search(workspaceId, { q: trimmed });
    setResults(res.messages ?? []);
    setNextCursor(res.nextCursor ?? null);
    setHasSearched(true);
    setLoading(false);
  }

  async function loadMore() {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    const res = await searchApi.search(workspaceId, {
      q: query.trim(),
      cursorCreatedAt: nextCursor.createdAt,
      cursorId: nextCursor.id,
    });
    setResults((prev) => [...prev, ...(res.messages ?? [])]);
    setNextCursor(res.nextCursor ?? null);
    setLoadingMore(false);
  }

  // 「さらに検索結果を表示」ボタン押下後はスクロールでも追加取得できる併用方式(画面設計書S-12、レビュー指摘により確定)
  useEffect(() => {
    if (!scrollLoadEnabled) return;
    const sentinel = sentinelRef.current;
    if (!sentinel) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) loadMore();
      },
      { root: sentinel.parentElement, threshold: 0 },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scrollLoadEnabled, nextCursor]);

  function handleResultClick(item: SearchResultItem) {
    if (!item.id) return;
    onClose();
    onNavigateToMessage({
      channelId: item.channelId,
      dmId: item.dmId,
      highlightId: item.parentId ?? item.id,
      replyId: item.parentId ? item.id : undefined,
    });
  }

  const countLabel = nextCursor ? `${results.length}件以上` : `${results.length}件`;

  return (
    <Modal title="メッセージ検索" onClose={onClose} widthClassName="max-w-xl" restoreFocusTo={restoreFocusTo}>
      <div className="p-4">
        <form onSubmit={runSearch} className="flex gap-2">
          <input
            autoFocus
            data-initial-focus
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="キーワードを入力"
            className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
          />
          <button
            type="submit"
            disabled={!query.trim() || loading}
            className="rounded-md bg-brand-500 px-4 py-2 text-sm font-medium text-white hover:bg-brand-600 disabled:opacity-40"
          >
            検索
          </button>
        </form>
        <p className="mt-2 text-xs text-slate-400">自分が参加しているチャンネル・DMの範囲のみを検索します。</p>

        <div className="mt-4 max-h-96 overflow-y-auto">
          {loading && <p className="py-4 text-center text-sm text-slate-400">検索中...</p>}
          {!loading && hasSearched && results.length === 0 && (
            <p className="py-4 text-center text-sm text-slate-400">該当するメッセージが見つかりませんでした。</p>
          )}
          {!loading &&
            results.map((item) => {
              const author = item.authorId ? userMap[item.authorId] : undefined;
              return (
                <button
                  key={item.id}
                  onClick={() => handleResultClick(item)}
                  className="block w-full rounded-md px-2 py-2 text-left hover:bg-slate-50"
                >
                  <div className="flex items-baseline gap-2">
                    <span className="text-sm font-bold text-slate-800">
                      {author?.displayName ?? "不明なユーザー"}
                    </span>
                    <span className="text-xs text-slate-400">
                      {item.createdAt && formatRelativeTime(item.createdAt)}
                    </span>
                  </div>
                  <p className="line-clamp-2 text-sm text-slate-600">{item.body}</p>
                </button>
              );
            })}

          {!loading && results.length > 0 && (
            <div ref={sentinelRef} className="flex flex-col items-center gap-1 py-2 text-center">
              <span className="text-xs text-slate-400">{countLabel}</span>
              {nextCursor && (
                <button
                  onClick={() => {
                    setScrollLoadEnabled(true);
                    loadMore();
                  }}
                  disabled={loadingMore}
                  className="text-xs font-medium text-brand-600 hover:underline disabled:opacity-40"
                >
                  {loadingMore ? "読み込み中..." : "さらに検索結果を表示"}
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}
