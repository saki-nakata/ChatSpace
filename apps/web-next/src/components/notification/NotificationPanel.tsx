import { useEffect, useId, useRef } from "react";
import { useNotificationStore } from "../../store/notificationStore";
import { formatRelativeTime } from "../../lib/time";
import type { NotificationResponse, UserResponse } from "../../api/types";
import { useDialogA11y } from "../../lib/useDialogA11y";

const TYPE_LABELS: Record<string, string> = {
  MENTION: "メンション",
  DM: "DM",
  CHANNEL_INVITE: "チャンネル招待",
  WORKSPACE_INVITE: "ワークスペース招待",
  THREAD_REPLY: "スレッド返信",
};

interface NotificationPanelProps {
  workspaceId: string;
  userMap: Record<string, UserResponse>;
  open: boolean;
  onClose: () => void;
  onNavigate: (notification: NotificationResponse) => void;
  restoreFocusTo?: HTMLElement | null;
}

/**
 * S-13通知パネル。画面設計書の要件により、パネルを閉じてもタブを閉じるまでは取得済み一覧・
 * スクロール位置を保持する必要があるため、`open=false`でも(トグルボタンを一度でも押した後は)
 * コンポーネント自体はアンマウントせず`hidden`クラスで隠すだけにする(呼び出し元のTopBar参照)。
 */
export default function NotificationPanel({
  workspaceId,
  userMap,
  open,
  onClose,
  onNavigate,
  restoreFocusTo,
}: NotificationPanelProps) {
  const headingId = useId();
  const notifications = useNotificationStore((s) => s.notifications);
  const loading = useNotificationStore((s) => s.loading);
  const loadingMore = useNotificationStore((s) => s.loadingMore);
  const nextCursor = useNotificationStore((s) => s.nextCursor);
  const fetchNotifications = useNotificationStore((s) => s.fetchNotifications);
  const loadMoreNotifications = useNotificationStore((s) => s.loadMoreNotifications);
  const markRead = useNotificationStore((s) => s.markRead);
  const markAllRead = useNotificationStore((s) => s.markAllRead);
  const sentinelRef = useRef<HTMLDivElement>(null);
  const dialogRef = useDialogA11y<HTMLDivElement>(open, onClose, restoreFocusTo);

  useEffect(() => {
    if (open) fetchNotifications(workspaceId);
  }, [open, workspaceId, fetchNotifications]);

  // パネル下端に近づいたタイミングで次の50件を取得する(画面設計書S-13、パネル内無限スクロール)
  useEffect(() => {
    if (!open) return;
    const sentinel = sentinelRef.current;
    if (!sentinel) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) loadMoreNotifications();
      },
      { root: sentinel.parentElement, threshold: 0 },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [open, loadMoreNotifications]);

  async function handleClick(n: NotificationResponse) {
    if (!n.readAt && n.id) await markRead(n.id);
    onClose();
    onNavigate(n);
  }

  return (
    <div
      ref={dialogRef}
      role="dialog"
      aria-modal="false"
      aria-labelledby={headingId}
      aria-hidden={!open}
      tabIndex={-1}
      className={`absolute right-4 top-12 z-40 flex w-[calc(100vw-2rem)] flex-col rounded-lg border border-slate-200 bg-white shadow-xl outline-none sm:w-80 ${
        open ? "" : "hidden"
      }`}
      style={{ maxHeight: "70vh" }}
    >
      <div className="flex items-center justify-between border-b border-slate-200 px-4 py-2">
        <h2 id={headingId} className="text-sm font-bold text-slate-800">
          通知
        </h2>
        <button
          onClick={() => markAllRead(workspaceId)}
          className="text-xs font-medium text-brand-600 hover:underline"
        >
          すべて既読にする
        </button>
      </div>
      <div className="flex-1 overflow-y-auto" aria-live="polite">
        {loading && <p className="px-4 py-4 text-center text-sm text-slate-400">読み込み中...</p>}
        {!loading && notifications.length === 0 && (
          <p className="px-4 py-4 text-center text-sm text-slate-400">通知はありません。</p>
        )}
        {!loading &&
          notifications.map((n) => {
            const actor = n.fromUserId ? userMap[n.fromUserId] : undefined;
            return (
              <button
                key={n.id}
                onClick={() => handleClick(n)}
                className={`block w-full border-b border-slate-100 px-4 py-2 text-left last:border-b-0 hover:bg-slate-50 ${
                  n.readAt ? "" : "bg-brand-50"
                }`}
              >
                <div className="flex items-center gap-2">
                  <span className="rounded bg-slate-200 px-1.5 py-0.5 text-[10px] font-medium text-slate-600">
                    {n.type ? (TYPE_LABELS[n.type] ?? n.type) : ""}
                  </span>
                  <span className="text-xs text-slate-400">{n.createdAt && formatRelativeTime(n.createdAt)}</span>
                </div>
                <p className="mt-1 text-sm text-slate-700">
                  {n.text ?? `${actor?.displayName ?? "誰か"}さんからの通知`}
                </p>
              </button>
            );
          })}
        {!loading && notifications.length > 0 && (
          <div ref={sentinelRef} className="py-2 text-center">
            {loadingMore && <span className="text-xs text-slate-400">読み込み中...</span>}
            {!nextCursor && !loadingMore && (
              <span className="text-xs text-slate-300">これ以上の通知はありません</span>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
