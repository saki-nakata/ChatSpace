package com.chatspace.api.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * DB設計書§3.8(attachmentsテーブル)に対応するエンティティ。
 *
 * <p>{@code messageId} はアップロード直後・未紐付け時はnull。配信エンドポイントは取得の都度ライブな権限チェックを再実行する(§3.8注記)。
 */
@Entity
@Table(name = "attachments")
public class Attachment {

  @Id private UUID id;

  @Column(name = "message_id")
  private UUID messageId;

  @Column(name = "uploader_id", nullable = false)
  private UUID uploaderId;

  @Column(name = "storage_key", nullable = false, unique = true, length = 255)
  private String storageKey;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "mime_type", nullable = false, length = 64)
  private String mimeType;

  @Column(name = "size_bytes", nullable = false)
  private int sizeBytes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AttachmentKind kind;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Attachment() {}

  public Attachment(
      UUID uploaderId,
      String storageKey,
      String fileName,
      String mimeType,
      int sizeBytes,
      AttachmentKind kind) {
    this.id = UUID.randomUUID();
    this.uploaderId = uploaderId;
    this.storageKey = storageKey;
    this.fileName = fileName;
    this.mimeType = mimeType;
    this.sizeBytes = sizeBytes;
    this.kind = kind;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getMessageId() {
    return messageId;
  }

  public UUID getUploaderId() {
    return uploaderId;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getFileName() {
    return fileName;
  }

  public String getMimeType() {
    return mimeType;
  }

  public int getSizeBytes() {
    return sizeBytes;
  }

  public AttachmentKind getKind() {
    return kind;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
