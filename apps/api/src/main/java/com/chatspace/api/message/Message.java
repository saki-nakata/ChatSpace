package com.chatspace.api.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DB設計書§3.7(messagesテーブル)に対応するエンティティ。
 *
 * <p>{@code channelId}/{@code dmId} はどちらか一方のみが非nullになる(排他的論理和、DB側のCHECK制約で強制)。スレッド返信は {@code
 * parent}/{@code replies} の自己参照で表現する(計画書§2)。ソフトデリートは {@code deletedAt} のnullable
 * カラムで表現し、検索クエリのみ除外対象とする(一覧・スレッド・コンテキスト取得はtombstoneとして含める。DB設計書§1.1)。
 */
@Entity
@Table(name = "messages")
public class Message {

  @Id private UUID id;

  @Column(name = "channel_id")
  private UUID channelId;

  @Column(name = "dm_id")
  private UUID dmId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private Message parent;

  @OneToMany(mappedBy = "parent")
  private List<Message> replies = new ArrayList<>();

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "edited_at")
  private Instant editedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  protected Message() {}

  public Message(UUID channelId, UUID dmId, Message parent, UUID authorId, String body) {
    this.id = UUID.randomUUID();
    this.channelId = channelId;
    this.dmId = dmId;
    this.parent = parent;
    this.authorId = authorId;
    this.body = body;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getChannelId() {
    return channelId;
  }

  public UUID getDmId() {
    return dmId;
  }

  public Message getParent() {
    return parent;
  }

  public List<Message> getReplies() {
    return replies;
  }

  public UUID getAuthorId() {
    return authorId;
  }

  public String getBody() {
    return body;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getEditedAt() {
    return editedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public UUID getParentId() {
    return parent == null ? null : parent.getId();
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void edit(String newBody, Instant editedAt) {
    this.body = newBody;
    this.editedAt = editedAt;
    this.updatedAt = editedAt;
  }

  public void markDeleted(Instant deletedAt) {
    this.deletedAt = deletedAt;
    this.updatedAt = deletedAt;
  }
}
