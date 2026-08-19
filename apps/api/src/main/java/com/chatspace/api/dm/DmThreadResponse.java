package com.chatspace.api.dm;

import com.chatspace.api.user.User;
import com.chatspace.api.user.UserResponse;
import java.time.Instant;
import java.util.UUID;

public record DmThreadResponse(
    UUID id,
    UserResponse otherUser,
    Instant createdAt,
    long unreadCount,
    String lastMessagePreview) {

  public static DmThreadResponse of(
      DmThread thread, User otherUser, long unreadCount, String lastMessagePreview) {
    return new DmThreadResponse(
        thread.getId(),
        UserResponse.from(otherUser),
        thread.getCreatedAt(),
        unreadCount,
        lastMessagePreview);
  }
}
