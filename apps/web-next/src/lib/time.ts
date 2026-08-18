/** メッセージ一覧表示用の時刻フォーマット(Slackに倣いHH:mm表示)。 */
export function formatTime(iso: string): string {
  const date = new Date(iso);
  return date.toLocaleTimeString("ja-JP", { hour: "2-digit", minute: "2-digit" });
}

export function formatDateLabel(iso: string): string {
  const date = new Date(iso);
  return date.toLocaleDateString("ja-JP", { year: "numeric", month: "long", day: "numeric" });
}

/** 検索結果・通知パネル用の相対時刻表示(画面設計書 S-12/S-13)。 */
export function formatRelativeTime(iso: string): string {
  const diffSec = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (diffSec < 60) return "たった今";
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}分前`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}時間前`;
  const diffDay = Math.floor(diffHour / 24);
  if (diffDay < 7) return `${diffDay}日前`;
  return formatDateLabel(iso);
}

/** 同一著者・5分以内の連続投稿はSlack同様にまとめて表示する(アバター・名前を省略)。 */
export function isSameGroup(prev: { authorId: string; createdAt: string } | undefined, current: { authorId: string; createdAt: string }): boolean {
  if (!prev) return false;
  if (prev.authorId !== current.authorId) return false;
  const gapMs = new Date(current.createdAt).getTime() - new Date(prev.createdAt).getTime();
  return gapMs < 5 * 60 * 1000;
}

/** ローカルタイムゾーンの暦日が同じかどうか(日付区切り線の判定用、24時間差ではなく暦日境界で判定する)。 */
export function isSameCalendarDay(a: string, b: string): boolean {
  const da = new Date(a);
  const db = new Date(b);
  return (
    da.getFullYear() === db.getFullYear() &&
    da.getMonth() === db.getMonth() &&
    da.getDate() === db.getDate()
  );
}
