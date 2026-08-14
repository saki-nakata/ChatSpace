import { FormEvent, useRef, useState } from "react";
import Modal from "./Modal";
import { useAuthStore } from "../../store/authStore";
import { uploadApi } from "../../api/resources";
import { attachmentUrl, ApiError } from "../../api/client";

interface Props {
  onClose: () => void;
}

export default function ProfileModal({ onClose }: Props) {
  const { user, updateProfile, logout } = useAuthStore();
  const [displayName, setDisplayName] = useState(user?.displayName ?? "");
  const [status, setStatus] = useState(user?.status ?? "");
  const [avatarUrl, setAvatarUrl] = useState(user?.avatarUrl ?? null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  async function handleAvatarChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setError(null);
    setUploading(true);
    try {
      const { attachment } = await uploadApi.upload(file);
      setAvatarUrl(attachment.url);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "アップロードに失敗しました。");
    } finally {
      setUploading(false);
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await updateProfile({ displayName, status, avatarUrl });
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "更新に失敗しました。");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal title="プロフィール設定" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="flex items-center gap-4">
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-full bg-brand-500 text-xl font-bold text-white"
          >
            {avatarUrl ? (
              <img src={attachmentUrl(avatarUrl)} alt="" className="h-full w-full object-cover" />
            ) : (
              (displayName || "?").slice(0, 1).toUpperCase()
            )}
          </button>
          <div>
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
              className="text-sm font-medium text-brand-600 hover:underline disabled:opacity-50"
            >
              {uploading ? "アップロード中..." : "アバター画像を変更"}
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/png,image/jpeg,image/gif,image/webp"
              className="hidden"
              onChange={handleAvatarChange}
            />
            <p className="text-xs text-slate-500">@{user?.userId}</p>
          </div>
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">表示名</label>
          <input
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
          />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">ステータス</label>
          <input
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            placeholder="例: 在宅勤務中"
          />
        </div>

        {error && <p className="text-sm text-red-600">{error}</p>}

        <div className="flex gap-2">
          <button
            type="submit"
            disabled={submitting}
            className="flex-1 rounded-md bg-brand-500 py-2 text-sm font-semibold text-white hover:bg-brand-600 disabled:opacity-50"
          >
            保存
          </button>
          <button
            type="button"
            onClick={() => logout()}
            className="rounded-md border border-slate-300 px-4 py-2 text-sm text-slate-600 hover:bg-slate-50"
          >
            ログアウト
          </button>
        </div>
      </form>
    </Modal>
  );
}
