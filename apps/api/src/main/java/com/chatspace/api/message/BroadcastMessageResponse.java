package com.chatspace.api.message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * STOMPブロードキャスト用のメッセージDTO(レビュー指摘対応)。{@link MessageResponse}をそのままイベント配信に使うと、 操作者視点で計算された{@code
 * reactedByMe}が購読者全員に配信されてしまうため、{@link BroadcastReactionSummary}
 * に置き換えたものを配信する。REST応答(GETエンドポイント)は引き続き{@link MessageResponse}をそのまま返す (viewerごとに正しい{@code
 * reactedByMe}が必要なため)。
 */
public record BroadcastMessageResponse(
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
    List<BroadcastReactionSummary> reactions,
    List<MessageAttachmentResponse> attachments) {

  static BroadcastMessageResponse from(MessageResponse response) {
    return new BroadcastMessageResponse(
        response.id(),
        response.channelId(),
        response.dmId(),
        response.parentId(),
        response.authorId(),
        response.body(),
        response.deleted(),
        response.createdAt(),
        response.updatedAt(),
        response.editedAt(),
        response.replyCount(),
        response.reactions().stream().map(BroadcastReactionSummary::from).toList(),
        response.attachments());
  }
}
