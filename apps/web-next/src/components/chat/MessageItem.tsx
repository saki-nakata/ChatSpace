import { useMemo, useRef, useState, type KeyboardEvent } from "react";
import Avatar from "./Avatar";
import type { DisplayMessage } from "./types";
import { formatTime } from "../../lib/time";
import { renderMessageBody } from "../../lib/markdown";
import { attachmentUrl } from "../../api/client";
import type { UserResponse } from "../../api/types";

const QUICK_REACTIONS = ["👍", "❤️", "😂", "🎉", "👀"];

interface MessageItemProps {
  message: DisplayMessage;
  author: UserResponse | undefined;
  grouped: boolean;
  currentUserId: string;
  userMap: Record<string, UserResponse>;
  onToggleReaction: (emoji: string) => void;
  onEdit: (body: string) => Promise<void>;
  onDelete: () => void;
  /** 渡された場合のみスレッド関連UI(返信ボタン・「N件の返信」リンク)を表示する(スレッドパネル内の表示では渡さない)。 */
  onOpenThread?: () => void;
  /** 検索・通知からのジャンプ先(S-12/S-13)。背景色を点灯させ、一定時間後に自然にフェードアウトさせる。 */
  highlighted?: boolean;
}

/** Slackのメッセージ行に倣う: グループ化時はアバター・名前を省略し、hover(640px以上)/常時(640px未満)でアクションバーを出す。 */
export default function MessageItem({
  message,
  author,
  grouped,
  currentUserId,
  userMap,
  onToggleReaction,
  onEdit,
  onDelete,
  onOpenThread,
  highlighted,
}: MessageItemProps) {
  const [showPicker, setShowPicker] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [editValue, setEditValue] = useState(message.body);
  const [saving, setSaving] = useState(false);
  const editTextareaRef = useRef<HTMLTextAreaElement>(null);
  const isMine = message.authorId === currentUserId;
  const displayName = author?.displayName ?? "不明なユーザー";
  // XSS対策: markedでのHTML化直後に必ずDOMPurifyでサニタイズしたものだけをdangerouslySetInnerHTMLへ渡す
  const bodyHtml = useMemo(
    () => (message.deleted ? "" : renderMessageBody(message.body)),
    [message.deleted, message.body],
  );

  function startEdit() {
    setEditValue(message.body);
    setIsEditing(true);
    requestAnimationFrame(() => editTextareaRef.current?.focus());
  }

  async function saveEdit() {
    const trimmed = editValue.trim();
    if (!trimmed || saving) return;
    setSaving(true);
    try {
      await onEdit(trimmed);
      setIsEditing(false);
    } finally {
      setSaving(false);
    }
  }

  function handleEditKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Escape") {
      setIsEditing(false);
      return;
    }
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      saveEdit();
    }
  }

  function handleDeleteClick() {
    if (window.confirm("このメッセージを削除しますか?この操作は取り消せません。")) onDelete();
  }

  return (
    <div
      className={`group relative flex flex-wrap gap-3 px-4 py-1 transition-colors duration-700 hover:bg-slate-50 ${grouped ? "" : "mt-2"} ${highlighted ? "bg-amber-100 hover:bg-amber-100" : ""}`}
      onMouseLeave={() => setShowPicker(false)}
    >
      <div className="w-9 flex-shrink-0">
        {!grouped && (
          <Avatar userId={message.authorId} displayName={displayName} avatarUrl={author?.avatarUrl} />
        )}
      </div>
      <div className="min-w-0 flex-1">
        {!grouped && (
          <div className="flex items-baseline gap-2">
            <span className="text-sm font-bold text-slate-800">{displayName}</span>
            <span className="text-xs text-slate-400">{formatTime(message.createdAt)}</span>
          </div>
        )}
        {message.deleted ? (
          <p className="text-sm italic text-slate-400">このメッセージは削除されました</p>
        ) : isEditing ? (
          <div className="max-w-lg">
            <textarea
              ref={editTextareaRef}
              value={editValue}
              onChange={(e) => setEditValue(e.target.value)}
              onKeyDown={handleEditKeyDown}
              rows={2}
              className="w-full resize-none rounded-md border border-brand-400 px-2 py-1 text-sm outline-none focus:ring-1 focus:ring-brand-400"
            />
            <div className="mt-1 flex gap-2 text-xs">
              <button
                onClick={saveEdit}
                disabled={!editValue.trim() || saving}
                className="font-medium text-brand-600 hover:underline disabled:opacity-40"
              >
                保存
              </button>
              <button onClick={() => setIsEditing(false)} className="text-slate-500 hover:underline">
                キャンセル
              </button>
            </div>
          </div>
        ) : (
          <div className="msg-body break-words text-sm text-slate-800">
            <span dangerouslySetInnerHTML={{ __html: bodyHtml }} />
            {message.editedAt && <span className="ml-1 text-xs text-slate-400">(編集済み)</span>}
          </div>
        )}

        {!message.deleted && message.attachments.length > 0 && (
          <div className="mt-1 flex flex-wrap gap-2">
            {message.attachments.map((a) => {
              const src = attachmentUrl(a.url ?? "");
              return a.kind === "VIDEO" ? (
                <video
                  key={a.id}
                  src={src}
                  controls
                  className="max-h-64 max-w-xs rounded-md border border-slate-200"
                />
              ) : (
                <a key={a.id} href={src} target="_blank" rel="noopener noreferrer">
                  <img
                    src={src}
                    alt={a.fileName ?? "添付画像"}
                    className="max-h-64 max-w-xs rounded-md border border-slate-200 object-contain"
                  />
                </a>
              );
            })}
          </div>
        )}

        {message.reactions.length > 0 && (
          <div className="mt-1 flex flex-wrap gap-1">
            {message.reactions.map((r) => {
              const mine = r.userIds.includes(currentUserId);
              const names = r.userIds.map((id) => userMap[id]?.displayName ?? "不明なユーザー").join("、");
              return (
                <button
                  key={r.emoji}
                  onClick={() => onToggleReaction(r.emoji)}
                  title={names}
                  className={`rounded-full border px-2 py-0.5 text-xs ${
                    mine
                      ? "border-brand-400 bg-brand-50 text-brand-700"
                      : "border-slate-200 bg-white text-slate-600 hover:border-slate-300"
                  }`}
                >
                  {r.emoji} {r.count}
                </button>
              );
            })}
          </div>
        )}

        {!message.deleted && message.replyCount > 0 && onOpenThread && (
          <button
            onClick={onOpenThread}
            className="mt-1 flex items-center gap-1 text-xs font-medium text-brand-600 hover:underline"
          >
            💬 {message.replyCount}件の返信
          </button>
        )}
      </div>

      {!message.deleted && !isEditing && (
        <div
          className={
            // 640px以上ではdisplay:noneではなくopacity+pointer-eventsで出し分ける。display:noneだと
            // フォーカス不能になりTabで到達できず、group-focus-within自体が発火し得ない
            // (実機確認で発見: 先にgroup-hover:flex/group-focus-within:flexの併記だけを試したところ、
            // 隠れているボタンにはそもそもTabで辿り着けず無効だった)。opacity-0のまま`display:flex`は
            // 維持することでTab移動そのものは常に可能にしておき、フォーカスが当たった瞬間に
            // group-focus-within:opacity-100で可視化する。
            "static mt-1 flex w-full items-center gap-1 rounded-md border border-slate-200 bg-white px-1 py-0.5 shadow-sm " +
            "sm:absolute sm:right-4 sm:top-0 sm:mt-0 sm:w-auto sm:opacity-0 sm:pointer-events-none " +
            "sm:group-hover:opacity-100 sm:group-hover:pointer-events-auto " +
            "sm:group-focus-within:opacity-100 sm:group-focus-within:pointer-events-auto"
          }
        >
          <div className="relative">
            <button
              onClick={() => setShowPicker((v) => !v)}
              className="rounded px-1.5 py-0.5 text-sm hover:bg-slate-100"
              title="リアクションを追加"
            >
              🙂+
            </button>
            {showPicker && (
              <div className="absolute right-0 top-full z-10 mt-1 flex gap-1 rounded-md border border-slate-200 bg-white p-1 shadow-md">
                {QUICK_REACTIONS.map((emoji) => (
                  <button
                    key={emoji}
                    onClick={() => {
                      onToggleReaction(emoji);
                      setShowPicker(false);
                    }}
                    className="rounded px-1 text-base hover:bg-slate-100"
                  >
                    {emoji}
                  </button>
                ))}
              </div>
            )}
          </div>
          {onOpenThread && (
            <button
              onClick={onOpenThread}
              className="rounded px-1.5 py-0.5 text-sm hover:bg-slate-100"
              title="スレッドで返信"
            >
              🧵
            </button>
          )}
          {isMine && (
            <button
              onClick={startEdit}
              className="rounded px-1.5 py-0.5 text-sm hover:bg-slate-100"
              title="編集"
            >
              ✏️
            </button>
          )}
          {isMine && (
            <button
              onClick={handleDeleteClick}
              className="rounded px-1.5 py-0.5 text-sm text-red-500 hover:bg-red-50"
              title="削除"
            >
              削除
            </button>
          )}
        </div>
      )}
    </div>
  );
}
