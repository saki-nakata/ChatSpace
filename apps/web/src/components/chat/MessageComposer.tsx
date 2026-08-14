import { KeyboardEvent, useMemo, useRef, useState } from "react";
import type { AttachmentDTO } from "@chatspace/shared";
import { SocketEvents } from "@chatspace/shared";
import { uploadApi } from "../../api/resources";
import { attachmentUrl, ApiError } from "../../api/client";
import { getSocket } from "../../lib/socket";

export interface MentionCandidate {
  userId: string;
  displayName: string;
}

type ScopeType = "channel" | "dm";

interface Props {
  onSend: (body: string, attachmentIds: string[]) => Promise<void>;
  placeholder?: string;
  autoFocus?: boolean;
  scopeType?: ScopeType;
  scopeId?: string;
  mentionCandidates?: MentionCandidate[];
}

const TYPING_THROTTLE_MS = 2000;

export default function MessageComposer({
  onSend,
  placeholder,
  autoFocus,
  scopeType,
  scopeId,
  mentionCandidates,
}: Props) {
  const [body, setBody] = useState("");
  const [attachments, setAttachments] = useState<AttachmentDTO[]>([]);
  const [uploading, setUploading] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mentionQuery, setMentionQuery] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const lastTypingEmitRef = useRef(0);

  const mentionMatches = useMemo(() => {
    if (mentionQuery === null || !mentionCandidates) return [];
    const needle = mentionQuery.toLowerCase();
    return mentionCandidates
      .filter(
        (c) => c.userId.toLowerCase().startsWith(needle) || c.displayName.toLowerCase().includes(needle),
      )
      .slice(0, 6);
  }, [mentionQuery, mentionCandidates]);

  function emitTyping() {
    if (!scopeType || !scopeId) return;
    const now = Date.now();
    if (now - lastTypingEmitRef.current < TYPING_THROTTLE_MS) return;
    lastTypingEmitRef.current = now;
    getSocket().emit(SocketEvents.Typing, scopeType === "channel" ? { channelId: scopeId } : { dmId: scopeId });
  }

  function handleChange(e: React.ChangeEvent<HTMLTextAreaElement>) {
    const value = e.target.value;
    setBody(value);
    emitTyping();

    if (mentionCandidates) {
      const cursor = e.target.selectionStart ?? value.length;
      const beforeCursor = value.slice(0, cursor);
      const match = beforeCursor.match(/(?:^|\s)@([a-zA-Z0-9_.-]{0,20})$/);
      setMentionQuery(match ? match[1] : null);
    }
  }

  function insertMention(candidate: MentionCandidate) {
    const textarea = textareaRef.current;
    const cursor = textarea?.selectionStart ?? body.length;
    const beforeCursor = body.slice(0, cursor);
    const afterCursor = body.slice(cursor);
    const replaced = beforeCursor.replace(/(?:^|\s)@([a-zA-Z0-9_.-]{0,20})$/, (m) =>
      (m.startsWith(" ") ? " " : "") + `@${candidate.userId} `,
    );
    const nextBody = replaced + afterCursor;
    setBody(nextBody);
    setMentionQuery(null);
    requestAnimationFrame(() => textarea?.focus());
  }

  async function handleFiles(files: FileList | null) {
    if (!files || files.length === 0) return;
    setError(null);
    setUploading(true);
    try {
      for (const file of Array.from(files)) {
        const { attachment } = await uploadApi.upload(file);
        setAttachments((prev) => [...prev, attachment]);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "アップロードに失敗しました。");
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  async function handleSend() {
    const trimmed = body.trim();
    if (!trimmed && attachments.length === 0) return;
    setSending(true);
    setError(null);
    try {
      await onSend(trimmed, attachments.map((a) => a.id));
      setBody("");
      setAttachments([]);
      setMentionQuery(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "送信に失敗しました。");
    } finally {
      setSending(false);
    }
  }

  function handleKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (mentionMatches.length > 0 && (e.key === "Enter" || e.key === "Tab") && mentionQuery !== null) {
      e.preventDefault();
      insertMention(mentionMatches[0]);
      return;
    }
    if (e.key === "Escape" && mentionQuery !== null) {
      setMentionQuery(null);
      return;
    }
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }

  return (
    <div className="relative border-t border-slate-200 bg-white p-3">
      {mentionMatches.length > 0 && (
        <div className="absolute bottom-full left-3 mb-1 w-56 overflow-hidden rounded-md border border-slate-200 bg-white shadow-lg">
          {mentionMatches.map((c) => (
            <button
              key={c.userId}
              onClick={() => insertMention(c)}
              className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-brand-50"
            >
              <span className="font-medium text-slate-700">{c.displayName}</span>
              <span className="text-xs text-slate-500">@{c.userId}</span>
            </button>
          ))}
        </div>
      )}

      {attachments.length > 0 && (
        <div className="mb-2 flex flex-wrap gap-2">
          {attachments.map((a) => (
            <div key={a.id} className="flex items-center gap-2 rounded-md border border-slate-200 bg-slate-50 p-1.5 text-xs">
              {a.kind === "IMAGE" ? (
                <img src={attachmentUrl(a.url)} alt={a.fileName} className="h-8 w-8 rounded object-cover" />
              ) : (
                <span>🎬</span>
              )}
              <span className="max-w-[8rem] truncate">{a.fileName}</span>
              <button
                onClick={() => setAttachments((prev) => prev.filter((x) => x.id !== a.id))}
                aria-label={`${a.fileName} を添付から外す`}
                className="text-slate-500 hover:text-red-500"
              >
                ✕
              </button>
            </div>
          ))}
        </div>
      )}

      {error && <p className="mb-2 text-xs text-red-600">{error}</p>}

      <div className="flex items-end gap-2">
        <button
          onClick={() => fileInputRef.current?.click()}
          disabled={uploading}
          title="画像・動画を添付"
          aria-label="画像・動画を添付"
          className="rounded-md border border-slate-200 px-2 py-2 text-slate-500 hover:bg-slate-50 disabled:opacity-50"
        >
          📎
        </button>
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept="image/png,image/jpeg,image/gif,image/webp,video/mp4,video/webm"
          className="hidden"
          onChange={(e) => handleFiles(e.target.files)}
        />
        <textarea
          ref={textareaRef}
          value={body}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          placeholder={placeholder ?? "メッセージを入力 (Markdown対応、Shift+Enterで改行、@でメンション)"}
          rows={1}
          autoFocus={autoFocus}
          className="max-h-32 flex-1 resize-none rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
        />
        <button
          onClick={handleSend}
          disabled={sending || uploading}
          className="rounded-md bg-brand-500 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-600 disabled:opacity-50"
        >
          送信
        </button>
      </div>
    </div>
  );
}
