/**
 * デスクトップ通知(フェーズ10、任意機能)。Web Push(タブ/ブラウザを閉じていても届く)ではなく、
 * ブラウザ組み込みのNotification APIのみを使う簡易版: タブを開いたまま他の作業をしている間に、
 * 既存のリアルタイム通知(`notificationStore`のSTOMP購読)をOS通知としても表示する。
 * ユーザーが明示的に有効化した場合のみ動作する(`enableDesktopNotifications`は許可ダイアログを伴うため
 * ユーザー操作起点で呼ぶこと、`ProfileModal`参照)。
 */
const STORAGE_KEY_PREFIX = "chatspace.desktopNotificationsEnabled";

/** ユーザーIDごとにキーを分ける(共用ブラウザでログアウト→別ユーザーでログインした際に設定が引き継がれないように、D-3)。 */
function storageKey(userId: string): string {
  return `${STORAGE_KEY_PREFIX}:${userId}`;
}

export function isNotificationSupported(): boolean {
  return typeof window !== "undefined" && "Notification" in window;
}

export function isDesktopNotificationsEnabled(userId: string): boolean {
  return (
    isNotificationSupported() &&
    Notification.permission === "granted" &&
    localStorage.getItem(storageKey(userId)) === "true"
  );
}

/** 許可ダイアログを表示する。ブラウザの制約上、必ずユーザー操作(クリック等)のハンドラ内から呼ぶこと。 */
export async function enableDesktopNotifications(userId: string): Promise<boolean> {
  if (!isNotificationSupported()) return false;
  const permission = await Notification.requestPermission();
  if (permission === "granted") {
    localStorage.setItem(storageKey(userId), "true");
    return true;
  }
  return false;
}

export function disableDesktopNotifications(userId: string): void {
  localStorage.setItem(storageKey(userId), "false");
}

/** タブが表示・フォーカスされている間は既存の通知パネル/バッジで十分なため表示しない。 */
export function showDesktopNotification(userId: string, title: string, body: string): void {
  if (!isDesktopNotificationsEnabled(userId)) return;
  if (document.visibilityState === "visible" && document.hasFocus()) return;
  new Notification(title, { body });
}
