package com.chatspace.api.dm;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DmThreadRepository extends JpaRepository<DmThread, UUID> {

  Optional<DmThread> findByWorkspaceIdAndUserAIdAndUserBId(
      UUID workspaceId, UUID userAId, UUID userBId);

  @Query(
      "SELECT dt FROM DmThread dt WHERE dt.workspaceId = :workspaceId AND (dt.userAId = :userId"
          + " OR dt.userBId = :userId) ORDER BY dt.createdAt DESC")
  List<DmThread> findAllForUser(
      @Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);
}
