/**
 * デスクトップ通知(フェーズ10、任意機能)。Web Push(タブ/ブラウザを閉じていても届く)ではなく、
 * ブラウザ組み込みのNotification APIのみを使う簡易版: タブを開いたまま他の作業をしている間に、
 * 既存のリアルタイム通知(`notificationStore`のSTOMP購読)をOS通知としても表示する。
 * ユーザーが明示的に有効化した場合のみ動作する(`enableDesktopNotifications`は許可ダイアログを伴うため
 * ユーザー操作起点で呼ぶこと、`ProfileModal`参照)。
 */
const STORAGE_KEY = "chatspace.desktopNotificationsEnabled";

export function isNotificationSupported(): boolean {
  return typeof window !== "undefined" && "Notification" in window;
}

export function isDesktopNotificationsEnabled(): boolean {
  return (
    isNotificationSupported() &&
    Notification.permission === "granted" &&
    localStorage.getItem(STORAGE_KEY) === "true"
  );
}

/** 許可ダイアログを表示する。ブラウザの制約上、必ずユーザー操作(クリック等)のハンドラ内から呼ぶこと。 */
export async function enableDesktopNotifications(): Promise<boolean> {
  if (!isNotificationSupported()) return false;
  const permission = await Notification.requestPermission();
  if (permission === "granted") {
    localStorage.setItem(STORAGE_KEY, "true");
    return true;
  }
  return false;
}

export function disableDesktopNotifications(): void {
  localStorage.setItem(STORAGE_KEY, "false");
}

/** タブが表示・フォーカスされている間は既存の通知パネル/バッジで十分なため表示しない。 */
export function showDesktopNotification(title: string, body: string): void {
  if (!isDesktopNotificationsEnabled()) return;
  if (document.visibilityState === "visible" && document.hasFocus()) return;
  new Notification(title, { body });
}
