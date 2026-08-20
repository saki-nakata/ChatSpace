package com.chatspace.api.realtime;

import com.chatspace.api.audit.AuditLogger;
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
  private final AuditLogger auditLogger;

  public WebSocketAuthInterceptor(JwtService jwtService, AuditLogger auditLogger) {
    this.jwtService = jwtService;
    this.auditLogger = auditLogger;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    Optional<String> token = extractToken(request);
    Optional<UUID> userId = token.flatMap(jwtService::verify);
    if (userId.isEmpty()) {
      // 拒否理由は「Cookie欠如」と「JWT不正/期限切れ」を区別する(運用時に設定不備と攻撃を切り分けるため)。
      // トークン本体は決してログに残さない(ログ運用設計書§1.4)
      auditLogger.stompHandshakeRejected(
          remoteAddress(request),
          originHeader(request),
          token.isEmpty() ? "missing_cookie" : "invalid_token");
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

  private String remoteAddress(ServerHttpRequest request) {
    return request.getRemoteAddress() == null
        ? "unknown"
        : request.getRemoteAddress().getAddress().getHostAddress();
  }

  private String originHeader(ServerHttpRequest request) {
    String origin = request.getHeaders().getOrigin();
    return origin == null ? "unknown" : origin;
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
