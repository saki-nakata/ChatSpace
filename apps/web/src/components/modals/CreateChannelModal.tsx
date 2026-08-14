import { FormEvent, useState } from "react";
import { CHANNEL_NAME_REGEX } from "@chatspace/shared";
import Modal from "./Modal";
import { channelApi } from "../../api/resources";
import { ApiError } from "../../api/client";
import { useWorkspaceStore } from "../../store/workspaceStore";
import { useNavigate } from "react-router-dom";

interface Props {
  workspaceId: string;
  onClose: () => void;
}

export default function CreateChannelModal({ workspaceId, onClose }: Props) {
  const navigate = useNavigate();
  const upsertChannel = useWorkspaceStore((s) => s.upsertChannel);
  const [name, setName] = useState("");
  const [type, setType] = useState<"PUBLIC" | "PRIVATE">("PUBLIC");
  const [memberUserIdsText, setMemberUserIdsText] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!CHANNEL_NAME_REGEX.test(name)) {
      setError("チャンネル名は英数字・_・- のみ、1〜50文字で入力してください。");
      return;
    }
    setSubmitting(true);
    try {
      const memberUserIds = memberUserIdsText
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
      const { channel } = await channelApi.create(workspaceId, { name, type, memberUserIds });
      upsertChannel(channel);
      onClose();
      navigate(`/w/${workspaceId}/c/${channel.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "作成に失敗しました。");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal title="チャンネルを作成" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">チャンネル名</label>
          <input
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="例: general"
            autoFocus
          />
        </div>

        <div>
          <span className="mb-1 block text-sm font-medium text-slate-700">公開範囲</span>
          <div className="flex gap-4 text-sm">
            <label className="flex items-center gap-1.5">
              <input type="radio" checked={type === "PUBLIC"} onChange={() => setType("PUBLIC")} />
              パブリック(誰でも参加可能)
            </label>
            <label className="flex items-center gap-1.5">
              <input type="radio" checked={type === "PRIVATE"} onChange={() => setType("PRIVATE")} />
              プライベート(招待制)
            </label>
          </div>
        </div>

        {type === "PRIVATE" && (
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">
              初期メンバーのユーザーID(カンマ区切り、任意)
            </label>
            <input
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              value={memberUserIdsText}
              onChange={(e) => setMemberUserIdsText(e.target.value)}
              placeholder="例: bob, carol"
            />
          </div>
        )}

        {error && <p className="text-sm text-red-600">{error}</p>}

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-brand-500 py-2 text-sm font-semibold text-white hover:bg-brand-600 disabled:opacity-50"
        >
          作成
        </button>
      </form>
    </Modal>
  );
}
