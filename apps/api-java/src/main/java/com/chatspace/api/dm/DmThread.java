package com.chatspace.api.dm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * DB設計書§3.6(dm_threadsテーブル)に対応するエンティティ。
 *
 * <p>{@code userAId} は常に {@code userBId} より小さいUUIDになるよう正規化して保存する(呼び出し側の責務。DM機能定義書参照)。
 */
@Entity
@Table(
    name = "dm_threads",
    uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "user_a_id", "user_b_id"}))
public class DmThread {

  @Id private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @Column(name = "user_a_id", nullable = false)
  private UUID userAId;

  @Column(name = "user_b_id", nullable = false)
  private UUID userBId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "last_read_at_a", nullable = false)
  private Instant lastReadAtA;

  @Column(name = "last_read_at_b", nullable = false)
  private Instant lastReadAtB;

  protected DmThread() {}

  public DmThread(UUID workspaceId, UUID userAId, UUID userBId) {
    this.id = UUID.randomUUID();
    this.workspaceId = workspaceId;
    this.userAId = userAId;
    this.userBId = userBId;
    this.createdAt = Instant.now();
    this.lastReadAtA = this.createdAt;
    this.lastReadAtB = this.createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getUserAId() {
    return userAId;
  }

  public UUID getUserBId() {
    return userBId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getLastReadAtA() {
    return lastReadAtA;
  }

  public Instant getLastReadAtB() {
    return lastReadAtB;
  }

  /** {@code userId} が userA/userB のどちらかに応じて、該当する既読位置を更新する(DM機能定義書§3.3)。 */
  public void markRead(UUID userId, Instant readAt) {
    if (userId.equals(userAId)) {
      this.lastReadAtA = readAt;
    } else if (userId.equals(userBId)) {
      this.lastReadAtB = readAt;
    }
  }
}
