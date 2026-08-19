package com.chatspace.api.message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * メッセージのDTO。ソフトデリート済みの場合は {@code deleted=true} とし、{@code body} は空文字に置き換える(tombstone方式、
 * メッセージング機能定義書§3.3・§6.3)。
 */
public record MessageResponse(
    UUID id,
    UUID channelId,
    UUID dmId,
    UUID parentId,
    UUID authorId,
    String body,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt,
    Instant editedAt,
    long replyCount,
    List<ReactionSummary> reactions,
    List<MessageAttachmentResponse> attachments) {

  static MessageResponse from(
      Message message,
      List<ReactionSummary> reactions,
      long replyCount,
      List<MessageAttachmentResponse> attachments) {
    return new MessageResponse(
        message.getId(),
        message.getChannelId(),
        message.getDmId(),
        message.getParentId(),
        message.getAuthorId(),
        message.isDeleted() ? "" : message.getBody(),
        message.isDeleted(),
        message.getCreatedAt(),
        message.getUpdatedAt(),
        message.getEditedAt(),
        replyCount,
        reactions,
        // 削除済みメッセージは本文同様に添付ファイルも隠す(tombstone方式、§3.3)
        message.isDeleted() ? List.of() : attachments);
  }
}
