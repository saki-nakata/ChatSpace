package com.chatspace.api.realtime;

import com.chatspace.api.auth.JwtService;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocketハンドシェイク時にCookie({@code chatspace_token})のJWTを検証する(リアルタイム通信機能定義書§5)。
 *
 * <p>RESTの{@code JwtAuthenticationFilter}(欠如・不正時は空のSecurityContextで後続へ委譲)とは異なり、WebSocket
 * ハンドシェイクは認証必須の入口であるため、欠如・不正・期限切れの場合は即時ハンドシェイクを拒否する(§14)。
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

  static final String USER_ID_ATTRIBUTE = "userId";

  private static final String COOKIE_NAME = "chatspace_token";

  private final JwtService jwtService;

  public WebSocketAuthInterceptor(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    Optional<UUID> userId = extractToken(request).flatMap(jwtService::verify);
    if (userId.isEmpty()) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
    attributes.put(USER_ID_ATTRIBUTE, userId.get());
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {
    // 何もしない
  }

  private Optional<String> extractToken(ServerHttpRequest request) {
    if (!(request instanceof ServletServerHttpRequest servletRequest)) {
      return Optional.empty();
    }
    Cookie[] cookies = servletRequest.getServletRequest().getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    return Arrays.stream(cookies)
        .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst();
  }
}
