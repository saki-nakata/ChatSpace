package com.chatspace.api.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

  Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

  boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

  List<WorkspaceMember> findByWorkspaceIdOrderByJoinedAtAsc(UUID workspaceId);

  List<WorkspaceMember> findByUserIdOrderByJoinedAtAsc(UUID userId);

  void deleteByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
