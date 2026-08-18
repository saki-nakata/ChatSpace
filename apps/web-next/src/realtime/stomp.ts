import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import { WEBSOCKET_ENDPOINT } from "./destinations";

const WS_ORIGIN = import.meta.env.VITE_WS_URL ?? "ws://localhost:8080";

/** サーバー側{@code RealtimeEvent}(type + payload)と対になるクライアント側の型(リアルタイム通信機能定義書§4.1)。 */
export interface RealtimeEvent<T = unknown> {
  type: string;
  payload: T;
}

let client: Client | null = null;

/**
 * STOMPクライアントをシングルトンで取得・接続する。Cookie(httpOnly JWT)はWebSocketハンドシェイクへ
 * 自動送信されるため、`Authorization`ヘッダは不要(計画書§8)。SockJSフォールバックは使わず、生WebSocketのみ。
 */
export function getStompClient(): Client {
  if (!client) {
    client = new Client({
      brokerURL: `${WS_ORIGIN}${WEBSOCKET_ENDPOINT}`,
      reconnectDelay: 5000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
    });
    client.activate();
  }
  return client;
}

export function disconnectStomp(): void {
  client?.deactivate();
  client = null;
}

/** `/app/**`宛のSEND(現状はタイピングイベントのみ)。未接続時は静かに無視する(ベストエフォート)。 */
export function sendToApp(destination: string, body = ""): void {
  const c = getStompClient();
  if (!c.connected) return;
  c.publish({ destination, body });
}

/**
 * JSONペイロードの{@link RealtimeEvent}を購読する共通ヘルパー。戻り値は購読解除用の関数。
 *
 * <p>{@code Client.activate()}呼び出し直後はまだWebSocketハンドシェイクが完了しておらず、
 * この状態で{@code Client.subscribe()}を呼ぶと`There is no underlying STOMP connection`で
 * 例外になる(実機ブラウザ確認で発見した競合状態)。接続済みなら即座に、未接続なら
 * {@code onConnect}(再接続時も含めて毎回発火する)を待ってから購読する設計にする。
 */
export function subscribeJson<T = unknown>(
  destination: string,
  onMessage: (event: RealtimeEvent<T>) => void,
): () => void {
  const c = getStompClient();
  let subscription: StompSubscription | null = null;
  let unsubscribed = false;

  const doSubscribe = () => {
    if (unsubscribed) return;
    subscription?.unsubscribe();
    subscription = c.subscribe(destination, (message: IMessage) => {
      const event = JSON.parse(message.body) as RealtimeEvent<T>;
      onMessage(event);
    });
  };

  if (c.connected) {
    doSubscribe();
  } else {
    const previousOnConnect = c.onConnect;
    c.onConnect = (frame) => {
      previousOnConnect?.(frame);
      doSubscribe();
    };
  }

  return () => {
    unsubscribed = true;
    subscription?.unsubscribe();
  };
}
