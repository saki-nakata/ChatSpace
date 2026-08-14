import { useEffect, useState } from "react";
import type { WorkspaceDTO } from "@chatspace/shared";
import { useAuthStore } from "../../store/authStore";
import { useNotificationStore } from "../../store/notificationStore";
import { attachmentUrl } from "../../api/client";
import SearchModal from "../modals/SearchModal";
import NotificationPanel from "../modals/NotificationPanel";
import ProfileModal from "../modals/ProfileModal";

interface Props {
  workspaceId: string;
  workspace: WorkspaceDTO | undefined;
  onOpenSidebar?: () => void;
}

export default function TopBar({ workspaceId, workspace, onOpenSidebar }: Props) {
  const user = useAuthStore((s) => s.user);
  const unreadCount = useNotificationStore((s) => s.unreadCount);
  const refreshUnreadCount = useNotificationStore((s) => s.refreshUnreadCount);
  const [showSearch, setShowSearch] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);
  const [showProfile, setShowProfile] = useState(false);

  useEffect(() => {
    refreshUnreadCount();
  }, [refreshUnreadCount]);

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-slate-200 bg-white px-4">
      <button
        onClick={onOpenSidebar}
        aria-label="サイドバーを開く"
        title="サイドバーを開く"
        className="rounded-md p-1.5 text-slate-600 hover:bg-slate-100 sm:hidden"
      >
        ☰
        <span className="sr-only">{workspace?.name}</span>
      </button>

      <div className="flex items-center gap-3">
        <button
          onClick={() => setShowSearch(true)}
          className="rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-500 hover:bg-slate-50"
        >
          🔍 検索
        </button>
        <button
          onClick={() => setShowNotifications((v) => !v)}
          aria-label={unreadCount > 0 ? `通知(未読${unreadCount}件)` : "通知"}
          className="relative rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-500 hover:bg-slate-50"
        >
          🔔
          {unreadCount > 0 && (
            <span className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white">
              {unreadCount > 99 ? "99+" : unreadCount}
            </span>
          )}
        </button>
        <button
          onClick={() => setShowProfile(true)}
          className="flex h-8 w-8 items-center justify-center overflow-hidden rounded-full bg-brand-500 text-sm font-bold text-white"
          title={user?.displayName}
          aria-label={`プロフィール設定(${user?.displayName ?? ""})`}
        >
          {user?.avatarUrl ? (
            <img src={attachmentUrl(user.avatarUrl)} alt="" className="h-full w-full object-cover" />
          ) : (
            (user?.displayName ?? "?").slice(0, 1).toUpperCase()
          )}
        </button>
      </div>

      {showSearch && <SearchModal workspaceId={workspaceId} onClose={() => setShowSearch(false)} />}
      {showNotifications && (
        <NotificationPanel workspaceId={workspaceId} onClose={() => setShowNotifications(false)} />
      )}
      {showProfile && <ProfileModal onClose={() => setShowProfile(false)} />}
    </header>
  );
}
