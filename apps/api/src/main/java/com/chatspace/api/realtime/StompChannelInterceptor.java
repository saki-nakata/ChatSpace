package com.chatspace.api.realtime;

import com.chatspace.api.audit.AuditLogger;
import com.chatspace.api.channel.ChannelAuthorizationService;
import com.chatspace.api.dm.DmAuthorizationService;
import com.chatspace.api.workspace.WorkspaceAuthorizationService;
import java.security.Principal;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * SUBSCRIBE/SEND宛先のdefault-deny認可(リアルタイム通信機能定義書§7・§8)。
 *
 * <p>CONNECT/DISCONNECT/UNSUBSCRIBE/HEARTBEATは{@code permitAll()}として対象から明示的に除外する
 * (これらまで拒否すると接続自体が確立しなくなる典型的な誤りを避ける、§8)。
 */
@Component
public class StompChannelInterceptor implements ChannelInterceptor {

  private final ChannelAuthorizationService channelAuthorizationService;
  private final DmAuthorizationService dmAuthorizationService;
  private final WorkspaceAuthorizationService workspaceAuthorizationService;
  private final AuditLogger auditLogger;

  public StompChannelInterceptor(
      ChannelAuthorizationService channelAuthorizationService,
      DmAuthorizationService dmAuthorizationService,
      WorkspaceAuthorizationService workspaceAuthorizationService,
      AuditLogger auditLogger) {
    this.channelAuthorizationService = channelAuthorizationService;
    this.dmAuthorizationService = dmAuthorizationService;
    this.workspaceAuthorizationService = workspaceAuthorizationService;
    this.auditLogger = auditLogger;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() == null) {
      return message;
    }
    // 拒否は監査ログへ記録する(ログ運用設計書§2「STOMP SUBSCRIBE/SEND拒否」)。同一ユーザー・同一IPからの
    // 頻発は権限探索の兆候になるため、運用側で頻度を追えるようにしておく(§5)
    try {
      switch (accessor.getCommand()) {
        case SUBSCRIBE -> authorizeSubscribe(accessor);
        case SEND -> authorizeSend(accessor);
        default -> {
          // CONNECT/DISCONNECT/UNSUBSCRIBE/HEARTBEAT等はpermitAll(§8)
        }
      }
    } catch (RuntimeException ex) {
      auditLogger.stompDestinationDenied(
          principalUserIdOrNull(accessor),
          accessor.getCommand().name(),
          accessor.getDestination(),
          ex.getClass().getSimpleName());
      throw ex;
    }
    return message;
  }

  private void authorizeSubscribe(StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    Principal principal = accessor.getUser();

    if (StompDestinations.USER_EVENTS_SUBSCRIPTION.equals(destination)) {
      // /user/queue/eventsは認証済みユーザーであれば常時許可(catch-allのdenyより先に評価、§7.1)
      requireAuthenticated(principal);
      return;
    }

    requireAuthenticated(principal);
    UUID userId = UUID.fromString(principal.getName());

    var channelId = StompDestinations.extractChannelId(destination);
    if (channelId.isPresent()) {
      channelAuthorizationService.requireChannelMember(channelId.get(), userId, null);
      return;
    }
    var dmId = StompDestinations.extractDmId(destination);
    if (dmId.isPresent()) {
      dmAuthorizationService.requireDmAccess(dmId.get(), userId, null);
      return;
    }
    // presence宛先は通常のworkspace宛先パターンの部分集合になるため、必ず先に判定する
    var presenceWorkspaceId = StompDestinations.extractWorkspacePresenceId(destination);
    if (presenceWorkspaceId.isPresent()) {
      workspaceAuthorizationService.requireMember(presenceWorkspaceId.get(), userId);
      return;
    }
    var workspaceId = StompDestinations.extractWorkspaceId(destination);
    if (workspaceId.isPresent()) {
      workspaceAuthorizationService.requireMember(workspaceId.get(), userId);
      return;
    }

    throw new MessagingException("許可されていない宛先です: " + destination);
  }

  private void authorizeSend(StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    if (destination == null || !destination.startsWith(StompDestinations.APP_PREFIX + "/")) {
      throw new MessagingException("許可されていない宛先です: " + destination);
    }
    requireAuthenticated(accessor.getUser());
  }

  /** 監査ログ用。未認証なら{@code null}(ここで例外を投げるとログ出力が本来の拒否理由を上書きしてしまう)。 */
  private UUID principalUserIdOrNull(StompHeaderAccessor accessor) {
    Principal principal = accessor.getUser();
    if (principal == null) {
      return null;
    }
    try {
      return UUID.fromString(principal.getName());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private void requireAuthenticated(Principal principal) {
    if (principal == null) {
      throw new MessagingException("認証が必要です。");
    }
  }
}
