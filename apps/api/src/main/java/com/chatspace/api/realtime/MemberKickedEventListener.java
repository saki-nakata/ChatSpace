package com.chatspace.api.realtime;

import com.chatspace.api.audit.AuditLogger;
import java.io.IOException;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * キック確定後(DBコミット完了後)の強制切断(リアルタイム通信機能定義書§10.3)。
 *
 * <p>{@code @TransactionalEventListener(phase = AFTER_COMMIT)}により、(a)トランザクションがロールバックした場合は
 * 発火しない、(b)コミット前の再接続でまだ有効なメンバーシップを参照されてしまう競合を避ける、の両方を保証する。
 * 単一インスタンス前提のため、プロセスが落ちればセッション自体も同時に消えるので、これだけで整合性が閉じる
 * (水平スケール対応は実施しないことが確定しており、outboxパターン等の追加対応は不要)。
 */
@Component
public class MemberKickedEventListener {

  private final SessionRegistry sessionRegistry;
  private final SimpMessagingTemplate messagingTemplate;
  private final AuditLogger auditLogger;

  public MemberKickedEventListener(
      SessionRegistry sessionRegistry,
      SimpMessagingTemplate messagingTemplate,
      AuditLogger auditLogger) {
    this.sessionRegistry = sessionRegistry;
    this.messagingTemplate = messagingTemplate;
    this.auditLogger = auditLogger;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMemberKicked(MemberKickedEvent event) {
    // 切断前にベストエフォートでMEMBER_REMOVEDを送信する(§10.4)
    messagingTemplate.convertAndSendToUser(
        event.userId().toString(),
        StompDestinations.USER_EVENTS_DESTINATION,
        new RealtimeEvent("MEMBER_REMOVED", Map.of("userId", event.userId())));

    int closedSessionCount = 0;
    for (WebSocketSession session : sessionRegistry.sessionsForUser(event.userId())) {
      try {
        session.close(CloseStatus.POLICY_VIOLATION);
        closedSessionCount++;
      } catch (IOException e) {
        // ベストエフォート。失敗しても次回のSUBSCRIBEが削除済みメンバーシップにより拒否されるため致命的ではない
      }
    }
    // ログ運用設計書§5: キック成功ログと強制切断がペアで記録されていることを運用時に確認できるようにする
    auditLogger.memberKicked(event.userId(), closedSessionCount);
  }

  /**
   * キック処理がロールバックした場合の記録(ログ運用設計書§2)。強制切断は行わない。
   *
   * <p>{@code AFTER_COMMIT}の{@link #onMemberKicked}と対になるイベントを残すことで、運用時に
   * 「キックのログがあるのに切断ログが無い」状態を、異常ではなくロールバックとして判別できるようにする。
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
  public void onMemberKickRolledBack(MemberKickedEvent event) {
    auditLogger.memberKickRolledBack(event.userId());
  }
}
