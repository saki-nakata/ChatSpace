package com.chatspace.api.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** DB設計書§3.9(reactionsテーブル)に対応するエンティティ。 */
@Entity
@Table(
    name = "reactions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "user_id", "emoji"}))
public class Reaction {

  @Id private UUID id;

  @Column(name = "message_id", nullable = false)
  private UUID messageId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 32)
  private String emoji;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Reaction() {}

  public Reaction(UUID messageId, UUID userId, String emoji) {
    this.id = UUID.randomUUID();
    this.messageId = messageId;
    this.userId = userId;
    this.emoji = emoji;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getMessageId() {
    return messageId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getEmoji() {
    return emoji;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
