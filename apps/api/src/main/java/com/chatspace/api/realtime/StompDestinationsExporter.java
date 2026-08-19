package com.chatspace.api.realtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link StompDestinations}の宛先名をJSONとして書き出す(計画書§7、フェーズ8)。
 *
 * <p>{@code apps/web-next}(フェーズ9で新設)のVitest契約テストがこのJSONと{@code
 * src/realtime/destinations.ts}の定数を突き合わせ、手動同期のドリフトを検出する。Spring コンテキストを起動する必要がないため、Gradleの{@code
 * JavaExec}タスクから直接{@code main}を実行する設計とした (統合テストのようにTestcontainers Postgresを起動する必要がなく高速)。
 */
public final class StompDestinationsExporter {

  private StompDestinationsExporter() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      throw new IllegalArgumentException("出力先ファイルパスを1つ指定してください。");
    }
    Map<String, String> destinations = new LinkedHashMap<>();
    destinations.put("websocketEndpoint", StompDestinations.WEBSOCKET_ENDPOINT);
    destinations.put("appPrefix", StompDestinations.APP_PREFIX);
    destinations.put("userEventsSubscription", StompDestinations.USER_EVENTS_SUBSCRIPTION);
    destinations.put("channelTopic", "/topic/channels.{channelId}");
    destinations.put("dmTopic", "/topic/dms.{dmId}");
    destinations.put("workspaceTopic", "/topic/workspaces.{workspaceId}");
    destinations.put("workspacePresenceTopic", "/topic/workspaces.{workspaceId}.presence");
    destinations.put(
        "channelTypingSend",
        StompDestinations.APP_PREFIX + StompDestinations.CHANNEL_TYPING_MAPPING);
    destinations.put(
        "dmTypingSend", StompDestinations.APP_PREFIX + StompDestinations.DM_TYPING_MAPPING);

    Path output = Path.of(args[0]);
    Files.createDirectories(output.toAbsolutePath().getParent());
    byte[] json =
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(destinations);
    Files.write(output, json);
  }
}
