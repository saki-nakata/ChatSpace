import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * {@code subscribeJson}の再接続時再購読の回帰テスト(実機デプロイ確認で発見したバグ)。
 *
 * <p>呼び出し時点で既にSTOMP接続済みだった場合、旧実装は{@code onConnect}フックを一切設定しないため、
 * その後切断→自動再接続(`reconnectDelay`)が起きても再購読されず、以後そのチャンネルの新着イベントが
 * 二度と届かなくなっていた。{@code @stomp/stompjs}の{@code Client}をモックし、
 * 「接続済み状態で購読開始→再接続」というシナリオで{@code subscribe}が2回目も呼ばれることを検証する。
 */

interface MockClientInstance {
  connected: boolean;
  onConnect: ((frame: unknown) => void) | undefined;
  subscribe: ReturnType<typeof vi.fn>;
  activate: ReturnType<typeof vi.fn>;
}

let mockClientInstance: MockClientInstance;

vi.mock("@stomp/stompjs", () => {
  return {
    Client: vi.fn().mockImplementation(() => {
      mockClientInstance = {
        connected: false,
        onConnect: undefined,
        subscribe: vi.fn().mockReturnValue({ unsubscribe: vi.fn() }),
        activate: vi.fn(),
      };
      return mockClientInstance;
    }),
  };
});

describe("subscribeJson", () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it("接続済み状態で購読開始しても、再接続時に再購読される(回帰テスト)", async () => {
    const { getStompClient, subscribeJson } = await import("./stomp");
    getStompClient();
    mockClientInstance.connected = true; // 呼び出し時点で既に接続済みの状態を模擬

    subscribeJson("/topic/test", vi.fn());
    expect(mockClientInstance.subscribe).toHaveBeenCalledTimes(1);

    // サーバー切断→stompjsのreconnectDelayによる自動再接続をシミュレート
    mockClientInstance.onConnect?.({});

    expect(mockClientInstance.subscribe).toHaveBeenCalledTimes(2);
  });

  it("未接続状態で購読開始した場合、接続確立時に購読される", async () => {
    const { getStompClient, subscribeJson } = await import("./stomp");
    getStompClient();

    subscribeJson("/topic/test", vi.fn());
    expect(mockClientInstance.subscribe).not.toHaveBeenCalled();

    mockClientInstance.onConnect?.({});
    expect(mockClientInstance.subscribe).toHaveBeenCalledTimes(1);
  });

  it("購読解除後は再接続してもsubscribeを呼ばない", async () => {
    const { getStompClient, subscribeJson } = await import("./stomp");
    getStompClient();
    mockClientInstance.connected = true;

    const unsubscribe = subscribeJson("/topic/test", vi.fn());
    expect(mockClientInstance.subscribe).toHaveBeenCalledTimes(1);

    unsubscribe();
    mockClientInstance.onConnect?.({});

    expect(mockClientInstance.subscribe).toHaveBeenCalledTimes(1);
  });

  it("複数購読のうち1つだけ解除しても、残りは再接続時に再購読される(積み上がりリークが無いことの確認、レビュー指摘対応)", async () => {
    const { getStompClient, subscribeJson } = await import("./stomp");
    getStompClient();
    mockClientInstance.connected = true;

    // チャンネル切り替え等を模して2つ購読し、片方(古い方)だけ解除する
    const unsubscribeFirst = subscribeJson("/topic/channel-a", vi.fn());
    subscribeJson("/topic/channel-b", vi.fn());
    expect(mockClientInstance.subscribe).toHaveBeenCalledTimes(2);

    unsubscribeFirst();
    mockClientInstance.subscribe.mockClear();

    // 再接続時、解除済みのchannel-aは再購読されず、channel-bのみ再購読されること
    mockClientInstance.onConnect?.({});

    expect(mockClientInstance.subscribe).toHaveBeenCalledTimes(1);
    expect(mockClientInstance.subscribe).toHaveBeenCalledWith("/topic/channel-b", expect.any(Function));
  });
});
