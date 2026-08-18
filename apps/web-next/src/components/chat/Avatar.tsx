import { avatarColorFor, initialOf } from "../../lib/avatarColor";
import { attachmentUrl } from "../../api/client";

interface AvatarProps {
  userId: string;
  displayName: string;
  avatarUrl?: string | null;
  size?: number;
}

/** Slack同様の角丸正方形アバター。画像未設定時はuserId由来の色 + イニシャルで代替する。 */
export default function Avatar({ userId, displayName, avatarUrl, size = 36 }: AvatarProps) {
  if (avatarUrl) {
    return (
      <img
        src={attachmentUrl(avatarUrl)}
        alt={displayName}
        style={{ width: size, height: size }}
        className="rounded-md object-cover"
      />
    );
  }
  return (
    <div
      style={{ width: size, height: size, backgroundColor: avatarColorFor(userId) }}
      className="flex items-center justify-center rounded-md text-sm font-semibold text-white"
    >
      {initialOf(displayName)}
    </div>
  );
}
