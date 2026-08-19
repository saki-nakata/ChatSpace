package com.chatspace.api.search;

import com.chatspace.api.message.Message;
import java.time.Instant;
import java.util.UUID;

/** 検索結果1件分のDTO(検索機能定義書§4)。検索クエリ自体が{@code deleted_at IS NULL}を課すため、tombstone表現は不要。 */
public record SearchResultItem(
    UUID id,
    UUID channelId,
    UUID dmId,
    UUID parentId,
    UUID authorId,
    String body,
    Instant createdAt) {

  static SearchResultItem from(Message message) {
    return new SearchResultItem(
        message.getId(),
        message.getChannelId(),
        message.getDmId(),
        message.getParentId(),
        message.getAuthorId(),
        message.getBody(),
        message.getCreatedAt());
  }
}
