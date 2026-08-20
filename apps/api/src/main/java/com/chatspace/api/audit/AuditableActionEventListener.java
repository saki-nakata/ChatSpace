package com.chatspace.api.audit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link AuditableActionEvent}をDBコミット後にのみ監査ログへ書き出す(フェーズ12、レビュー指摘対応)。
 *
 * <p>{@code AFTER_COMMIT}に限定することで、「ロールバックされたのに成功ログだけが残る」不整合を防ぐ。
 */
@Component
public class AuditableActionEventListener {

  private final AuditLogger auditLogger;

  public AuditableActionEventListener(AuditLogger auditLogger) {
    this.auditLogger = auditLogger;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onAuditableAction(AuditableActionEvent event) {
    if (event.ownerOnly()) {
      auditLogger.ownerActionSucceeded(
          event.actorUserId(), event.workspaceId(), event.action(), event.targetResourceId());
    } else {
      auditLogger.memberActionSucceeded(
          event.actorUserId(), event.workspaceId(), event.action(), event.targetResourceId());
    }
  }
}
