import { type FormEvent, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useWorkspaceStore } from "../store/workspaceStore";
import { useAuthStore } from "../store/authStore";
import { ApiError } from "../api/client";

export default function WorkspacePickerPage() {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const workspaces = useWorkspaceStore((s) => s.workspaces);
  const fetchWorkspaces = useWorkspaceStore((s) => s.fetchWorkspaces);
  const createWorkspace = useWorkspaceStore((s) => s.createWorkspace);
  const [name, setName] = useState("");
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchWorkspaces();
  }, [fetchWorkspaces]);

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    setError(null);
    setCreating(true);
    try {
      const workspace = await createWorkspace(name.trim());
      setName("");
      navigate(`/w/${workspace.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "作成に失敗しました。");
    } finally {
      setCreating(false);
    }
  }

  return (
    <div className="mx-auto flex h-full max-w-2xl flex-col px-6 py-10">
      <div className="mb-8 flex items-center justify-between">
        <h1 className="text-xl font-bold text-brand-700">ChatSpace</h1>
        <div className="flex items-center gap-3 text-sm text-slate-500">
          <span>{user?.displayName}</span>
          <button onClick={() => logout()} className="text-slate-500 hover:text-slate-700">
            ログアウト
          </button>
        </div>
      </div>

      <h2 className="mb-3 text-sm font-semibold text-slate-500">ワークスペースを選択</h2>
      <div className="mb-8 space-y-2">
        {workspaces.length === 0 && (
          <p className="rounded-md border border-dashed border-slate-300 p-4 text-sm text-slate-500">
            まだワークスペースがありません。下から新規作成してください。
          </p>
        )}
        {workspaces.map((w) => (
          <button
            key={w.id}
            onClick={() => navigate(`/w/${w.id}`)}
            className="flex w-full items-center justify-between rounded-lg border border-slate-200 bg-white px-4 py-3 text-left shadow-sm transition hover:border-brand-300 hover:shadow"
          >
            <div>
              <p className="font-medium text-slate-800">{w.name}</p>
              <p className="text-xs text-slate-500">{w.myRole === "OWNER" ? "オーナー" : "メンバー"}</p>
            </div>
            <span className="text-slate-300">&rarr;</span>
          </button>
        ))}
      </div>

      <h2 className="mb-3 text-sm font-semibold text-slate-500">新規ワークスペースを作成</h2>
      <form onSubmit={handleCreate} className="flex gap-2">
        <input
          className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="ワークスペース名"
        />
        <button
          type="submit"
          disabled={creating}
          className="rounded-md bg-brand-500 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-600 disabled:opacity-50"
        >
          作成
        </button>
      </form>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
    </div>
  );
}
