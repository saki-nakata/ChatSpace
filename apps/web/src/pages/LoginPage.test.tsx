import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/client";
import { useAuthStore } from "../store/authStore";
import LoginPage from "./LoginPage";

const navigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => navigate };
});
vi.mock("../api/resources", () => ({
  authApi: { me: vi.fn(), login: vi.fn(), signup: vi.fn(), logout: vi.fn() },
  userApi: { updateProfile: vi.fn() },
  notificationApi: {},
  workspaceApi: {},
}));
vi.mock("../realtime/stomp", () => ({
  getStompClient: vi.fn(),
  disconnectStomp: vi.fn(),
  subscribeJson: vi.fn(() => () => {}),
}));

function renderLoginPage() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  );
}

describe("LoginPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ user: null, status: "idle" });
  });

  it("入力した資格情報でログインし、成功したらトップへ遷移する", async () => {
    const user = userEvent.setup();
    const login = vi.fn().mockResolvedValue(undefined);
    useAuthStore.setState({ login });
    renderLoginPage();

    await user.type(screen.getByLabelText("ユーザーID"), "alice");
    await user.type(screen.getByLabelText("パスワード"), "password123");
    await user.click(screen.getByRole("button", { name: "ログイン" }));

    expect(login).toHaveBeenCalledWith("alice", "password123");
    // 履歴を積まない(戻るでログイン画面に戻れてしまうのを防ぐ)
    expect(navigate).toHaveBeenCalledWith("/", { replace: true });
  });

  /**
   * バックエンドはユーザーID列挙を防ぐため「不存在」と「パスワード不一致」を同一メッセージで返す。
   * フロントはそのメッセージをそのまま出し、独自に区別しないこと。
   */
  it("APIのエラーメッセージをそのまま表示する", async () => {
    const user = userEvent.setup();
    const login = vi
      .fn()
      .mockRejectedValue(new ApiError(400, "ユーザーIDまたはパスワードが正しくありません。"));
    useAuthStore.setState({ login });
    renderLoginPage();

    await user.type(screen.getByLabelText("ユーザーID"), "alice");
    await user.type(screen.getByLabelText("パスワード"), "wrong");
    await user.click(screen.getByRole("button", { name: "ログイン" }));

    expect(
      await screen.findByText("ユーザーIDまたはパスワードが正しくありません。"),
    ).toBeInTheDocument();
    expect(navigate).not.toHaveBeenCalled();
  });

  /** ApiError以外(ネットワーク断など)は内部情報を出さず定型文にする。 */
  it("API由来でない例外は定型のエラー文言にする", async () => {
    const user = userEvent.setup();
    const login = vi.fn().mockRejectedValue(new TypeError("Failed to fetch"));
    useAuthStore.setState({ login });
    renderLoginPage();

    await user.type(screen.getByLabelText("ユーザーID"), "alice");
    await user.type(screen.getByLabelText("パスワード"), "password123");
    await user.click(screen.getByRole("button", { name: "ログイン" }));

    expect(await screen.findByText("ログインに失敗しました。")).toBeInTheDocument();
    expect(screen.queryByText(/Failed to fetch/)).not.toBeInTheDocument();
  });

  /** 二重送信すると429(レート制限)を自分で引き起こしうるため、送信中は押せないこと。 */
  it("送信中はボタンを無効化する", async () => {
    const user = userEvent.setup();
    let resolveLogin!: () => void;
    const login = vi.fn().mockReturnValue(
      new Promise<void>((resolve) => {
        resolveLogin = resolve;
      }),
    );
    useAuthStore.setState({ login });
    renderLoginPage();

    await user.type(screen.getByLabelText("ユーザーID"), "alice");
    await user.type(screen.getByLabelText("パスワード"), "password123");
    await user.click(screen.getByRole("button", { name: "ログイン" }));

    const submitting = screen.getByRole("button", { name: "ログイン中..." });
    expect(submitting).toBeDisabled();

    resolveLogin();
    expect(await screen.findByRole("button", { name: "ログイン" })).toBeEnabled();
  });

  it("再送信時に前回のエラー表示をクリアする", async () => {
    const user = userEvent.setup();
    const login = vi
      .fn()
      .mockRejectedValueOnce(new ApiError(400, "ユーザーIDまたはパスワードが正しくありません。"))
      .mockResolvedValueOnce(undefined);
    useAuthStore.setState({ login });
    renderLoginPage();

    await user.type(screen.getByLabelText("ユーザーID"), "alice");
    await user.type(screen.getByLabelText("パスワード"), "wrong");
    await user.click(screen.getByRole("button", { name: "ログイン" }));
    expect(
      await screen.findByText("ユーザーIDまたはパスワードが正しくありません。"),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "ログイン" }));

    expect(
      screen.queryByText("ユーザーIDまたはパスワードが正しくありません。"),
    ).not.toBeInTheDocument();
  });

  it("初期フォーカスがユーザーID入力に当たる", () => {
    renderLoginPage();

    expect(screen.getByLabelText("ユーザーID")).toHaveFocus();
  });
});
