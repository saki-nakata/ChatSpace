package com.chatspace.api.workspace;

import com.chatspace.api.user.User;
import com.chatspace.api.user.UserResponse;
import java.time.Instant;

public record WorkspaceMemberResponse(UserResponse user, WorkspaceRole role, Instant joinedAt) {

  public static WorkspaceMemberResponse from(WorkspaceMember member, User user) {
    return new WorkspaceMemberResponse(
        UserResponse.from(user), member.getRole(), member.getJoinedAt());
  }
}
