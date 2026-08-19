package com.chatspace.api.channel;

import java.time.Instant;
import java.util.UUID;

public record ChannelResponse(
    UUID id,
    UUID workspaceId,
    String name,
    ChannelType type,
    Instant createdAt,
    boolean isMember,
    long unreadCount) {

  public static ChannelResponse from(Channel channel, boolean isMember, long unreadCount) {
    return new ChannelResponse(
        channel.getId(),
        channel.getWorkspaceId(),
        channel.getName(),
        channel.getType(),
        channel.getCreatedAt(),
        isMember,
        unreadCount);
  }
}
