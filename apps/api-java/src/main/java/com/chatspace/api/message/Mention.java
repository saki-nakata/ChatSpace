package com.chatspace.api.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * DB設計書§3.10(mentionsテーブル)に対応するエンティティ。
 *
 * <p>{@code
 * MentionResolver}(フェーズ5)は、投稿時点のライブなチャンネルメンバーシップとのみ突合してレコードを作成する(非メンバーへの通知・存在漏洩防止、§3.10注記)。
 */
@Entity
@Table(name = "mentions")
public class Mention {

  @Id private UUID id;

  @Column(name = "message_id", nullable = false)
  private UUID messageId;

  @Column(name = "mentioned_user_id", nullable = false)
  private UUID mentionedUserId;

  protected Mention() {}

  public Mention(UUID messageId, UUID mentionedUserId) {
    this.id = UUID.randomUUID();
    this.messageId = messageId;
    this.mentionedUserId = mentionedUserId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getMessageId() {
    return messageId;
  }

  public UUID getMentionedUserId() {
    return mentionedUserId;
  }
}
