import { useState } from "react";
import { Link, NavLink, useNavigate, useParams } from "react-router-dom";
import type { WorkspaceDTO } from "@chatspace/shared";
import { useWorkspaceStore } from "../../store/workspaceStore";
import { usePresenceStore } from "../../store/presenceStore";
import { channelApi } from "../../api/resources";
import CreateChannelModal from "../modals/CreateChannelModal";
import StartDMModal from "../modals/StartDMModal";
import WorkspaceMembersModal from "../modals/WorkspaceMembersModal";

interface Props {
  workspaceId: string;
  workspace: WorkspaceDTO | undefined;
  isOpen?: boolean;
  onClose?: () => void;
}

export default function Sidebar({ workspaceId, workspace, isOpen = false, onClose }: Props) {
  const navigate = useNavigate();
  const { channelId: activeChannelId } = useParams<{ channelId?: string }>();
  const { channels, dmThreads, refreshChannels } = useWorkspaceStore();
  const onlineUserIds = usePresenceStore((s) => s.onlineUserIds);
  const [showCreateChannel, setShowCreateChannel] = useState(false);
  const [showStartDM, setShowStartDM] = useState(false);
  const [showMembers, setShowMembers] = useState(false);

  const isOwner = workspace?.myRole === "OWNER";

  async function handleChannelClick(channelId: string, isMember: boolean, type: string) {
    if (!isMember) {
      if (type !== "PUBLIC") return; // プライベートは招待されないと入れない
      await channelApi.join(workspaceId, channelId);
      await refreshChannels(workspaceId);
    }
    navigate(`/w/${workspaceId}/c/${channelId}`);
    onClose?.();
  }

  return (
    <>
      {isOpen && (
        <div
          className="fixed inset-0 z-30 bg-black/40 sm:hidden"
          onClick={onClose}
          aria-hidden="true"
        />
      )}
      <aside
        className={`fixed inset-y-0 left-0 z-40 flex w-64 shrink-0 -translate-x-full transform flex-col border-r border-slate-200 bg-brand-900 text-brand-50 transition-transform duration-200 sm:static sm:z-auto sm:translate-x-0 ${
          isOpen ? "translate-x-0" : ""
        }`}
      >
      <div className="flex items-center justify-between px-4 py-4">
        <div className="min-w-0">
          <Link to="/" className="text-xs text-brand-300 hover:text-white">
            &larr; ワークスペース一覧
          </Link>
          <h1 className="truncate text-lg font-bold">{workspace?.name ?? "..."}</h1>
        </div>
        <button
          onClick={onClose}
          aria-label="サイドバーを閉じる"
          className="rounded p-1 text-brand-300 hover:text-white sm:hidden"
        >
          ✕
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-2 pb-4">
        <SectionHeader
          label="チャンネル"
          onAdd={isOwner ? () => setShowCreateChannel(true) : undefined}
          addTitle="チャンネルを作成"
        />
        <ul className="mb-4 space-y-0.5">
          {channels.map((c) => {
            const isActive = c.id === activeChannelId;
            return (
              <li key={c.id}>
                <button
                  onClick={() => handleChannelClick(c.id, c.isMember, c.type)}
                  className={`flex w-full items-center justify-between rounded px-2 py-1.5 text-left text-sm hover:bg-brand-800 ${
                    isActive ? "bg-brand-800 text-white" : "text-brand-100"
                  }`}
                >
                  <span className="truncate">
                    {c.type === "PRIVATE" ? "🔒" : "#"} {c.name}
                  </span>
                  {c.unreadCount > 0 && (
                    <span className="ml-2 rounded-full bg-brand-400 px-1.5 py-0.5 text-[10px] font-bold text-brand-950">
                      {c.unreadCount}
                    </span>
                  )}
                </button>
              </li>
            );
          })}
        </ul>

        <SectionHeader label="ダイレクトメッセージ" onAdd={() => setShowStartDM(true)} addTitle="DMを開始" />
        <ul className="space-y-0.5">
          {dmThreads.map((d) => (
            <li key={d.id}>
              <NavLink
                to={`/w/${workspaceId}/dm/${d.id}`}
                onClick={() => onClose?.()}
                className={({ isActive }) =>
                  `flex items-center justify-between rounded px-2 py-1.5 text-sm hover:bg-brand-800 ${
                    isActive ? "bg-brand-800 text-white" : "text-brand-100"
                  }`
                }
              >
                <span className="flex min-w-0 items-center gap-1.5 truncate">
                  <span
                    className={`h-2 w-2 shrink-0 rounded-full ${
                      onlineUserIds.has(d.otherUser.id) ? "bg-emerald-400" : "bg-brand-700"
                    }`}
                    title={onlineUserIds.has(d.otherUser.id) ? "オンライン" : "オフライン"}
                  />
                  <span className="truncate">{d.otherUser.displayName}</span>
                </span>
                {d.unreadCount > 0 && (
                  <span className="ml-2 rounded-full bg-brand-400 px-1.5 py-0.5 text-[10px] font-bold text-brand-950">
                    {d.unreadCount}
                  </span>
                )}
              </NavLink>
            </li>
          ))}
        </ul>
      </div>

      <div className="border-t border-brand-800 px-3 py-3">
        <button onClick={() => setShowMembers(true)} className="text-xs text-brand-300 hover:text-white">
          メンバー管理 {isOwner && "(オーナー)"}
        </button>
      </div>

      {showCreateChannel && (
        <CreateChannelModal workspaceId={workspaceId} onClose={() => setShowCreateChannel(false)} />
      )}
      {showStartDM && <StartDMModal workspaceId={workspaceId} onClose={() => setShowStartDM(false)} />}
      {showMembers && (
        <WorkspaceMembersModal workspaceId={workspaceId} isOwner={isOwner} onClose={() => setShowMembers(false)} />
      )}
      </aside>
    </>
  );
}

function SectionHeader({ label, onAdd, addTitle }: { label: string; onAdd?: () => void; addTitle?: string }) {
  return (
    <div className="mb-1 mt-3 flex items-center justify-between px-2">
      <span className="text-xs font-semibold uppercase tracking-wide text-brand-300">{label}</span>
      {onAdd && (
        <button
          onClick={onAdd}
          title={addTitle}
          aria-label={addTitle}
          className="rounded px-1 text-brand-300 hover:bg-brand-800 hover:text-white"
        >
          +
        </button>
      )}
    </div>
  );
}
