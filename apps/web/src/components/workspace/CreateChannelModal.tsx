import { useState } from "react";
import Modal from "../common/Modal";
import { channelApi } from "../../api/resources";
import { ApiError } from "../../api/client";
import type { ChannelResponse } from "../../api/types";

const CHANNEL_NAME_REGEX = /^[a-zA-Z0-9_-]{1,50}$/;

interface CreateChannelModalProps {
  workspaceId: string;
  onClose: () => void;
  onCreated: (channel: ChannelResponse) => void;
  restoreFocusTo?: HTMLElement | null;
}

/** S-08チャンネル作成モーダル(画面設計書、ワークスペースオーナーのみ起動可能)。 */
export default function CreateChannelModal({
  workspaceId,
  onClose,
  onCreated,
  restoreFocusTo,
}: CreateChannelModalProps) {
  const [name, setName] = useState("");
  const [type, setType] = useState<"PUBLIC" | "PRIVATE">("PUBLIC");
  const [memberIdsText, setMemberIdsText] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!CHANNEL_NAME_REGEX.test(name)) {
      setError("チャンネル名は英数字・_・- のみ、1〜50文字で入力してください。");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const memberUserIds =
        type === "PRIVATE"
          ? memberIdsText
              .split(",")
              .map((s) => s.trim())
              .filter((s) => s.length > 0)
          : undefined;
      const channel = await channelApi.create(workspaceId, { name, type, memberUserIds });
      onCreated(channel);
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "チャンネルの作成に失敗しました。");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal title="チャンネルを作成" onClose={onClose} restoreFocusTo={restoreFocusTo}>
      <form onSubmit={handleSubmit} className="space-y-4 p-4">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-500">チャンネル名</label>
          <input
            autoFocus
            data-initial-focus
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="例: general"
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
          />
        </div>

        <fieldset>
          <legend className="mb-1 block text-xs font-medium text-slate-500">公開範囲</legend>
          <label className="flex items-center gap-2 py-1 text-sm text-slate-700">
            <input
              type="radio"
              name="channel-type"
              checked={type === "PUBLIC"}
              onChange={() => setType("PUBLIC")}
            />
            パブリック(誰でも参加可能)
          </label>
          <label className="flex items-center gap-2 py-1 text-sm text-slate-700">
            <input
              type="radio"
              name="channel-type"
              checked={type === "PRIVATE"}
              onChange={() => setType("PRIVATE")}
            />
            プライベート(招待制)
          </label>
        </fieldset>

        {type === "PRIVATE" && (
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-500">
              初期メンバーのユーザーID(任意)
            </label>
            <input
              type="text"
              value={memberIdsText}
              onChange={(e) => setMemberIdsText(e.target.value)}
              placeholder="例: bob, carol"
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
            />
          </div>
        )}

        {error && <p className="text-sm text-red-600">{error}</p>}

        <button
          type="submit"
          disabled={!name.trim() || submitting}
          className="w-full rounded-md bg-brand-500 px-4 py-2 text-sm font-medium text-white hover:bg-brand-600 disabled:opacity-40"
        >
          {submitting ? "作成中..." : "作成"}
        </button>
      </form>
    </Modal>
  );
}
