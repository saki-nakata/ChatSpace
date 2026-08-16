import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useNotificationStore } from "../../store/notificationStore";
import { useAuthStore } from "../../store/authStore";
import type { NotificationResponse, UserResponse } from "../../api/types";
import SearchModal from "../search/SearchModal";
import NotificationPanel from "../notification/NotificationPanel";
import ProfileModal from "../profile/ProfileModal";
import Avatar from "../chat/Avatar";

interface TopBarProps {
  workspaceId: string;
  userMap: Record<string, UserResponse>;
  /** 640px未満でサイドバードロワーを開く(フェーズ9-F)。 */
  onOpenSidebar: (e: React.MouseEvent<HTMLButtonElement>) => void;
}

/** S-04トップバー(検索・通知ベル・プロフィールアバター)。 */
export default function TopBar({ workspaceId, userMap, onOpenSidebar }: TopBarProps) {
  const navigate = useNavigate();
  const currentUser = useAuthStore((s) => s.user);
  const unreadCount = useNotificationStore((s) => s.unreadCount);
  const [searchOpen, setSearchOpen] = useState(false);
  const [notifOpen, setNotifOpen] = useState(false);
  const [notifEverOpened, setNotifEverOpened] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  // 起動元要素はrender中に`ref.current`を読めない(react-hooks/refs)ため、クリックハンドラ内で
  // `e.currentTarget`を捕まえてstateへ保存する(閉じた際のフォーカス復帰に使う、useDialogA11y参照)。
  const [searchTrigger, setSearchTrigger] = useState<HTMLElement | null>(null);
  const [notifTrigger, setNotifTrigger] = useState<HTMLElement | null>(null);
  const [profileTrigger, setProfileTrigger] = useState<HTMLElement | null>(null);
  const notifPanelRef = useRef<HTMLDivElement>(null);
  const notifButtonRef = useRef<HTMLButtonElement>(null);

  // 通知パネル(S-13)は背景クリックのみで閉じる(画面設計書、Escapeはパネル自体のuseDialogA11yで対応)
  useEffect(() => {
    if (!notifOpen) return;
    function handleMouseDown(e: MouseEvent) {
      const target = e.target as Node;
      if (notifPanelRef.current?.contains(target) || notifButtonRef.current?.contains(target)) return;
      setNotifOpen(false);
    }
    document.addEventListener("mousedown", handleMouseDown);
    return () => document.removeEventListener("mousedown", handleMouseDown);
  }, [notifOpen]);

  function navigateToMessage(target: {
    channelId?: string | null;
    dmId?: string | null;
    highlightId: string;
    replyId?: string;
  }) {
    const params = new URLSearchParams({ highlight: target.highlightId });
    if (target.replyId) params.set("reply", target.replyId);
    const base = target.channelId
      ? `/w/${workspaceId}/c/${target.channelId}`
      : `/w/${workspaceId}/dm/${target.dmId}`;
    navigate(`${base}?${params.toString()}`);
  }

  function handleNotificationNavigate(n: NotificationResponse) {
    if (!n.messageId) return;
    navigateToMessage({
      channelId: n.channelId,
      dmId: n.dmId,
      highlightId: n.threadParentId ?? n.messageId,
      replyId: n.threadParentId ? n.messageId : undefined,
    });
  }

  if (!currentUser) return null;

  return (
    <header className="relative flex items-center justify-between border-b border-slate-200 bg-white px-4 py-2">
      <div className="flex items-center gap-2">
        <button
          onClick={onOpenSidebar}
          aria-label="サイドバーを開く"
          className="rounded-md p-1.5 text-slate-600 hover:bg-slate-100 sm:hidden"
        >
          ☰
        </button>
        <button
          onClick={(e) => {
            setSearchTrigger(e.currentTarget);
            setSearchOpen(true);
          }}
          className="rounded-md px-3 py-1.5 text-sm text-slate-500 hover:bg-slate-100"
        >
          🔍 検索
        </button>
      </div>
      <div className="flex items-center gap-3 text-sm text-slate-500">
        <button
          ref={notifButtonRef}
          onClick={(e) => {
            setNotifTrigger(e.currentTarget);
            setNotifEverOpened(true);
            setNotifOpen((v) => !v);
          }}
          aria-label="通知"
          aria-expanded={notifOpen}
          className="relative rounded-md px-2 py-1.5 hover:bg-slate-100"
        >
          🔔
          {unreadCount > 0 && (
            <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-brand-500 px-1 text-[10px] font-medium text-white">
              {unreadCount > 99 ? "99+" : unreadCount}
            </span>
          )}
        </button>
        <button
          onClick={(e) => {
            setProfileTrigger(e.currentTarget);
            setProfileOpen(true);
          }}
          aria-label="プロフィール設定"
          className="rounded-md p-0.5 hover:bg-slate-100"
        >
          <Avatar
            userId={currentUser.id ?? ""}
            displayName={currentUser.displayName ?? ""}
            avatarUrl={currentUser.avatarUrl}
            size={28}
          />
        </button>
      </div>

      {notifEverOpened && (
        <div ref={notifPanelRef}>
          <NotificationPanel
            workspaceId={workspaceId}
            userMap={userMap}
            open={notifOpen}
            onClose={() => setNotifOpen(false)}
            onNavigate={handleNotificationNavigate}
            restoreFocusTo={notifTrigger}
          />
        </div>
      )}

      {searchOpen && (
        <SearchModal
          workspaceId={workspaceId}
          userMap={userMap}
          onClose={() => setSearchOpen(false)}
          onNavigateToMessage={navigateToMessage}
          restoreFocusTo={searchTrigger}
        />
      )}

      {profileOpen && (
        <ProfileModal onClose={() => setProfileOpen(false)} restoreFocusTo={profileTrigger} />
      )}
    </header>
  );
}
