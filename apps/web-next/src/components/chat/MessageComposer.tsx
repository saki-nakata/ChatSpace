import { type ChangeEvent, type KeyboardEvent, useEffect, useMemo, useRef, useState } from "react";
import { channelApi, uploadApi } from "../../api/resources";
import { ApiError } from "../../api/client";
import type { AttachmentResponse, UserResponse } from "../../api/types";

interface MentionSource {
  workspaceId: string;
  channelId: string;
}

interface MessageComposerProps {
  onSend: (body: string, attachmentIds?: string[]) => Promise<void>;
  placeholder?: string;
  /** 入力のたびに呼ぶ(親がSTOMPタイピングイベントのスロットリング送信を担う)。 */
  onTyping?: () => void;
  /** チャンネルスコープの場合のみ渡す。DMにはメンション自動補完のAPIが存在しないため対象外(メンション機能定義書§3.3)。 */
  mentionSource?: MentionSource;
}

const MENTION_TRIGGER = /(?:^|\s)@([a-zA-Z0-9_.-]{0,20})$/;
const MENTION_DEBOUNCE_MS = 200;
/** バックエンドのMimeSniffer(添付ファイル機能定義書§3.1)が実際に許可する6形式のみをファイル選択ダイアログに出す。 */
const ACCEPTED_ATTACHMENT_TYPES =
  "image/png,image/jpeg,image/gif,image/webp,video/mp4,video/webm";
/** CreateMessageRequest.attachmentIdsのバリデーション上限(メッセージング機能定義書§5)と一致させる。 */
const MAX_ATTACHMENTS = 10;

interface PendingAttachment {
  key: string;
  fileName: string;
  previewUrl: string;
  uploading: boolean;
  error: string | null;
  attachment: AttachmentResponse | null;
}

