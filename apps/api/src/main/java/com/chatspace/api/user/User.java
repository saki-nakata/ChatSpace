package com.chatspace.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** DB設計書§3.1(usersテーブル)に対応するエンティティ。 */
@Entity
@Table(name = "users")
public class User {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false, unique = true, length = 32)
  private String userId;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "display_name", nullable = false, length = 64)
  private String displayName;

  @Column(name = "avatar_url", columnDefinition = "TEXT")
  private String avatarUrl;

  @Column(length = 128)
  private String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected User() {}

  public User(String userId, String passwordHash, String displayName) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.passwordHash = passwordHash;
    this.displayName = displayName;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public String getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  /** プロフィール編集(S-14)。nullのフィールドは更新しない(部分更新、呼び出し元のコメント参照)。 */
  public void updateProfile(String displayName, String status, String avatarUrl) {
    if (displayName != null) this.displayName = displayName;
    if (status != null) this.status = status;
    if (avatarUrl != null) this.avatarUrl = avatarUrl;
  }
}
