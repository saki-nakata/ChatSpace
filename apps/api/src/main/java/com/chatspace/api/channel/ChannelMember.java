package com.chatspace.api.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** DB設計書§3.5(channel_membersテーブル)に対応するエンティティ。 */
@Entity
@Table(
    name = "channel_members",
    uniqueConstraints = @UniqueConstraint(columnNames = {"channel_id", "user_id"}))
public class ChannelMember {

  @Id private UUID id;

  @Column(name = "channel_id", nullable = false)
  private UUID channelId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  @Column(name = "last_read_at", nullable = false)
  private Instant lastReadAt;

  protected ChannelMember() {}

  public ChannelMember(UUID channelId, UUID userId) {
    this.id = UUID.randomUUID();
    this.channelId = channelId;
    this.userId = userId;
    this.joinedAt = Instant.now();
    this.lastReadAt = this.joinedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getChannelId() {
    return channelId;
  }

  public UUID getUserId() {
    return userId;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public Instant getLastReadAt() {
    return lastReadAt;
  }

  public void markRead(Instant readAt) {
    this.lastReadAt = readAt;
  }
}
