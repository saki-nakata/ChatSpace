package com.chatspace.api.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** DB設計書§3.4(channelsテーブル)に対応するエンティティ。 */
@Entity
@Table(
    name = "channels",
    uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "name"}))
public class Channel {

  @Id private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @Column(nullable = false, length = 80)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ChannelType type;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Channel() {}

  public Channel(UUID workspaceId, String name, ChannelType type) {
    this.id = UUID.randomUUID();
    this.workspaceId = workspaceId;
    this.name = name;
    this.type = type;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public String getName() {
    return name;
  }

  public ChannelType getType() {
    return type;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
