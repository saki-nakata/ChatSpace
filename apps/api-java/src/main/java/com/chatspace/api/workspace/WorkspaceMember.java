package com.chatspace.api.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** DB設計書§3.3(workspace_membersテーブル)に対応するエンティティ。 */
@Entity
@Table(
    name = "workspace_members",
    uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "user_id"}))
public class WorkspaceMember {

  @Id private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private WorkspaceRole role;

  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  protected WorkspaceMember() {}

  public WorkspaceMember(UUID workspaceId, UUID userId, WorkspaceRole role) {
    this.id = UUID.randomUUID();
    this.workspaceId = workspaceId;
    this.userId = userId;
    this.role = role;
    this.joinedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getUserId() {
    return userId;
  }

  public WorkspaceRole getRole() {
    return role;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }
}
