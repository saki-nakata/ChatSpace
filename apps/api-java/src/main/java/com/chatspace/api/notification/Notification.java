package com.chatspace.api.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * DB設計書§3.11(notificationsテーブル)に対応するエンティティ。
 *
 * <p>一覧取得・未読件数・既読化の全エンドポイントで、{@code channelId}/{@code dmId}/{@code workspaceId}
 * に対する現在のライブなメンバーシップをAND結合で再検証すること(通知のスコープ漏洩防止、§3.11注記。フェーズ5で実装)。
 */
@Entity
@Table(name = "notifications")
public class Notification {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationType type;

  @Column(name = "workspace_id")
  private UUID workspaceId;

  @Column(name = "channel_id")
  private UUID channelId;

  @Column(name = "dm_id")
  private UUID dmId;

  @Column(name = "message_id")
  private UUID messageId;

  @Column(name = "thread_parent_id")
  private UUID threadParentId;

  @Column(name = "from_user_id")
  private UUID fromUserId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String text;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "read_at")
  private Instant readAt;

  protected Notification() {}

  public Notification(
      UUID userId,
      NotificationType type,
      UUID workspaceId,
      UUID channelId,
      UUID dmId,
      UUID messageId,
      UUID threadParentId,
      UUID fromUserId,
      String text) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.type = type;
    this.workspaceId = workspaceId;
    this.channelId = channelId;
    this.dmId = dmId;
    this.messageId = messageId;
    this.threadParentId = threadParentId;
    this.fromUserId = fromUserId;
    this.text = text;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public NotificationType getType() {
    return type;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getChannelId() {
    return channelId;
  }

  public UUID getDmId() {
    return dmId;
  }

  public UUID getMessageId() {
    return messageId;
  }

  public UUID getThreadParentId() {
    return threadParentId;
  }

  public UUID getFromUserId() {
    return fromUserId;
  }

  public String getText() {
    return text;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public void markRead(Instant readAt) {
    this.readAt = readAt;
  }
}
