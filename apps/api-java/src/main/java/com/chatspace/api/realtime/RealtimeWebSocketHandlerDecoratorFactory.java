package com.chatspace.api.realtime;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

/**
 * {@code WebSocketMessageBrokerConfigurer#configureWebSocketTransport}経由で登録し、生の{@link
 * WebSocketSession}の 接続確立・切断を{@link SessionRegistry}・{@link
 * PresenceService}へ反映する(リアルタイム通信機能定義書§10.2)。
 */
@Component
public class RealtimeWebSocketHandlerDecoratorFactory implements WebSocketHandlerDecoratorFactory {

  private final SessionRegistry sessionRegistry;
  private final PresenceService presenceService;

  public RealtimeWebSocketHandlerDecoratorFactory(
      SessionRegistry sessionRegistry, PresenceService presenceService) {
    this.sessionRegistry = sessionRegistry;
    this.presenceService = presenceService;
  }

  @Override
  public WebSocketHandler decorate(WebSocketHandler handler) {
    return new WebSocketHandlerDecorator(handler) {

      @Override
      public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        parseUserId(session)
            .ifPresent(
                userId -> {
                  sessionRegistry.register(userId, session);
                  presenceService.onConnect(userId);
                });
      }

      @Override
      public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus)
          throws Exception {
        UUID userId = sessionRegistry.unregister(session.getId());
        if (userId != null) {
          presenceService.onDisconnect(userId);
        }
        super.afterConnectionClosed(session, closeStatus);
      }

      private Optional<UUID> parseUserId(WebSocketSession session) {
        Principal principal = session.getPrincipal();
        if (principal == null) {
          return Optional.empty();
        }
        try {
          return Optional.of(UUID.fromString(principal.getName()));
        } catch (IllegalArgumentException e) {
          return Optional.empty();
        }
      }
    };
  }
}
