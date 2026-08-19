import { useId, type ReactNode } from "react";
import { useDialogA11y } from "../../lib/useDialogA11y";

interface ModalProps {
  title: string;
  onClose: () => void;
  children: ReactNode;
  widthClassName?: string;
  /** 閉じた際にフォーカスを戻す起動元要素(呼び出し元のボタンクリックハンドラの`e.currentTarget`等)。 */
  restoreFocusTo?: HTMLElement | null;
}

/**
 * S-08〜S-12・S-14共通のモーダル基盤(画面設計書§3、新規要件)。
 * `role="dialog"` `aria-modal="true"` `aria-labelledby`・Escapeでの閉じる操作・フォーカストラップ・
 * 閉じた際の起動元へのフォーカス復帰を提供する(詳細は`useDialogA11y`参照)。
 */
export default function Modal({
  title,
  onClose,
  children,
  widthClassName = "max-w-lg",
  restoreFocusTo,
}: ModalProps) {
  const titleId = useId();
  const dialogRef = useDialogA11y<HTMLDivElement>(true, onClose, restoreFocusTo);

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center bg-black/40 px-4 pt-8 sm:pt-24"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={`w-full ${widthClassName} rounded-lg bg-white shadow-xl outline-none`}
      >
        <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
          <h2 id={titleId} className="text-sm font-bold text-slate-800">
            {title}
          </h2>
          <button
            onClick={onClose}
            aria-label="閉じる"
            data-initial-focus-skip
            className="rounded px-2 py-1 text-sm text-slate-400 hover:bg-slate-100"
          >
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
