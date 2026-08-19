package com.chatspace.api.workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(
    UUID id, String name, UUID ownerId, Instant createdAt, WorkspaceRole myRole) {

  public static WorkspaceResponse from(Workspace workspace, WorkspaceRole myRole) {
    return new WorkspaceResponse(
        workspace.getId(),
        workspace.getName(),
        workspace.getOwnerId(),
        workspace.getCreatedAt(),
        myRole);
  }
}