/** Slackの下部メッセージ入力欄に倣う。Enterで送信、Shift+Enterで改行、@でメンション自動補完。 */
export default function MessageComposer({
  onSend,
  placeholder,
  onTyping,
  mentionSource,
}: MessageComposerProps) {
  const [body, setBody] = useState("");
  const [sending, setSending] = useState(false);
  const [mentionQuery, setMentionQuery] = useState<string | null>(null);
  const [candidates, setCandidates] = useState<UserResponse[]>([]);
  const [pendingAttachments, setPendingAttachments] = useState<PendingAttachment[]>([]);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // 入力欄の自動リサイズ(Shift+Enterで改行しても1行のまま固定されていた問題の修正、max-h-40が
  // 実効的な上限として働く。この上限を超えると通常のtextarea同様にスクロールする)。
  useEffect(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${el.scrollHeight}px`;
  }, [body]);

  const visibleCandidates = useMemo(() => candidates.slice(0, 6), [candidates]);
  const uploadingCount = pendingAttachments.filter((a) => a.uploading).length;
  const readyAttachmentIds = pendingAttachments
    .map((a) => a.attachment?.id)
    .filter((id): id is string => !!id);

  async function handleSend() {
    const trimmed = body.trim();
    // メッセージ本文はメッセージング機能定義書§5により添付ファイルの有無に関わらず必須(1〜4000文字)。
    if (!trimmed || sending || uploadingCount > 0) return;
    setSending(true);
    try {
      await onSend(trimmed, readyAttachmentIds.length > 0 ? readyAttachmentIds : undefined);
      setBody("");
      setMentionQuery(null);
      pendingAttachments.forEach((a) => URL.revokeObjectURL(a.previewUrl));
      setPendingAttachments([]);
    } finally {
      setSending(false);
    }
  }

  function handleFilesSelected(e: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    e.target.value = ""; // 同じファイルを連続選択できるようリセット
    if (files.length === 0) return;

    const room = MAX_ATTACHMENTS - pendingAttachments.length;
    const accepted = files.slice(0, Math.max(room, 0));

    const entries: PendingAttachment[] = accepted.map((file) => ({
      key: `${file.name}-${file.size}-${Date.now()}-${Math.random()}`,
      fileName: file.name,
      previewUrl: URL.createObjectURL(file),
      uploading: true,
      error: null,
      attachment: null,
    }));
    setPendingAttachments((prev) => [...prev, ...entries]);

    entries.forEach((entry, i) => {
      uploadApi
        .upload(accepted[i])
        .then((attachment) => {
          setPendingAttachments((prev) =>
            prev.map((a) =>
              a.key === entry.key ? { ...a, uploading: false, attachment: attachment ?? null } : a,
            ),
          );
        })
        .catch((err: unknown) => {
          const message = err instanceof ApiError ? err.message : "アップロードに失敗しました。";
          setPendingAttachments((prev) =>
            prev.map((a) => (a.key === entry.key ? { ...a, uploading: false, error: message } : a)),
          );
        });
    });
  }

  function removeAttachment(key: string) {
    setPendingAttachments((prev) => {
      const target = prev.find((a) => a.key === key);
      if (target) URL.revokeObjectURL(target.previewUrl);
      return prev.filter((a) => a.key !== key);
    });
  }

  function handleChange(e: ChangeEvent<HTMLTextAreaElement>) {
    const value = e.target.value;
    setBody(value);
    onTyping?.();

    if (!mentionSource) return;
    const cursor = e.target.selectionStart ?? value.length;
    const match = value.slice(0, cursor).match(MENTION_TRIGGER);
    const query = match ? match[1] : null;
    setMentionQuery(query);
    if (query === null) {
      setCandidates([]);
      return;
    }
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      const res = await channelApi.mentionCandidates(
        mentionSource.workspaceId,
        mentionSource.channelId,
        query,
      );
      setCandidates(res.candidates ?? []);
    }, MENTION_DEBOUNCE_MS);
  }

  function insertMention(candidate: UserResponse) {
    const textarea = textareaRef.current;
    const cursor = textarea?.selectionStart ?? body.length;
    const before = body.slice(0, cursor);
    const after = body.slice(cursor);
    const replaced = before.replace(MENTION_TRIGGER, (m) => `${m.startsWith(" ") ? " " : ""}@${candidate.userId} `);
    setBody(replaced + after);
    setMentionQuery(null);
    setCandidates([]);
    requestAnimationFrame(() => textarea?.focus());
  }

  function handleKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (mentionQuery !== null && visibleCandidates.length > 0 && (e.key === "Enter" || e.key === "Tab")) {
      e.preventDefault();
      insertMention(visibleCandidates[0]);
      return;
    }
    if (e.key === "Escape" && mentionQuery !== null) {
      setMentionQuery(null);
      setCandidates([]);
      return;
    }
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }

  return (
    <div className="relative border-t border-slate-200 bg-white px-4 py-3">
      {mentionQuery !== null && visibleCandidates.length > 0 && (
        <div className="absolute bottom-full left-4 mb-1 w-56 overflow-hidden rounded-md border border-slate-200 bg-white shadow-lg">
          {visibleCandidates.map((c) => (
            <button
              key={c.id}
              onClick={() => insertMention(c)}
              className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-brand-50"
            >
              <span className="font-medium text-slate-700">{c.displayName}</span>
              <span className="text-xs text-slate-500">@{c.userId}</span>
            </button>
          ))}
        </div>
      )}

      {pendingAttachments.length > 0 && (
        <div className="mb-2 flex flex-wrap gap-2">
          {pendingAttachments.map((a) => (
            <div
              key={a.key}
              className="relative flex h-16 w-16 flex-shrink-0 items-center justify-center overflow-hidden rounded-md border border-slate-200 bg-slate-50"
              title={a.fileName}
            >
              {a.attachment?.kind === "VIDEO" ? (
                <video src={a.previewUrl} className="h-full w-full object-cover" muted />
              ) : (
                <img src={a.previewUrl} alt={a.fileName} className="h-full w-full object-cover" />
              )}
              {a.uploading && (
                <div className="absolute inset-0 flex items-center justify-center bg-black/40 text-xs text-white">
                  送信中...
                </div>
              )}
              {a.error && (
                <div className="absolute inset-0 flex items-center justify-center bg-red-500/80 p-1 text-center text-[10px] text-white">
                  {a.error}
                </div>
              )}
              <button
                onClick={() => removeAttachment(a.key)}
                aria-label="添付ファイルを削除"
                className="absolute right-0.5 top-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-black/60 text-[10px] leading-none text-white hover:bg-black/80"
              >
                ✕
              </button>
            </div>
          ))}
        </div>
      )}

      <div className="flex items-end gap-2 rounded-lg border border-slate-300 px-3 py-2 focus-within:border-brand-500 focus-within:ring-1 focus-within:ring-brand-500">
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept={ACCEPTED_ATTACHMENT_TYPES}
          onChange={handleFilesSelected}
          className="hidden"
        />
        <button
          onClick={() => fileInputRef.current?.click()}
          disabled={pendingAttachments.length >= MAX_ATTACHMENTS}
          title="画像・動画を添付"
          className="rounded-md px-2 py-1.5 text-lg text-slate-500 hover:bg-slate-100 disabled:opacity-40"
        >
          📎
        </button>
        <textarea
          ref={textareaRef}
          className="max-h-40 flex-1 resize-none text-sm outline-none"
          rows={1}
          value={body}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          placeholder={placeholder ?? "メッセージを入力..."}
        />
        <button
          onClick={handleSend}
          disabled={!body.trim() || sending || uploadingCount > 0}
          className="rounded-md bg-brand-500 px-3 py-1.5 text-sm font-medium text-white transition hover:bg-brand-600 disabled:opacity-40"
        >
          送信
        </button>
      </div>
    </div>
  );
}
