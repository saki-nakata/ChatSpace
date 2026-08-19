import { Link, NavLink } from "react-router-dom";
import type { ChannelResponse, DmThreadResponse } from "../../api/types";
import { useDialogA11y } from "../../lib/useDialogA11y";

interface SidebarProps {
  workspaceName: string | undefined;
  channels: ChannelResponse[];
  dms: DmThreadResponse[];
  isOwner: boolean;
  onlineUserIds: Set<string>;
  /** 640px未満でのドロワー開閉状態(640px以上では常時表示、画面設計書§2)。 */
  isOpen: boolean;
  onClose: () => void;
  onCreateChannel: (e: React.MouseEvent<HTMLButtonElement>) => void;
  onStartDm: (e: React.MouseEvent<HTMLButtonElement>) => void;
  onOpenMembers: (e: React.MouseEvent<HTMLButtonElement>) => void;
  restoreFocusTo?: HTMLElement | null;
}

const NAV_LINK_BASE = "flex items-center justify-between rounded px-2 py-1 text-sm hover:bg-brand-800";

/**
 * S-04サイドバー(フェーズ9-F、`apps/web`の`Sidebar.tsx`を移植)。640px未満では既定非表示の
 * ドロワーとして左からオーバーレイ表示(背後に半透明の黒背景、クリックで閉じる)、640px以上では
 * 常時表示の静的サイドバーに戻る。開いている間はEscapeで閉じ、閉じた際は起動元(☰ボタン)へ
 * フォーカスを戻す(`useDialogA11y`、`isOpen`が常にfalseの640px以上では実質無効化される)。
 */
export default function Sidebar({
  workspaceName,
  channels,
  dms,
  isOwner,
  onlineUserIds,
  isOpen,
  onClose,
  onCreateChannel,
  onStartDm,
  onOpenMembers,
  restoreFocusTo,
}: SidebarProps) {
  const asideRef = useDialogA11y<HTMLElement>(isOpen, onClose, restoreFocusTo);

  return (
    <>
      {isOpen && (
        <div className="fixed inset-0 z-30 bg-black/40 sm:hidden" onClick={onClose} aria-hidden="true" />
      )}
      <aside
        ref={asideRef}
        tabIndex={-1}
        className={`fixed inset-y-0 left-0 z-40 flex w-64 shrink-0 -translate-x-full transform flex-col border-r border-slate-200 bg-brand-900 text-brand-50 outline-none transition-transform duration-200 sm:static sm:z-auto sm:translate-x-0 ${
          isOpen ? "translate-x-0" : ""
        }`}
      >
        <div className="flex items-center justify-between border-b border-brand-800 px-4 py-3">
          <div className="min-w-0">
            <Link to="/" data-initial-focus className="text-xs text-brand-300 hover:text-white">
              &larr; ワークスペース一覧
            </Link>
            <p className="truncate text-sm font-bold text-white">{workspaceName ?? "..."}</p>
          </div>
          <button
            onClick={onClose}
            aria-label="サイドバーを閉じる"
            data-initial-focus-skip
            className="rounded p-1 text-brand-300 hover:text-white sm:hidden"
          >
            ✕
          </button>
        </div>
        <nav className="flex-1 overflow-y-auto px-2 py-3">
          <div className="mb-1 flex items-center justify-between px-2">
            <p className="text-xs font-semibold uppercase text-brand-300">チャンネル</p>
            {isOwner && (
              <button
                onClick={onCreateChannel}
                className="text-xs text-brand-300 hover:text-white"
                title="チャンネルを作成"
              >
                +
              </button>
            )}
          </div>
          {channels.map((c) => {
            const unread = !!c.unreadCount && c.unreadCount > 0;
            return (
              <NavLink
                key={c.id}
                to={`c/${c.id}`}
                onClick={onClose}
                className={({ isActive }) =>
                  `${NAV_LINK_BASE} ${
                    isActive
                      ? "bg-brand-800 text-white"
                      : unread
                        ? "font-semibold text-white"
                        : "text-brand-100"
                  }`
                }
              >
                <span className="truncate">
                  {c.type === "PRIVATE" ? "🔒 " : "# "}
                  {c.name}
                </span>
                {unread && (
                  <span className="ml-2 rounded-full bg-brand-500 px-1.5 py-0.5 text-xs">{c.unreadCount}</span>
                )}
              </NavLink>
            );
          })}

          <div className="mb-1 mt-4 flex items-center justify-between px-2">
            <p className="text-xs font-semibold uppercase text-brand-300">ダイレクトメッセージ</p>
            <button onClick={onStartDm} className="text-xs text-brand-300 hover:text-white" title="DMを開始">
              +
            </button>
          </div>
          {dms.map((dm) => {
            const unread = !!dm.unreadCount && dm.unreadCount > 0;
            return (
              <NavLink
                key={dm.id}
                to={`dm/${dm.id}`}
                onClick={onClose}
                className={({ isActive }) =>
                  `${NAV_LINK_BASE} ${
                    isActive
                      ? "bg-brand-800 text-white"
                      : unread
                        ? "font-semibold text-white"
                        : "text-brand-100"
                  }`
                }
              >
                <span className="flex min-w-0 items-center gap-1.5 truncate">
                  <span
                    className={`h-2 w-2 shrink-0 rounded-full ${
                      dm.otherUser?.id && onlineUserIds.has(dm.otherUser.id) ? "bg-emerald-400" : "bg-brand-700"
                    }`}
                    aria-hidden="true"
                  />
                  <span className="truncate">{dm.otherUser?.displayName ?? "不明なユーザー"}</span>
                </span>
                {unread && (
                  <span className="ml-2 rounded-full bg-brand-500 px-1.5 py-0.5 text-xs">{dm.unreadCount}</span>
                )}
              </NavLink>
            );
          })}
        </nav>
        <div className="border-t border-brand-800 px-4 py-3">
          <button onClick={onOpenMembers} className="mb-2 block text-xs font-medium text-brand-300 hover:text-white">
            メンバー管理{isOwner ? "(オーナー)" : ""}
          </button>
          <p className="text-xs text-brand-300">オンライン: {onlineUserIds.size}人</p>
        </div>
      </aside>
    </>
  );
}
