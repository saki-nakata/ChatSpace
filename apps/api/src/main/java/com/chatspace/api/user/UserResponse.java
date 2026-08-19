package com.chatspace.api.user;

import java.time.Instant;
import java.util.UUID;

/** パスワードハッシュを含まない、外部公開用のユーザー表現。 */
public record UserResponse(
    UUID id,
    String userId,
    String displayName,
    String avatarUrl,
    String status,
    Instant createdAt) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getUserId(),
        user.getDisplayName(),
        user.getAvatarUrl(),
        user.getStatus(),
        user.getCreatedAt());
  }
}
