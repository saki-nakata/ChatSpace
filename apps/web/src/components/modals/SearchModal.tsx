import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { MessageDTO } from "@chatspace/shared";
import Modal from "./Modal";
import { searchApi } from "../../api/resources";
import { formatRelative } from "../../lib/time";

interface Props {
  workspaceId: string;
  onClose: () => void;
}

export default function SearchModal({ workspaceId, onClose }: Props) {
  const navigate = useNavigate();
  const [q, setQ] = useState("");
  const [results, setResults] = useState<MessageDTO[]>([]);
  const [searched, setSearched] = useState(false);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!q.trim()) return;
    setLoading(true);
    try {
      const { messages } = await searchApi.search(workspaceId, q.trim());
      setResults(messages);
      setSearched(true);
    } finally {
      setLoading(false);
    }
  }

  function goToMessage(message: MessageDTO) {
    onClose();
    // スレッド返信がヒットした場合、メイン一覧には表示されないため、
    // 親メッセージを軸に(highlight)、その返信を(reply)スレッドパネルで開いてハイライトする。
    const highlight = message.parentId ?? message.id;
    const params = new URLSearchParams({ highlight });
    if (message.parentId) params.set("reply", message.id);

    if (message.channelId) {
      navigate(`/w/${workspaceId}/c/${message.channelId}?${params.toString()}`);
    } else if (message.dmId) {
      navigate(`/w/${workspaceId}/dm/${message.dmId}?${params.toString()}`);
    }
  }

  return (
    <Modal title="メッセージ検索" onClose={onClose} widthClassName="max-w-lg">
      <form onSubmit={handleSubmit} className="mb-4 flex gap-2">
        <input
          className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="キーワードを入力"
          autoFocus
        />
        <button
          type="submit"
          disabled={loading}
          className="rounded-md bg-brand-500 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-600 disabled:opacity-50"
        >
          検索
        </button>
      </form>

      <p className="mb-2 text-xs text-slate-500">
        自分が参加しているチャンネル・DMの範囲のみを検索します。
      </p>

      {searched && results.length === 0 && !loading && (
        <p className="text-sm text-slate-500">該当するメッセージが見つかりませんでした。</p>
      )}

      <ul className="space-y-2">
        {results.map((m) => (
          <li key={m.id}>
            <button
              onClick={() => goToMessage(m)}
              className="w-full rounded-md border border-slate-200 p-3 text-left text-sm hover:border-brand-300 hover:bg-brand-50"
            >
              <div className="mb-1 flex items-center justify-between text-xs text-slate-500">
                <span className="font-medium text-slate-600">{m.author.displayName}</span>
                <span>{formatRelative(m.createdAt)}</span>
              </div>
              <p className="line-clamp-2 whitespace-pre-wrap text-slate-700">{m.body}</p>
            </button>
          </li>
        ))}
      </ul>
    </Modal>
  );
}
