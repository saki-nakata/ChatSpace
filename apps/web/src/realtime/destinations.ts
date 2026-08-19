/**
 * STOMP宛先名(リアルタイム通信機能定義書§4)。`apps/api`の`StompDestinations.java`を正とし、手動で同期する。
 *
 * <p>ドリフト防止のため、Vitestの契約テスト(`destinations.contract.test.ts`)が
 * `apps/api/build/generated/stomp-destinations.json`(`./gradlew exportStompDestinations`で生成)と
 * この定数群を突き合わせる(計画書§7、フェーズ8で用意したJSON書き出しパイプラインをフェーズ9で消費する)。
 */
export const WEBSOCKET_ENDPOINT = "/ws";
export const APP_PREFIX = "/app";
export const USER_EVENTS_SUBSCRIPTION = "/user/queue/events";

export function channelTopic(channelId: string): string {
  return `/topic/channels.${channelId}`;
}

export function dmTopic(dmId: string): string {
  return `/topic/dms.${dmId}`;
}

export function workspaceTopic(workspaceId: string): string {
  return `/topic/workspaces.${workspaceId}`;
}

export function workspacePresenceTopic(workspaceId: string): string {
  return `/topic/workspaces.${workspaceId}.presence`;
}

export function channelTypingSend(channelId: string): string {
  return `${APP_PREFIX}/channels.${channelId}.typing`;
}

export function dmTypingSend(dmId: string): string {
  return `${APP_PREFIX}/dms.${dmId}.typing`;
}

/** 契約テスト用に、テンプレート文字列({@code {channelId}}等のプレースホルダ付き)の形で一覧化したもの。 */
export const DESTINATION_TEMPLATES = {
  websocketEndpoint: WEBSOCKET_ENDPOINT,
  appPrefix: APP_PREFIX,
  userEventsSubscription: USER_EVENTS_SUBSCRIPTION,
  channelTopic: "/topic/channels.{channelId}",
  dmTopic: "/topic/dms.{dmId}",
  workspaceTopic: "/topic/workspaces.{workspaceId}",
  workspacePresenceTopic: "/topic/workspaces.{workspaceId}.presence",
  channelTypingSend: `${APP_PREFIX}/channels.{channelId}.typing`,
  dmTypingSend: `${APP_PREFIX}/dms.{dmId}.typing`,
} as const;
