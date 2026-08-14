/** ブラウザ通知(Notification API)の薄いラッパー。未対応・未許可の環境では何もしない。 */

export function requestNotificationPermission(): void {
  if (typeof Notification === "undefined") return;
  if (Notification.permission === "default") {
    Notification.requestPermission().catch(() => {});
  }
}

export function showBrowserNotification(title: string, body: string): void {
  if (typeof Notification === "undefined") return;
  if (Notification.permission !== "granted") return;
  if (typeof document !== "undefined" && document.visibilityState === "visible") return;
  try {
    new Notification(title, { body });
  } catch {
    // 環境によっては例外を投げることがあるが、通知は本質機能ではないため無視する
  }
}
