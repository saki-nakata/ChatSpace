package com.chatspace.api.message;

import java.time.Instant;
import java.util.UUID;

/** `(createdAt, id)` の複合カーソル(DB設計書§1.1・メッセージング機能定義書§6.6)。 */
public record Cursor(Instant createdAt, UUID id) {

  static Cursor of(Message message) {
    return new Cursor(message.getCreatedAt(), message.getId());
  }
}
