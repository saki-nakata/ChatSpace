import { useEffect, useState } from "react";

/** Tailwindの`sm`ブレークポイント(640px)未満かどうか。 */
const MOBILE_QUERY = "(max-width: 639px)";

/**
 * 現在の画面幅がTailwindの`sm`ブレークポイント未満かを追跡する(フェーズ9-Fレビュー対応)。
 * サイドバードロワーの「640px以上にリサイズしたら開閉状態をリセットする」「モバイルの間だけ
 * 閉時に`inert`を付与する」判定に使う。
 */
export function useIsMobile(): boolean {
  const [isMobile, setIsMobile] = useState(() => window.matchMedia(MOBILE_QUERY).matches);

  useEffect(() => {
    const mql = window.matchMedia(MOBILE_QUERY);
    const handler = (e: MediaQueryListEvent) => setIsMobile(e.matches);
    mql.addEventListener("change", handler);
    return () => mql.removeEventListener("change", handler);
  }, []);

  return isMobile;
}
