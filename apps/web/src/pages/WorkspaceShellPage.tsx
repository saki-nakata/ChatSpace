import { useEffect, useRef, useState } from "react";
import { Outlet, useLocation, useNavigate, useParams } from "react-router-dom";
import { SocketEvents } from "@chatspace/shared";
import type { ChannelDTO, DMThreadDTO, MessageDTO, NotificationDTO } from "@chatspace/shared";
import { useWorkspaceStore } from "../store/workspaceStore";
import { useAuthStore } from "../store/authStore";
import { useNotificationStore } from "../store/notificationStore";
import { usePresenceStore } from "../store/presenceStore";
import { getSocket } from "../lib/socket";
import { requestNotificationPermission, showBrowserNotification } from "../lib/browserNotify";
import { workspaceApi } from "../api/resources";
import Sidebar from "../components/layout/Sidebar";
import TopBar from "../components/layout/TopBar";

export default function WorkspaceShellPage() {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const workspaces = useWorkspaceStore((s) => s.workspaces);
  const channels = useWorkspaceStore((s) => s.channels);
  const dmThreads = useWorkspaceStore((s) => s.dmThreads);
  const enterWorkspace = useWorkspaceStore((s) => s.enterWorkspace);
  const upsertChannel = useWorkspaceStore((s) => s.upsertChannel);
  const removeChannel = useWorkspaceStore((s) => s.removeChannel);
  const upsertDMThread = useWorkspaceStore((s) => s.upsertDMThread);
  const bumpUnreadFromMessage = useWorkspaceStore((s) => s.bumpUnreadFromMessage);
  const loadNotifications = useNotificationStore((s) => s.load);
  const pushNotification = useNotificationStore((s) => s.push);
  const setOnline = usePresenceStore((s) => s.setOnline);
  const setUserPresence = usePresenceStore((s) => s.setUserPresence);
  const [ready, setReady] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // socket イベントハンドラは workspaceId/user が変わらない限り張り直さないため、
  // 「今どのチャンネル/DMを開いているか」は ref で保持し、ハンドラ発火のたびに最新値を読む。
  // ref への書き込みはレンダー中ではなく必ず effect 内で行う。
  const activeThreadKeyRef = useRef<string | null>(null);
  useEffect(() => {
    const match = location.pathname.match(/\/(c|dm)\/([^/]+)/);
    activeThreadKeyRef.current = match ? `${match[1] === "c" ? "c" : "d"}:${match[2]}` : null;
  }, [location.pathname]);

  useEffect(() => {
    if (!workspaceId) return;
    setReady(false);
    enterWorkspace(workspaceId).then(() => setReady(true));
    loadNotifications(workspaceId);
    workspaceApi.presence(workspaceId).then(({ onlineUserIds }) => setOnline(onlineUserIds));
  }, [workspaceId, enterWorkspace, loadNotifications, setOnline]);

  useEffect(() => {
    requestNotificationPermission();
  }, []);

  // 未読件数をブラウザタブのタイトルに反映する
  useEffect(() => {
    const totalUnread =
      channels.reduce((sum, c) => sum + c.unreadCount, 0) + dmThreads.reduce((sum, d) => sum + d.unreadCount, 0);
    document.title = totalUnread > 0 ? `(${totalUnread > 99 ? "99+" : totalUnread}) ChatSpace` : "ChatSpace";
    return () => {
      document.title = "ChatSpace";
    };
  }, [channels, dmThreads]);

  useEffect(() => {
    if (!workspaceId || !user) return;
    const socket = getSocket();

    const onChannelCreated = (channel: ChannelDTO) => {
      if (channel.workspaceId !== workspaceId) return;
      upsertChannel(channel);
      socket.emit(SocketEvents.JoinChannel, channel.id);
    };
    const onChannelDeleted = (payload: { workspaceId: string; channelId: string }) => {
      if (payload.workspaceId !== workspaceId) return;
      removeChannel(payload.channelId);
      if (activeThreadKeyRef.current === `c:${payload.channelId}`) {
        navigate(`/w/${workspaceId}`);
      }
    };
    const onWorkspaceKicked = (payload: { workspaceId: string; userId: string }) => {
      if (payload.workspaceId !== workspaceId) return;
      if (payload.userId === user.id) {
        navigate("/", { replace: true });
      }
    };
    const onMessageCreated = (message: MessageDTO) => {
      bumpUnreadFromMessage(message, activeThreadKeyRef.current, user.id);
    };
    const onNotification = (notification: NotificationDTO) => {
      pushNotification(notification);
      const from = notification.fromUser?.displayName ?? "ChatSpace";
      showBrowserNotification(from, notification.text);
    };
    const onDMThreadCreated = (dm: DMThreadDTO) => {
      if (dm.workspaceId !== workspaceId) return;
      upsertDMThread(dm);
      socket.emit(SocketEvents.JoinDM, dm.id);
    };
    const onPresenceUpdated = (payload: { userId: string; online: boolean }) => {
      setUserPresence(payload.userId, payload.online);
    };

    socket.on(SocketEvents.ChannelCreated, onChannelCreated);
    socket.on(SocketEvents.ChannelDeleted, onChannelDeleted);
    socket.on(SocketEvents.WorkspaceMemberKicked, onWorkspaceKicked);
    socket.on(SocketEvents.MessageCreated, onMessageCreated);
    socket.on(SocketEvents.Notification, onNotification);
    socket.on(SocketEvents.DMThreadCreated, onDMThreadCreated);
    socket.on(SocketEvents.PresenceUpdated, onPresenceUpdated);

    return () => {
      socket.off(SocketEvents.ChannelCreated, onChannelCreated);
      socket.off(SocketEvents.ChannelDeleted, onChannelDeleted);
      socket.off(SocketEvents.WorkspaceMemberKicked, onWorkspaceKicked);
      socket.off(SocketEvents.MessageCreated, onMessageCreated);
      socket.off(SocketEvents.Notification, onNotification);
      socket.off(SocketEvents.DMThreadCreated, onDMThreadCreated);
      socket.off(SocketEvents.PresenceUpdated, onPresenceUpdated);
    };
  }, [
    workspaceId,
    user,
    navigate,
    upsertChannel,
    removeChannel,
    upsertDMThread,
    bumpUnreadFromMessage,
    pushNotification,
    setUserPresence,
  ]);

  const workspace = workspaces.find((w) => w.id === workspaceId);

  if (!workspaceId) return null;

  return (
    <div className="flex h-full overflow-x-hidden">
      <Sidebar
        workspaceId={workspaceId}
        workspace={workspace}
        isOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />
      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar workspaceId={workspaceId} workspace={workspace} onOpenSidebar={() => setSidebarOpen(true)} />
        <div className="min-h-0 flex-1">{ready && <Outlet />}</div>
      </div>
    </div>
  );
}
