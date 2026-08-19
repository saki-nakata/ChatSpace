import { useEffect, useRef } from "react";

const FOCUSABLE_SELECTOR = 'input, textarea, select, button, [href], [tabindex]:not([tabindex="-1"])';
/**
 * 初期フォーカス候補の選定にのみ使うセレクタ。Tabキーでのフォーカストラップ対象(`FOCUSABLE_SELECTOR`)には
 * 含めたいが、DOM順で先頭に来がちな閉じるボタン(`Modal`が`data-initial-focus-skip`を付与)は
 * 初期フォーカスの既定ターゲットとしては不適切なため除外する。`FOCUSABLE_SELECTOR`はカンマ区切りの
 * セレクタリストのため、`:not()`を素朴に末尾連結すると最後の枝にしか掛からない(実機確認で発見した
 * バグ)。`:is()`でリスト全体を1つのセレクタとしてまとめてから`:not()`を掛ける。
 */
const INITIAL_FOCUS_CANDIDATE_SELECTOR = `:is(${FOCUSABLE_SELECTOR}):not([data-initial-focus-skip])`;

/**
 * `role="dialog"`要素にEscapeでの閉じる操作・フォーカストラップ・開閉時のフォーカス管理を付与する
 * (画面設計書§3「モーダルのフォーカストラップ」「モーダルの初期フォーカス・フォーカス復帰」、新規要件)。
 * S-08〜S-12・S-14の各モーダルとS-13通知パネルで共通利用する。
 *
 * @param active 現在ダイアログが開いているか。`false`の間はリスナーを一切登録しない
 *   (常時マウントしたまま`display:none`で隠す通知パネル(S-13、スクロール位置保持のため)にも対応するため)。
 * @param restoreFocusTo 閉じた際にフォーカスを戻す起動元要素。呼び出し元がクリックイベントの
 *   `e.currentTarget`等から明示的に渡すこと。ReactのautoFocusはこのフックのuseEffectより先(コミット時)に
 *   実行され、その時点で`document.activeElement`は既にダイアログ内の要素に変わってしまっているため、
 *   フック内部で`document.activeElement`を自己キャプチャする方式では起動元を正しく記録できない
 *   (レビュー: 実機確認で発見。Escape後にフォーカスが起動元へ戻らずbodyに落ちる不具合の直接の原因)。
 */
export function useDialogA11y<T extends HTMLElement>(
  active: boolean,
  onClose: () => void,
  restoreFocusTo?: HTMLElement | null,
) {
  const containerRef = useRef<T>(null);
  const previouslyFocusedRef = useRef<HTMLElement | null>(null);
  // 呼び出し元(TopBar等)が`onClose`をインラインの矢印関数で渡すと親の再描画のたびに参照が変わるため、
  // 素直に依存配列へ入れると「開いている間ずっと同じダイアログセッション」を扱う下のeffectが親の
  // 無関係な再描画のたびに再実行され、直前フォーカス要素の記録がモーダル自身の要素で上書きされてしまう
  // (レビュー: 実機確認で発見。Escape後にフォーカスが起動元ボタンへ戻らずbodyに落ちる不具合の原因だった)。
  // refに退避して読むことで、このeffect自体は`active`の真偽が変化した時だけ実行されるようにする。
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!active) return;
    const container = containerRef.current;
    previouslyFocusedRef.current = restoreFocusTo ?? (document.activeElement as HTMLElement | null);
    // 初期フォーカス先の決定優先順位:
    // 1. `[data-initial-focus]`が明示されていればそれを常に最優先する(各モーダルのautoFocus指定要素に
    //    併記させる想定)。DOM順や`document.activeElement`の状態に一切依存しないため、StrictMode開発時の
    //    effect二重実行(setup→cleanup→setup)が挟まっても常に同じ要素へ収束する。
    // 2. 上記が無い場合のみ、ReactのautoFocus(このuseEffectより先、コミット時に実行される)が既に
    //    当てている`document.activeElement`を尊重する。ただしこれはStrictMode下では信頼できないことが
    //    実機確認で判明した(cleanupのフォーカス復帰でactiveElementが起動元要素に書き換わるため、続く
    //    2回目のeffect実行時点では既に無効な判定になる)。そのため`data-initial-focus`を持つ入力には
    //    必ず1.のマーカーも付与すること。
    // 3. どちらも無ければ、閉じるボタン等(`data-initial-focus-skip`)を除いた先頭のフォーカス可能要素。
    const activeIsCandidate =
      document.activeElement instanceof HTMLElement &&
      container?.contains(document.activeElement) &&
      document.activeElement.matches(INITIAL_FOCUS_CANDIDATE_SELECTOR);
    const initialFocusTarget =
      container?.querySelector<HTMLElement>("[data-initial-focus]") ??
      (activeIsCandidate ? (document.activeElement as HTMLElement) : null) ??
      container?.querySelector<HTMLElement>(INITIAL_FOCUS_CANDIDATE_SELECTOR);
    (initialFocusTarget ?? container)?.focus();

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        onCloseRef.current();
        return;
      }
      if (e.key !== "Tab" || !container) return;
      const focusables = Array.from(
        container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR),
      ).filter((el) => !el.hasAttribute("disabled"));
      if (focusables.length === 0) return;
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      previouslyFocusedRef.current?.focus();
    };
    // restoreFocusToはactiveがtrueになった瞬間の値のみを使う(onCloseと同じ理由でrefパターンにしても
    // よいが、呼び出し元は毎回同じref.currentを渡す設計のため実質的に安定しており、深追いの必要はない)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active]);

  return containerRef;
}
