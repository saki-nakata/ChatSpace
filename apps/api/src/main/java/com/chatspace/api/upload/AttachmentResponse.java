package com.chatspace.api.upload;

import com.chatspace.api.message.Attachment;
import com.chatspace.api.message.AttachmentKind;
import java.util.UUID;

/** 添付ファイル機能定義書§4のレスポンススキーマに対応する。 */
public record AttachmentResponse(
    UUID id, String url, String fileName, String mimeType, int sizeBytes, AttachmentKind kind) {

  static AttachmentResponse from(Attachment attachment) {
    return new AttachmentResponse(
        attachment.getId(),
        "/uploads/" + attachment.getStorageKey(),
        attachment.getFileName(),
        attachment.getMimeType(),
        attachment.getSizeBytes(),
        attachment.getKind());
  }
}
