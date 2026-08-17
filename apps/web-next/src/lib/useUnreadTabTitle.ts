import { useEffect } from "react";

const BASE_TITLE = "ChatSpace";

/**
 * タブタイトルに未読件数を反映する(フェーズ10、画面設計書「上記の要件に加えて」節)。
 * サイドバーの未読バッジと同じ値(現在のワークスペース内チャンネル・DMの`unreadCount`合計)を使う
 * (通知ベルの`unreadCount`はメンション・招待等の別概念のため、意図的に混ぜない)。
 */
export function useUnreadTabTitle(totalUnread: number): void {
  useEffect(() => {
    document.title = totalUnread > 0 ? `(${totalUnread}) ${BASE_TITLE}` : BASE_TITLE;
    return () => {
      document.title = BASE_TITLE;
    };
  }, [totalUnread]);
}
