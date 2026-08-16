package com.chatspace.api.message;

import java.util.UUID;

/**
 * {@link MessageResponse}に埋め込む添付ファイルDTO。{@code upload.AttachmentResponse}と項目は同一だが、 {@code
 * upload}パッケージが既に{@code message}パッケージに依存しているため、逆方向の依存(循環)を避けて 本パッケージ内に別途定義する。
 */
public record MessageAttachmentResponse(
    UUID id, String url, String fileName, String mimeType, int sizeBytes, AttachmentKind kind) {

  static MessageAttachmentResponse from(Attachment attachment) {
    return new MessageAttachmentResponse(
        attachment.getId(),
        "/uploads/" + attachment.getStorageKey(),
        attachment.getFileName(),
        attachment.getMimeType(),
        attachment.getSizeBytes(),
        attachment.getKind());
  }
}
