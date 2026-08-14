import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import type { NotificationDTO } from "@chatspace/shared";
import { useNotificationStore } from "../../store/notificationStore";
import { formatRelative } from "../../lib/time";

interface Props {
  workspaceId: string;
  onClose: () => void;
}

const TYPE_LABEL: Record<NotificationDTO["type"], string> = {
  MENTION: "メンション",
  DM: "DM",
  CHANNEL_INVITE: "チャンネル招待",
  WORKSPACE_INVITE: "ワークスペース招待",
  THREAD_REPLY: "スレッド返信",
};

export default function NotificationPanel({ workspaceId, onClose }: Props) {
  const navigate = useNavigate();
  const { notifications, load, markRead, markAllRead } = useNotificationStore();

  useEffect(() => {
    load(workspaceId);
  }, [workspaceId, load]);

  async function handleClick(n: NotificationDTO) {
    if (!n.readAt) await markRead(n.id);
    onClose();

    const params = new URLSearchParams();
    if (n.threadParentId) {
      params.set("highlight", n.threadParentId);
      if (n.messageId) params.set("reply", n.messageId);
    } else if (n.messageId) {
      params.set("highlight", n.messageId);
    }
    const query = params.toString() ? `?${params.toString()}` : "";

    if (n.channelId) {
      navigate(`/w/${workspaceId}/c/${n.channelId}${query}`);
    } else if (n.dmId) {
      navigate(`/w/${workspaceId}/dm/${n.dmId}${query}`);
    }
  }

  return (
    <div className="fixed inset-0 z-50" onClick={onClose}>
      <div
        className="absolute right-4 top-14 w-80 max-h-[70vh] overflow-y-auto rounded-lg border border-slate-200 bg-white shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
          <span className="text-sm font-semibold text-slate-700">通知</span>
          <button onClick={() => markAllRead(workspaceId)} className="text-xs text-brand-600 hover:underline">
            すべて既読にする
          </button>
        </div>
        {notifications.length === 0 ? (
          <p className="p-4 text-sm text-slate-500">通知はありません。</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {notifications.map((n) => (
              <li key={n.id}>
                <button
                  onClick={() => handleClick(n)}
                  className={`block w-full px-4 py-3 text-left text-sm hover:bg-slate-50 ${
                    n.readAt ? "text-slate-500" : "bg-brand-50/60 text-slate-800"
                  }`}
                >
                  <div className="mb-0.5 flex items-center gap-2">
                    <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-semibold text-slate-500">
                      {TYPE_LABEL[n.type]}
                    </span>
                    <span className="text-[11px] text-slate-500">{formatRelative(n.createdAt)}</span>
                  </div>
                  <p>{n.text}</p>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
