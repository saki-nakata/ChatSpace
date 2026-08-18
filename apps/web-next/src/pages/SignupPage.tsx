import { type FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuthStore } from "../store/authStore";
import { ApiError } from "../api/client";

export default function SignupPage() {
  const navigate = useNavigate();
  const signup = useAuthStore((s) => s.signup);
  const [userId, setUserId] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await signup(userId, password, displayName);
      navigate("/", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "登録に失敗しました。");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center bg-brand-50 px-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-8 shadow-sm">
        <h1 className="mb-1 text-2xl font-bold text-brand-700">ChatSpace</h1>
        <p className="mb-6 text-sm text-slate-500">新規アカウント登録</p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="signup-userId" className="mb-1 block text-sm font-medium text-slate-700">
              ユーザーID
            </label>
            <input
              id="signup-userId"
              name="userId"
              autoComplete="username"
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              autoFocus
              required
              pattern="^[a-zA-Z0-9_.\-]{3,20}$"
              title="英数字・._- のみ、3〜20文字"
            />
          </div>
          <div>
            <label htmlFor="signup-displayName" className="mb-1 block text-sm font-medium text-slate-700">
              表示名
            </label>
            <input
              id="signup-displayName"
              name="displayName"
              autoComplete="nickname"
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              required
              maxLength={50}
            />
          </div>
          <div>
            <label htmlFor="signup-password" className="mb-1 block text-sm font-medium text-slate-700">
              パスワード
            </label>
            <input
              id="signup-password"
              name="password"
              type="password"
              autoComplete="new-password"
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={8}
            />
          </div>

          {error && <p className="text-sm text-red-600">{error}</p>}

          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-md bg-brand-500 py-2 text-sm font-semibold text-white transition hover:bg-brand-600 disabled:opacity-50"
          >
            {submitting ? "登録中..." : "登録"}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-500">
          既にアカウントをお持ちですか?{" "}
          <Link to="/login" className="font-medium text-brand-600 hover:underline">
            ログイン
          </Link>
        </p>
      </div>
    </div>
  );
}
