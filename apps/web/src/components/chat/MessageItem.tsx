import { useMemo, useState } from "react";
import type { MessageDTO } from "@chatspace/shared";
import { renderMessageBody } from "../../lib/markdown";
import { formatTime } from "../../lib/time";
import { attachmentUrl } from "../../api/client";
import ReactionBar from "./ReactionBar";

interface Props {
  message: MessageDTO;
  isOwn: boolean;
  onEdit: (body: string) => Promise<void>;
  onDelete: () => Promise<void>;
  onReact: (emoji: string) => Promise<void>;
  onOpenThread?: () => void;
  showThreadButton?: boolean;
}

export default function MessageItem({
  message,
  isOwn,
  onEdit,
  onDelete,
  onReact,
  onOpenThread,
  showThreadButton = true,
}: Props) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(message.body);
  const html = useMemo(() => renderMessageBody(message.body), [message.body]);

  if (message.deletedAt) {
    return (
      <div className="group flex gap-3 rounded px-3 py-1.5 text-sm italic text-slate-500 hover:bg-slate-50">
        <div className="w-9 shrink-0" />
        <span>このメッセージは削除されました</span>
      </div>
    );
  }

  async function handleEditSubmit() {
    const trimmed = draft.trim();
    if (!trimmed) return;
    await onEdit(trimmed);
    setEditing(false);
  }

  return (
    <div className="group flex gap-3 rounded px-3 py-1.5 hover:bg-slate-50">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-md bg-brand-500 text-sm font-bold text-white">
        {message.author.avatarUrl ? (
          <img src={attachmentUrl(message.author.avatarUrl)} alt="" className="h-full w-full object-cover" />
        ) : (
          message.author.displayName.slice(0, 1).toUpperCase()
        )}
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex items-baseline gap-2">
          <span className="text-sm font-semibold text-slate-800">{message.author.displayName}</span>
          <span className="text-xs text-slate-500">{formatTime(message.createdAt)}</span>
          {message.editedAt && <span className="text-xs text-slate-500">(編集済み)</span>}
        </div>

        {editing ? (
          <div className="mt-1 space-y-1">
            <textarea
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              rows={2}
              autoFocus
              className="w-full resize-none rounded-md border border-slate-300 px-2 py-1 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
            <div className="flex gap-2 text-xs">
              <button onClick={handleEditSubmit} className="font-medium text-brand-600 hover:underline">
                保存
              </button>
              <button
                onClick={() => {
                  setDraft(message.body);
                  setEditing(false);
                }}
                className="text-slate-500 hover:underline"
              >
                キャンセル
              </button>
            </div>
          </div>
        ) : (
          <div className="msg-body text-sm text-slate-700" dangerouslySetInnerHTML={{ __html: html }} />
        )}

        {message.attachments.length > 0 && (
          <div className="mt-1 flex flex-wrap gap-2">
            {message.attachments.map((a) =>
              a.kind === "IMAGE" ? (
                <img
                  key={a.id}
                  src={attachmentUrl(a.url)}
                  alt={a.fileName}
                  className="max-h-56 rounded-md border border-slate-200"
                />
              ) : (
                <video key={a.id} src={attachmentUrl(a.url)} controls className="max-h-56 rounded-md border border-slate-200" />
              ),
            )}
          </div>
        )}

        <ReactionBar reactions={message.reactions} onToggle={onReact} />

        {showThreadButton && message.replyCount > 0 && (
          <button onClick={onOpenThread} className="mt-1 block text-xs font-medium text-brand-600 hover:underline">
            {message.replyCount} 件の返信を表示
          </button>
        )}
      </div>

      {!editing && (
        <div className="hidden shrink-0 items-start gap-1 text-xs text-slate-500 group-hover:flex">
          {showThreadButton && (
            <button
              onClick={onOpenThread}
              title="スレッドで返信"
              aria-label="スレッドで返信"
              className="rounded p-1 hover:bg-slate-200"
            >
              💬
            </button>
          )}
          {isOwn && (
            <>
              <button
                onClick={() => setEditing(true)}
                title="編集"
                aria-label="メッセージを編集"
                className="rounded p-1 hover:bg-slate-200"
              >
                ✏️
              </button>
              <button
                onClick={onDelete}
                title="削除"
                aria-label="メッセージを削除"
                className="rounded p-1 hover:bg-slate-200"
              >
                🗑️
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
