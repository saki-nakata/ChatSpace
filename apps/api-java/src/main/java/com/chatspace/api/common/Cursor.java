package com.chatspace.api.common;

import java.time.Instant;
import java.util.UUID;

/** `(createdAt, id)` の複合カーソル(DB設計書§1.1)。メッセージ・スレッド返信・通知等のページングで共通利用する。 */
public record Cursor(Instant createdAt, UUID id) {

  public static Cursor of(Instant createdAt, UUID id) {
    return new Cursor(createdAt, id);
  }
}
