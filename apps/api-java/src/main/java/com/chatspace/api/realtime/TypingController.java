package com.chatspace.api.realtime;

import com.chatspace.api.channel.ChannelAuthorizationService;
import com.chatspace.api.dm.DmAuthorizationService;
import java.security.Principal;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * タイピングインジケーター(リアルタイム通信機能定義書§12)。クライアントは{@code /app/channels.{channelId}.typing} (または{@code
 * /app/dms.{dmId}.typing})へSENDし、対象チャンネル/DMのメンバーシップ検証後に{@code TYPING_UPDATE}を
 * トピックへブロードキャストする。永続化はしない。
 *
 * <p>{@code channelId}/{@code dmId}は{@code @DestinationVariable}のUUID自動変換により形式検証される(不正な
 * UUID文字列は変換エラーとしてメッセージが処理されない、§9のペイロードバリデーション)。
 *
 * <p>「送信者自身には配信しない」(§12)という要件について: Spring標準シンプルブローカーには送信者除外配信の
 * 標準APIが無いため、送信者自身のuserIdを含めて全購読者へ配信し、クライアント側でuserId比較により自分自身の
 * イベントを無視する設計とした(揮発性のUXイベントであり認可上のリスクは無いための簡略化)。
 */
@Controller
public class TypingController {

  private final ChannelAuthorizationService channelAuthorizationService;
  private final DmAuthorizationService dmAuthorizationService;
  private final SimpMessagingTemplate messagingTemplate;

  public TypingController(
      ChannelAuthorizationService channelAuthorizationService,
      DmAuthorizationService dmAuthorizationService,
      SimpMessagingTemplate messagingTemplate) {
    this.channelAuthorizationService = channelAuthorizationService;
    this.dmAuthorizationService = dmAuthorizationService;
    this.messagingTemplate = messagingTemplate;
  }

  @MessageMapping(StompDestinations.CHANNEL_TYPING_MAPPING)
  public void channelTyping(@DestinationVariable UUID channelId, Principal principal) {
    UUID userId = UUID.fromString(principal.getName());
    channelAuthorizationService.requireChannelMember(channelId, userId, null);
    messagingTemplate.convertAndSend(
        StompDestinations.channelTopic(channelId),
        new RealtimeEvent("TYPING_UPDATE", new TypingPayload(userId, channelId, null)));
  }

  @MessageMapping(StompDestinations.DM_TYPING_MAPPING)
  public void dmTyping(@DestinationVariable UUID dmId, Principal principal) {
    UUID userId = UUID.fromString(principal.getName());
    dmAuthorizationService.requireDmAccess(dmId, userId, null);
    messagingTemplate.convertAndSend(
        StompDestinations.dmTopic(dmId),
        new RealtimeEvent("TYPING_UPDATE", new TypingPayload(userId, null, dmId)));
  }

  private record TypingPayload(UUID userId, UUID channelId, UUID dmId) {}
}
