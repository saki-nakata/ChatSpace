import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useRef, useState } from "react";
import { describe, expect, it, vi } from "vitest";
import Modal from "./Modal";

/**
 * S-08〜S-12・S-14共通のモーダル基盤のアクセシビリティ検証
 * (画面設計書§3「モーダルのフォーカストラップ」「初期フォーカス・フォーカス復帰」)。
 *
 * `useDialogA11y`はレビュー・実機確認で複数回バグが見つかっている箇所
 * (初期フォーカス候補セレクタの`:not()`の掛かり方、`onClose`の参照変化による
 * effect再実行、フォーカス復帰先の自己キャプチャ)。回帰を自動で捕まえられるようにする。
 */
describe("Modal", () => {
  it("role=dialog・aria-modal・aria-labelledbyを持つ", () => {
    render(
      <Modal title="チャンネルを作成" onClose={vi.fn()}>
        <input aria-label="チャンネル名" />
      </Modal>,
    );

    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveAttribute("aria-modal", "true");
    // タイトルがアクセシブル名として紐づいていること(スクリーンリーダーでの識別に必要)
    expect(dialog).toHaveAccessibleName("チャンネルを作成");
  });

  it("Escapeキーで閉じる", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Modal title="テスト" onClose={onClose}>
        <input aria-label="入力" />
      </Modal>,
    );

    await user.keyboard("{Escape}");

    expect(onClose).toHaveBeenCalledOnce();
  });

  it("背景のオーバーレイをクリックすると閉じる", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const { container } = render(
      <Modal title="テスト" onClose={onClose}>
        <input aria-label="入力" />
      </Modal>,
    );

    await user.click(container.firstElementChild as HTMLElement);

    expect(onClose).toHaveBeenCalledOnce();
  });

  it("ダイアログ内部のクリックでは閉じない", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Modal title="テスト" onClose={onClose}>
        <input aria-label="入力" />
      </Modal>,
    );

    await user.click(screen.getByRole("dialog"));

    expect(onClose).not.toHaveBeenCalled();
  });

  /**
   * 初期フォーカスは閉じるボタン(`data-initial-focus-skip`)を飛ばして最初の入力へ当てる。
   * DOM順では閉じるボタンの方が先に来るため、除外が効いていないとここで落ちる。
   */
  it("初期フォーカスが閉じるボタンではなく最初の入力に当たる", () => {
    render(
      <Modal title="テスト" onClose={vi.fn()}>
        <input aria-label="チャンネル名" />
      </Modal>,
    );

    expect(screen.getByLabelText("チャンネル名")).toHaveFocus();
  });

  it("data-initial-focusが指定されていればそれを最優先する", () => {
    render(
      <Modal title="テスト" onClose={vi.fn()}>
        <input aria-label="先に現れる入力" />
        <input aria-label="明示指定の入力" data-initial-focus />
      </Modal>,
    );

    expect(screen.getByLabelText("明示指定の入力")).toHaveFocus();
  });

  describe("フォーカストラップ", () => {
    it("最後の要素でTabを押すと先頭へ戻る", async () => {
      const user = userEvent.setup();
      render(
        <Modal title="テスト" onClose={vi.fn()}>
          <input aria-label="入力" />
          <button>送信</button>
        </Modal>,
      );

      // 閉じるボタン → 入力 → 送信 の順。最後(送信)からTabで先頭(閉じる)へ戻る
      screen.getByText("送信").focus();
      await user.tab();

      expect(screen.getByLabelText("閉じる")).toHaveFocus();
    });

    it("先頭の要素でShift+Tabを押すと最後へ回り込む", async () => {
      const user = userEvent.setup();
      render(
        <Modal title="テスト" onClose={vi.fn()}>
          <input aria-label="入力" />
          <button>送信</button>
        </Modal>,
      );

      screen.getByLabelText("閉じる").focus();
      await user.tab({ shift: true });

      expect(screen.getByText("送信")).toHaveFocus();
    });

    /** disabled な要素はフォーカスできないため、トラップの端の計算から除外する必要がある。 */
    it("disabledな要素は循環の対象に含めない", async () => {
      const user = userEvent.setup();
      render(
        <Modal title="テスト" onClose={vi.fn()}>
          <input aria-label="入力" />
          <button>送信</button>
          <button disabled>無効なボタン</button>
        </Modal>,
      );

      screen.getByText("送信").focus();
      await user.tab();

      expect(screen.getByLabelText("閉じる")).toHaveFocus();
    });
  });

  /**
   * 閉じた後に起動元ボタンへフォーカスが戻ること。
   * キーボード利用者はフォーカスがbodyに落ちると、そこまでの位置を見失う。
   * 過去に「Escape後にフォーカスが起動元へ戻らずbodyに落ちる」不具合が2度発生している箇所。
   */
  it("閉じたときに起動元のボタンへフォーカスを戻す", async () => {
    const user = userEvent.setup();

    function Harness() {
      const triggerRef = useRef<HTMLButtonElement>(null);
      const [open, setOpen] = useState(false);
      return (
        <>
          <button ref={triggerRef} onClick={() => setOpen(true)}>
            開く
          </button>
          {open && (
            <Modal
              title="テスト"
              onClose={() => setOpen(false)}
              restoreFocusTo={triggerRef.current}
            >
              <input aria-label="入力" />
            </Modal>
          )}
        </>
      );
    }

    render(<Harness />);
    await user.click(screen.getByText("開く"));
    expect(screen.getByLabelText("入力")).toHaveFocus();

    await user.keyboard("{Escape}");

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByText("開く")).toHaveFocus();
  });

  /**
   * 親の再描画で`onClose`の参照が変わってもフォーカス管理が壊れないこと(回帰テスト)。
   * `onClose`をそのまま依存配列へ入れていた頃は、親の無関係な再描画のたびに
   * effectが再実行され、復帰先の記録がモーダル自身の要素で上書きされていた。
   */
  it("親が再描画されてもフォーカス復帰先を見失わない", async () => {
    const user = userEvent.setup();

    function Harness() {
      const triggerRef = useRef<HTMLButtonElement>(null);
      const [open, setOpen] = useState(false);
      const [tick, setTick] = useState(0);
      return (
        <>
          <button ref={triggerRef} onClick={() => setOpen(true)}>
            開く
          </button>
          <button onClick={() => setTick((t) => t + 1)}>再描画 {tick}</button>
          {open && (
            // onCloseを毎回新しい関数として渡す(実際の呼び出し元と同じ書き方)
            <Modal
              title="テスト"
              onClose={() => setOpen(false)}
              restoreFocusTo={triggerRef.current}
            >
              <input aria-label="入力" />
            </Modal>
          )}
        </>
      );
    }

    render(<Harness />);
    await user.click(screen.getByText("開く"));
    // モーダルを開いたまま親を再描画させ、onCloseの参照を変化させる
    await user.click(screen.getByText(/再描画/));
    await user.keyboard("{Escape}");

    expect(screen.getByText("開く")).toHaveFocus();
  });
});
