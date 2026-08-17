import { useEffect, useState } from "react";
import { Outlet, useNavigate, useParams } from "react-router-dom";
import { useAuthStore } from "../store/authStore";
import { useNotificationStore } from "../store/notificationStore";
import { usePresenceStore } from "../store/presenceStore";
import { useWorkspaceStore } from "../store/workspaceStore";
import { channelApi, dmApi, workspaceApi } from "../api/resources";
import type { ChannelResponse, DmThreadResponse, UserResponse } from "../api/types";
import { useUnreadTabTitle } from "../lib/useUnreadTabTitle";
import TopBar from "../components/layout/TopBar";
import Sidebar from "../components/layout/Sidebar";
import CreateChannelModal from "../components/workspace/CreateChannelModal";
import StartDmModal from "../components/workspace/StartDmModal";
import WorkspaceMembersModal from "../components/workspace/WorkspaceMembersModal";

export interface WorkspaceShellContext {
  channels: ChannelResponse[];
  dms: DmThreadResponse[];
  isOwner: boolean;
  onlineUserIds: Set<string>;
  refreshSidebar: () => void;
}

/**
 * S-04ワークスペースシェル(サイドバー・トップバー)。チャンネル・DM一覧の表示とプレゼンス・通知の
 * リアルタイム購読を配線する。検索(S-12)・通知パネル(S-13)は`TopBar`に委譲(フェーズ9-D)。
 * 管理系モーダル(S-08〜S-11)はここで、S-14(プロフィール編集)は`TopBar`で扱う(フェーズ9-E)。
 * サイドバーは`components/layout/Sidebar.tsx`に分離し、640px未満ではドロワー化する(フェーズ9-F)。
 */
export default function WorkspaceShellPage() {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const fetchUnreadCount = useNotificationStore((s) => s.fetchUnreadCount);
  const subscribeNotifications = useNotificationStore((s) => s.subscribeRealtime);
  const onlineUserIds = usePresenceStore((s) => s.onlineUserIds);
  const fetchInitialPresence = usePresenceStore((s) => s.fetchInitialPresence);
  const subscribePresence = usePresenceStore((s) => s.subscribeRealtime);
  const workspaces = useWorkspaceStore((s) => s.workspaces);
  const fetchWorkspaces = useWorkspaceStore((s) => s.fetchWorkspaces);
  const [channels, setChannels] = useState<ChannelResponse[]>([]);
  const [dms, setDms] = useState<DmThreadResponse[]>([]);
  // 検索結果・通知パネルの表示者名解決用。単一チャンネル/DMのuserMapと異なりワークスペース全体が対象になり得るため
  // (画面設計書S-12/S-13は検索・通知の対象を特定のチャンネル/DMに限定しない)、ワークスペースメンバー全体を保持する。
  const [memberMap, setMemberMap] = useState<Record<string, UserResponse>>({});
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const [createChannelOpen, setCreateChannelOpen] = useState(false);
  const [createChannelTrigger, setCreateChannelTrigger] = useState<HTMLElement | null>(null);
  const [startDmOpen, setStartDmOpen] = useState(false);
  const [startDmTrigger, setStartDmTrigger] = useState<HTMLElement | null>(null);
  const [membersOpen, setMembersOpen] = useState(false);
  const [membersTrigger, setMembersTrigger] = useState<HTMLElement | null>(null);
  const [sidebarTrigger, setSidebarTrigger] = useState<HTMLElement | null>(null);

  const currentWorkspace = workspaces.find((w) => w.id === workspaceId);
  const isOwner = currentWorkspace?.myRole === "OWNER";

  // タブタイトルの未読件数(フェーズ10)。サイドバーのバッジと同じ「現ワークスペース内の未読メッセージ数」を使う。
  const totalUnread =
    channels.reduce((sum, c) => sum + (c.unreadCount ?? 0), 0) +
    dms.reduce((sum, d) => sum + (d.unreadCount ?? 0), 0);
  useUnreadTabTitle(totalUnread);

  function refreshSidebar() {
    if (!workspaceId) return;
    channelApi.list(workspaceId).then(setChannels);
    dmApi.list(workspaceId).then(setDms);
  }

  useEffect(() => {
    if (!workspaceId) return;
    refreshSidebar();
    fetchInitialPresence(workspaceId);
    fetchUnreadCount();
    fetchWorkspaces();
    workspaceApi.members(workspaceId).then((members) => {
      const map: Record<string, UserResponse> = {};
      for (const m of members) {
        if (m.user?.id) map[m.user.id] = m.user;
      }
      setMemberMap(map);
    });
    const unsubscribePresence = subscribePresence(workspaceId);
    const unsubscribeNotifications = subscribeNotifications();
    return () => {
      unsubscribePresence();
      unsubscribeNotifications();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspaceId]);

  if (!workspaceId || !user) return null;

  const context: WorkspaceShellContext = { channels, dms, isOwner, onlineUserIds, refreshSidebar };

  return (
    <div className="flex h-full">
      <Sidebar
        workspaceName={currentWorkspace?.name}
        channels={channels}
        dms={dms}
        isOwner={isOwner}
        onlineUserIds={onlineUserIds}
        isOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        onCreateChannel={(e) => {
          setCreateChannelTrigger(e.currentTarget);
          setCreateChannelOpen(true);
        }}
        onStartDm={(e) => {
          setStartDmTrigger(e.currentTarget);
          setStartDmOpen(true);
        }}
        onOpenMembers={(e) => {
          setMembersTrigger(e.currentTarget);
          setMembersOpen(true);
        }}
        restoreFocusTo={sidebarTrigger}
      />

      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar
          workspaceId={workspaceId}
          userMap={memberMap}
          onOpenSidebar={(e) => {
            setSidebarTrigger(e.currentTarget);
            setSidebarOpen(true);
          }}
        />
        <main className="flex-1 overflow-hidden">
          <Outlet context={context} />
        </main>
      </div>

      {createChannelOpen && (
        <CreateChannelModal
          workspaceId={workspaceId}
          onClose={() => setCreateChannelOpen(false)}
          onCreated={(channel) => {
            refreshSidebar();
            if (channel.id) navigate(`c/${channel.id}`);
          }}
          restoreFocusTo={createChannelTrigger}
        />
      )}

      {startDmOpen && (
        <StartDmModal
          workspaceId={workspaceId}
          onClose={() => setStartDmOpen(false)}
          onStarted={(thread) => {
            refreshSidebar();
            if (thread.id) navigate(`dm/${thread.id}`);
          }}
          restoreFocusTo={startDmTrigger}
        />
      )}

      {membersOpen && (
        <WorkspaceMembersModal
          workspaceId={workspaceId}
          currentUserId={user.id ?? ""}
          onlineUserIds={onlineUserIds}
          onClose={() => setMembersOpen(false)}
          onSelfLeft={() => navigate("/", { replace: true })}
          restoreFocusTo={membersTrigger}
        />
      )}
    </div>
  );
}
