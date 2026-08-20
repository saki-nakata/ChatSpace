package com.chatspace.api.realtime;

import com.chatspace.api.audit.AuditLogger;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link SimpMessagingTemplate}のラッパー。チャンネル/DM/ワークスペーストピックへのイベント発行口を集約する (リアルタイム通信機能定義書§4.1・§4.2)。
 *
 * <p><b>配信タイミング(レビュー指摘対応)</b>: 呼び出し元(各Service)は{@code @Transactional}メソッドの内側から
 * このクラスのメソッドを呼ぶが、実際の配信はトランザクション同期が有効な場合{@code AFTER_COMMIT}まで遅延する。
 * これにより、コミットが失敗(ロールバック)した際にDBに存在しないメッセージ等がクライアントへ配信されてしまう 不整合を防ぐ。キック時の強制切断が{@code
 * MemberKickedEventListener}で既に{@code AFTER_COMMIT}にしているのと
 * 同じ設計意図を、呼び出し側を一切変更せずにこのクラス1箇所へ集約する形で全配信経路に適用する。
 */
@Component
public class RealtimeEventPublisher {

  private final SimpMessagingTemplate messagingTemplate;
  private final AuditLogger auditLogger;

  public RealtimeEventPublisher(SimpMessagingTemplate messagingTemplate, AuditLogger auditLogger) {
    this.auditLogger = auditLogger;
    this.messagingTemplate = messagingTemplate;
  }

  public void messageCreated(UUID channelId, UUID dmId, Object payload) {
    sendToConversation(channelId, dmId, "MESSAGE_CREATED", payload);
  }

  public void messageUpdated(UUID channelId, UUID dmId, Object payload) {
    sendToConversation(channelId, dmId, "MESSAGE_UPDATED", payload);
  }

  public void messageDeleted(UUID channelId, UUID dmId, Object payload) {
    sendToConversation(channelId, dmId, "MESSAGE_DELETED", payload);
  }

  public void reactionUpdated(UUID channelId, UUID dmId, Object payload) {
    sendToConversation(channelId, dmId, "REACTION_UPDATED", payload);
  }

  public void channelCreated(UUID workspaceId, Object payload) {
    sendToWorkspace(workspaceId, "CHANNEL_CREATED", payload);
  }

  /**
   * プライベートチャンネル作成時、ワークスペース全体へブロードキャストせず実メンバーの個人キューにのみ配信する (レビュー指摘:
   * 無条件にワークスペーストピックへ送るとチャンネル名・存在が非参加メンバーに漏洩する)。
   */
  public void channelCreatedForUser(UUID targetUserId, Object payload) {
    sendToUser(targetUserId, "CHANNEL_CREATED", payload);
  }

  public void channelDeleted(UUID workspaceId, Object payload) {
    sendToWorkspace(workspaceId, "CHANNEL_DELETED", payload);
  }

  /** プライベートチャンネル削除時、実メンバーの個人キューにのみ配信する(上記{@link #channelCreatedForUser}と同じ理由)。 */
  public void channelDeletedForUser(UUID targetUserId, Object payload) {
    sendToUser(targetUserId, "CHANNEL_DELETED", payload);
  }

  /**
   * 新規DMスレッド作成を相手ユーザー個人宛に通知する。
   *
   * <p>計画書§4.2の一覧表ではDM_THREAD_CREATEDをワークスペーストピックのペイロードとして分類しているが、 それをそのまま{@code
   * /topic/workspaces.{id}}へブロードキャストすると「誰と誰がDMを始めたか」という
   * プライベートな関係情報がワークスペースの全メンバーに漏洩してしまう(DM機能定義書§6の404-not-403方針・ 存在秘匿の原則と矛盾する)。そのため実装では個人宛キュー({@code
   * /user/queue/events}相当)経由で 相手ユーザーにのみ配信するよう修正した(フェーズ4での実装時に発見した設計矛盾への対応)。
   */
  public void dmThreadCreatedForUser(UUID targetUserId, Object payload) {
    sendToUser(targetUserId, "DM_THREAD_CREATED", payload);
  }

  /**
   * メンション・DM・招待・スレッド返信の各通知を本人の個人キューへ配信する(通知機能定義書§3.2)。
   *
   * <p>配信はDBコミット後の副作用であり、ここで例外を投げても通知レコード自体は既に永続化されている。
   * 呼び出し元の処理を巻き添えで失敗させないよう例外は握るが、握りつぶすと「通知が届かない」障害が 誰にも気付かれないため、監査ログへ必ず記録する(ログ運用設計書§2「通知配信失敗」)。
   *
   * @param notificationType 通知種別({@code NotificationType}の名前)。本文は渡さないこと
   */
  public void notification(UUID recipientUserId, String notificationType, Object payload) {
    dispatch(
        () -> {
          try {
            messagingTemplate.convertAndSendToUser(
                recipientUserId.toString(),
                StompDestinations.USER_EVENTS_DESTINATION,
                new RealtimeEvent("NOTIFICATION", payload));
          } catch (RuntimeException ex) {
            auditLogger.notificationDeliveryFailed(
                recipientUserId, notificationType, ex.getClass().getSimpleName());
          }
        });
  }

  public void channelMemberKicked(UUID workspaceId, Object payload) {
    sendToWorkspace(workspaceId, "CHANNEL_MEMBER_KICKED", payload);
  }

  /** プライベートチャンネルからのキック時、残存メンバーの個人キューにのみ配信する(上記と同じ理由)。 */
  public void channelMemberKickedForUser(UUID targetUserId, Object payload) {
    sendToUser(targetUserId, "CHANNEL_MEMBER_KICKED", payload);
  }

  public void workspaceMemberKicked(UUID workspaceId, Object payload) {
    sendToWorkspace(workspaceId, "WORKSPACE_MEMBER_KICKED", payload);
  }

  private void sendToConversation(UUID channelId, UUID dmId, String type, Object payload) {
    String destination =
        channelId != null
            ? StompDestinations.channelTopic(channelId)
            : StompDestinations.dmTopic(dmId);
    dispatch(() -> messagingTemplate.convertAndSend(destination, new RealtimeEvent(type, payload)));
  }

  private void sendToWorkspace(UUID workspaceId, String type, Object payload) {
    dispatch(
        () ->
            messagingTemplate.convertAndSend(
                StompDestinations.workspaceTopic(workspaceId), new RealtimeEvent(type, payload)));
  }

  private void sendToUser(UUID userId, String type, Object payload) {
    dispatch(
        () ->
            messagingTemplate.convertAndSendToUser(
                userId.toString(),
                StompDestinations.USER_EVENTS_DESTINATION,
                new RealtimeEvent(type, payload)));
  }

  /** トランザクション同期が有効な場合はコミット後まで配信を遅延し、無効な場合(トランザクション外からの呼び出し)は 即時配信する。呼び出し元のServiceは何も変更する必要がない。 */
  private void dispatch(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }
}
