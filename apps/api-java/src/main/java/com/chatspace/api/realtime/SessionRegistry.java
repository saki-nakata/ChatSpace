package com.chatspace.api.realtime;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * 接続中のWebSocketセッションをユーザーID単位で追跡する(リアルタイム通信機能定義書§10.2)。
 *
 * <p>キック時の強制切断(接続単位)に使う。単一インスタンス前提のためローカルメモリ上の{@code Map}で完結する。
 */
@Component
public class SessionRegistry {

  private final Map<String, WebSocketSession> sessionsById = new ConcurrentHashMap<>();
  private final Map<String, UUID> userIdBySessionId = new ConcurrentHashMap<>();
  private final Map<UUID, Set<String>> sessionIdsByUserId = new ConcurrentHashMap<>();

  public void register(UUID userId, WebSocketSession session) {
    sessionsById.put(session.getId(), session);
    userIdBySessionId.put(session.getId(), userId);
    sessionIdsByUserId
        .computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet())
        .add(session.getId());
  }

  /** セッションの登録を解除し、紐づいていたユーザーIDを返す(見つからない場合はnull)。 */
  public UUID unregister(String sessionId) {
    sessionsById.remove(sessionId);
    UUID userId = userIdBySessionId.remove(sessionId);
    if (userId != null) {
      sessionIdsByUserId.computeIfPresent(
          userId,
          (key, sessionIds) -> {
            sessionIds.remove(sessionId);
            return sessionIds.isEmpty() ? null : sessionIds;
          });
    }
    return userId;
  }

  public List<WebSocketSession> sessionsForUser(UUID userId) {
    return sessionIdsByUserId.getOrDefault(userId, Set.of()).stream()
        .map(sessionsById::get)
        .filter(Objects::nonNull)
        .toList();
  }
}
