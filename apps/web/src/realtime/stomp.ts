import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import { WEBSOCKET_ENDPOINT } from "./destinations";

/**
 * `VITE_WS_URL`未設定時のフォールバック。Renderへの同梱配信(フェーズ14)ではフロントとAPIが同一オリジンになるため、
 * 現在ページのオリジンから導出する(ビルド時に固定URLを焼き込まない)。ローカル開発では`apps/web/.env`が
 * `VITE_WS_URL`を明示設定しているため、この関数は使われない。
 */
function defaultWsOrigin(): string {
  if (typeof window === "undefined") return "ws://localhost:8080";
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${window.location.host}`;
}

const WS_ORIGIN = import.meta.env.VITE_WS_URL ?? defaultWsOrigin();

/** サーバー側{@code RealtimeEvent}(type + payload)と対になるクライアント側の型(リアルタイム通信機能定義書§4.1)。 */
export interface RealtimeEvent<T = unknown> {
  type: string;
  payload: T;
}

let client: Client | null = null;

/**
 * 再接続のたびに再実行する購読処理の集合。{@code subscribeJson}が追加し、返り値の購読解除関数が
 * 確実に取り除く(レビュー指摘対応: 以前は{@code onConnect}への追加のみでチェーンから取り除く手段が無く、
 * チャンネル/DM切り替えのたびにクロージャが積み上がり続けるリークになっていた)。
 */
const resubscribeCallbacks = new Set<() => void>();

/**
 * STOMPクライアントをシングルトンで取得・接続する。Cookie(httpOnly JWT)はWebSocketハンドシェイクへ
 * 自動送信されるため、`Authorization`ヘッダは不要(計画書§8)。SockJSフォールバックは使わず、生WebSocketのみ。
 *
 * <p>{@code onConnect}はここで一度だけ設定し、{@link #resubscribeCallbacks}を反復して各購読を
 * 再実行する。STOMPは接続ごとに独立したセッションのため、切断→自動再接続(`reconnectDelay`)が起きると
 * サーバー側の購読状態は失われるため、初回接続時・再接続時のどちらでも同じ経路で再購読させる。
 */
export function getStompClient(): Client {
  if (!client) {
    client = new Client({
      brokerURL: `${WS_ORIGIN}${WEBSOCKET_ENDPOINT}`,
      reconnectDelay: 5000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
    });
    client.onConnect = () => {
      resubscribeCallbacks.forEach((resubscribe) => resubscribe());
    };
    client.activate();
  }
  return client;
}

export function disconnectStomp(): void {
  client?.deactivate();
  client = null;
  // 次回getStompClient()は新しいClientインスタンスを作るため、古いクライアントを参照したままの
  // クロージャが残っているとそれを誤って呼んでしまう(レビュー指摘対応)
  resubscribeCallbacks.clear();
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
 * 例外になる(実機ブラウザ確認で発見した競合状態)。接続済みなら即座に購読するが、
 * **接続済み・未接続のどちらの場合でも{@link #resubscribeCallbacks}へ登録する**(実機デプロイ確認で
 * 発見したバグの修正)。STOMPは接続ごとに独立したセッションのため、切断→自動再接続
 * (`reconnectDelay`)が起きるとサーバー側の購読状態は失われる。呼び出し時点で既に接続済みだった
 * 場合には再購読の仕組みが無かった旧実装では、再接続後にサーバーへ再度SUBSCRIBEが送られず、
 * 以後そのチャンネルの新着イベントが二度と届かなくなっていた。返り値の購読解除関数は
 * {@link #resubscribeCallbacks}からも確実に取り除く(チャンネル/DM切り替えのたびに再購読処理が
 * 積み上がり続けるリークを防ぐ、レビュー指摘対応)。
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

  resubscribeCallbacks.add(doSubscribe);

  if (c.connected) {
    doSubscribe();
  }

  return () => {
    unsubscribed = true;
    resubscribeCallbacks.delete(doSubscribe);
    subscription?.unsubscribe();
  };
}
