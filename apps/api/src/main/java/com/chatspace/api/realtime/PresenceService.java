package com.chatspace.api.realtime;

import com.chatspace.api.workspace.WorkspaceMember;
import com.chatspace.api.workspace.WorkspaceMemberRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * プレゼンス(オンライン/オフライン)管理(リアルタイム通信機能定義書§11)。
 *
 * <p>単一インスタンス前提のため、接続数を{@code Map<UUID, Integer>}でローカル追跡する(複数タブ/デバイス対応)。
 * 最初の接続でオンラインに遷移し、全接続が切れた時点でオフラインに遷移する。水平スケール対応(Redis共有プレゼンスへの 切替)は検討の結果実施しないことが確定している。
 *
 * <p>{@code SimpMessagingTemplate}は{@code @Lazy}で注入する: 本クラスは{@code
 * RealtimeWebSocketHandlerDecoratorFactory}経由で{@code WebSocketConfig}({@code
 * WebSocketMessageBrokerConfigurer})から参照されるが、{@code SimpMessagingTemplate}自体の生成処理は 全{@code
 * WebSocketMessageBrokerConfigurer}を先に収集するため、即時注入すると循環依存 (BeanCurrentlyInCreationException)になる。
 */
@Service
public class PresenceService {

  private final Map<UUID, Integer> connectionCounts = new ConcurrentHashMap<>();
  private final WorkspaceMemberRepository workspaceMemberRepository;
  private final SimpMessagingTemplate messagingTemplate;

  public PresenceService(
      WorkspaceMemberRepository workspaceMemberRepository,
      @Lazy SimpMessagingTemplate messagingTemplate) {
    this.workspaceMemberRepository = workspaceMemberRepository;
    this.messagingTemplate = messagingTemplate;
  }

  public boolean isOnline(UUID userId) {
    return connectionCounts.containsKey(userId);
  }

  public void onConnect(UUID userId) {
    int newCount = connectionCounts.merge(userId, 1, Integer::sum);
    if (newCount == 1) {
      broadcastPresence(userId, true);
    }
  }

  public void onDisconnect(UUID userId) {
    AtomicBoolean becameOffline = new AtomicBoolean(false);
    connectionCounts.computeIfPresent(
        userId,
        (id, count) -> {
          if (count <= 1) {
            becameOffline.set(true);
            return null;
          }
          return count - 1;
        });
    if (becameOffline.get()) {
      broadcastPresence(userId, false);
    }
  }

  private void broadcastPresence(UUID userId, boolean online) {
    List<WorkspaceMember> memberships =
        workspaceMemberRepository.findByUserIdOrderByJoinedAtAsc(userId);
    RealtimeEvent event =
        new RealtimeEvent("PRESENCE_UPDATED", new PresencePayload(userId, online));
    memberships.forEach(
        m ->
            messagingTemplate.convertAndSend(
                StompDestinations.workspacePresenceTopic(m.getWorkspaceId()), event));
  }

  private record PresencePayload(UUID userId, boolean online) {}
}
