package com.chatspace.api.realtime;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP宛先名の定数・組み立て・抽出を一元管理する(リアルタイム通信機能定義書§4)。
 *
 * <p>フロントエンド{@code apps/web-next/src/realtime/destinations.ts}へ手動同期し、フェーズ8でドリフト検出の契約テストを追加する予定
 * (計画書§7)。
 */
public final class StompDestinations {

  public static final String WEBSOCKET_ENDPOINT = "/ws";
  public static final String APP_PREFIX = "/app";

  /** {@code convertAndSend}時に渡す実際の宛先("/user"プレフィックスは{@code convertAndSendToUser}が別途付与する)。 */
  public static final String USER_EVENTS_DESTINATION = "/queue/events";

  /** クライアントがSUBSCRIBEフレームで指定する宛先(Spring内部で {@code /user/{userId}/queue/events} に解決される)。 */
  public static final String USER_EVENTS_SUBSCRIPTION = "/user/queue/events";

  private static final Pattern CHANNEL_TOPIC =
      Pattern.compile("^/topic/channels\\.([0-9a-fA-F-]{36})$");
  private static final Pattern DM_TOPIC = Pattern.compile("^/topic/dms\\.([0-9a-fA-F-]{36})$");
  private static final Pattern WORKSPACE_PRESENCE_TOPIC =
      Pattern.compile("^/topic/workspaces\\.([0-9a-fA-F-]{36})\\.presence$");
  private static final Pattern WORKSPACE_TOPIC =
      Pattern.compile("^/topic/workspaces\\.([0-9a-fA-F-]{36})$");

  private StompDestinations() {}

  public static String channelTopic(UUID channelId) {
    return "/topic/channels." + channelId;
  }

  public static String dmTopic(UUID dmId) {
    return "/topic/dms." + dmId;
  }

  public static String workspaceTopic(UUID workspaceId) {
    return "/topic/workspaces." + workspaceId;
  }

  public static String workspacePresenceTopic(UUID workspaceId) {
    return "/topic/workspaces." + workspaceId + ".presence";
  }

  public static Optional<UUID> extractChannelId(String destination) {
    return extract(CHANNEL_TOPIC, destination);
  }

  public static Optional<UUID> extractDmId(String destination) {
    return extract(DM_TOPIC, destination);
  }

  public static Optional<UUID> extractWorkspacePresenceId(String destination) {
    return extract(WORKSPACE_PRESENCE_TOPIC, destination);
  }

  public static Optional<UUID> extractWorkspaceId(String destination) {
    return extract(WORKSPACE_TOPIC, destination);
  }

  private static Optional<UUID> extract(Pattern pattern, String destination) {
    if (destination == null) {
      return Optional.empty();
    }
    Matcher matcher = pattern.matcher(destination);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(matcher.group(1)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
