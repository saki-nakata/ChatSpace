import { useState } from "react";
import Modal from "../common/Modal";
import { dmApi } from "../../api/resources";
import { ApiError } from "../../api/client";
import type { DmThreadResponse } from "../../api/types";

interface StartDmModalProps {
  workspaceId: string;
  onClose: () => void;
  onStarted: (thread: DmThreadResponse) => void;
  restoreFocusTo?: HTMLElement | null;
}

/** S-11 DM開始モーダル(画面設計書)。 */
export default function StartDmModal({ workspaceId, onClose, onStarted, restoreFocusTo }: StartDmModalProps) {
  const [userId, setUserId] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = userId.trim();
    if (!trimmed || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const thread = await dmApi.getOrCreate(workspaceId, { userId: trimmed });
      onStarted(thread);
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "DMの開始に失敗しました。");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal title="ダイレクトメッセージを開始" onClose={onClose} restoreFocusTo={restoreFocusTo}>
      <form onSubmit={handleSubmit} className="space-y-4 p-4">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-500">相手のユーザーID</label>
          <input
            autoFocus
            data-initial-focus
            type="text"
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
            placeholder="例: bob"
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
          />
          <p className="mt-1 text-xs text-slate-400">同じワークスペースのメンバーとのみDMできます。</p>
        </div>

        {error && <p className="text-sm text-red-600">{error}</p>}

        <button
          type="submit"
          disabled={!userId.trim() || submitting}
          className="w-full rounded-md bg-brand-500 px-4 py-2 text-sm font-medium text-white hover:bg-brand-600 disabled:opacity-40"
        >
          {submitting ? "開始中..." : "開始"}
        </button>
      </form>
    </Modal>
  );
}
