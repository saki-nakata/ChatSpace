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
    List<ReactionSummary> reactions) {

  static MessageResponse from(Message message, List<ReactionSummary> reactions, long replyCount) {
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
        reactions);
  }
}
