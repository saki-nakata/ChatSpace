package com.chatspace.api.channel;

import com.chatspace.api.user.User;
import com.chatspace.api.user.UserResponse;
import java.time.Instant;

public record ChannelMemberResponse(UserResponse user, Instant joinedAt) {

  public static ChannelMemberResponse from(ChannelMember member, User user) {
    return new ChannelMemberResponse(UserResponse.from(user), member.getJoinedAt());
  }
}
