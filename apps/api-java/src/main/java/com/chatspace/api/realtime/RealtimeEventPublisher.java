package com.chatspace.api.realtime;

import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link SimpMessagingTemplate}のラッパー。チャンネル/DM/ワークスペーストピックへのイベント発行口を集約する (リアルタイム通信機能定義書§4.1・§4.2)。
 */
@Component
public class RealtimeEventPublisher {

  private final SimpMessagingTemplate messagingTemplate;

  public RealtimeEventPublisher(SimpMessagingTemplate messagingTemplate) {
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

  public void channelDeleted(UUID workspaceId, Object payload) {
    sendToWorkspace(workspaceId, "CHANNEL_DELETED", payload);
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

  public void channelMemberKicked(UUID workspaceId, Object payload) {
    sendToWorkspace(workspaceId, "CHANNEL_MEMBER_KICKED", payload);
  }

  public void workspaceMemberKicked(UUID workspaceId, Object payload) {
    sendToWorkspace(workspaceId, "WORKSPACE_MEMBER_KICKED", payload);
  }

  private void sendToConversation(UUID channelId, UUID dmId, String type, Object payload) {
    String destination =
        channelId != null
            ? StompDestinations.channelTopic(channelId)
            : StompDestinations.dmTopic(dmId);
    messagingTemplate.convertAndSend(destination, new RealtimeEvent(type, payload));
  }

  private void sendToWorkspace(UUID workspaceId, String type, Object payload) {
    messagingTemplate.convertAndSend(
        StompDestinations.workspaceTopic(workspaceId), new RealtimeEvent(type, payload));
  }

  private void sendToUser(UUID userId, String type, Object payload) {
    messagingTemplate.convertAndSendToUser(
        userId.toString(),
        StompDestinations.USER_EVENTS_DESTINATION,
        new RealtimeEvent(type, payload));
  }
}
