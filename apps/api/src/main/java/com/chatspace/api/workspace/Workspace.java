package com.chatspace.api.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** DB設計書§3.2(workspacesテーブル)に対応するエンティティ。 */
@Entity
@Table(name = "workspaces")
public class Workspace {

  @Id private UUID id;

  @Column(nullable = false, length = 64)
  private String name;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Workspace() {}

  public Workspace(String name, UUID ownerId) {
    this.id = UUID.randomUUID();
    this.name = name;
    this.ownerId = ownerId;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
