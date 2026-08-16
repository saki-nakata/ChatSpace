package com.chatspace.api.realtime;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * {@link WebSocketAuthInterceptor}がハンドシェイク属性にセットした内部ユーザーIDから{@link Principal}を組み立てる
 * (リアルタイム通信機能定義書§5)。{@code Principal#getName()}が内部ユーザーID(UUID文字列)になるようにし、 {@code
 * convertAndSendToUser()}呼び出し時に渡すユーザーIDと一致させる(§7.1、Principal名の一致保証)。
 */
public class UserIdHandshakeHandler extends DefaultHandshakeHandler {

  @Override
  protected Principal determineUser(
      ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
    Object userId = attributes.get(WebSocketAuthInterceptor.USER_ID_ATTRIBUTE);
    if (userId == null) {
      return null;
    }
    String userIdString = userId.toString();
    return () -> userIdString;
  }
}
