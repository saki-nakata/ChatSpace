import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import Modal from "../common/Modal";
import Avatar from "../chat/Avatar";
import { uploadApi } from "../../api/resources";
import { ApiError } from "../../api/client";
import { useAuthStore } from "../../store/authStore";

const ACCEPTED_AVATAR_TYPES = "image/png,image/jpeg,image/gif,image/webp";

interface ProfileModalProps {
  onClose: () => void;
  restoreFocusTo?: HTMLElement | null;
}

/** S-14プロフィール編集モーダル(画面設計書)。 */
export default function ProfileModal({ onClose, restoreFocusTo }: ProfileModalProps) {
  const user = useAuthStore((s) => s.user);
  const updateProfile = useAuthStore((s) => s.updateProfile);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  const [displayName, setDisplayName] = useState(user?.displayName ?? "");
  const [status, setStatus] = useState(user?.status ?? "");
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const [avatarError, setAvatarError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  if (!user) return null;

  async function handleAvatarSelected(file: File) {
    setUploadingAvatar(true);
    setAvatarError(null);
    try {
      const attachment = await uploadApi.upload(file);
      if (attachment?.url) {
        // 画面設計書S-14: 選択と同時に即座にアップロードし、保存ボタンを待たずにプレビューを更新する
        await updateProfile({ avatarUrl: attachment.url });
      }
    } catch (err) {
      setAvatarError(err instanceof ApiError ? err.message : "アバター画像のアップロードに失敗しました。");
    } finally {
      setUploadingAvatar(false);
    }
  }

  async function handleSave() {
    const trimmedName = displayName.trim();
    if (!trimmedName || saving) return;
    setSaving(true);
    setSaveError(null);
    try {
      await updateProfile({ displayName: trimmedName, status });
      onClose();
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : "保存に失敗しました。");
    } finally {
      setSaving(false);
    }
  }

  async function handleLogout() {
    await logout();
    navigate("/login", { replace: true });
  }

  return (
    <Modal title="プロフィール設定" onClose={onClose} restoreFocusTo={restoreFocusTo}>
      <div className="space-y-4 p-4">
        <div className="flex items-center gap-3">
          <button
            onClick={() => fileInputRef.current?.click()}
            disabled={uploadingAvatar}
            className="relative rounded-md disabled:opacity-60"
            title="アバター画像を変更"
          >
            <Avatar
              userId={user.id ?? ""}
              displayName={user.displayName ?? ""}
              avatarUrl={user.avatarUrl}
              size={56}
            />
            {uploadingAvatar && (
              <span className="absolute inset-0 flex items-center justify-center rounded-md bg-black/40 text-[10px] text-white">
                送信中...
              </span>
            )}
          </button>
          <div>
            <button
              onClick={() => fileInputRef.current?.click()}
              disabled={uploadingAvatar}
              className="text-sm font-medium text-brand-600 hover:underline disabled:opacity-60"
            >
              アバター画像を変更
            </button>
            <p className="text-xs text-slate-400">@{user.userId}</p>
          </div>
          <input
            ref={fileInputRef}
            type="file"
            accept={ACCEPTED_AVATAR_TYPES}
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              e.target.value = "";
              if (file) handleAvatarSelected(file);
            }}
          />
        </div>
        {avatarError && <p className="text-sm text-red-600">{avatarError}</p>}

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-500">表示名</label>
          <input
            autoFocus
            data-initial-focus
            type="text"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
          />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-500">ステータス</label>
          <input
            type="text"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            placeholder="例: 在宅勤務中"
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
          />
        </div>

        {saveError && <p className="text-sm text-red-600">{saveError}</p>}

        <div className="flex items-center justify-between pt-2">
          <button
            onClick={handleSave}
            disabled={!displayName.trim() || saving}
            className="rounded-md bg-brand-500 px-4 py-2 text-sm font-medium text-white hover:bg-brand-600 disabled:opacity-40"
          >
            {saving ? "保存中..." : "保存"}
          </button>
          <button onClick={handleLogout} className="text-sm text-slate-500 hover:text-slate-700">
            ログアウト
          </button>
        </div>
      </div>
    </Modal>
  );
}
