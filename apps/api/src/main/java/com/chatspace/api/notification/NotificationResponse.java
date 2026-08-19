package com.chatspace.api.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    NotificationType type,
    UUID workspaceId,
    UUID channelId,
    UUID dmId,
    UUID messageId,
    UUID threadParentId,
    UUID fromUserId,
    String text,
    Instant createdAt,
    Instant readAt) {

  static NotificationResponse from(Notification notification) {
    return new NotificationResponse(
        notification.getId(),
        notification.getType(),
        notification.getWorkspaceId(),
        notification.getChannelId(),
        notification.getDmId(),
        notification.getMessageId(),
        notification.getThreadParentId(),
        notification.getFromUserId(),
        notification.getText(),
        notification.getCreatedAt(),
        notification.getReadAt());
  }
}
