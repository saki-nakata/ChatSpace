import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import Modal from "./Modal";
import { dmApi } from "../../api/resources";
import { ApiError } from "../../api/client";
import { useWorkspaceStore } from "../../store/workspaceStore";

interface Props {
  workspaceId: string;
  onClose: () => void;
}

export default function StartDMModal({ workspaceId, onClose }: Props) {
  const navigate = useNavigate();
  const upsertDMThread = useWorkspaceStore((s) => s.upsertDMThread);
  const [userId, setUserId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!userId.trim()) return;
    setSubmitting(true);
    try {
      const { dmThread } = await dmApi.open(workspaceId, userId.trim());
      upsertDMThread(dmThread);
      onClose();
      navigate(`/w/${workspaceId}/dm/${dmThread.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "DMの開始に失敗しました。");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal title="ダイレクトメッセージを開始" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">相手のユーザーID</label>
          <input
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
            placeholder="例: bob"
            autoFocus
          />
          <p className="mt-1 text-xs text-slate-500">同じワークスペースのメンバーとのみDMできます。</p>
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-brand-500 py-2 text-sm font-semibold text-white hover:bg-brand-600 disabled:opacity-50"
        >
          開始
        </button>
      </form>
    </Modal>
  );
}
